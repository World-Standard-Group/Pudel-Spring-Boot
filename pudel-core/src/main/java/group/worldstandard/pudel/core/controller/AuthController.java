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

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Handle Discord OAuth2 callback.
     * POST /api/auth/discord/callback
     * <p>
     * For DPoP-bound tokens, include a DPoP proof in the "DPoP" header.
     * The response will indicate the token_type as "DPoP" or "Bearer".
     */
    @Operation(summary = "Discord OAuth2 callback", description = "Exchange Discord authorization code for a JWT token. Include DPoP header for proof-of-possession binding.")
    @PostMapping("/discord/callback")
    public ResponseEntity<?> discordCallback(@RequestBody OAuthCallbackRequest request,
                                             @RequestHeader(value = DPOP_HEADER, required = false) String dpopProof,
                                             @RequestHeader(value = DPOP_KEY_ID_HEADER, required = false) String dpopKeyId,
                                             HttpServletRequest httpRequest) {
        log.info("Received OAuth callback request (DPoP: {})", dpopProof != null);

        if (request.getCode() == null || request.getCode().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Authorization code is required"));
        }

        // Build request URI for DPoP validation
        String httpUri = httpRequest.getRequestURL().toString();
        String httpMethod = httpRequest.getMethod();

        // Use database-backed key validation (keyId from header)
        OAuthCallbackResponse response = authService.handleOAuthCallback(
                request.getCode(),
                dpopProof,
                httpMethod,
                httpUri,
                dpopKeyId
        );

        if (response == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Failed to authenticate with Discord"));
        }

        log.info("Successfully authenticated user: {} (token_type: {})",
                response.getUser().getId(), response.getTokenType());
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh an existing DPoP-bound session without a full Discord re-login.
     * POST /api/auth/refresh
     * <p>
     * The client sends its current (still-valid) DPoP-bound access token in the
     * Authorization: DPoP <token> header, plus a fresh DPoP proof in the "DPoP" header
     * and the key id in X-DPoP-Key-Id. The server re-validates the binding, refreshes
     * the Discord access token server-side using the stored refresh token, and returns a new
     * DPoP-bound access token bound to the SAME key thumbprint. This is the RFC 9449
     * BFF refresh flow that prevents the "key mismatch 401 → forced re-login" on restart.
     */
    @Operation(summary = "Refresh DPoP-bound session",
            description = "Re-issue a DPoP-bound token bound to the same key, refreshing the Discord token server-side. No full re-login required.")
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = DPOP_HEADER, required = false) String dpopProof,
            @RequestHeader(value = DPOP_KEY_ID_HEADER, required = false) String dpopKeyId,
            HttpServletRequest httpRequest) {

        if (authHeader == null || dpopProof == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Missing Authorization or DPoP proof for refresh"));
        }

        String token = null;
        if (authHeader.startsWith("DPoP ")) {
            token = authHeader.substring(5);
        } else if (authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid Authorization scheme for refresh"));
        }

        String httpUri = httpRequest.getRequestURL().toString();
        String httpMethod = httpRequest.getMethod();

        OAuthCallbackResponse response = authService.refresh(
                dpopProof, httpMethod, httpUri, dpopKeyId, token);

        if (response == null) {
            // Refresh impossible (token revoked, key desync, refresh token invalid) → force re-login.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Session cannot be refreshed; please log in again"));
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Logout endpoint - revokes token binding for DPoP tokens.
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null) {
            String token = null;
            if (authHeader.startsWith("DPoP ")) {
                token = authHeader.substring(5);
            } else if (authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }

            if (token != null) {
                authService.revokeToken(token);
                log.info("Token revoked on logout");
            }
        }

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