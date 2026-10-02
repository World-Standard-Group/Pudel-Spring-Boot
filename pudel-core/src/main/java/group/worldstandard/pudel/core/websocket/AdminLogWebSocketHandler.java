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
package group.worldstandard.pudel.core.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Maintains the admin log-stream WebSocket sessions.
 * The handshake already verified the encrypted cookie and the database-backed admin token.
 * This handler only tracks active connections and frames every event as a JSON payload.
 */
@Component
public class AdminLogWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(AdminLogWebSocketHandler.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger sessionCounter = new AtomicInteger();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        sessionCounter.incrementAndGet();
        log.debug("Admin log WebSocket connected: {} (active={})", session.getId(), sessions.size());
        session.sendMessage(new TextMessage("{\"type\":\"connected\"}"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.debug("Admin log WebSocket closed: {} ({}), active={}",
                session.getId(), status, sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // The log stream is server-push only. Ignore client heartbeats/messages.
        log.debug("Ignoring WebSocket message from admin log stream {}: {}", session.getId(), message.getPayload());
    }

    public int broadcast(Map<String, Object> payload) {
        if (sessions.isEmpty()) {
            return 0;
        }

        try {
            String json = new tools.jackson.databind.ObjectMapper().writeValueAsString(payload);
            TextMessage message = new TextMessage(json);
            int delivered = 0;
            for (WebSocketSession session : sessions.values()) {
                try {
                    if (session.isOpen()) {
                        synchronized (session) {
                            session.sendMessage(message);
                        }
                        delivered++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to send admin log WebSocket message", e);
                }
            }
            return delivered;
        } catch (Exception e) {
            log.error("Unable to serialize admin log WebSocket payload", e);
            return 0;
        }
    }

    public int getActiveSessionCount() {
        return sessionCounter.get();
    }

    public void closeAll(CloseStatus status) {
        for (WebSocketSession session : sessions.values()) {
            try {
                if (session.isOpen()) {
                    session.close(status);
                }
            } catch (Exception e) {
                log.warn("Failed to close admin log WebSocket session", e);
            }
        }
        sessions.clear();
    }
}
