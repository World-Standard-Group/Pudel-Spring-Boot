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

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT utility for token generation and validation using RSA key pairs.
 * Supports DPoP (Demonstrating Proof-of-Possession) for enhanced security.
 */
@Component
public class JwtUtil {
    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /**
     * Token type for DPoP-bound tokens.
     */
    public static final String TOKEN_TYPE_DPOP = "DPoP";

    /**
     * Token type for standard Bearer tokens.
     */
    public static final String TOKEN_TYPE_BEARER = "Bearer";

    /**
     * Claim key for DPoP JWK thumbprint binding.
     */
    public static final String DPOP_THUMBPRINT_CLAIM = "cnf";
    public static final String DPOP_JKT_CLAIM = "jkt";

    @Value("${pudel.jwt.private-key-path:keys/private.key}")
    private String privateKeyPath;

    @Value("${pudel.jwt.public-key-path:keys/public.key}")
    private String publicKeyPath;

    @Value("${pudel.jwt.expiration:604800000}")
    private Long jwtExpiration;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            this.privateKey = loadPrivateKey(privateKeyPath);
            this.publicKey = loadPublicKey(publicKeyPath);
            log.info("RSA keys loaded successfully");
        } catch (Exception e) {
            log.error("Failed to load RSA keys", e);
            throw new RuntimeException("Failed to initialize JWT keys", e);
        }
    }

    /**
     * Load private key from PEM file.
     */
    private PrivateKey loadPrivateKey(String path) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] keyBytes = readPemKeyBytes(path, new String[]{
                "-----BEGIN PRIVATE KEY-----",
                "-----END PRIVATE KEY-----",
                "-----BEGIN RSA PRIVATE KEY-----",
                "-----END RSA PRIVATE KEY-----"
        });
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
    }

    /**
     * Load public key from PEM file.
     */
    private PublicKey loadPublicKey(String path) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] keyBytes = readPemKeyBytes(path, new String[]{
                "-----BEGIN PUBLIC KEY-----",
                "-----END PUBLIC KEY-----",
                "-----BEGIN RSA PUBLIC KEY-----",
                "-----END RSA PUBLIC KEY-----"
        });
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    /**
     * Read a PEM-encoded key file, strip the provided headers/footers and
     * whitespace, and return the decoded key bytes.
     */
    private byte[] readPemKeyBytes(String path, String[] headers) throws IOException {
        String keyContent = Files.readString(Path.of(path));
        for (String header : headers) {
            keyContent = keyContent.replace(header, "");
        }
        String pemBody = keyContent.replaceAll("\\s", "");
        return Base64.getDecoder().decode(pemBody);
    }

    /**
     * Generate JWT token from user ID.
     */
    public String generateToken(String userId) {
        return generateToken(userId, new HashMap<>());
    }

    /**
     * Generate JWT token with claims using RSA private key.
     */
    public String generateToken(String userId, Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        Date expiryDate = new Date(now + jwtExpiration);

        return Jwts.builder()
                .subject(userId)
                .claims(claims)
                .issuedAt(new Date(now))
                .expiration(expiryDate)
                .signWith(privateKey, Jwts.SIG.RS512)
                .compact();
    }

    /**
     * Generate a DPoP-bound JWT token.
     * The token will be bound to the client's public key via the JWK thumbprint.
     *
     * @param userId the user ID
     * @param claims additional claims
     * @param dpopThumbprint the JWK thumbprint from the DPoP proof
     * @return the DPoP-bound JWT token
     */
    public String generateDPoPBoundToken(String userId, Map<String, Object> claims, String dpopThumbprint) {
        long now = System.currentTimeMillis();
        Date expiryDate = new Date(now + jwtExpiration);

        // Create the confirmation claim with JWK thumbprint
        // Per RFC 9449, this is: "cnf": {"jkt": "<thumbprint>"}
        Map<String, Object> cnf = new HashMap<>();
        cnf.put(DPOP_JKT_CLAIM, dpopThumbprint);

        Map<String, Object> allClaims = new HashMap<>(claims);
        allClaims.put(DPOP_THUMBPRINT_CLAIM, cnf);

        return Jwts.builder()
                .subject(userId)
                .claims(allClaims)
                .issuedAt(new Date(now))
                .expiration(expiryDate)
                .signWith(privateKey, Jwts.SIG.RS512)
                .compact();
    }

    /**
     * Check if a token is DPoP-bound.
     */
    public boolean isDPoPBoundToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return claims.containsKey(DPOP_THUMBPRINT_CLAIM);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the DPoP JWK thumbprint from a token.
     *
     * @param token the JWT token
     * @return the JWK thumbprint or null if not a DPoP-bound token
     */
    @SuppressWarnings("unchecked")
    public String getDPoPThumbprint(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Map<String, Object> cnf = claims.get(DPOP_THUMBPRINT_CLAIM, Map.class);
            if (cnf != null) {
                return (String) cnf.get(DPOP_JKT_CLAIM);
            }
            return null;
        } catch (Exception e) {
            log.debug("Could not get DPoP thumbprint from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get user ID from token using RSA public key.
     */
    public String getUserIdFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Could not get user ID from token ({}: {})",
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            return null;
        }
    }

    /**
     * Get all claims from token.
     */
    public Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Could not get claims from token ({}: {})",
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            return null;
        }
    }

    /**
     * Validate JWT token using RSA public key.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn(e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JWT validation failed ({}: {})",
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
        }
        return false;
    }

    /**
     * Get expiration time from token.
     */
    public Date getExpirationDateFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getExpiration();
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Could not get expiration date from token ({}: {})",
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
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

    /**
     * Get the public key (useful for external services that need to verify tokens).
     */
    public PublicKey getPublicKey() {
        return publicKey;
    }

    /**
     * Get the private key (for internal use only).
     */
    protected PrivateKey getPrivateKey() {
        return privateKey;
    }
}
