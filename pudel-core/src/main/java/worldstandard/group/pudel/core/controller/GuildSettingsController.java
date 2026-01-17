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
import worldstandard.group.pudel.core.entity.GuildSettings;
import worldstandard.group.pudel.core.service.GuildSettingsService;

/**
 * REST controller for guild settings management.
 */
@RestController
@RequestMapping("/api/guilds")
@CrossOrigin(origins = "*", maxAge = 3600)
public class GuildSettingsController {
    private static final Logger log = LoggerFactory.getLogger(GuildSettingsController.class);

    private final GuildSettingsService guildSettingsService;

    public GuildSettingsController(GuildSettingsService guildSettingsService) {
        this.guildSettingsService = guildSettingsService;
    }

    /**
     * Get guild settings.
     * GET /api/guilds/{guildId}/settings
     */
    @GetMapping("/{guildId}/settings")
    public ResponseEntity<?> getGuildSettings(@PathVariable String guildId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getPrincipal() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("User not authenticated"));
            }

            GuildSettings settings = guildSettingsService.getOrCreateGuildSettings(guildId);
            log.debug("Retrieved settings for guild: {}", guildId);
            return ResponseEntity.ok(settings);
        } catch (Exception e) {
            log.error("Error retrieving guild settings", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to retrieve guild settings"));
        }
    }

    /**
     * Update guild settings.
     * PATCH /api/guilds/{guildId}/settings
     */
    @PatchMapping("/{guildId}/settings")
    public ResponseEntity<?> updateGuildSettings(
            @PathVariable String guildId,
            @RequestBody GuildSettings updatedSettings) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getPrincipal() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("User not authenticated"));
            }

            // Validate settings
            if (!guildSettingsService.validateSettings(updatedSettings)) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Invalid settings provided"));
            }

            GuildSettings settings = guildSettingsService.updateGuildSettings(guildId, updatedSettings);
            log.info("Updated settings for guild: {}", guildId);
            return ResponseEntity.ok(settings);
        } catch (Exception e) {
            log.error("Error updating guild settings", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to update guild settings"));
        }
    }

    /**
     * Create guild settings.
     * POST /api/guilds/{guildId}/settings
     */
    @PostMapping("/{guildId}/settings")
    public ResponseEntity<?> createGuildSettings(@PathVariable String guildId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getPrincipal() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("User not authenticated"));
            }

            GuildSettings settings = guildSettingsService.getOrCreateGuildSettings(guildId);
            log.info("Created/verified settings for guild: {}", guildId);
            return ResponseEntity.status(HttpStatus.CREATED).body(settings);
        } catch (Exception e) {
            log.error("Error creating guild settings", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to create guild settings"));
        }
    }

    /**
     * Delete guild settings.
     * DELETE /api/guilds/{guildId}/settings
     */
    @DeleteMapping("/{guildId}/settings")
    public ResponseEntity<?> deleteGuildSettings(@PathVariable String guildId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getPrincipal() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("User not authenticated"));
            }

            guildSettingsService.deleteGuildSettings(guildId);
            log.info("Deleted settings for guild: {}", guildId);
            return ResponseEntity.ok(new SuccessResponse("Guild settings deleted successfully"));
        } catch (Exception e) {
            log.error("Error deleting guild settings", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to delete guild settings"));
        }
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

