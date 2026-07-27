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
package group.worldstandard.pudel.core.brain.context;

import group.worldstandard.pudel.core.config.brain.PudelBrainConfig;
import group.worldstandard.pudel.core.config.brain.PudelBrainConfig.PassiveContext;
import group.worldstandard.pudel.core.service.SchemaManagementService;
import group.worldstandard.pudel.core.service.MemoryEmbeddingService;
import group.worldstandard.pudel.core.brain.ollama.OllamaClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.messages.MessageSnapshot;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Processes passive context collection for the reworked PudelBrain.
 * <p>
 * Collects messages that Pudel observes but doesn't directly respond to,
 * building understanding of ongoing conversations. Messages are queued,
 * deduplicated (newest per user+channel), and expire after a configurable age.
 * <p>
 * Key features:
 * - Deduplication: only keeps newest message per user+channel
 * - Age expiration: drops messages older than configured max age
 * - Rate limiting: processes in controlled batches
 * - Entity extraction: extracts users, channels, roles, emojis, URLs, attachments
 * - Message ID tracking: stores the Discord message ID for each context entry
 * - Reply tracking: captures reply-to references
 * - Forwarded message tracking: captures forwarded message data
 * - Attachment URL collection: stores Discord CDN links
 * <p>
 * Unlike the old processor, this version does NOT use LLM analysis for
 * passive context. Instead, it stores raw messages with extracted entities
 * for later retrieval via MCP tools when the LLM needs context.
 */
