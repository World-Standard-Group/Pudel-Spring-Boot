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

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import worldstandard.group.pudel.core.service.SubscriptionService;
import worldstandard.group.pudel.core.service.SubscriptionService.GuildUsage;
import worldstandard.group.pudel.core.service.SubscriptionService.TierInfo;
import worldstandard.group.pudel.core.service.SubscriptionService.UserUsage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for subscription and usage endpoints.
 */
@RestController
@RequestMapping("/api/subscription")
@CrossOrigin(origins = "*")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * Get guild usage statistics.
     */
    @GetMapping("/guild/{guildId}/usage")
    public ResponseEntity<Map<String, Object>> getGuildUsage(@PathVariable("guildId") String guildId) {
        try {
            long guildIdLong = Long.parseLong(guildId);
            GuildUsage usage = subscriptionService.getGuildUsage(guildIdLong);

            Map<String, Object> response = new HashMap<>();
            response.put("guildId", guildId);
            response.put("tier", usage.tierName);
            response.put("active", usage.active);

            Map<String, Object> dialogue = new HashMap<>();
            dialogue.put("current", usage.dialogueCount);
            dialogue.put("limit", usage.dialogueLimit);
            dialogue.put("percentage", calculatePercentage(usage.dialogueCount, usage.dialogueLimit));
            response.put("dialogue", dialogue);

            Map<String, Object> memory = new HashMap<>();
            memory.put("current", usage.memoryCount);
            memory.put("limit", usage.memoryLimit);
            memory.put("percentage", calculatePercentage(usage.memoryCount, usage.memoryLimit));
            response.put("memory", memory);


            return ResponseEntity.ok(response);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid guild ID"));
        }
    }

    /**
     * Get user usage statistics.
     */
    @GetMapping("/user/{userId}/usage")
    public ResponseEntity<Map<String, Object>> getUserUsage(@PathVariable("userId") String userId) {
        try {
            long userIdLong = Long.parseLong(userId);
            UserUsage usage = subscriptionService.getUserUsage(userIdLong);

            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("tier", usage.tierName);
            response.put("active", usage.active);

            Map<String, Object> dialogue = new HashMap<>();
            dialogue.put("current", usage.dialogueCount);
            dialogue.put("limit", usage.dialogueLimit);
            dialogue.put("percentage", calculatePercentage(usage.dialogueCount, usage.dialogueLimit));
            response.put("dialogue", dialogue);

            Map<String, Object> memory = new HashMap<>();
            memory.put("current", usage.memoryCount);
            memory.put("limit", usage.memoryLimit);
            memory.put("percentage", calculatePercentage(usage.memoryCount, usage.memoryLimit));
            response.put("memory", memory);

            return ResponseEntity.ok(response);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user ID"));
        }
    }

    /**
     * Get all subscription tiers info from configuration.
     */
    @GetMapping("/tiers")
    public ResponseEntity<Map<String, Object>> getTiers() {
        List<TierInfo> tiers = subscriptionService.getAllTiers();

        Map<String, Object> response = tiers.stream()
                .collect(Collectors.toMap(
                        tier -> tier.tierKey,
                        tier -> {
                            Map<String, Object> tierMap = new HashMap<>();
                            tierMap.put("name", tier.name);
                            tierMap.put("description", tier.description);

                            if (tier.userLimits != null) {
                                Map<String, Object> userLimits = new HashMap<>();
                                userLimits.put("dialogueLimit", tier.userLimits.getDialogueLimit());
                                userLimits.put("memoryLimit", tier.userLimits.getMemoryLimit());
                                tierMap.put("userLimits", userLimits);
                            }

                            if (tier.guildLimits != null) {
                                Map<String, Object> guildLimits = new HashMap<>();
                                guildLimits.put("dialogueLimit", tier.guildLimits.getDialogueLimit());
                                guildLimits.put("memoryLimit", tier.guildLimits.getMemoryLimit());
                                tierMap.put("guildLimits", guildLimits);
                            }

                            if (tier.features != null) {
                                Map<String, Object> features = new HashMap<>();
                                features.put("chatbot", tier.features.isChatbot());
                                features.put("customPersonality", tier.features.isCustomPersonality());
                                features.put("pluginLimit", tier.features.getPluginLimit());
                                features.put("voiceEnabled", tier.features.isVoiceEnabled());
                                features.put("prioritySupport", tier.features.isPrioritySupport());
                                tierMap.put("features", features);
                            }

                            return tierMap;
                        }
                ));

        return ResponseEntity.ok(response);
    }

    /**
     * Get a specific tier info.
     */
    @GetMapping("/tiers/{tierName}")
    public ResponseEntity<?> getTier(@PathVariable("tierName") String tierName) {
        TierInfo tier = subscriptionService.getTierInfo(tierName);
        if (tier == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("tierKey", tier.tierKey);
        response.put("name", tier.name);
        response.put("description", tier.description);

        if (tier.userLimits != null) {
            Map<String, Object> userLimits = new HashMap<>();
            userLimits.put("dialogueLimit", tier.userLimits.getDialogueLimit());
            userLimits.put("memoryLimit", tier.userLimits.getMemoryLimit());
            response.put("userLimits", userLimits);
        }

        if (tier.guildLimits != null) {
            Map<String, Object> guildLimits = new HashMap<>();
            guildLimits.put("dialogueLimit", tier.guildLimits.getDialogueLimit());
            guildLimits.put("memoryLimit", tier.guildLimits.getMemoryLimit());
            response.put("guildLimits", guildLimits);
        }

        if (tier.features != null) {
            Map<String, Object> features = new HashMap<>();
            features.put("chatbot", tier.features.isChatbot());
            features.put("customPersonality", tier.features.isCustomPersonality());
            features.put("pluginLimit", tier.features.getPluginLimit());
            features.put("voiceEnabled", tier.features.isVoiceEnabled());
            features.put("prioritySupport", tier.features.isPrioritySupport());
            response.put("features", features);
        }

        return ResponseEntity.ok(response);
    }

    private double calculatePercentage(long current, long limit) {
        if (limit == Long.MAX_VALUE || limit == 0) {
            return 0;
        }
        return Math.round((double) current / limit * 10000.0) / 100.0; // 2 decimal places
    }
}

