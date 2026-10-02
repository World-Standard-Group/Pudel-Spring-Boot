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

import group.worldstandard.pudel.core.entity.DPoPKey;
import group.worldstandard.pudel.core.repository.DPoPKeyRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.OctetPrivateJwk;
import io.jsonwebtoken.security.PublicJwk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Database-backed BFF DPoP session-key management.
 * <p>
 * Each browser receives an opaque, AES-GCM-encrypted cookie containing a key id. The
 * matching Ed25519 private key, public JWK, access JWT, and admin JWT remain server side.
 * The private key is never exposed to the SPA.
 */
@Component
public class DPoPKeyManager {
    private static final Logger log = LoggerFactory.getLogger(DPoPKeyManager.class);
    public static final String ANONYMOUS_USER_ID = "anonymous";

    private final DPoPKeyRepository dpopKeyRepository;
    private final ObjectMapper objectMapper;

    /**
     * Session keys live exactly as long as {@code pudel.jwt.expiration}.
     */
    @Value("${pudel.jwt.expiration:604800000}")
    private long jwtExpirationMillis;

    /**
     * Maximum number of active DPoP keys retained for one authenticated user.
     */
    @Value("${pudel.dpop.max-keys-per-user:10}")
    private int maxKeysPerUser;

    public DPoPKeyManager(DPoPKeyRepository dpopKeyRepository, ObjectMapper objectMapper) {
        this.dpopKeyRepository = dpopKeyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new anonymous browser session key and persists it.
     */
    @Transactional
    public DPoPKeyInfo createSessionKey() {
        return generateAndStoreKeyPair(ANONYMOUS_USER_ID, null);
    }

    /**
     * Compatibility bootstrap used by the legacy DPoP controller.
     */
    @Transactional
    public DPoPKeyInfo initializeKeyPair(String userId, String keyId) {
        if (keyId != null && !keyId.isBlank()) {
            Optional<DPoPKey> existing = findActiveSession(keyId);
            if (existing.isPresent() && userId != null && userId.equals(existing.get().getUserId())) {
                return new DPoPKeyInfo(keyId, parseJwk(existing.get().getPublicKeyJwk()));
            }
        }
        return generateAndStoreKeyPair(userId == null || userId.isBlank() ? ANONYMOUS_USER_ID : userId, null);
    }

    @Transactional
    public DPoPKeyInfo generateAndStoreKeyPair(String userId, String tokenThumbprint) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
            KeyPair keyPair = generator.generateKeyPair();

            PublicJwk<PublicKey> publicJwk = Jwks.builder().octetKey(keyPair.getPublic()).build();
            OctetPrivateJwk<PrivateKey, PublicKey> privateJwk =
                    Jwks.builder().octetKeyPair(keyPair).build();
            String publicKeyJson = Jwks.json(publicJwk);
            String privateKeyJson = Jwks.UNSAFE_JSON(privateJwk);
            String thumbprint = publicJwk.thumbprint().toString();
            String keyId = generateKeyId();
            Instant expiresAt = Instant.now().plusMillis(jwtExpirationMillis);

            DPoPKey row = new DPoPKey();
            row.setKeyId(keyId);
            row.setUserId(userId == null || userId.isBlank() ? ANONYMOUS_USER_ID : userId);
            row.setTokenThumbprint(tokenThumbprint);
            row.setPublicKeyJwk(publicKeyJson);
            row.setPrivateKeyJwk(privateKeyJson);
            row.setPublicKeyThumbprint(thumbprint);
            row.setExpiresAt(expiresAt);
            row.setIsActive(true);
            row = dpopKeyRepository.save(row);

            log.info("Generated Ed25519 DPoP session key: keyId={}, userId={}, expiresAt={}",
                    keyId, row.getUserId(), expiresAt);
            return new DPoPKeyInfo(keyId, parseJwk(publicKeyJson));
        } catch (Exception e) {
            log.error("Failed to generate an Ed25519 DPoP session key", e);
            throw new IllegalStateException("Failed to generate DPoP session key", e);
        }
    }

    /**
     * Binds a previously anonymous browser key to the logged-in Discord user and stores
     * the new BFF access JWT server side.
     */
    @Transactional
    public void bindSessionKey(String keyId, String userId, String accessToken) {
        DPoPKey key = requireActiveSession(keyId);
        key.setUserId(userId);
        key.setAccessToken(accessToken);
        key.setIsActive(true);
        dpopKeyRepository.save(key);
        cleanupOldKeys(userId);
        log.info("Bound browser session {} to user {}", keyId, userId);
    }

    /**
     * Replaces the server-side access JWT for the same browser session.
     */
    @Transactional
    public void storeAccessToken(String keyId, String accessToken) {
        DPoPKey key = requireActiveSession(keyId);
        key.setAccessToken(accessToken);
        dpopKeyRepository.save(key);
    }

    /**
     * Stores the mutual-auth admin JWT against the same browser session key.
     */
    @Transactional
    public void storeAdminToken(String keyId, String adminToken) {
        DPoPKey key = requireActiveSession(keyId);
        key.setAdminToken(adminToken);
        dpopKeyRepository.save(key);
        log.info("Stored admin session for browser key {}", keyId);
    }

    /**
     * Clears the admin JWT but keeps the ordinary browser session alive.
     */
    @Transactional
    public void clearAdminToken(String keyId) {
        dpopKeyRepository.findByKeyId(keyId).ifPresent(key -> {
            key.setAdminToken(null);
            dpopKeyRepository.save(key);
        });
    }

    public Optional<DPoPKey> findActiveSession(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            return Optional.empty();
        }
        return dpopKeyRepository.findByKeyId(keyId)
                .filter(DPoPKey::getIsActive)
                .filter(key -> key.getExpiresAt() != null && key.getExpiresAt().isAfter(Instant.now()));
    }

    public DPoPKey requireActiveSession(String keyId) {
        return findActiveSession(keyId).orElseThrow(() ->
                new IllegalStateException("Active DPoP session not found: " + keyId));
    }

    public Map<String, Object> getPublicKeyJwk(String keyId) {
        DPoPKey dpopKey = requireActiveSession(keyId);
        return parseJwk(dpopKey.getPublicKeyJwk());
    }

    /**
     * Signs an internal DPoP proof for a request that has already authenticated through
     * the encrypted cookie. This is not a client-callable proof oracle.
     */
    @Transactional
    public String signDPoPProof(String payloadJson, String keyId) {
        try {
            DPoPKey dpopKey = requireActiveSession(keyId);
            Map<String, Object> privateJwk = parseJwk(dpopKey.getPrivateKeyJwk());
            java.security.Key key = Jwks.parser().build()
                    .parse(objectMapper.writeValueAsString(privateJwk)).toKey();
            if (!(key instanceof PrivateKey privateKey)) {
                throw new IllegalStateException("Stored DPoP JWK did not reconstruct a private key: " + keyId);
            }

            dpopKeyRepository.updateLastUsed(keyId, Instant.now());

            Map<String, Object> headers = new java.util.LinkedHashMap<>();
            headers.put("typ", "dpop+jwt");
            headers.put("alg", "EdDSA");
            headers.put("jwk", parseJwk(dpopKey.getPublicKeyJwk()));

            return Jwts.builder()
                    .header().add(headers).and()
                    .content(payloadJson)
                    .signWith(privateKey, Jwts.SIG.EdDSA)
                    .compact();
        } catch (Exception e) {
            log.error("Failed to sign the internal DPoP proof for key {}", keyId, e);
            throw new IllegalStateException("Failed to sign DPoP proof", e);
        }
    }

    public String getPublicKeyThumbprint(String keyId) {
        return requireActiveSession(keyId).getPublicKeyThumbprint();
    }

    @Transactional
    public void clearKeyPair(String keyId) {
        dpopKeyRepository.deactivateByKeyId(keyId);
    }

    @Transactional
    public void clearAllUserKeys(String userId) {
        dpopKeyRepository.deactivateAllByUserId(userId);
    }

    private void cleanupOldKeys(String userId) {
        List<DPoPKey> activeKeys = dpopKeyRepository.findByUserIdAndIsActiveTrue(userId);
        if (activeKeys.size() >= maxKeysPerUser) {
            activeKeys.sort(Comparator.comparing(DPoPKey::getLastUsedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder())));
            int keysToRemove = Math.max(1, maxKeysPerUser / 4);
            for (int i = 0; i < keysToRemove && i < activeKeys.size(); i++) {
                dpopKeyRepository.deactivateByKeyId(activeKeys.get(i).getKeyId());
                log.debug("Deactivated old DPoP key: {} for user: {}",
                        activeKeys.get(i).getKeyId(), userId);
            }
        }
    }

    private String generateKeyId() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJwk(String jwkJson) {
        try {
            return objectMapper.readValue(jwkJson, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse stored DPoP JWK", e);
        }
    }

    private static String sha256Base64Url(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static class DPoPKeyInfo {
        private final String keyId;
        private final Map<String, Object> publicKeyJwk;

        public DPoPKeyInfo(String keyId, Map<String, Object> publicKeyJwk) {
            this.keyId = keyId;
            this.publicKeyJwk = publicKeyJwk;
        }

        public String getKeyId() {
            return keyId;
        }

        public Map<String, Object> getPublicKeyJwk() {
            return publicKeyJwk;
        }

        @Override
        public String toString() {
            return "DPoPKeyInfo{keyId=" + sha256Base64Url(keyId) + "}";
        }
    }
}
