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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

/**
 * Registers the admin log stream over native WebSocket. Strict allowed origins keep
 * cross-site browser sockets out, while the handshake verifies the encrypted session.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {
    private final AdminLogWebSocketHandler adminLogWebSocketHandler;
    private final AdminLogHandshakeInterceptor adminLogHandshakeInterceptor;

    @Value("${pudel.cors.allowed-origins}")
    private List<String> allowedOrigins;

    public WebSocketConfiguration(AdminLogWebSocketHandler adminLogWebSocketHandler,
                                  AdminLogHandshakeInterceptor adminLogHandshakeInterceptor) {
        this.adminLogWebSocketHandler = adminLogWebSocketHandler;
        this.adminLogHandshakeInterceptor = adminLogHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(adminLogWebSocketHandler, "/ws/admin/logs")
                .addInterceptors(adminLogHandshakeInterceptor)
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }
}
