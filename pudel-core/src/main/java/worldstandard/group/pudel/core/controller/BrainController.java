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
import worldstandard.group.pudel.core.brain.PudelBrain;
import worldstandard.group.pudel.core.brain.memory.MemoryManager;
import worldstandard.group.pudel.core.service.SubscriptionService;
import worldstandard.group.pudel.model.PudelModelService;
import worldstandard.group.pudel.model.analyzer.TextAnalysis;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for brain/memory statistics and analysis.
 */
@RestController
@RequestMapping("/api/brain")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class BrainController {

    private final PudelBrain pudelBrain;
    private final SubscriptionService subscriptionService;
    private final PudelModelService modelService;

    public BrainController(PudelBrain pudelBrain,
                           SubscriptionService subscriptionService,
                           PudelModelService modelService) {
        this.pudelBrain = pudelBrain;
        this.subscriptionService = subscriptionService;
        this.modelService = modelService;
    }

    /**
     * Get brain status including model availability.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getBrainStatus() {
        Map<String, Object> status = new HashMap<>();

        // LangChain4j text analyzer status
        status.put("textAnalyzerAvailable", pudelBrain.isLLMAnalyzerAvailable());
        status.put("brainActive", true);

        // Model service status
        PudelModelService.ModelHealth modelHealth = modelService.getHealth();
        status.put("ollamaAvailable", modelHealth.ollamaAvailable());
        status.put("ollamaVersion", modelHealth.ollamaVersion());
        status.put("loadedModels", modelHealth.loadedModels());
        status.put("embeddingAvailable", modelHealth.embeddingAvailable());
        status.put("embeddingDimension", modelHealth.embeddingDimension());
        status.put("embeddingCacheStats", modelHealth.embeddingCacheStats());

        return ResponseEntity.ok(status);
    }

    /**
     * Get detailed model health information.
     */
    @GetMapping("/model/health")
    public ResponseEntity<PudelModelService.ModelHealth> getModelHealth() {
        return ResponseEntity.ok(modelService.getHealth());
    }

    /**
     * Analyze a text message without storing it.
     * Useful for testing text analysis.
     */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeText(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Text is required"));
        }

        TextAnalysis analysis = pudelBrain.analyzeText(text);

        Map<String, Object> result = new HashMap<>();
        result.put("language", analysis.language());
        result.put("intent", analysis.intent());
        result.put("confidence", analysis.confidence());
        result.put("sentiment", analysis.sentiment());
        result.put("entities", analysis.entities());
        result.put("keywords", analysis.keywords());
        result.put("containsInterestingInfo", analysis.containsInterestingInfo());
        result.put("isQuestion", analysis.isQuestion());
        result.put("isCommand", analysis.isCommand());
        result.put("isGreeting", analysis.isGreeting());
        result.put("isFarewell", analysis.isFarewell());
        result.put("llmAnalyzerUsed", pudelBrain.isLLMAnalyzerAvailable());

        return ResponseEntity.ok(result);
    }

    /**
     * Get memory statistics for a guild.
     */
    @GetMapping("/memory/guild/{guildId}")
    public ResponseEntity<Map<String, Object>> getGuildMemoryStats(@PathVariable String guildId) {
        try {
            long id = Long.parseLong(guildId);
            MemoryManager.MemoryStats stats = pudelBrain.getMemoryManager().getMemoryStats(true, id);
            SubscriptionService.GuildUsage usage = subscriptionService.getGuildUsage(id);

            Map<String, Object> result = new HashMap<>();
            result.put("dialogueCount", stats.dialogueCount());
            result.put("dialogueLimit", stats.dialogueLimit());
            result.put("dialogueUsagePercent", stats.dialogueUsagePercent());
            result.put("memoryCount", stats.memoryCount());
            result.put("memoryLimit", stats.memoryLimit());
            result.put("memoryUsagePercent", stats.memoryUsagePercent());
            result.put("passiveContextCount", stats.passiveContextCount());
            result.put("tier", usage.tierName);
            result.put("active", usage.active);

            return ResponseEntity.ok(result);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid guild ID"));
        }
    }

    /**
     * Get memory statistics for a user.
     */
    @GetMapping("/memory/user/{userId}")
    public ResponseEntity<Map<String, Object>> getUserMemoryStats(@PathVariable String userId) {
        try {
            long id = Long.parseLong(userId);
            MemoryManager.MemoryStats stats = pudelBrain.getMemoryManager().getMemoryStats(false, id);
            SubscriptionService.UserUsage usage = subscriptionService.getUserUsage(id);

            Map<String, Object> result = new HashMap<>();
            result.put("dialogueCount", stats.dialogueCount());
            result.put("dialogueLimit", stats.dialogueLimit());
            result.put("dialogueUsagePercent", stats.dialogueUsagePercent());
            result.put("memoryCount", stats.memoryCount());
            result.put("memoryLimit", stats.memoryLimit());
            result.put("memoryUsagePercent", stats.memoryUsagePercent());
            result.put("passiveContextCount", stats.passiveContextCount());
            result.put("tier", usage.tierName);
            result.put("active", usage.active);

            return ResponseEntity.ok(result);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user ID"));
        }
    }

    /**
     * Get all subscription tiers.
     */
    @GetMapping("/tiers")
    public ResponseEntity<Object> getSubscriptionTiers() {
        return ResponseEntity.ok(subscriptionService.getAllTiers());
    }
}

