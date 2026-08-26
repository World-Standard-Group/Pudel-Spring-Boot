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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * BFF-style DPoP key management service with database persistence.
 * <p>
 * This implementation stores DPoP keypairs in the database instead of HttpSession,
 * solving the issue where keys were lost when sessions expired or in stateless architectures.
 * <p>
 * Key features:
 * - Keys persist across page refreshes and session timeouts
 * - Keys are indexed by a stable keyId that the frontend can store
 * - Keys are bound to user ID and optionally to a specific access token
 * - Automatic key expiration and cleanup
 * <p>
 * Flow:
 * 1. Frontend requests a new DPoP key (or retrieves existing one by keyId)
 * 2. Backend generates/retrieves keypair from database
 * 3. Backend returns public key JWK and keyId to frontend
 * 4. Frontend stores keyId in localStorage for persistence
 * 5. Frontend includes keyId in DPoP sign requests
 * 6. Backend retrieves the correct key by keyId and signs the proof
 */
@Component
public class DPoPKeyManager {
    private static final Logger log = LoggerFactory.getLogger(DPoPKeyManager.class);

    private final DPoPKeyRepository dpopKeyRepository;
    private final ObjectMapper objectMapper;

    /**
     * Default DPoP key expiration time in hours.
     * Keys should expire after the access token expires.
     */
    @Value("${pudel.dpop.key-expiration-hours:168}")
    private int keyExpirationHours; // Default: 7 days

    /**
     * Maximum number of active DPoP keys per user.
     * Prevents key accumulation from multiple devices/browsers.
     */
    @Value("${pudel.dpop.max-keys-per-user:10}")
    private int maxKeysPerUser;

