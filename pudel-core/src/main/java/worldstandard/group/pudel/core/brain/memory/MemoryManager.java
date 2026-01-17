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
package worldstandard.group.pudel.core.brain.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import worldstandard.group.pudel.core.service.GuildDataService;
import worldstandard.group.pudel.core.service.SchemaManagementService;
import worldstandard.group.pudel.core.service.SubscriptionService;
import worldstandard.group.pudel.core.service.UserDataService;
import worldstandard.group.pudel.model.analyzer.TextAnalysis;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Memory Manager for Pudel's Brain.
 * <p>
 * Manages dialogue history, memories, and context storage with:
 * - Subscription-based capacity limits
 * - Semantic similarity search (using pgvector if available)
 * - Passive context tracking
 * - Memory pruning/cleanup
 * <p>
 * Memory is stored in guild/user-specific schemas.
 */
@Component
public class MemoryManager {

    private static final Logger logger = LoggerFactory.getLogger(MemoryManager.class);

    private final JdbcTemplate jdbcTemplate;
    private final SchemaManagementService schemaManagementService;
    private final SubscriptionService subscriptionService;
    private final GuildDataService guildDataService;
    private final UserDataService userDataService;

    public MemoryManager(JdbcTemplate jdbcTemplate,
                         SchemaManagementService schemaManagementService,
                         @Lazy SubscriptionService subscriptionService,
                         GuildDataService guildDataService,
                         UserDataService userDataService) {
        this.jdbcTemplate = jdbcTemplate;
        this.schemaManagementService = schemaManagementService;
        this.subscriptionService = subscriptionService;
        this.guildDataService = guildDataService;
        this.userDataService = userDataService;
    }

