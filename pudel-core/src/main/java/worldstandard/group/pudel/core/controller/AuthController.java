/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 Napapon Kamanee
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
package worldstandard.group.pudel.core.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import worldstandard.group.pudel.core.dto.OAuthCallbackRequest;
import worldstandard.group.pudel.core.dto.OAuthCallbackResponse;
import worldstandard.group.pudel.core.service.AuthService;

/**
 * REST controller for authentication endpoints.
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Handle Discord OAuth2 callback.
     * POST /api/auth/discord/callback
     */
    @PostMapping("/discord/callback")
    public ResponseEntity<?> discordCallback(@RequestBody OAuthCallbackRequest request) {
        log.info("Received OAuth callback request");

        if (request.getCode() == null || request.getCode().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Authorization code is required"));
        }

        OAuthCallbackResponse response = authService.handleOAuthCallback(request.getCode());

        if (response == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Failed to authenticate with Discord"));
        }

        log.info("Successfully authenticated user: {}", response.getUser().getId());
        return ResponseEntity.ok(response);
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
}

