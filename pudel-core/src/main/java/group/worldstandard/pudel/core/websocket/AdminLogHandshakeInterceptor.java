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

import group.worldstandard.pudel.core.config.springboot.JwtUtil;
import group.worldstandard.pudel.core.entity.DPoPKey;
import group.worldstandard.pudel.core.service.DPoPKeyManager;
import group.worldstandard.pudel.core.session.SessionAuthenticationService;
import group.worldstandard.pudel.core.session.SessionCookieService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Authenticates the native WebSocket handshake using the encrypted HttpOnly session
 * cookie and the admin token persisted against the same database key row.
 */
@Component
public class AdminLogHandshakeInterceptor implements HandshakeInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AdminLogHandshakeInterceptor.class);

    private final SessionCookieService cookieService;
    private final DPoPKeyManager dpopKeyManager;
    private final JwtUtil jwtUtil;

    public AdminLogHandshakeInterceptor(SessionCookieService cookieService,
                                        DPoPKeyManager dpopKeyManager,
                                        JwtUtil jwtUtil) {
        this.cookieService = cookieService;
        this.dpopKeyManager = dpopKeyManager;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        try {
            String keyId = cookieService.readKeyId(
                    ((ServletServerHttpRequest) request).getServletRequest()).orElse(null);
            DPoPKey session = keyId == null ? null : dpopKeyManager.findActiveSession(keyId).orElse(null);
            String adminToken = session == null ? null : session.getAdminToken();

            if (adminToken == null || adminToken.isBlank() || !jwtUtil.validateToken(adminToken)) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                log.debug("Rejected admin log WebSocket handshake: missing/invalid admin session");
                return false;
            }

            Claims claims = jwtUtil.getClaimsFromToken(adminToken);
            if (claims == null || !"pudel-admin-session".equals(claims.getSubject())
                    || claims.getExpiration() == null
                    || System.currentTimeMillis() >= claims.getExpiration().getTime()) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                log.debug("Rejected admin log WebSocket handshake: not an active admin session");
                return false;
            }

            attributes.put(SessionAuthenticationService.KEY_ID_ATTRIBUTE, keyId);
            attributes.put("adminRole", claims.get("adminRole", String.class));
            return true;
        } catch (Exception e) {
            log.warn("Admin log WebSocket handshake failed", e);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // No additional cleanup required.
    }
}
