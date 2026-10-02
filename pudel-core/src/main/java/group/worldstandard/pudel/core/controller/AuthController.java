/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 World Standard Group
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed with an additional permission known as the
 * "Pudel Plugin Exception".
 *
 * See the LICENSE and PLUGIN_EXCEPTION files in the project root for details.
 */
package group.worldstandard.pudel.core.controller;

import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import group.worldstandard.pudel.core.dto.OAuthCallbackRequest;
import group.worldstandard.pudel.core.dto.OAuthCallbackResponse;
import group.worldstandard.pudel.core.service.AuthService;
import group.worldstandard.pudel.core.service.DPoPKeyManager;
import group.worldstandard.pudel.core.session.SessionCookieService;
import jakarta.servlet.http.HttpServletResponse;

/**
 * REST controller for authentication endpoints.
 * Supports DPoP (Demonstrating Proof-of-Possession) for enhanced token security.
 * <p>
 * <b>BFF (Backend-for-Frontend) DPoP Approach:</b>
 * <p>
 * In this implementation, we use a BFF approach to enhance security against XSS attacks:
 * <ol>
 *   <li>Backend generates and manages DPoP keypair per user session</li>
 *   <li>Frontend requests public key from /api/dpop/public-key endpoint</li>
 *   <li>Frontend creates DPoP proof payload (jti, htm, htu, iat, ath)</li>
 *   <li>Frontend sends payload to /api/dpop/sign endpoint for backend signing</li>
 *   <li>Backend returns signed DPoP proof to frontend</li>
 *   <li>Frontend uses signed proof in DPoP header</li>
 *   <li>Private key never leaves backend, enhancing security against XSS</li>
 * </ol>
 * <p>
 * To use DPoP with this BFF approach:
 * <ol>
 *   <li>GET /api/dpop/public-key to obtain the backend-managed public key</li>
 *   <li>Create DPoP proof payload (jti, htm, htu, iat, ath)</li>
 *   <li>POST /api/dpop/sign with the payload to get a signed proof</li>
 *   <li>Include the signed proof in the DPoP header when requesting tokens</li>
 *   <li>Use the "DPoP" authorization scheme instead of "Bearer" for protected resources</li>
 * </ol>
 */
@Tag(name = "Authentication", description = "Discord OAuth2 authentication and DPoP token management")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private static final String DPOP_HEADER = "DPoP";
    private static final String DPOP_KEY_ID_HEADER = "X-DPoP-Key-Id";

    private final AuthService authService;
    private final SessionCookieService cookieService;
    private final DPoPKeyManager dpopKeyManager;

    public AuthController(AuthService authService,
                          SessionCookieService cookieService,
                          DPoPKeyManager dpopKeyManager) {
        this.authService = authService;
        this.cookieService = cookieService;
        this.dpopKeyManager = dpopKeyManager;
    }

    /**
     * Handle Discord OAuth2 callback.
     * POST /api/auth/discord/callback
     * <p>
     * The browser's encrypted cookie identifies the database session. The resulting JWT is
     * stored in that session row and is never returned to the SPA.
     */
    @Operation(summary = "Discord OAuth2 callback",
            description = "Exchange the Discord authorization code and bind the server-held token to the encrypted browser session.")
    @PostMapping("/discord/callback")
    public ResponseEntity<?> discordCallback(@RequestBody OAuthCallbackRequest request,
                                             HttpServletRequest httpRequest) {
        if (request.getCode() == null || request.getCode().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Authorization code is required"));
        }

        String browserKeyId = cookieService.readKeyId(httpRequest).orElse(null);
        if (browserKeyId == null || dpopKeyManager.findActiveSession(browserKeyId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Missing or invalid browser session"));
        }

        OAuthCallbackResponse response = authService.handleOAuthCallback(
                request.getCode(), browserKeyId);

        if (response == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Failed to authenticate with Discord"));
        }

        log.info("Successfully authenticated user: {} (token_type: {})",
                response.getUser().getId(), response.getTokenType());
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh the server-held session without exposing any token or proof to the SPA.
     * POST /api/auth/refresh
     */
    @Operation(summary = "Refresh the encrypted browser session",
            description = "Re-issues the server-held DPoP-bound token using only the encrypted browser cookie.")
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest httpRequest) {
        String browserKeyId = cookieService.readKeyId(httpRequest).orElse(null);
        if (browserKeyId == null || dpopKeyManager.findActiveSession(browserKeyId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Missing or invalid browser session"));
        }

        OAuthCallbackResponse response = authService.refresh(browserKeyId);
        if (response == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Session cannot be refreshed; please log in again"));
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Return the user bound to the encrypted browser session.
     * GET /api/auth/me
     */
    @Operation(summary = "Get the authenticated user",
            description = "Restores user state after a page refresh using only the encrypted cookie.")
    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest httpRequest) {
        String browserKeyId = cookieService.readKeyId(httpRequest).orElse(null);
        if (browserKeyId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Missing browser session"));
        }

        var user = authService.getCurrentUser(browserKeyId);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Browser session is not authenticated"));
        }

        return ResponseEntity.ok(user);
    }

    /**
     * Logout endpoint - revokes and clears the encrypted browser session.
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        cookieService.readKeyId(httpRequest).ifPresent(keyId -> {
            dpopKeyManager.clearKeyPair(keyId);
            log.info("Revoked browser session on logout");
        });
        cookieService.clearCookie(httpResponse);

        return ResponseEntity.ok().body(new SuccessResponse("Logged out successfully"));
    }

    /**
     * Get current authenticated user's guilds.
     * GET /api/auth/user/guilds
     */
    @GetMapping("/user/guilds")
    public ResponseEntity<?> getUserGuilds() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("User not authenticated"));
        }

        String userId = (String) auth.getPrincipal();
        log.debug("Fetching guilds for user: {}", userId);

        Object guilds = authService.getUserGuilds(userId);
        if (guilds == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch guilds"));
        }

        return ResponseEntity.ok(guilds);
    }

    /**
     * Get specific guild information for authenticated user.
     * GET /api/auth/user/guilds/{guildId}
     */
    @GetMapping("/user/guilds/{guildId}")
    public ResponseEntity<?> getGuildInfo(@PathVariable String guildId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("User not authenticated"));
        }

        String userId = (String) auth.getPrincipal();
        log.debug("Fetching guild {} info for user: {}", guildId, userId);

        Object guildInfo = authService.getGuildInfo(userId, guildId);
        if (guildInfo == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Guild not found or user doesn't have access"));
        }

        return ResponseEntity.ok(guildInfo);
    }

    /**
     * Error response DTO.
     */
    public static class ErrorResponse {
        private String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }

    /**
     * Success response DTO.
     */
    public static class SuccessResponse {
        private String message;

        public SuccessResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}