    public DPoPKeyManager(DPoPKeyRepository dpopKeyRepository, ObjectMapper objectMapper) {
        this.dpopKeyRepository = dpopKeyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Initialize or retrieve a DPoP keypair for a user.
     * <p>
     * If a keyId is provided and exists in the database, that key is returned.
     * Otherwise, a new keypair is generated and stored.
     *
     * @param userId  The user ID (from JWT token)
     * @param keyId   Optional key ID from frontend (for key retrieval)
     * @return DPoPKeyInfo containing the keyId and public key JWK
     */
    @Transactional
    public DPoPKeyInfo initializeKeyPair(String userId, String keyId) {
        // If keyId provided, try to find existing key
        if (keyId != null && !keyId.isEmpty()) {
            Optional<DPoPKey> existingKey = dpopKeyRepository.findByKeyId(keyId);
            if (existingKey.isPresent() && existingKey.get().getIsActive()) {
                DPoPKey key = existingKey.get();
                // Verify the key belongs to this user
                if (key.getUserId().equals(userId)) {
                    // Update last used timestamp
                    dpopKeyRepository.updateLastUsed(keyId, Instant.now());
                    log.debug("Retrieved existing DPoP key for user: {}, keyId: {}", userId, keyId);
                    return new DPoPKeyInfo(keyId, parseJwk(key.getPublicKeyJwk()));
                } else {
                    log.warn("DPoP key {} belongs to different user. Expected: {}, Actual: {}",
                            keyId, userId, key.getUserId());
                }
            } else {
                log.debug("DPoP key {} not found or inactive, generating new key", keyId);
            }
        }

        // Check if user has too many active keys
        long activeKeyCount = dpopKeyRepository.countByUserIdAndIsActiveTrue(userId);
        if (activeKeyCount >= maxKeysPerUser) {
            // Deactivate oldest keys to make room
            cleanupOldKeys(userId);
        }

        // Generate new keypair
        return generateAndStoreKeyPair(userId, null);
    }

    /**
     * Generate a new DPoP keypair and store it in the database.
     *
     * @param userId          The user ID
     * @param tokenThumbprint Optional token thumbprint to bind this key to
     * @return DPoPKeyInfo containing the new keyId and public key JWK
     */
    @Transactional
    public DPoPKeyInfo generateAndStoreKeyPair(String userId, String tokenThumbprint) {
        try {
            // Generate EC key pair for DPoP (P-256 curve)
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
            keyPairGenerator.initialize(ecSpec);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            // Convert keys to JWK format
            Map<String, Object> publicKeyJwk = convertPublicKeyToJwk((ECPublicKey) keyPair.getPublic());
            Map<String, Object> privateKeyJwk = convertPrivateKeyToJwk((ECPrivateKey) keyPair.getPrivate(), publicKeyJwk);

            // Calculate thumbprint
            String thumbprint = calculateJwkThumbprint(publicKeyJwk);

            // Generate unique key ID
            String keyId = generateKeyId();

            // Serialize JWKs to JSON
            String publicKeyJwkJson = objectMapper.writeValueAsString(publicKeyJwk);
            String privateKeyJwkJson = objectMapper.writeValueAsString(privateKeyJwk);

            // Calculate expiration
            Instant expiresAt = Instant.now().plus(keyExpirationHours, ChronoUnit.HOURS);

            // Store in database
            DPoPKey dpopKey = new DPoPKey();
            dpopKey.setKeyId(keyId);
            dpopKey.setUserId(userId);
            dpopKey.setTokenThumbprint(tokenThumbprint);
            dpopKey.setPublicKeyJwk(publicKeyJwkJson);
            dpopKey.setPrivateKeyJwk(privateKeyJwkJson);
            dpopKey.setPublicKeyThumbprint(thumbprint);
            dpopKey.setExpiresAt(expiresAt);
            dpopKey.setIsActive(true);

            dpopKeyRepository.save(dpopKey);

            log.info("Generated new DPoP keypair for user: {}, keyId: {}, thumbprint: {}", userId, keyId, thumbprint);

            return new DPoPKeyInfo(keyId, publicKeyJwk);
        } catch (Exception e) {
            log.error("Failed to generate DPoP keypair", e);
            throw new RuntimeException("Failed to initialize DPoP keypair", e);
        }
    }

    /**
     * Get the DPoP public key in JWK format by keyId.
     *
     * @param keyId The key identifier
     * @return Public key as JWK map
     */
    public Map<String, Object> getPublicKeyJwk(String keyId) {
        DPoPKey dpopKey = dpopKeyRepository.findByKeyId(keyId)
                .orElseThrow(() -> new RuntimeException("DPoP key not found: " + keyId));

        if (!dpopKey.getIsActive()) {
            throw new RuntimeException("DPoP key is no longer active: " + keyId);
        }

        if (dpopKey.getExpiresAt().isBefore(Instant.now())) {
            throw new RuntimeException("DPoP key has expired: " + keyId);
        }

        return parseJwk(dpopKey.getPublicKeyJwk());
    }

    /**
     * Sign a DPoP proof payload with the stored private key.
     *
     * @param payloadJson JSON string containing the DPoP proof payload (jti, htm, htu, iat, ath)
     * @param keyId       The key identifier
     * @return Signed DPoP proof JWT
     */
    public String signDPoPProof(String payloadJson, String keyId) {
        try {
            DPoPKey dpopKey = dpopKeyRepository.findByKeyId(keyId)
                    .orElseThrow(() -> new RuntimeException("DPoP key not found: " + keyId));

            if (!dpopKey.getIsActive()) {
                throw new RuntimeException("DPoP key is no longer active: " + keyId);
            }

            if (dpopKey.getExpiresAt().isBefore(Instant.now())) {
                throw new RuntimeException("DPoP key has expired: " + keyId);
            }

            // Parse private key JWK and reconstruct KeyPair
            Map<String, Object> privateKeyJwk = parseJwk(dpopKey.getPrivateKeyJwk());
            KeyPair keyPair = reconstructKeyPair(privateKeyJwk);

            // Update last used timestamp
            dpopKeyRepository.updateLastUsed(keyId, Instant.now());

            // Add typ header
            Map<String, Object> headers = new HashMap<>();
            headers.put("typ", "dpop+jwt");
            headers.put("alg", "ES256");
            headers.put("jwk", parseJwk(dpopKey.getPublicKeyJwk())); // Include public key in header

            return Jwts.builder()
                    .header().add(headers).and()
                    .content(payloadJson)
                    .signWith(keyPair.getPrivate(), Jwts.SIG.ES256)
                    .compact();
        } catch (Exception e) {
            log.error("Failed to sign DPoP proof for keyId: {}", keyId, e);
            throw new RuntimeException("Failed to sign DPoP proof", e);
        }
    }

    /**
     * Get the JWK thumbprint of a stored public key.
     *
     * @param keyId The key identifier
     * @return Base64url-encoded JWK thumbprint
     */
    public String getPublicKeyThumbprint(String keyId) {
        DPoPKey dpopKey = dpopKeyRepository.findByKeyId(keyId)
                .orElseThrow(() -> new RuntimeException("DPoP key not found: " + keyId));

        return dpopKey.getPublicKeyThumbprint();
    }

    /**
     * Clear (deactivate) a DPoP key by keyId.
     *
     * @param keyId The key identifier
     */
    @Transactional
    public void clearKeyPair(String keyId) {
        int updated = dpopKeyRepository.deactivateByKeyId(keyId);
        if (updated > 0) {
            log.debug("DPoP key deactivated: {}", keyId);
        }
    }

    /**
     * Clear all DPoP keys for a user (for logout).
     *
     * @param userId The user ID
     */
    @Transactional
    public void clearAllUserKeys(String userId) {
        int updated = dpopKeyRepository.deactivateAllByUserId(userId);
        log.debug("Deactivated {} DPoP keys for user: {}", updated, userId);
    }

    /**
     * Clean up old keys for a user when they exceed the maximum.
     * Deactivates the oldest keys first.
     */
    private void cleanupOldKeys(String userId) {
        List<DPoPKey> activeKeys = dpopKeyRepository.findByUserIdAndIsActiveTrue(userId);
        if (activeKeys.size() >= maxKeysPerUser) {
            // Sort by last used (oldest first), then deactivate oldest
            activeKeys.sort(Comparator.comparing(DPoPKey::getLastUsedAt, 
                    Comparator.nullsFirst(Comparator.naturalOrder())));
            
            // Deactivate oldest keys to make room (remove 25% of max)
            int keysToRemove = Math.max(1, maxKeysPerUser / 4);
            for (int i = 0; i < keysToRemove && i < activeKeys.size(); i++) {
                dpopKeyRepository.deactivateByKeyId(activeKeys.get(i).getKeyId());
                log.debug("Deactivated old DPoP key: {} for user: {}", 
                        activeKeys.get(i).getKeyId(), userId);
            }
        }
    }

    /**
     * Generate a unique key ID.
     */
    private String generateKeyId() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Convert ECPublicKey to JWK format.
     */
    private Map<String, Object> convertPublicKeyToJwk(ECPublicKey publicKey) {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "EC");
        jwk.put("crv", "P-256");
        jwk.put("x", base64UrlEncode(publicKey.getW().getAffineX().toByteArray()));
        jwk.put("y", base64UrlEncode(publicKey.getW().getAffineY().toByteArray()));
        return jwk;
    }

