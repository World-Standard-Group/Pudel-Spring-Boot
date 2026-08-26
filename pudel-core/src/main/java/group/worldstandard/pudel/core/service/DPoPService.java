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

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for validating DPoP proofs in BFF-style architecture with database-backed keys.
 * <p>
 * In BFF style, the backend holds the private key and signs DPoP proofs for the frontend.
 * This service validates that the DPoP proof was signed by the correct session key.
 * <p>
 * Key changes from previous implementation:
 * - Keys are retrieved from database by keyId (from X-DPoP-Key-Id header)
 * - No dependency on HttpSession for key storage
 * - Works correctly in stateless architectures
 * <p>
 * Validation steps:
 * 1. Parse the DPoP proof JWT
 * 2. Extract the JWK from the header
 * 3. Verify the signature using the public key from the database
 * 4. Validate claims (htm, htu, iat, jti, ath)
 */
@Service
public class DPoPService {
    private static final Logger log = LoggerFactory.getLogger(DPoPService.class);

    private final DPoPKeyManager dpopKeyManager;

    /**
     * Single-use ledger for DPoP proof {@code jti}s (RFC 9449 §11.1 replay defense).
     * Keyed by {@code keyId:jti}; value is the proof's expiry (iat + 60s skew window).
     * A jti seen again before its window lapses is a replay and is rejected. The map
     * is bounded by the proof lifetime: entries are lazily purged on first access after
     * expiry, so memory stays flat (one entry per distinct proof per key, max ~60s old).
     * In-memory by design — matches the admin session's accepted no-persistence stance.
     */
    private final ConcurrentHashMap<String, Long> usedJtis = new ConcurrentHashMap<>();

    private static final long JTI_SKEW_WINDOW_MS = 60_000L;

    public DPoPService(DPoPKeyManager dpopKeyManager) {
        this.dpopKeyManager = dpopKeyManager;
    }

    /**
     * Result of DPoP proof validation.
     */
    public record DPoPValidationResult(boolean valid, String error, String thumbprint) {
        public static DPoPValidationResult valid(String thumbprint) {
            return new DPoPValidationResult(true, null, thumbprint);
        }

        public static DPoPValidationResult invalid(String error) {
            return new DPoPValidationResult(false, error, null);
        }
    }

