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
package group.worldstandard.pudel.core.controller;

import group.worldstandard.pudel.core.service.DPoPKeyManager;
import group.worldstandard.pudel.core.session.SessionCookieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Issues the SPA's single browser credential: an HttpOnly, Secure, Strict, AES-GCM
 * encrypted cookie containing an opaque database key id.
 */
@Tag(name = "Session", description = "Encrypted browser session bootstrap")
@RestController
@RequestMapping("/api/session")
public class SessionController {
    private final SessionCookieService cookieService;
    private final DPoPKeyManager dpopKeyManager;

    @Value("${pudel.jwt.expiration:604800000}")
    private long jwtExpirationMillis;

    public SessionController(SessionCookieService cookieService, DPoPKeyManager dpopKeyManager) {
        this.cookieService = cookieService;
        this.dpopKeyManager = dpopKeyManager;
    }

    @Operation(summary = "Bootstrap a browser session",
            description = "Creates or reuses the HttpOnly encrypted browser session cookie. Returns no key id or token.")
    @GetMapping("/bootstrap")
    public ResponseEntity<?> bootstrap(HttpServletRequest request, HttpServletResponse response) {
        try {
            String keyId = cookieService.readKeyId(request)
                    .filter(id -> dpopKeyManager.findActiveSession(id).isPresent())
                    .orElseGet(() -> dpopKeyManager.createSessionKey().getKeyId());

            Instant expiresAt = Instant.now().plusMillis(jwtExpirationMillis);
            cookieService.writeCookie(response, keyId, expiresAt);
            return ResponseEntity.ok(Map.of(
                    "ready", true,
                    "expiresAt", expiresAt.toEpochMilli(),
                    "maxAgeSeconds", cookieService.getMaxAgeSeconds()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "session_bootstrap_failed",
                    "message", "Unable to bootstrap the encrypted browser session"
            ));
        }
    }

    @Operation(summary = "Rotate the browser session cookie",
            description = "Forces a fresh Ed25519 browser key and encrypted cookie. Existing tokens remain only for the old key id.")
    @PostMapping("/rotate")
    public ResponseEntity<?> rotate(HttpServletResponse response) {
        try {
            DPoPKeyManager.DPoPKeyInfo info = dpopKeyManager.createSessionKey();
            Instant expiresAt = Instant.now().plusMillis(jwtExpirationMillis);
            cookieService.writeCookie(response, info.getKeyId(), expiresAt);
            return ResponseEntity.ok(Map.of(
                    "ready", true,
                    "expiresAt", expiresAt.toEpochMilli()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "session_rotation_failed",
                    "message", "Unable to rotate the encrypted browser session"
            ));
        }
    }
}
