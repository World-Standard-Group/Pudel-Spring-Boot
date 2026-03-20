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

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Custom Logback appender that stores log entries in a bounded in-memory ring buffer.
 * <p>
 * Configured in {@code logback-spring.xml} and exposes a static API so that
 * Spring-managed services (e.g. {@link LogService}) can read the buffer
 * and subscribe to new entries without circular dependency issues.
 * <p>
 * The buffer is bounded to {@link #MAX_ENTRIES} and evicts oldest entries first.
 */
public class InMemoryLogAppender extends AppenderBase<ILoggingEvent> {

    /** Maximum number of log entries to keep in the ring buffer. */
    public static final int MAX_ENTRIES = 10_000;

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());

    /** The shared ring buffer — thread-safe, bounded on append. */
    private static final Deque<LogEntry> BUFFER = new ConcurrentLinkedDeque<>();

    /** Listeners that receive every new log entry in real time. */
    private static final List<Consumer<LogEntry>> LISTENERS = new CopyOnWriteArrayList<>();

    // ------------------------------------------------------------------
    // Logback lifecycle
    // ------------------------------------------------------------------

    @Override
    protected void append(ILoggingEvent event) {
        LogEntry entry = toLogEntry(event);

        BUFFER.addLast(entry);

        // Evict oldest if over capacity
        while (BUFFER.size() > MAX_ENTRIES) {
            BUFFER.pollFirst();
        }

        // Notify real-time listeners
        for (Consumer<LogEntry> listener : LISTENERS) {
            try {
                listener.accept(entry);
            } catch (Exception ignored) {
                // Listener may have been removed concurrently
            }
        }
    }

    // ------------------------------------------------------------------
    // Static API for LogService
    // ------------------------------------------------------------------

    /**
     * Get a snapshot of the current ring buffer (oldest → newest).
     */
    public static List<LogEntry> getEntries() {
        return new ArrayList<>(BUFFER);
    }

    /**
     * Get the last {@code n} entries.
     */
    public static List<LogEntry> getLastEntries(int n) {
        List<LogEntry> all = new ArrayList<>(BUFFER);
        int size = all.size();
        return size <= n ? all : all.subList(size - n, size);
    }

    /**
     * Clear the buffer.
     */
    public static void clearBuffer() {
        BUFFER.clear();
    }

    /**
     * Subscribe to real-time log entries.
     */
    public static void addListener(Consumer<LogEntry> listener) {
        LISTENERS.add(listener);
    }

    /**
     * Unsubscribe from real-time log entries.
     */
    public static void removeListener(Consumer<LogEntry> listener) {
        LISTENERS.remove(listener);
    }

    public static int getBufferSize() {
        return BUFFER.size();
    }

    // ------------------------------------------------------------------
    // Conversion helper
    // ------------------------------------------------------------------

    private LogEntry toLogEntry(ILoggingEvent event) {
        String timestamp = TIMESTAMP_FMT.format(Instant.ofEpochMilli(event.getTimeStamp()));
        String level = event.getLevel().toString();
        String logger = event.getLoggerName();
        String message = event.getFormattedMessage();
        String thread = event.getThreadName();

        // Build stacktrace if present
        String stacktrace = null;
        if (event.getThrowableProxy() != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(event.getThrowableProxy().getClassName())
              .append(": ")
              .append(event.getThrowableProxy().getMessage());

            if (event.getThrowableProxy().getStackTraceElementProxyArray() != null) {
                for (var ste : event.getThrowableProxy().getStackTraceElementProxyArray()) {
                    sb.append("\n\tat ").append(ste.getSTEAsString());
                }
            }
            stacktrace = sb.toString();
        }

        return new LogEntry(timestamp, level, logger, message, thread, stacktrace);
    }

    // ------------------------------------------------------------------
    // DTO
    // ------------------------------------------------------------------

    /**
     * Immutable log entry record.
     */
    public record LogEntry(
            String timestamp,
            String level,
            String logger,
            String message,
            String thread,
            String stacktrace
    ) {}
}

