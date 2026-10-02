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
package group.worldstandard.pudel.core.service;

import group.worldstandard.pudel.core.service.InMemoryLogAppender.LogEntry;
import group.worldstandard.pudel.core.websocket.AdminLogWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service layer for streaming application logs to authenticated admin WebSockets.
 * Reads from the {@link InMemoryLogAppender} ring buffer and delegates live push to
 * {@link AdminLogWebSocketHandler}; there is no client heartbeat/polling interaction.
 */
@Service
public class LogService {
    private static final Logger log = LoggerFactory.getLogger(LogService.class);

    private final AdminLogWebSocketHandler adminLogWebSocketHandler;

    public LogService(AdminLogWebSocketHandler adminLogWebSocketHandler) {
        this.adminLogWebSocketHandler = adminLogWebSocketHandler;
        InMemoryLogAppender.addListener(this::broadcastLogEntry);
        log.info("LogService initialized — subscribed to InMemoryLogAppender over WebSocket");
    }

    /**
     * Get the last {@code count} log entries, optionally filtered by level.
     */
    public List<LogEntry> getRecentLogs(int count, String levelFilter) {
        List<LogEntry> entries = InMemoryLogAppender.getLastEntries(count);

        if (levelFilter != null && !levelFilter.isBlank()) {
            String upper = levelFilter.toUpperCase();
            entries = entries.stream()
                    .filter(e -> matchesLevel(e.level(), upper))
                    .toList();
        }
        return entries;
    }

    /**
     * Get all buffered log entries.
     */
    public List<LogEntry> getAllLogs() {
        return InMemoryLogAppender.getEntries();
    }

    /**
     * Clear the log buffer.
     */
    public void clearLogs() {
        InMemoryLogAppender.clearBuffer();
        log.info("Log buffer cleared by admin");
    }

    /**
     * Get buffer statistics.
     */
    public Map<String, Object> getStats() {
        List<LogEntry> all = InMemoryLogAppender.getEntries();
        long errorCount = all.stream().filter(e -> "ERROR".equals(e.level())).count();
        long warnCount = all.stream().filter(e -> "WARN".equals(e.level())).count();
        long infoCount = all.stream().filter(e -> "INFO".equals(e.level())).count();
        long debugCount = all.stream().filter(e -> "DEBUG".equals(e.level())).count();

        return Map.of(
                "bufferSize", InMemoryLogAppender.getBufferSize(),
                "maxSize", InMemoryLogAppender.MAX_ENTRIES,
                "activeStreams", adminLogWebSocketHandler.getActiveSessionCount(),
                "errorCount", errorCount,
                "warnCount", warnCount,
                "infoCount", infoCount,
                "debugCount", debugCount
        );
    }

    /**
     * Broadcast a log entry to all authenticated admin WebSocket sessions.
     */
    private void broadcastLogEntry(LogEntry entry) {
        int delivered = adminLogWebSocketHandler.broadcast(Map.of("type", "log", "entry", entry));
        if (delivered == 0) {
            log.trace("No active admin log WebSocket sessions for log entry");
        }
    }

    /**
     * Check if a log level matches the filter.
     * "ERROR" matches only ERROR; "WARN" matches WARN+ERROR; etc.
     */
    private boolean matchesLevel(String entryLevel, String filterLevel) {
        return switch (filterLevel) {
            case "ERROR" -> "ERROR".equals(entryLevel);
            case "WARN" -> "ERROR".equals(entryLevel) || "WARN".equals(entryLevel);
            case "INFO" -> "ERROR".equals(entryLevel) || "WARN".equals(entryLevel) || "INFO".equals(entryLevel);
            case "DEBUG" -> true; // all levels
            case "TRACE" -> true;
            default -> entryLevel.equalsIgnoreCase(filterLevel);
        };
    }
}
