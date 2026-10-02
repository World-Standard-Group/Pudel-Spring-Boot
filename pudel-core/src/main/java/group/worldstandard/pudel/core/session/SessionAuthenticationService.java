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
package group.worldstandard.pudel.core.session;

import group.worldstandard.pudel.core.config.springboot.JwtUtil;
import group.worldstandard.pudel.core.entity.DPoPKey;
import group.worldstandard.pudel.core.service.DPoPKeyManager;
import group.worldstandard.pudel.core.service.DPoPService;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves and authenticates the encrypted browser session, then applies an internal,
 * request-bound EdDSA DPoP proof before the request reaches a controller.
 */
@Service
public class SessionAuthenticationService {
    public static final String KEY_ID_ATTRIBUTE = "pudel.session.keyId";

    private final SessionCookieService cookieService;
    private final DPoPKeyManager dpopKeyManager;
    private final DPoPService dpopService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SessionAuthenticationService(SessionCookieService cookieService,
                                        DPoPKeyManager dpopKeyManager,
                                        DPoPService dpopService,
                                        JwtUtil jwtUtil) {
        this.cookieService = cookieService;
        this.dpopKeyManager = dpopKeyManager;
        this.dpopService = dpopService;
        this.jwtUtil = jwtUtil;
    }

    public Optional<DPoPKey> resolveActiveSession(HttpServletRequest request) {
        return cookieService.readKeyId(request)
                .flatMap(dpopKeyManager::findActiveSession);
    }

    /**
     * Authenticates an ordinary resource request using only the cookie. The DPoP proof
     * is generated and validated server side, so it never traverses the SPA.
     */
    public SessionAuthenticationResult authenticate(HttpServletRequest request) {
        Optional<DPoPKey> sessionOpt = resolveActiveSession(request);
        if (sessionOpt.isEmpty()) {
            return SessionAuthenticationResult.unauthorized("Missing or invalid browser session");
        }

        DPoPKey session = sessionOpt.get();
        String token = session.getAccessToken();
        if (token == null || token.isBlank()) {
            return SessionAuthenticationResult.unauthorized("Browser session is not authenticated");
        }
        if (!jwtUtil.validateToken(token)) {
            return SessionAuthenticationResult.unauthorized("Browser session token expired or invalid");
        }

        String method = request.getMethod().toUpperCase();
        String uri = request.getRequestURL().toString();
        DPoPService.DPoPValidationResult proofResult =
                dpopService.validateProofForResource(
                        signInternalProof(session.getKeyId(), method, uri, token),
                        method,
                        uri,
                        token,
                        session.getKeyId());

        if (!proofResult.valid()) {
            return SessionAuthenticationResult.unauthorized("DPoP session validation failed");
        }

        String tokenThumbprint = jwtUtil.getDPoPThumbprint(token);
        if (tokenThumbprint == null || !tokenThumbprint.equals(proofResult.thumbprint())) {
            return SessionAuthenticationResult.unauthorized("Token is not bound to this browser session");
        }

        String userId = jwtUtil.getUserIdFromToken(token);
        if (userId == null || !userId.equals(session.getUserId())) {
            return SessionAuthenticationResult.unauthorized("Browser session user mismatch");
        }

        return SessionAuthenticationResult.authenticated(session, userId);
    }

    /**
     * Used by login/refresh controllers, whose token may be absent or being replaced.
     */
    public SessionAuthenticationResult authenticateBrowserKey(HttpServletRequest request, String accessToken) {
        Optional<DPoPKey> sessionOpt = resolveActiveSession(request);
        if (sessionOpt.isEmpty()) {
            return SessionAuthenticationResult.unauthorized("Missing or invalid browser session");
        }

        DPoPKey session = sessionOpt.get();
        String method = request.getMethod().toUpperCase();
        String uri = request.getRequestURL().toString();
        DPoPService.DPoPValidationResult proofResult =
                dpopService.validateProofForResource(
                        signInternalProof(session.getKeyId(), method, uri, accessToken),
                        method,
                        uri,
                        accessToken,
                        session.getKeyId());
        if (!proofResult.valid()) {
            return SessionAuthenticationResult.unauthorized("DPoP session validation failed");
        }

        if (accessToken != null) {
            String tokenThumbprint = jwtUtil.getDPoPThumbprint(accessToken);
            if (tokenThumbprint != null && !tokenThumbprint.equals(proofResult.thumbprint())) {
                return SessionAuthenticationResult.unauthorized("Token is not bound to this browser session");
            }
        }

        return SessionAuthenticationResult.authenticated(session, session.getUserId());
    }

    public String signInternalProof(String keyId, String method, String uri, String accessToken) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("htm", method.toUpperCase());
        payload.put("htu", uri);
        payload.put("iat", Instant.now().getEpochSecond());
        if (accessToken != null && !accessToken.isBlank()) {
            payload.put("ath", accessTokenHash(accessToken));
        }
        return dpopKeyManager.signDPoPProof(payload.toString(), keyId);
    }

    private String accessTokenHash(String token) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record SessionAuthenticationResult(boolean authenticated, DPoPKey session, String userId, String error) {
        public static SessionAuthenticationResult authenticated(DPoPKey session, String userId) {
            return new SessionAuthenticationResult(true, session, userId, null);
        }

        public static SessionAuthenticationResult unauthorized(String error) {
            return new SessionAuthenticationResult(false, null, null, error);
        }
    }
}
