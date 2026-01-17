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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import worldstandard.group.pudel.core.service.GuildDataService;
import worldstandard.group.pudel.core.service.SchemaManagementService;

import java.util.List;
import java.util.Map;

/**
 * Controller for managing guild-specific schema data (dialogue, memory, etc.).
 */
@RestController
@RequestMapping("/api/guilds/{guildId}/data")
@CrossOrigin(origins = "*")
public class GuildDataController {

    private static final Logger logger = LoggerFactory.getLogger(GuildDataController.class);

    private final GuildDataService guildDataService;
    private final SchemaManagementService schemaManagementService;

    public GuildDataController(GuildDataService guildDataService,
                               SchemaManagementService schemaManagementService) {
        this.guildDataService = guildDataService;
        this.schemaManagementService = schemaManagementService;
    }

    /**
     * Check if guild schema exists.
     */
    @GetMapping("/schema/status")
    public ResponseEntity<Map<String, Object>> getSchemaStatus(@PathVariable("guildId") String guildId) {
        try {
            long guildIdLong = Long.parseLong(guildId);
            boolean exists = schemaManagementService.schemaExists(guildIdLong);
            String schemaName = schemaManagementService.getGuildSchemaName(guildIdLong);

            return ResponseEntity.ok(Map.of(
                    "guildId", guildId,
                    "schemaName", schemaName,
                    "exists", exists
            ));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid guild ID"));
        }
    }

    /**
     * Initialize guild schema (create if not exists).
     */
    @PostMapping("/schema/initialize")
    public ResponseEntity<Map<String, Object>> initializeSchema(@PathVariable("guildId") String guildId) {
        try {
            long guildIdLong = Long.parseLong(guildId);

            if (!schemaManagementService.schemaExists(guildIdLong)) {
                schemaManagementService.createGuildSchema(guildIdLong);
                logger.info("Created schema for guild {}", guildId);
                return ResponseEntity.ok(Map.of("message", "Schema created successfully"));
            } else {
                return ResponseEntity.ok(Map.of("message", "Schema already exists"));
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid guild ID"));
        } catch (Exception e) {
            logger.error("Error initializing schema for guild {}: {}", guildId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to initialize schema"));
        }
    }

    // ===========================================
    // Dialogue History Endpoints
    // ===========================================

    /**
     * Get recent dialogue history for a user in this guild.
     */
    @GetMapping("/dialogue/user/{userId}")
    public ResponseEntity<?> getUserDialogue(
            @PathVariable("guildId") String guildId,
            @PathVariable("userId") String userId,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            long guildIdLong = Long.parseLong(guildId);
            long userIdLong = Long.parseLong(userId);

            List<Map<String, Object>> dialogue = guildDataService.getRecentDialogue(guildIdLong, userIdLong, limit);
            return ResponseEntity.ok(dialogue);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid ID format"));
        }
    }

    /**
     * Get dialogue history for a channel.
     */
    @GetMapping("/dialogue/channel/{channelId}")
    public ResponseEntity<?> getChannelDialogue(
            @PathVariable("guildId") String guildId,
            @PathVariable("channelId") String channelId,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            long guildIdLong = Long.parseLong(guildId);
            long channelIdLong = Long.parseLong(channelId);

            List<Map<String, Object>> dialogue = guildDataService.getChannelDialogue(guildIdLong, channelIdLong, limit);
            return ResponseEntity.ok(dialogue);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid ID format"));
        }
    }

    // ===========================================
    // Memory Endpoints
    // ===========================================

    /**
     * Get a memory entry by key.
     */
    @GetMapping("/memory/{key}")
    public ResponseEntity<?> getMemory(
            @PathVariable("guildId") String guildId,
            @PathVariable("key") String key) {
        try {
            long guildIdLong = Long.parseLong(guildId);

            return guildDataService.getMemory(guildIdLong, key)
                    .map(value -> ResponseEntity.ok(Map.of("key", key, "value", value)))
                    .orElse(ResponseEntity.notFound().build());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid guild ID"));
        }
    }

    /**
     * Get all memories by category.
     */
    @GetMapping("/memory/category/{category}")
    public ResponseEntity<?> getMemoriesByCategory(
            @PathVariable("guildId") String guildId,
            @PathVariable("category") String category) {
        try {
            long guildIdLong = Long.parseLong(guildId);

            List<Map<String, Object>> memories = guildDataService.getMemoriesByCategory(guildIdLong, category);
            return ResponseEntity.ok(memories);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid guild ID"));
        }
    }

    /**
     * Store a memory entry.
     */
    @PostMapping("/memory")
    public ResponseEntity<?> storeMemory(
            @PathVariable("guildId") String guildId,
            @RequestBody MemoryRequest request) {
        try {
            long guildIdLong = Long.parseLong(guildId);

            if (request.key == null || request.key.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Key is required"));
            }
            if (request.value == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Value is required"));
            }

            guildDataService.storeMemory(guildIdLong, request.key, request.value,
                    request.category != null ? request.category : "general",
                    request.createdBy);

            return ResponseEntity.ok(Map.of("message", "Memory stored successfully"));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid guild ID"));
        }
    }

    /**
     * Delete a memory entry.
     */
    @DeleteMapping("/memory/{key}")
    public ResponseEntity<?> deleteMemory(
            @PathVariable("guildId") String guildId,
            @PathVariable("key") String key) {
        try {
            long guildIdLong = Long.parseLong(guildId);

            boolean deleted = guildDataService.deleteMemory(guildIdLong, key);
            if (deleted) {
                return ResponseEntity.ok(Map.of("message", "Memory deleted successfully"));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid guild ID"));
        }
    }

    // ===========================================
    // User Preferences Endpoints
    // ===========================================

    /**
     * Get user preferences within this guild.
     */
    @GetMapping("/preferences/user/{userId}")
    public ResponseEntity<?> getUserPreferences(
            @PathVariable("guildId") String guildId,
            @PathVariable("userId") String userId) {
        try {
            long guildIdLong = Long.parseLong(guildId);
            long userIdLong = Long.parseLong(userId);

            return guildDataService.getUserPreference(guildIdLong, userIdLong)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid ID format"));
        }
    }

    /**
     * Update user preferences within this guild.
     */
    @PostMapping("/preferences/user/{userId}")
    public ResponseEntity<?> updateUserPreferences(
            @PathVariable("guildId") String guildId,
            @PathVariable("userId") String userId,
            @RequestBody UserPreferenceRequest request) {
        try {
            long guildIdLong = Long.parseLong(guildId);
            long userIdLong = Long.parseLong(userId);

            guildDataService.storeUserPreference(guildIdLong, userIdLong,
                    request.preferredName, request.customSettings, request.notes);

            return ResponseEntity.ok(Map.of("message", "Preferences updated successfully"));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid ID format"));
        }
    }

    // Request DTOs
    public static class MemoryRequest {
        public String key;
        public String value;
        public String category;
        public Long createdBy;
    }

    public static class UserPreferenceRequest {
        public String preferredName;
        public String customSettings;
        public String notes;
    }
}

