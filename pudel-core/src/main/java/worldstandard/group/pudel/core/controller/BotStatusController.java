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

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for bot status and statistics endpoints.
 */
@RestController
@RequestMapping("/api/bot")
@CrossOrigin(origins = "*", maxAge = 3600)
public class BotStatusController {
    private static final Logger log = LoggerFactory.getLogger(BotStatusController.class);

    private final JDA jda;
    private final long startup = System.currentTimeMillis();

    public BotStatusController(JDA jda) {
        this.jda = jda;
    }

    /**
     * Get current bot status.
     * GET /api/bot/status
     */
    @GetMapping("/status")
    public ResponseEntity<?> getBotStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("online", jda.getStatus().name());
            status.put("uptime", calculateUptime());
            status.put("shardCount", jda.getShardInfo().getShardTotal());
            status.put("currentShard", jda.getShardInfo().getShardId() + 1);
            status.put("guildCount", jda.getGuildCache().size());
            status.put("userCount", jda.getUserCache().size());
            status.put("responseTime", jda.getGatewayPing());
            status.put("timestamp", OffsetDateTime.now().toString());

            log.debug("Bot status retrieved");
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error retrieving bot status", e);
            return ResponseEntity.status(500).body(new ErrorResponse("Failed to retrieve bot status"));
        }
    }

    /**
     * Get bot version information.
     * GET /api/bot/version
     */
    @GetMapping("/version")
    public ResponseEntity<?> getBotVersion() {
        try {
            Map<String, Object> version = new HashMap<>();
            version.put("version", "1.0.0");
            version.put("name", "Pudel");
            version.put("codename", "Maid Edition");
            version.put("jdaVersion", net.dv8tion.jda.api.JDAInfo.VERSION);
            version.put("javaVersion", System.getProperty("java.version"));
            version.put("springBootVersion", org.springframework.boot.SpringBootVersion.getVersion());

            return ResponseEntity.ok(version);
        } catch (Exception e) {
            log.error("Error retrieving bot version", e);
            return ResponseEntity.status(500).body(new ErrorResponse("Failed to retrieve bot version"));
        }
    }

    /**
     * Get bot statistics.
     * GET /api/bot/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getBotStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // Guild statistics
            stats.put("guildCount", jda.getGuildCache().size());
            stats.put("userCount", jda.getUserCache().size());
            stats.put("memberCount", jda.getGuilds().stream()
                    .mapToInt(Guild::getMemberCount)
                    .sum());

            // Channel statistics
            stats.put("textChannelCount", jda.getTextChannelCache().size());
            stats.put("voiceChannelCount", jda.getVoiceChannelCache().size());

            // Role statistics
            stats.put("roleCount", jda.getRoleCache().size());

            // Performance
            stats.put("averageGatewayPing", jda.getGatewayPing());
            stats.put("restPingMs", jda.getRestPing().complete());

            // Time
            stats.put("uptime", calculateUptime());
            stats.put("timestamp", OffsetDateTime.now().toString());

            log.debug("Bot statistics retrieved");
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error retrieving bot statistics", e);
            return ResponseEntity.status(500).body(new ErrorResponse("Failed to retrieve bot statistics"));
        }
    }

    /**
     * Calculate bot uptime in human-readable format.
     */
    private String calculateUptime() {
        long totalSeconds = (System.currentTimeMillis() - startup) / 1000;

        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        return String.format("%d days, %d hours, %d minutes, %d seconds", days, hours, minutes, seconds);
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

