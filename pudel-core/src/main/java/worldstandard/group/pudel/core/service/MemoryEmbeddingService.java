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
package worldstandard.group.pudel.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import worldstandard.group.pudel.core.config.ChatbotConfig;
import worldstandard.group.pudel.core.config.MemoryConfig;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for managing vector embeddings and semantic memory search.
 * Uses PostgreSQL with pgvector extension for IVFFlat indexing.
 * <p>
 * This service manages the embedding tables in guild/user schemas
 * for semantic similarity search of memories and dialogue history.
 */
@Service
@Transactional
public class MemoryEmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryEmbeddingService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ChatbotConfig chatbotConfig;
    private final MemoryConfig memoryConfig;
    private final SchemaManagementService schemaManagementService;

    // Flag to track if pgvector is available
    private Boolean pgvectorAvailable = null;

    public MemoryEmbeddingService(JdbcTemplate jdbcTemplate,
                                   ChatbotConfig chatbotConfig,
                                   MemoryConfig memoryConfig,
                                   SchemaManagementService schemaManagementService) {
        this.jdbcTemplate = jdbcTemplate;
        this.chatbotConfig = chatbotConfig;
        this.memoryConfig = memoryConfig;
        this.schemaManagementService = schemaManagementService;
    }

    /**
     * Check if pgvector extension is available.
     */
    public boolean isPgvectorAvailable() {
        if (pgvectorAvailable == null) {
            try {
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
                        Integer.class
                );
                pgvectorAvailable = count != null && count > 0;

                if (!pgvectorAvailable) {
                    logger.warn("pgvector extension not found. Semantic search will be disabled. " +
                            "Run 'CREATE EXTENSION vector;' to enable it.");
                } else {
                    logger.info("pgvector extension is available");
                }
            } catch (Exception e) {
                logger.warn("Error checking for pgvector: {}. Semantic search will be disabled.", e.getMessage());
                pgvectorAvailable = false;
            }
        }
        return pgvectorAvailable;
    }

    /**
     * Create embedding tables in a guild schema.
     */
    public void createGuildEmbeddingTables(long guildId) {
        if (!chatbotConfig.getEmbedding().isEnabled() || !isPgvectorAvailable()) {
            return;
        }

        String schemaName = schemaManagementService.getGuildSchemaName(guildId);
        int dimension = chatbotConfig.getEmbedding().getDimension();

        try {
            // Create memory embeddings table
            String memoryEmbeddingsTable = String.format("""
                CREATE TABLE IF NOT EXISTS %s.memory_embeddings (
                    id BIGSERIAL PRIMARY KEY,
                    memory_id BIGINT REFERENCES %s.memory(id) ON DELETE CASCADE,
                    embedding vector(%d) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """, schemaName, schemaName, dimension);
            jdbcTemplate.execute(memoryEmbeddingsTable);

            // Create dialogue embeddings table
            String dialogueEmbeddingsTable = String.format("""
                CREATE TABLE IF NOT EXISTS %s.dialogue_embeddings (
                    id BIGSERIAL PRIMARY KEY,
                    dialogue_id BIGINT REFERENCES %s.dialogue_history(id) ON DELETE CASCADE,
                    embedding vector(%d) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """, schemaName, schemaName, dimension);
            jdbcTemplate.execute(dialogueEmbeddingsTable);

            // Create IVFFlat indexes
            createIvfFlatIndex(schemaName, "memory_embeddings", "embedding", dimension);
            createIvfFlatIndex(schemaName, "dialogue_embeddings", "embedding", dimension);

            logger.info("Created embedding tables for guild schema: {}", schemaName);
        } catch (Exception e) {
            logger.error("Error creating embedding tables for guild {}: {}", guildId, e.getMessage());
        }
    }

    /**
     * Create embedding tables in a user schema.
     */
    public void createUserEmbeddingTables(long userId) {
        if (!chatbotConfig.getEmbedding().isEnabled() || !isPgvectorAvailable()) {
            return;
        }

        String schemaName = schemaManagementService.getUserSchemaName(userId);
        int dimension = chatbotConfig.getEmbedding().getDimension();

        try {
            // Create memory embeddings table
            String memoryEmbeddingsTable = String.format("""
                CREATE TABLE IF NOT EXISTS %s.memory_embeddings (
                    id BIGSERIAL PRIMARY KEY,
                    memory_id BIGINT REFERENCES %s.memory(id) ON DELETE CASCADE,
                    embedding vector(%d) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """, schemaName, schemaName, dimension);
            jdbcTemplate.execute(memoryEmbeddingsTable);

            // Create dialogue embeddings table
            String dialogueEmbeddingsTable = String.format("""
                CREATE TABLE IF NOT EXISTS %s.dialogue_embeddings (
                    id BIGSERIAL PRIMARY KEY,
                    dialogue_id BIGINT REFERENCES %s.dialogue_history(id) ON DELETE CASCADE,
                    embedding vector(%d) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """, schemaName, schemaName, dimension);
            jdbcTemplate.execute(dialogueEmbeddingsTable);

            // Create IVFFlat indexes
            createIvfFlatIndex(schemaName, "memory_embeddings", "embedding", dimension);
            createIvfFlatIndex(schemaName, "dialogue_embeddings", "embedding", dimension);

            logger.info("Created embedding tables for user schema: {}", schemaName);
        } catch (Exception e) {
            logger.error("Error creating embedding tables for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Create IVFFlat index on an embedding column.
     */
    private void createIvfFlatIndex(String schemaName, String tableName, String columnName, int dimension) {
        int lists = chatbotConfig.getEmbedding().getIvfLists();
        String indexName = String.format("idx_%s_%s_ivfflat", tableName, columnName);

        try {
            // Check if index already exists
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = ? AND indexname = ?",
                    Integer.class,
                    schemaName, indexName
            );

            if (exists != null && exists > 0) {
                logger.debug("IVFFlat index {} already exists", indexName);
                return;
            }

            // Create IVFFlat index for cosine similarity
            String createIndex = String.format(
                    "CREATE INDEX %s ON %s.%s USING ivfflat (%s vector_cosine_ops) WITH (lists = %d)",
                    indexName, schemaName, tableName, columnName, lists
            );
            jdbcTemplate.execute(createIndex);

            logger.info("Created IVFFlat index: {}.{}", schemaName, indexName);
        } catch (Exception e) {
            logger.warn("Could not create IVFFlat index {} (may need more data): {}", indexName, e.getMessage());
        }
    }

    /**
     * Store an embedding for a memory entry.
     */
    public void storeMemoryEmbedding(String schemaName, long memoryId, float[] embedding) {
        if (!isPgvectorAvailable()) return;

        try {
            String vectorString = vectorToString(embedding);
            jdbcTemplate.update(
                    String.format("INSERT INTO %s.memory_embeddings (memory_id, embedding) VALUES (?, ?::vector)", schemaName),
                    memoryId, vectorString
            );
        } catch (Exception e) {
            logger.error("Error storing memory embedding: {}", e.getMessage());
        }
    }

    /**
     * Store an embedding for a dialogue entry.
     */
    public void storeDialogueEmbedding(String schemaName, long dialogueId, float[] embedding) {
        if (!isPgvectorAvailable()) return;

        try {
            String vectorString = vectorToString(embedding);
            jdbcTemplate.update(
                    String.format("INSERT INTO %s.dialogue_embeddings (dialogue_id, embedding) VALUES (?, ?::vector)", schemaName),
                    dialogueId, vectorString
            );
        } catch (Exception e) {
            logger.error("Error storing dialogue embedding: {}", e.getMessage());
        }
    }

    /**
     * Search for similar memories using vector similarity.
     */
    public List<Map<String, Object>> searchSimilarMemories(String schemaName, float[] queryEmbedding, int limit) {
        if (!isPgvectorAvailable()) {
            return List.of();
        }

        try {
            String vectorString = vectorToString(queryEmbedding);
            int probes = chatbotConfig.getEmbedding().getIvfProbes();
            double minSimilarity = memoryConfig.getSemanticSearch().getMinSimilarity();

            // Set probes for this query
            jdbcTemplate.execute(String.format("SET ivfflat.probes = %d", probes));

            String sql = String.format("""
                SELECT m.*, 1 - (me.embedding <=> ?::vector) as similarity
                FROM %s.memory m
                JOIN %s.memory_embeddings me ON m.id = me.memory_id
                WHERE 1 - (me.embedding <=> ?::vector) >= ?
                ORDER BY me.embedding <=> ?::vector
                LIMIT ?
                """, schemaName, schemaName);

            return jdbcTemplate.queryForList(sql, vectorString, vectorString, minSimilarity, vectorString, limit);
        } catch (Exception e) {
            logger.error("Error searching similar memories: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Search for similar dialogue entries using vector similarity.
     */
    public List<Map<String, Object>> searchSimilarDialogue(String schemaName, float[] queryEmbedding, int limit) {
        if (!isPgvectorAvailable()) {
            return List.of();
        }

        try {
            String vectorString = vectorToString(queryEmbedding);
            int probes = chatbotConfig.getEmbedding().getIvfProbes();
            double minSimilarity = memoryConfig.getSemanticSearch().getMinSimilarity();

            jdbcTemplate.execute(String.format("SET ivfflat.probes = %d", probes));

            String sql = String.format("""
                SELECT d.*, 1 - (de.embedding <=> ?::vector) as similarity
                FROM %s.dialogue_history d
                JOIN %s.dialogue_embeddings de ON d.id = de.dialogue_id
                WHERE 1 - (de.embedding <=> ?::vector) >= ?
                ORDER BY de.embedding <=> ?::vector
                LIMIT ?
                """, schemaName, schemaName);

            return jdbcTemplate.queryForList(sql, vectorString, vectorString, minSimilarity, vectorString, limit);
        } catch (Exception e) {
            logger.error("Error searching similar dialogue: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Convert float array to PostgreSQL vector string format.
     */
    private String vectorToString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Scheduled cleanup of old dialogue entries when capacity is reached.
     * Runs daily at 3 AM.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldDialogueEntries() {
        if (!memoryConfig.getAutoCleanup().isEnabled()) {
            return;
        }

        logger.info("Starting scheduled dialogue cleanup");

        try {
            // Get all guild schemas
            List<String> schemas = jdbcTemplate.queryForList(
                    "SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'guild_%' OR schema_name LIKE 'user_%'",
                    String.class
            );

            for (String schema : schemas) {
                cleanupSchemaDialogue(schema);
            }

            logger.info("Completed scheduled dialogue cleanup for {} schemas", schemas.size());
        } catch (Exception e) {
            logger.error("Error during scheduled cleanup: {}", e.getMessage());
        }
    }

    /**
     * Cleanup old dialogue entries in a schema.
     */
    private void cleanupSchemaDialogue(String schemaName) {
        try {
            int minAgeDays = memoryConfig.getAutoCleanup().getMinAgeDays();
            int keepPercentage = memoryConfig.getAutoCleanup().getKeepPercentage();

            // Get current count
            Long currentCount = jdbcTemplate.queryForObject(
                    String.format("SELECT COUNT(*) FROM %s.dialogue_history", schemaName),
                    Long.class
            );

            if (currentCount == null || currentCount == 0) {
                return;
            }

            // Calculate how many to keep
            long keepCount = (currentCount * keepPercentage) / 100;
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(minAgeDays);

            // Delete old entries beyond the keep threshold
            String deleteSql = String.format("""
                DELETE FROM %s.dialogue_history
                WHERE id NOT IN (
                    SELECT id FROM %s.dialogue_history
                    ORDER BY created_at DESC
                    LIMIT %d
                )
                AND created_at < ?
                """, schemaName, schemaName, keepCount);

            int deleted = jdbcTemplate.update(deleteSql, cutoffDate);

            if (deleted > 0) {
                logger.info("Cleaned up {} old dialogue entries from {}", deleted, schemaName);
            }
        } catch (Exception e) {
            logger.debug("Cleanup skipped for {} (may not have dialogue table): {}", schemaName, e.getMessage());
        }
    }
}

