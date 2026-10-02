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
package group.worldstandard.pudel.core.config.springboot;

import group.worldstandard.pudel.core.session.SessionAuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Cookie-only authentication for Pudel's SPA requests.
 * <p>
 * Authorization headers are rejected by design. The encrypted HttpOnly cookie resolves a
 * database Ed25519 key; the BFF then signs and validates a fresh internal DPoP proof for
 * every protected request and exposes only the Discord user id to Spring Security.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    public static final String SESSION_KEY_ID_ATTRIBUTE =
            SessionAuthenticationService.KEY_ID_ATTRIBUTE;

    private final SessionAuthenticationService sessionAuthenticationService;

    public JwtAuthenticationFilter(SessionAuthenticationService sessionAuthenticationService) {
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && !authHeader.isBlank()) {
            reject(response, "invalid_token",
                    "Authorization headers are not accepted; use the encrypted session cookie");
            return;
        }

        SessionAuthenticationService.SessionAuthenticationResult result =
                sessionAuthenticationService.authenticate(request);
        if (!result.authenticated()) {
            log.debug("Cookie session rejected on {}: {}", request.getRequestURI(), result.error());
            reject(response, "invalid_session", result.error());
            return;
        }

        request.setAttribute(SESSION_KEY_ID_ATTRIBUTE, result.session().getKeyId());
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("DPOP_VERIFIED"));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                result.userId(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String error, String description) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        String safeDescription = description == null ? "" : description.replace("\"", "'");
        response.getWriter().write(
                "{\"error\":\"" + error + "\",\"error_description\":\"" + safeDescription + "\"}");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return path.startsWith("/api/session/")
                || path.startsWith("/api/auth/discord/")
                || path.equals("/api/auth/refresh")
                || path.equals("/api/auth/logout")
                || path.startsWith("/api/bot/")
                || path.startsWith("/ws/admin/")
                || path.equals("/api/admin/logs/stream")
                || (path.equals("/api/plugins") && "GET".equals(request.getMethod()))
                || (path.equals("/api/plugins/installed") && "GET".equals(request.getMethod()))
                || (path.matches("/api/plugins/installed/[^/]+") && "GET".equals(request.getMethod()))
                || (path.equals("/api/plugins/enabled") && "GET".equals(request.getMethod()))
                || (path.matches("/api/plugins/[^/]+") && "GET".equals(request.getMethod()))
                || path.startsWith("/api/dpop/");
    }
}