@Component
public class PassiveContextProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PassiveContextProcessor.class);

    private final PudelBrainConfig brainConfig;
    private final EntityExtractor entityExtractor;
    private final JdbcTemplate jdbcTemplate;
    private final SchemaManagementService schemaManagementService;
    private final MemoryEmbeddingService memoryEmbeddingService;
    private final OllamaClient ollamaClient;

    // Queue for incoming passive context messages
    private final ConcurrentLinkedQueue<PendingContext> pendingQueue = new ConcurrentLinkedQueue<>();

    // Deduplication map: "userId:channelId" -> messageId (keeps newest)
    private final ConcurrentHashMap<String, Long> latestMessageIds = new ConcurrentHashMap<>();

    // Statistics
    private final AtomicLong totalSubmitted = new AtomicLong(0);
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalExpired = new AtomicLong(0);
    private final AtomicLong totalDeduplicated = new AtomicLong(0);

    /**
     * Inner record to hold a pending context message with routing information.
     */
    private record PendingContext(
            long messageId,
            long userId,
            long channelId,
            MessageReceivedEvent event,
            long targetId,
            boolean isGuild,
            Instant timestamp
    ) {
        PendingContext(long messageId, long userId, long channelId,
                       MessageReceivedEvent event, long targetId, boolean isGuild) {
            this(messageId, userId, channelId, event, targetId, isGuild, Instant.now());
        }
    }

    public PassiveContextProcessor(PudelBrainConfig brainConfig, EntityExtractor entityExtractor,
                                    JdbcTemplate jdbcTemplate, SchemaManagementService schemaManagementService,
                                    MemoryEmbeddingService memoryEmbeddingService, OllamaClient ollamaClient) {
        this.brainConfig = brainConfig;
        this.entityExtractor = entityExtractor;
        this.jdbcTemplate = jdbcTemplate;
        this.schemaManagementService = schemaManagementService;
        this.memoryEmbeddingService = memoryEmbeddingService;
        this.ollamaClient = ollamaClient;
    }

    @PostConstruct
    public void init() {
        PassiveContext config = brainConfig.getPassiveContext();
        if (config.isEnabled()) {
            logger.info("PassiveContextProcessor initialized: maxQueueSize={}, maxAgeMs={}, batchSize={}",
                    config.getMaxQueueSize(), config.getMaxAgeMs(), config.getBatchSize());
        } else {
            logger.info("PassiveContextProcessor disabled");
        }
    }

    @PreDestroy
    public void shutdown() {
        logger.info("PassiveContextProcessor shutting down. Stats: submitted={}, processed={}, expired={}, deduplicated={}",
                totalSubmitted.get(), totalProcessed.get(), totalExpired.get(), totalDeduplicated.get());
    }

    /**
     * Submit a message for passive context collection.
     * <p>
     * The message is queued for later processing. Deduplication happens
     * at submission time (only newest message per user+channel is kept).
     *
     * @param event the message received event
     * @param targetId guild ID (if isGuild) or user ID (if DM) for schema routing
     * @param isGuild whether this is a guild context
     */
    public void submit(MessageReceivedEvent event, long targetId, boolean isGuild) {
        if (!brainConfig.getPassiveContext().isEnabled()) {
            return;
        }

        // Skip bot messages
        if (event.getAuthor().isBot()) {
            return;
        }

        long messageId = event.getMessage().getIdLong();
        long userId = event.getAuthor().getIdLong();
        long channelId = event.getChannel().getIdLong();
        String dedupKey = userId + ":" + channelId;

        // Deduplication: only keep newest message per user+channel
        Long existing = latestMessageIds.put(dedupKey, messageId);
        if (existing != null && existing != messageId) {
            totalDeduplicated.incrementAndGet();
            pendingQueue.removeIf(p -> p.userId == userId && p.channelId == channelId);
        }

        PassiveContext config = brainConfig.getPassiveContext();
        if (pendingQueue.size() >= config.getMaxQueueSize()) {
            pendingQueue.poll();
        }

        pendingQueue.offer(new PendingContext(messageId, userId, channelId, event, targetId, isGuild));
        totalSubmitted.incrementAndGet();
    }

    /**
     * Process pending context entries in batches.
     */
    @Scheduled(fixedDelayString = "${pudel.brain.passive-context.processing-interval-ms:1000}")
    public void processBatch() {
        if (!brainConfig.getPassiveContext().isEnabled() || pendingQueue.isEmpty()) {
            return;
        }

        PassiveContext config = brainConfig.getPassiveContext();
        int batchSize = config.getBatchSize();
        long maxAgeMs = config.getMaxAgeMs();
        Instant cutoffTime = Instant.now().minusNanos(maxAgeMs * 1_000_000);

        List<PendingContext> batch = new ArrayList<>();
        PendingContext pending;
        int expired = 0;

        while ((pending = pendingQueue.poll()) != null) {
            if (pending.timestamp.isBefore(cutoffTime)) {
                expired++;
                continue;
            }

            String dedupKey = pending.userId + ":" + pending.channelId;
            Long latest = latestMessageIds.get(dedupKey);
            if (latest != null && latest != pending.messageId) {
                continue;
            }

            batch.add(pending);
            if (batch.size() >= batchSize) {
                break;
            }
        }

        if (expired > 0) {
            totalExpired.addAndGet(expired);
        }

        if (!batch.isEmpty()) {
            processEntries(batch);
            totalProcessed.addAndGet(batch.size());
        }
    }

    /**
     * Process a batch of pending context entries.
     */
    private void processEntries(List<PendingContext> batch) {
        for (PendingContext pending : batch) {
            try {
                PassiveContextEntry entry = buildContextEntry(pending.event, brainConfig.getPassiveContext());
                storeContextEntry(entry, pending.targetId, pending.isGuild);
            } catch (Exception e) {
                logger.debug("Error processing passive context for message {}: {}",
                        pending.messageId, e.getMessage());
            }
        }
    }

    /**
     * Build a PassiveContextEntry from a message event.
     */
    private PassiveContextEntry buildContextEntry(MessageReceivedEvent event, PassiveContext config) {
        Message message = event.getMessage();
        long messageId = message.getIdLong();
        long userId = event.getAuthor().getIdLong();
        long channelId = event.getChannel().getIdLong();
        String content = message.getContentRaw();

        Map<String, List<String>> entities = config.isExtractEntities()
                ? entityExtractor.extractEntities(event)
                : Map.of();

        List<String> attachmentUrls = config.isTrackAttachments()
                ? entityExtractor.extractAttachmentUrls(message)
                : List.of();

        Long replyToMessageId = null;
        Message referenced = message.getReferencedMessage();
        if (referenced != null) {
            replyToMessageId = referenced.getIdLong();
        }

        List<PassiveContextEntry.ForwardedMessageRef> forwardedMessages = buildForwardedRefs(message, entities);

        // If content is empty but we have forwarded messages, use the first forwarded content as the main content
        if (content.isBlank() && !forwardedMessages.isEmpty()) {
            content = forwardedMessages.getFirst().content();
            if (content == null || content.isBlank()) {
                content = "[Forwarded message]";
            }
        }

        return new PassiveContextEntry(
                messageId, userId, channelId, content,
                entities, attachmentUrls, replyToMessageId,
                forwardedMessages, Instant.now()
        );
    }

    /**
     * Build forwarded message references from MessageSnapshot API.
     * Uses message.getMessageSnapshots() to properly access forwarded message content.
     * <p>
     * In JDA 6.x, forwarded messages are stored as MessageSnapshot objects, not embeds.
     * The MessageSnapshot contains the original message content, attachments, embeds, etc.
     */
    private List<PassiveContextEntry.ForwardedMessageRef> buildForwardedRefs(Message message,
            Map<String, List<String>> entities) {
        List<PassiveContextEntry.ForwardedMessageRef> refs = new ArrayList<>();

        // Use MessageSnapshot API to get forwarded message content (JDA 6.x)
        List<MessageSnapshot> snapshots = message.getMessageSnapshots();
        if (!snapshots.isEmpty()) {
            for (MessageSnapshot snapshot : snapshots) {
                String content = snapshot.getContentRaw();

                // Build full content from snapshot
                StringBuilder fullContent = new StringBuilder();
                if (!content.isBlank()) {
                    fullContent.append(content);
                }

                // Include embed content from snapshot
                if (!snapshot.getEmbeds().isEmpty()) {
                    for (var embed : snapshot.getEmbeds()) {
                        if (embed.getTitle() != null && !embed.getTitle().isBlank()) {
                            if (!fullContent.isEmpty()) fullContent.append("\n");
                            fullContent.append("**").append(embed.getTitle()).append("**");
                        }
                        if (embed.getDescription() != null && !embed.getDescription().isBlank()) {
                            if (!fullContent.isEmpty()) fullContent.append("\n");
                            fullContent.append(embed.getDescription());
                        }
                        for (var field : embed.getFields()) {
                            if (!fullContent.isEmpty()) fullContent.append("\n");
                            fullContent.append(field.getName()).append(": ").append(field.getValue());
                        }
                    }
                }

                // Include attachment info from snapshot
                if (!snapshot.getAttachments().isEmpty()) {
                    if (!fullContent.isEmpty()) fullContent.append("\n");
                    fullContent.append("[Attachments: ").append(snapshot.getAttachments().size()).append("]");
                }

                // Only add if we have meaningful content
                if (!fullContent.isEmpty()) {
                    refs.add(new PassiveContextEntry.ForwardedMessageRef(fullContent.toString()));
                }
            }
        }

        // Fallback: Also check message embeds directly for backward compatibility
        if (refs.isEmpty() && !message.getEmbeds().isEmpty()) {
            for (var embed : message.getEmbeds()) {
                String content = embed.getDescription() != null ? embed.getDescription() : "";
                // Only add if we have meaningful content
                if (!content.isBlank() || !embed.getFields().isEmpty()) {
                    // Build content from embed fields
                    StringBuilder fullContent = new StringBuilder(content);
                    for (var field : embed.getFields()) {
                        if (!fullContent.isEmpty()) fullContent.append("\n");
                        fullContent.append(field.getName()).append(": ").append(field.getValue());
                    }
                    refs.add(new PassiveContextEntry.ForwardedMessageRef(fullContent.toString()));
                }
            }
        }

        return refs;
    }

    /**
     * Store a context entry in the database.
     * Stores to the guild/user schema's passive_context table with message_id, entities,
     * attachment_urls and a forwarded_message_id reference. Forwarded message content is
     * recorded in the dedicated forwarded_messages table (author left empty, since Discord
     * does not expose the original author via MessageSnapshot). The message text is also
     * embedded via Ollama (when pgvector + embeddings are enabled) for semantic search.
     *
     * @param entry the context entry to store
     * @param targetId guild ID (if isGuild) or user ID (if DM) for schema routing
     * @param isGuild whether this is a guild context
     */
    private void storeContextEntry(PassiveContextEntry entry, long targetId, boolean isGuild) {
        try {
            String schemaName;
            if (isGuild) {
                schemaName = schemaManagementService.getGuildSchemaName(targetId);
            } else {
                schemaName = schemaManagementService.getUserSchemaName(targetId);
            }

            if (schemaName == null) {
                logger.debug("No schema found for target {}, skipping passive context storage", targetId);
                return;
            }

            // Convert entities to JSON
            String entitiesJson = entitiesToJson(entry.entities());
            String attachmentUrlsArray = entry.attachmentUrls().isEmpty() ? "{}"
                    : "{\"" + String.join("\",\"", entry.attachmentUrls()) + "\"}";

            // Reference to the first forwarded message (Discord does not expose the
            // original message id via MessageSnapshot, so it is 0 when unknown).
            Long forwardedMessageId = entry.forwardedMessages().isEmpty()
                    ? null
                    : entry.forwardedMessages().getFirst().messageId();

            String sql = "INSERT INTO " + schemaName + ".passive_context " +
                    "(message_id, user_id, channel_id, content, entities, attachment_urls, forwarded_message_id, created_at) " +
                    "VALUES (?, ?, ?, ?, ?::jsonb, ?::text[], ?, ?) " +
                    "ON CONFLICT (message_id) DO UPDATE SET " +
                    "content = EXCLUDED.content, entities = EXCLUDED.entities, " +
                    "attachment_urls = EXCLUDED.attachment_urls, forwarded_message_id = EXCLUDED.forwarded_message_id";

            jdbcTemplate.update(sql,
                    entry.messageId(),
                    entry.userId(),
                    entry.channelId(),
                    entry.content(),
                    entitiesJson,
                    "{" + entry.attachmentUrls().stream().map(s -> "\"" + s + "\"").collect(java.util.stream.Collectors.joining(",")) + "}",
                    forwardedMessageId,
                    Timestamp.from(entry.timestamp())
            );

            // Record forwarded messages in the dedicated table (author intentionally empty).
            if (!entry.forwardedMessages().isEmpty()) {
                Long passiveContextId = jdbcTemplate.queryForObject(
                        "SELECT id FROM " + schemaName + ".passive_context WHERE message_id = ?",
                        Long.class, entry.messageId());
                if (passiveContextId != null) {
                    for (PassiveContextEntry.ForwardedMessageRef fwd : entry.forwardedMessages()) {
                        jdbcTemplate.update(
                                "INSERT INTO " + schemaName + ".forwarded_messages " +
                                        "(passive_context_id, message_id, author_id, author_name, content) " +
                                        "VALUES (?, ?, NULL, NULL, ?)",
                                passiveContextId, fwd.messageId(), fwd.content());
                    }
                }
            }

            // Embed the message text for semantic search (no-op if pgvector/embeddings disabled).
            Long passiveContextId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + schemaName + ".passive_context WHERE message_id = ?",
                    Long.class, entry.messageId());
            if (passiveContextId != null) {
                memoryEmbeddingService.storePassiveContextEmbedding(schemaName, passiveContextId, entry.content());
            }

            logger.debug("Stored passive context: msg={} user={} channel={} schema={}",
                    entry.messageId(), entry.userId(), entry.channelId(), schemaName);

        } catch (Exception e) {
            logger.debug("Error storing passive context for message {}: {}",
                    entry.messageId(), e.getMessage());
        }
    }

    /**
     * Convert entities map to JSON string.
     */
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

    /**
     * Get recent passive context as a formatted string for inclusion in the user message.
     * This is used when the bot is mentioned and needs to see recent forwarded content.
     */
    public String getRecentContextForChannel(long channelId, boolean isGuild, long targetId, int limit) {
        List<PassiveContextEntry> entries = getRecentContext(channelId, isGuild, targetId, limit);
        if (entries.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (PassiveContextEntry entry : entries) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append("[Message ").append(entry.messageId()).append("] ");
            sb.append("<@").append(entry.userId()).append(">: ");
            sb.append(entry.content());
            if (!entry.forwardedMessages().isEmpty()) {
                for (var fwd : entry.forwardedMessages()) {
                    sb.append("\n  [Forwarded]: ");
                    sb.append(fwd.content());
                }
            }
        }
        return sb.toString();
    }

    /**
     * Retrieve recent passive context for a channel.
     * Queries the database for the most recent passive context entries.
     */
    public List<PassiveContextEntry> getRecentContext(long channelId, boolean isGuild, long targetId, int limit) {
        try {
            String schemaName = isGuild
                    ? schemaManagementService.getGuildSchemaName(targetId)
                    : schemaManagementService.getUserSchemaName(targetId);

            // Check if passive_context table exists
            Integer tableExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'passive_context'",
                    Integer.class, schemaName
            );
            if (tableExists == null || tableExists == 0) {
                return List.of();
            }

            String sql = "SELECT message_id, user_id, channel_id, content, entities, attachment_urls, " +
                    "forwarded_message_id, created_at FROM " + schemaName + ".passive_context " +
                    "WHERE channel_id = ? ORDER BY created_at DESC LIMIT ?";

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, channelId, limit);

            List<PassiveContextEntry> entries = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                // Handle JSONB columns that may be returned as PGobject instead of String
                String entitiesJson = extractStringValue(row.get("entities"));
                String attachmentUrlsStr = extractStringValue(row.get("attachment_urls"));

                Map<String, List<String>> entities = parseEntitiesJson(entitiesJson);
                List<String> attachmentUrls = parseTextArray(attachmentUrlsStr);

                // Handle null values that may exist in old data
                Number messageIdNum = (Number) row.get("message_id");
                Number userIdNum = (Number) row.get("user_id");
                Number channelIdNum = (Number) row.get("channel_id");
                Timestamp createdAt = (Timestamp) row.get("created_at");

                if (messageIdNum == null || userIdNum == null || channelIdNum == null || createdAt == null) {
                    logger.debug("Skipping passive context entry with null values: msgId={}, userId={}, channelId={}",
                            messageIdNum, userIdNum, channelIdNum);
                    continue;
                }

                List<PassiveContextEntry.ForwardedMessageRef> forwardedRefs =
                        fetchForwardedMessages(schemaName, messageIdNum.longValue());

                entries.add(new PassiveContextEntry(
                        messageIdNum.longValue(),
                        userIdNum.longValue(),
                        channelIdNum.longValue(),
                        (String) row.get("content"),
                        entities,
                        attachmentUrls,
                        null, // replyToMessageId not stored directly in passive_context
                        forwardedRefs,
                        createdAt.toInstant()
                ));
            }
            return entries;

        } catch (Exception e) {
            logger.debug("Error getting recent context for channel {}: {}", channelId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetch forwarded message references for a passive context entry from the
     * dedicated forwarded_messages table.
     */
    private List<PassiveContextEntry.ForwardedMessageRef> fetchForwardedMessages(String schemaName, long messageId) {
        try {
            Long passiveContextId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + schemaName + ".passive_context WHERE message_id = ?",
                    Long.class, messageId);
            if (passiveContextId == null) {
                return List.of();
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT message_id, content FROM " + schemaName + ".forwarded_messages WHERE passive_context_id = ?",
                    passiveContextId);
            List<PassiveContextEntry.ForwardedMessageRef> refs = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Number fwdMsgId = (Number) row.get("message_id");
                String content = extractStringValue(row.get("content"));
                refs.add(new PassiveContextEntry.ForwardedMessageRef(
                        fwdMsgId != null ? fwdMsgId.longValue() : 0L, content));
            }
            return refs;
        } catch (Exception e) {
            logger.debug("Error fetching forwarded messages for {}: {}", messageId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetch a specific message from passive context by message ID.
     */
    public PassiveContextEntry fetchByMessageId(long messageId, long channelId, boolean isGuild, long targetId) {
        try {
            String schemaName = isGuild
                    ? schemaManagementService.getGuildSchemaName(targetId)
                    : schemaManagementService.getUserSchemaName(targetId);

            // First check the in-memory queue
            for (PendingContext pending : pendingQueue) {
                if (pending.messageId == messageId) {
                    PassiveContext config = brainConfig.getPassiveContext();
                    return buildContextEntry(pending.event, config);
                }
            }

            // Then check the database
            String sql = "SELECT message_id, user_id, channel_id, content, entities, attachment_urls, " +
                    "forwarded_message_id, created_at FROM " + schemaName + ".passive_context " +
                    "WHERE message_id = ?";

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, messageId);
            if (rows.isEmpty()) {
                return null;
            }

            Map<String, Object> row = rows.getFirst();
            // Handle JSONB columns that may be returned as PGobject instead of String
            String entitiesJson = extractStringValue(row.get("entities"));
            String attachmentUrlsStr = extractStringValue(row.get("attachment_urls"));

            Map<String, List<String>> entities = parseEntitiesJson(entitiesJson);
            List<String> attachmentUrls = parseTextArray(attachmentUrlsStr);

            // Parse forwarded content from the dedicated table
            List<PassiveContextEntry.ForwardedMessageRef> forwardedRefs =
                    fetchForwardedMessages(schemaName, messageId);

            return new PassiveContextEntry(
                    ((Number) row.get("message_id")).longValue(),
                    ((Number) row.get("user_id")).longValue(),
                    ((Number) row.get("channel_id")).longValue(),
                    (String) row.get("content"),
                    entities,
                    attachmentUrls,
                    null, // replyToMessageId not stored in passive_context
                    forwardedRefs,
                    ((Timestamp) row.get("created_at")).toInstant()
            );

        } catch (Exception e) {
            logger.debug("Error fetching message {} from passive context: {}", messageId, e.getMessage());
            return null;
        }
    }

    /**
     * Parse entities JSON string back to a Map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, List<String>> parseEntitiesJson(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) {
            return Map.of();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * Extract a String value from a database column that may be returned as
     * PGobject (for JSONB/TEXT[] types) instead of String.
     */
    private String extractStringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return str;
        }
        // Handle PGobject (JSONB, TEXT[], etc.)
        return value.toString();
    }

    /**
     * Parse a PostgreSQL text array string to a List.
     */
    private List<String> parseTextArray(String arrayStr) {
        if (arrayStr == null || arrayStr.isBlank() || arrayStr.equals("{}")) {
            return List.of();
        }
        // Remove curly braces and split by comma
        String trimmed = arrayStr.substring(1, arrayStr.length() - 1);
        if (trimmed.isBlank()) {
            return List.of();
        }
        return Arrays.stream(trimmed.split(","))
                .map(s -> s.replace("\"", "").trim())
                .filter(s -> !s.isBlank())
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get queue statistics.
     */
    public QueueStats getStats() {
        return new QueueStats(
                pendingQueue.size(),
                totalSubmitted.get(),
                totalProcessed.get(),
                totalExpired.get(),
                totalDeduplicated.get()
        );
    }

    // ===============================
    // Inner Types
    // ===============================

    public record QueueStats(
            int queueSize,
            long totalSubmitted,
            long totalProcessed,
            long totalExpired,
            long totalDeduplicated
    ) {}
}