    /**
     * Convert ECPrivateKey to JWK format (includes public key coordinates).
     */
    private Map<String, Object> convertPrivateKeyToJwk(ECPrivateKey privateKey, Map<String, Object> publicKeyJwk) {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "EC");
        jwk.put("crv", "P-256");
        jwk.put("x", publicKeyJwk.get("x"));
        jwk.put("y", publicKeyJwk.get("y"));
        jwk.put("d", base64UrlEncode(privateKey.getS().toByteArray()));
        return jwk;
    }

    /**
     * Reconstruct a KeyPair from a private key JWK.
     */
    private KeyPair reconstructKeyPair(Map<String, Object> privateKeyJwk) throws Exception {
        byte[] xBytes = Base64.getUrlDecoder().decode((String) privateKeyJwk.get("x"));
        byte[] yBytes = Base64.getUrlDecoder().decode((String) privateKeyJwk.get("y"));
        byte[] dBytes = Base64.getUrlDecoder().decode((String) privateKeyJwk.get("d"));

        // Create public key
        ECPoint ecPoint = new ECPoint(new BigInteger(1, xBytes), new BigInteger(1, yBytes));
        ECParameterSpec ecSpec = getP256Spec();
        ECPublicKeySpec publicKeySpec = new ECPublicKeySpec(ecPoint, ecSpec);

        // Create private key
        ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(new BigInteger(1, dBytes), ecSpec);

        // Generate key pair
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return new KeyPair(keyFactory.generatePublic(publicKeySpec), keyFactory.generatePrivate(privateKeySpec));
    }

    /**
     * Get the P-256 curve specification.
     */
    private ECParameterSpec getP256Spec() throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    /**
     * Parse a JWK JSON string to a Map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJwk(String jwkJson) {
        try {
            return objectMapper.readValue(jwkJson, Map.class);
        } catch (JacksonIOException e) {
            throw new RuntimeException("Failed to parse JWK", e);
        }
    }

    /**
     * Calculate JWK thumbprint per RFC 7638.
     */
    private String calculateJwkThumbprint(Map<String, Object> jwk) throws Exception {
        String kty = (String) jwk.get("kty");
        ObjectNode json = objectMapper.createObjectNode();

        if ("RSA".equals(kty)) {
            json.put("e", (String) jwk.get("e"));
            json.put("kty", "RSA");
            json.put("n", (String) jwk.get("n"));
        } else if ("EC".equals(kty)) {
            json.put("crv", (String) jwk.get("crv"));
            json.put("kty", "EC");
            json.put("x", (String) jwk.get("x"));
            json.put("y", (String) jwk.get("y"));
        } else {
            throw new IllegalArgumentException("Unsupported key type: " + kty);
        }

        String jsonString = objectMapper.writeValueAsString(json);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(jsonString.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    /**
     * Base64url encode a byte array (without padding).
     */
    private String base64UrlEncode(byte[] input) {
        // Remove leading zero byte if present (BigInteger.toByteArray() may add it)
        if (input.length > 0 && input[0] == 0) {
            input = Arrays.copyOfRange(input, 1, input.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input);
    }

    /**
     * Result class for DPoP key initialization.
     */
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
    }
}