    /**
     * Validate a DPoP proof for a resource request (BFF-style with database-backed keys).
     *
     * @param dpopProof   The DPoP proof JWT from the DPoP header
     * @param httpMethod  The HTTP method of the request
     * @param httpUri     The full URI of the request
     * @param accessToken The access token (for ath validation)
     * @param keyId       The DPoP key identifier (from X-DPoP-Key-Id header)
     * @return Validation result
     */
    public DPoPValidationResult validateProofForResource(String dpopProof,
                                                         String httpMethod,
                                                         String httpUri,
                                                         String accessToken,
                                                         String keyId) {
        try {
            if (keyId == null || keyId.isEmpty()) {
                return DPoPValidationResult.invalid("Missing DPoP key ID");
            }

            // Get the public key from database by keyId
            Map<String, Object> publicKeyJwk = dpopKeyManager.getPublicKeyJwk(keyId);

            // Reconstruct public key from JWK for signature verification
            ECPublicKey publicKey = reconstructPublicKeyFromJwk(publicKeyJwk);

            // Verify the JWT signature and parse claims
            Claims claims;
            try {
                claims = Jwts.parser()
                        .verifyWith(publicKey)
                        .build()
                        .parseSignedClaims(dpopProof)
                        .getPayload();
            } catch (Exception e) {
                log.debug("DPoP proof signature verification failed: {}", e.getMessage());
                return DPoPValidationResult.invalid("Invalid DPoP proof signature");
            }

            // Validate htm (HTTP method)
            String htm = claims.get("htm", String.class);
            if (htm == null || !htm.equalsIgnoreCase(httpMethod)) {
                return DPoPValidationResult.invalid("Invalid htm claim");
            }

            // Validate htu (HTTP URI) - normalize both for comparison
            String htu = claims.get("htu", String.class);
            if (htu == null) {
                return DPoPValidationResult.invalid("Missing htu claim");
            }
            String normalizedHtu = normalizeUriForComparison(htu);
            String normalizedHttpUri = normalizeUriForComparison(httpUri);
            if (!normalizedHtu.equals(normalizedHttpUri)) {
                log.debug("DPoP htu validation failed: htu='{}' (normalized='{}'), httpUri='{}' (normalized='{}')",
                        htu, normalizedHtu, httpUri, normalizedHttpUri);
                return DPoPValidationResult.invalid("Invalid htu claim");
            }

            // Validate iat (issued at) - must be recent (within 60 seconds)
            Date iat = claims.getIssuedAt();
            if (iat == null) {
                return DPoPValidationResult.invalid("Missing iat claim");
            }
            long now = System.currentTimeMillis();
            long iatMillis = iat.getTime();
            if (Math.abs(now - iatMillis) > 60000) { // 60 seconds tolerance
                return DPoPValidationResult.invalid("iat too old or in future");
            }

            // Validate jti (unique identifier) — single-use to defeat proof replay (RFC 9449 §11.1)
            String jti = claims.getId();
            if (jti == null || jti.isEmpty()) {
                return DPoPValidationResult.invalid("Missing jti claim");
            }
            String jtiKey = keyId + ":" + jti;
            long proofExpiry = iat.getTime() + JTI_SKEW_WINDOW_MS;
            // putIfAbsent: first use of this jti stores the expiry and returns null (allowed).
            // A second use within the window finds the existing entry -> reject as replay.
            // Stale entries are purged lazily first so a long-expired jti can be reused safely.
            usedJtis.entrySet().removeIf(e -> e.getValue() < System.currentTimeMillis());
            Long existing = usedJtis.putIfAbsent(jtiKey, proofExpiry);
            if (existing != null) {
                log.debug("DPoP jti reuse detected (replay): key={}", jtiKey);
                return DPoPValidationResult.invalid("jti already used (replay detected)");
            }

            // Validate ath (access token hash) if access token provided
            if (accessToken != null) {
                String ath = claims.get("ath", String.class);
                if (ath == null) {
                    return DPoPValidationResult.invalid("Missing ath claim for DPoP-bound token");
                }
                String expectedAth = calculateAccessTokenHash(accessToken);
                if (!expectedAth.equals(ath)) {
                    return DPoPValidationResult.invalid("Invalid ath claim");
                }
            }

            // Return the thumbprint for binding verification
            String thumbprint = dpopKeyManager.getPublicKeyThumbprint(keyId);
            return DPoPValidationResult.valid(thumbprint);

        } catch (Exception e) {
            log.warn("DPoP proof validation error", e);
            return DPoPValidationResult.invalid("Invalid DPoP proof: Unexpected Exception.");
        }
    }

    /**
     * Reconstruct an ECPublicKey from a JWK map.
     */
    private ECPublicKey reconstructPublicKeyFromJwk(Map<String, Object> jwk) throws Exception {
        byte[] xBytes = Base64.getUrlDecoder().decode((String) jwk.get("x"));
        byte[] yBytes = Base64.getUrlDecoder().decode((String) jwk.get("y"));

        // Create public key
        ECPoint ecPoint = new ECPoint(new BigInteger(1, xBytes), new BigInteger(1, yBytes));
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec ecSpec = parameters.getParameterSpec(ECParameterSpec.class);
        ECPublicKeySpec publicKeySpec = new ECPublicKeySpec(ecPoint, ecSpec);

        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return (ECPublicKey) keyFactory.generatePublic(publicKeySpec);
    }

    /**
     * Calculate SHA-256 hash of access token (base64url encoded) per RFC 9449.
     */
    private String calculateAccessTokenHash(String accessToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(accessToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Normalize URI for comparison, stripping scheme to handle reverse proxy scenarios.
     * The frontend uses https but the backend may receive http from a reverse proxy.
     * We compare only host + path to avoid scheme mismatches.
     */
    private String normalizeUriForComparison(String uri) {
        try {
            URI parsed = new URI(uri);
            String host = parsed.getHost();
            int port = parsed.getPort();
            String scheme = parsed.getScheme();

            // Strip standard ports (80 for HTTP, 443 for HTTPS)
            boolean isStandardPort = (port == -1) ||
                    ("https".equalsIgnoreCase(scheme) && port == 443) ||
                    ("http".equalsIgnoreCase(scheme) && port == 80);

            String hostPart = isStandardPort ? host : host + ":" + port;
            // Return host + path only (no scheme) to handle reverse proxy scenarios
            return hostPart + parsed.getPath();
        } catch (Exception e) {
            return uri;
        }
    }
}
