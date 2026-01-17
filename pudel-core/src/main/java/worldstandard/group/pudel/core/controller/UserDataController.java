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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import worldstandard.group.pudel.core.service.SchemaManagementService;
import worldstandard.group.pudel.core.service.UserDataService;

import java.util.List;
import java.util.Map;

/**
 * Controller for managing user-specific schema data (DM conversations, personal settings).
 */
@RestController
@RequestMapping("/api/user/data")
@CrossOrigin(origins = "*")
public class UserDataController {

    private static final Logger logger = LoggerFactory.getLogger(UserDataController.class);

    private final UserDataService userDataService;
    private final SchemaManagementService schemaManagementService;

    public UserDataController(UserDataService userDataService,
                              SchemaManagementService schemaManagementService) {
        this.userDataService = userDataService;
        this.schemaManagementService = schemaManagementService;
    }

    /**
     * Get current authenticated user's ID.
     */
    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() != null) {
            return auth.getName();
        }
        return null;
    }

    /**
     * Check if user schema exists.
     */
    @GetMapping("/schema/status")
    public ResponseEntity<Map<String, Object>> getSchemaStatus() {
        String userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        try {
            long userIdLong = Long.parseLong(userId);
            boolean exists = schemaManagementService.userSchemaExists(userIdLong);
            String schemaName = schemaManagementService.getUserSchemaName(userIdLong);

            return ResponseEntity.ok(Map.of(
                    "userId", userId,
                    "schemaName", schemaName,
                    "exists", exists
            ));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user ID"));
        }
    }

    /**
     * Initialize user schema (create if not exists).
     */
    @PostMapping("/schema/initialize")
    public ResponseEntity<Map<String, Object>> initializeSchema() {
        String userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        try {
            long userIdLong = Long.parseLong(userId);

            if (!schemaManagementService.userSchemaExists(userIdLong)) {
                schemaManagementService.createUserSchema(userIdLong);
                logger.info("Created schema for user {}", userId);
                return ResponseEntity.ok(Map.of("message", "Schema created successfully"));
            } else {
                return ResponseEntity.ok(Map.of("message", "Schema already exists"));
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user ID"));
        } catch (Exception e) {
            logger.error("Error initializing schema for user {}: {}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to initialize schema"));
        }
    }

    // ===========================================
    // Pudel Settings Endpoints (for DM)
    // ===========================================

    /**
     * Get user's personal Pudel settings for DM conversations.
     */
    @GetMapping("/pudel-settings")
    public ResponseEntity<?> getPudelSettings() {
        String userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        try {
            long userIdLong = Long.parseLong(userId);
            userDataService.ensureUserSchema(userIdLong);

            return userDataService.getPudelSettings(userIdLong)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.ok(Map.of(
                            "biography", "",
                            "personality", "",
                            "preferences", "",
                            "dialogue_style", ""
                    )));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user ID"));
        }
    }

    /**
     * Update user's personal Pudel settings.
     */
    @PatchMapping("/pudel-settings")
    public ResponseEntity<?> updatePudelSettings(@RequestBody PudelSettingsRequest request) {
        String userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        try {
            long userIdLong = Long.parseLong(userId);
            userDataService.ensureUserSchema(userIdLong);

            userDataService.updatePudelSettings(userIdLong,
                    request.biography, request.personality,
                    request.preferences, request.dialogueStyle);

            return ResponseEntity.ok(Map.of("message", "Settings updated successfully"));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user ID"));
        }
    }

    // ===========================================
    // Dialogue History Endpoints (for DM)
    // ===========================================

    /**
     * Get recent DM dialogue history.
     */
    @GetMapping("/dialogue")
    public ResponseEntity<?> getDialogue(@RequestParam(defaultValue = "50") int limit) {
        String userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        try {
            long userIdLong = Long.parseLong(userId);

            List<Map<String, Object>> dialogue = userDataService.getRecentDialogue(userIdLong, limit);
            return ResponseEntity.ok(dialogue);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user ID"));
        }
    }

    // ===========================================
    // Memory Endpoints (for DM)
    // ===========================================

    /**
     * Get all memories.
     */
    @GetMapping("/memory")
    public ResponseEntity<?> getAllMemories() {
        String userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        try {
            long userIdLong = Long.parseLong(userId);

            List<Map<String, Object>> memories = userDataService.getAllMemories(userIdLong);
            return ResponseEntity.ok(memories);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user ID"));
        }
    }

    /**
     * Get a memory entry by key.
     */
    @GetMapping("/memory/{key}")
    public ResponseEntity<?> getMemory(@PathVariable("key") String key) {
        String userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        try {
            long userIdLong = Long.parseLong(userId);

            return userDataService.getMemory(userIdLong, key)
                    .map(value -> ResponseEntity.ok(Map.of("key", key, "value", value)))
                    .orElse(ResponseEntity.notFound().build());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user ID"));
        }
    }

    /**
     * Store a memory entry.
     */
    @PostMapping("/memory")
    public ResponseEntity<?> storeMemory(@RequestBody MemoryRequest request) {
        String userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        try {
            long userIdLong = Long.parseLong(userId);

            if (request.key == null || request.key.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Key is required"));
            }
            if (request.value == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Value is required"));
            }

            userDataService.storeMemory(userIdLong, request.key, request.value,
                    request.category != null ? request.category : "general");

            return ResponseEntity.ok(Map.of("message", "Memory stored successfully"));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user ID"));
        }
    }

    /**
     * Delete a memory entry.
     */
    @DeleteMapping("/memory/{key}")
    public ResponseEntity<?> deleteMemory(@PathVariable("key") String key) {
        String userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        try {
            long userIdLong = Long.parseLong(userId);

            boolean deleted = userDataService.deleteMemory(userIdLong, key);
            if (deleted) {
                return ResponseEntity.ok(Map.of("message", "Memory deleted successfully"));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user ID"));
        }
    }

    // Request DTOs
    public static class PudelSettingsRequest {
        public String biography;
        public String personality;
        public String preferences;
        public String dialogueStyle;
    }

    public static class MemoryRequest {
        public String key;
        public String value;
        public String category;
    }
}