    /**
     * Store a dialogue exchange.
     * Checks capacity limits before storing.
     */
    public boolean storeDialogue(String userMessage, String botResponse, String intent,
                                 long userId, long channelId, boolean isGuild, long targetId) {
        try {
            if (isGuild) {
                // Check capacity
                if (!subscriptionService.canStoreGuildDialogue(targetId)) {
                    logger.debug("Guild {} has reached dialogue capacity", targetId);
                    // Try to prune old entries
                    pruneOldDialogue(true, targetId);
                    // Check again after pruning
                    if (!subscriptionService.canStoreGuildDialogue(targetId)) {
                        return false;
                    }
                }

                guildDataService.storeDialogue(targetId, userId, channelId, userMessage, botResponse, intent);
            } else {
                // Check capacity
                if (!subscriptionService.canStoreUserDialogue(targetId)) {
                    logger.debug("User {} has reached dialogue capacity", targetId);
                    // Try to prune old entries
                    pruneOldDialogue(false, targetId);
                    // Check again after pruning
                    if (!subscriptionService.canStoreUserDialogue(targetId)) {
                        return false;
                    }
                }

                userDataService.storeDialogue(targetId, userMessage, botResponse, intent);
            }
            return true;
        } catch (Exception e) {
            logger.error("Error storing dialogue: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Store passive context (messages that Pudel observes but doesn't respond to).
     * Used for building memory when Pudel isn't directly addressed.
     */
    public void storePassiveContext(String message, long userId, long channelId,
                                    TextAnalysis analysis,
                                    boolean isGuild, long targetId) {
        try {
            String schemaName = isGuild
                    ? schemaManagementService.getGuildSchemaName(targetId)
                    : schemaManagementService.getUserSchemaName(targetId);

            // Check if passive_context table exists
            if (!tableExists(schemaName, "passive_context")) {
                createPassiveContextTable(schemaName);
            }

            // Check capacity (count passive context + dialogue toward memory limit)
            if (isGuild) {
                if (!subscriptionService.canStoreGuildMemory(targetId)) {
                    return; // Silently skip if at capacity
                }
            } else {
                if (!subscriptionService.canStoreUserMemory(targetId)) {
                    return;
                }
            }

            // Store context
            String sql = "INSERT INTO " + schemaName + ".passive_context " +
                    "(user_id, channel_id, content, intent, sentiment, entities, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)";

            String entitiesJson = entitiesToJson(analysis.entities());

            jdbcTemplate.update(sql,
                    userId,
                    channelId,
                    message,
                    analysis.intent(),
                    analysis.sentiment(),
                    entitiesJson,
                    Timestamp.valueOf(LocalDateTime.now())
            );

            logger.debug("Stored passive context for {} {}", isGuild ? "guild" : "user", targetId);

        } catch (Exception e) {
            logger.debug("Error storing passive context: {}", e.getMessage());
        }
    }

    /**
     * Retrieve relevant memories based on message content.
     * Uses keyword matching and recency.
     */
    public List<MemoryEntry> retrieveRelevantMemories(String message, boolean isGuild, long targetId) {
        List<MemoryEntry> memories = new ArrayList<>();

        try {
            String schemaName = isGuild
                    ? schemaManagementService.getGuildSchemaName(targetId)
                    : schemaManagementService.getUserSchemaName(targetId);

            // Extract keywords from message for search
            String[] words = message.toLowerCase().split("\\s+");
            Set<String> keywords = new HashSet<>();
            for (String word : words) {
                // Skip common words
                if (word.length() > 3 && !isStopWord(word)) {
                    keywords.add(word);
                }
            }

            if (keywords.isEmpty()) {
                // If no keywords, just get recent memories
                return getRecentMemories(schemaName, 5);
            }

            // Search dialogue history for keyword matches
            List<MemoryEntry> dialogueMemories = searchDialogueHistory(schemaName, keywords, 5);
            memories.addAll(dialogueMemories);

            // Search stored memories
            List<MemoryEntry> storedMemories = searchStoredMemories(schemaName, keywords, 3);
            memories.addAll(storedMemories);

            // Search passive context
            List<MemoryEntry> contextMemories = searchPassiveContext(schemaName, keywords, 3);
            memories.addAll(contextMemories);

            // Sort by relevance and limit
            memories.sort((a, b) -> Double.compare(b.relevance(), a.relevance()));
            if (memories.size() > 10) {
                memories = memories.subList(0, 10);
            }

        } catch (Exception e) {
            logger.debug("Error retrieving memories: {}", e.getMessage());
        }

        return memories;
    }

    /**
     * Get recent dialogue history.
     */
    public List<Map<String, Object>> getRecentDialogue(boolean isGuild, long targetId, long userId, int limit) {
        if (isGuild) {
            return guildDataService.getRecentDialogue(targetId, userId, limit);
        } else {
            return userDataService.getRecentDialogue(targetId, limit);
        }
    }

    /**
     * Prune old dialogue entries when capacity is reached.
     * Keeps the most recent entries based on subscription tier.
     */
    public void pruneOldDialogue(boolean isGuild, long targetId) {
        try {
            String schemaName = isGuild
                    ? schemaManagementService.getGuildSchemaName(targetId)
                    : schemaManagementService.getUserSchemaName(targetId);

            // Get current limit
            long limit = isGuild
                    ? subscriptionService.getGuildDialogueLimit(targetId)
                    : subscriptionService.getUserDialogueLimit(targetId);

            if (limit <= 0) {
                return; // Unlimited
            }

            // Keep 80% of limit, delete oldest 20%
            long keepCount = (long) (limit * 0.8);

            String sql = "DELETE FROM " + schemaName + ".dialogue_history " +
                    "WHERE id NOT IN (" +
                    "  SELECT id FROM " + schemaName + ".dialogue_history " +
                    "  ORDER BY created_at DESC LIMIT ?" +
                    ")";

            int deleted = jdbcTemplate.update(sql, keepCount);
            if (deleted > 0) {
                logger.info("Pruned {} old dialogue entries for {} {}", deleted, isGuild ? "guild" : "user", targetId);
            }

        } catch (Exception e) {
            logger.debug("Error pruning dialogue: {}", e.getMessage());
        }
    }

    /**
     * Get memory statistics for a target.
     */
    public MemoryStats getMemoryStats(boolean isGuild, long targetId) {
        try {
            String schemaName = isGuild
                    ? schemaManagementService.getGuildSchemaName(targetId)
                    : schemaManagementService.getUserSchemaName(targetId);

            long dialogueCount = countRows(schemaName, "dialogue_history");
            long memoryCount = countRows(schemaName, "memory");
            long passiveCount = countRows(schemaName, "passive_context");

            long dialogueLimit = isGuild
                    ? subscriptionService.getGuildDialogueLimit(targetId)
                    : subscriptionService.getUserDialogueLimit(targetId);

            long memoryLimit = isGuild
                    ? subscriptionService.getGuildMemoryLimit(targetId)
                    : subscriptionService.getUserMemoryLimit(targetId);

            return new MemoryStats(
                    dialogueCount,
                    memoryCount,
                    passiveCount,
                    dialogueLimit,
                    memoryLimit
            );

        } catch (Exception e) {
            logger.debug("Error getting memory stats: {}", e.getMessage());
            return new MemoryStats(0, 0, 0, 0, 0);
        }
    }

    // ===============================
    // Private Helper Methods
    // ===============================

    private boolean tableExists(String schemaName, String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?",
                    Integer.class,
                    schemaName, tableName
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void createPassiveContextTable(String schemaName) {
        try {
            String sql = "CREATE TABLE IF NOT EXISTS " + schemaName + ".passive_context (" +
                    "id BIGSERIAL PRIMARY KEY, " +
                    "user_id BIGINT NOT NULL, " +
                    "channel_id BIGINT NOT NULL, " +
                    "content TEXT NOT NULL, " +
                    "intent VARCHAR(50), " +
                    "sentiment VARCHAR(20), " +
                    "entities JSONB, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";
            jdbcTemplate.execute(sql);

            // Create index on content for searching
            String indexSql = "CREATE INDEX IF NOT EXISTS idx_" + schemaName.replace("_", "") + "_passive_content " +
                    "ON " + schemaName + ".passive_context USING gin(to_tsvector('english', content))";
            jdbcTemplate.execute(indexSql);

        } catch (Exception e) {
            logger.debug("Error creating passive_context table: {}", e.getMessage());
        }
    }

    private long countRows(String schemaName, String tableName) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schemaName + "." + tableName,
                    Long.class
            );
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private List<MemoryEntry> getRecentMemories(String schemaName, int limit) {
        List<MemoryEntry> memories = new ArrayList<>();
        try {
            String sql = "SELECT 'dialogue' as type, user_message as content, created_at FROM " + schemaName + ".dialogue_history " +
                    "ORDER BY created_at DESC LIMIT ?";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, limit);
            for (Map<String, Object> row : rows) {
                memories.add(new MemoryEntry(
                        (String) row.get("type"),
                        (String) row.get("content"),
                        0.5,
                        ((Timestamp) row.get("created_at")).toLocalDateTime()
                ));
            }
        } catch (Exception e) {
            logger.debug("Error getting recent memories: {}", e.getMessage());
        }
        return memories;
    }

    private List<MemoryEntry> searchDialogueHistory(String schemaName, Set<String> keywords, int limit) {
        List<MemoryEntry> memories = new ArrayList<>();
        try {
            String searchPattern = "%" + String.join("% OR user_message ILIKE %", keywords) + "%";
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT user_message, bot_response, created_at FROM ").append(schemaName).append(".dialogue_history WHERE ");

            List<Object> params = new ArrayList<>();
            boolean first = true;
            for (String keyword : keywords) {
                if (!first) sql.append(" OR ");
                sql.append("user_message ILIKE ?");
                params.add("%" + keyword + "%");
                first = false;
            }
            sql.append(" ORDER BY created_at DESC LIMIT ?");
            params.add(limit);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            for (Map<String, Object> row : rows) {
                String content = (String) row.get("user_message");
                double relevance = calculateRelevance(content, keywords);
                memories.add(new MemoryEntry(
                        "dialogue",
                        content + " -> " + row.get("bot_response"),
                        relevance,
                        ((Timestamp) row.get("created_at")).toLocalDateTime()
                ));
            }
        } catch (Exception e) {
            logger.debug("Error searching dialogue history: {}", e.getMessage());
        }
        return memories;
    }

    private List<MemoryEntry> searchStoredMemories(String schemaName, Set<String> keywords, int limit) {
        List<MemoryEntry> memories = new ArrayList<>();
        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT key, value, created_at FROM ").append(schemaName).append(".memory WHERE ");

            List<Object> params = new ArrayList<>();
            boolean first = true;
            for (String keyword : keywords) {
                if (!first) sql.append(" OR ");
                sql.append("key ILIKE ? OR value ILIKE ?");
                params.add("%" + keyword + "%");
                params.add("%" + keyword + "%");
                first = false;
            }
            sql.append(" ORDER BY created_at DESC LIMIT ?");
            params.add(limit);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            for (Map<String, Object> row : rows) {
                String content = row.get("key") + ": " + row.get("value");
                double relevance = calculateRelevance(content, keywords);
                memories.add(new MemoryEntry(
                        "memory",
                        content,
                        relevance,
                        ((Timestamp) row.get("created_at")).toLocalDateTime()
                ));
            }
        } catch (Exception e) {
            logger.debug("Error searching stored memories: {}", e.getMessage());
        }
        return memories;
    }

    private List<MemoryEntry> searchPassiveContext(String schemaName, Set<String> keywords, int limit) {
        List<MemoryEntry> memories = new ArrayList<>();
        try {
            if (!tableExists(schemaName, "passive_context")) {
                return memories;
            }

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT content, created_at FROM ").append(schemaName).append(".passive_context WHERE ");

            List<Object> params = new ArrayList<>();
            boolean first = true;
            for (String keyword : keywords) {
                if (!first) sql.append(" OR ");
                sql.append("content ILIKE ?");
                params.add("%" + keyword + "%");
                first = false;
            }
            sql.append(" ORDER BY created_at DESC LIMIT ?");
            params.add(limit);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            for (Map<String, Object> row : rows) {
                String content = (String) row.get("content");
                double relevance = calculateRelevance(content, keywords) * 0.8; // Lower weight for passive context
                memories.add(new MemoryEntry(
                        "context",
                        content,
                        relevance,
                        ((Timestamp) row.get("created_at")).toLocalDateTime()
                ));
            }
        } catch (Exception e) {
            logger.debug("Error searching passive context: {}", e.getMessage());
        }
        return memories;
    }

    private double calculateRelevance(String content, Set<String> keywords) {
        if (content == null || content.isEmpty()) return 0.0;

        String lower = content.toLowerCase();
        int matches = 0;
        for (String keyword : keywords) {
            if (lower.contains(keyword)) {
                matches++;
            }
        }
        return (double) matches / keywords.size();
    }

    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of(
                "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
                "have", "has", "had", "do", "does", "did", "will", "would", "could",
                "should", "can", "may", "might", "must", "shall", "this", "that",
                "these", "those", "i", "you", "he", "she", "it", "we", "they",
                "my", "your", "his", "her", "its", "our", "their", "and", "or",
                "but", "if", "then", "than", "so", "as", "at", "by", "for", "from",
                "in", "into", "of", "on", "to", "with", "about", "what", "which",
                "who", "whom", "how", "when", "where", "why"
        );
        return stopWords.contains(word.toLowerCase());
    }

