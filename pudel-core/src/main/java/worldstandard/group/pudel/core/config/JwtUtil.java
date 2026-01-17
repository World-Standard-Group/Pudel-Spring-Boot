/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 Napapon Kamanee
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
package worldstandard.group.pudel.core.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT utility for token generation and validation.
 */
@Component
public class JwtUtil {
    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${pudel.jwt.secret:your-secret-key-change-this-in-production}")
    private String jwtSecret;

    @Value("${pudel.jwt.expiration:604800000}")
    private Long jwtExpiration;

    /**
     * Generate JWT token from user ID.
     */
    public String generateToken(String userId) {
        return generateToken(userId, new HashMap<>());
    }

    /**
     * Generate JWT token with claims.
     */
    public String generateToken(String userId, Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        Date expiryDate = new Date(now + jwtExpiration);

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());

        return Jwts.builder()
                .subject(userId)
                .claims(claims)
                .issuedAt(new Date(now))
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Get user ID from token.
     */
    public String getUserIdFromToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Could not get user ID from token", e);
            return null;
        }
    }

    /**
     * Validate JWT token.
     */
    public boolean validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JWT validation failed", e);
            return false;
        }
    }

    /**
     * Get expiration time from token.
     */
    public Date getExpirationDateFromToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getExpiration();
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Could not get expiration date from token", e);
            return null;
        }
    }

    /**
     * Check if token is expired.
     */
    public boolean isTokenExpired(String token) {
        Date expirationDate = getExpirationDateFromToken(token);
        if (expirationDate == null) {
            return true;
        }
        return expirationDate.before(new Date());
    }
}

