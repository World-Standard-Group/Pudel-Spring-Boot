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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service layer for streaming application logs to admin clients via SSE.
 * <p>
 * Reads from the {@link InMemoryLogAppender} static ring buffer and
 * manages active {@link SseEmitter} connections for real-time streaming.
 */
@Service
public class LogService {

    private static final Logger log = LoggerFactory.getLogger(LogService.class);

    /** Active SSE emitters (one per connected admin client). */
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public LogService() {
        // Subscribe to new log entries from the Logback appender
        InMemoryLogAppender.addListener(this::broadcastLogEntry);
        log.info("LogService initialized — subscribed to InMemoryLogAppender");
    }

    // ------------------------------------------------------------------
    // REST helpers
    // ------------------------------------------------------------------

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
                "activeStreams", emitters.size(),
                "errorCount", errorCount,
                "warnCount", warnCount,
                "infoCount", infoCount,
                "debugCount", debugCount
        );
    }

    // ------------------------------------------------------------------
    // SSE streaming
    // ------------------------------------------------------------------

    /**
     * Create a new SSE emitter for real-time log streaming.
     *
     * @param sendHistory if true, send the last 200 entries immediately
     * @return a configured {@link SseEmitter}
     */
    public SseEmitter createStream(boolean sendHistory) {
        // 30 minute timeout for long-lived log streams
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("Log SSE stream completed. Active streams: {}", emitters.size());
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.debug("Log SSE stream timed out. Active streams: {}", emitters.size());
        });
        emitter.onError(e -> {
            emitters.remove(emitter);
            log.debug("Log SSE stream error: {}. Active streams: {}", e.getMessage(), emitters.size());
        });

        emitters.add(emitter);
        log.debug("New log SSE stream created. Active streams: {}", emitters.size());

        // Send recent history first
        if (sendHistory) {
            try {
                List<LogEntry> history = InMemoryLogAppender.getLastEntries(200);
                for (LogEntry entry : history) {
                    emitter.send(SseEmitter.event()
                            .name("log")
                            .data(entry));
                }
                emitter.send(SseEmitter.event()
                        .name("info")
                        .data(Map.of("message", "History loaded", "count", history.size())));
            } catch (IOException e) {
                emitters.remove(emitter);
                log.debug("Failed to send history to SSE emitter: {}", e.getMessage());
            }
        }

        return emitter;
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    /**
     * Broadcast a log entry to all active SSE emitters.
     */
    private void broadcastLogEntry(LogEntry entry) {
        List<SseEmitter> deadEmitters = new java.util.ArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("log")
                        .data(entry));
            } catch (IOException | IllegalStateException e) {
                deadEmitters.add(emitter);
            }
        }

        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
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