    private String entitiesToJson(Map<String, List<String>> entities) {
        if (entities == null || entities.isEmpty()) {
            return "{}";
        }

        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, List<String>> entry : entities.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":[");
            boolean firstVal = true;
            for (String val : entry.getValue()) {
                if (!firstVal) json.append(",");
                json.append("\"").append(val.replace("\"", "\\\"")).append("\"");
                firstVal = false;
            }
            json.append("]");
            first = false;
        }
        json.append("}");
        return json.toString();
    }

    // ===============================
    // Inner Classes / Records
    // ===============================

    /**
     * A memory entry retrieved from storage.
     */
    public record MemoryEntry(
            String type,        // "dialogue", "memory", "context"
            String content,
            double relevance,
            LocalDateTime timestamp
    ) {}

    /**
     * Memory statistics for a target.
     */
    public record MemoryStats(
            long dialogueCount,
            long memoryCount,
            long passiveContextCount,
            long dialogueLimit,
            long memoryLimit
    ) {
        public double dialogueUsagePercent() {
            if (dialogueLimit <= 0) return 0;
            return (double) dialogueCount / dialogueLimit * 100;
        }

        public double memoryUsagePercent() {
            if (memoryLimit <= 0) return 0;
            return (double) (memoryCount + passiveContextCount) / memoryLimit * 100;
        }
    }
}

