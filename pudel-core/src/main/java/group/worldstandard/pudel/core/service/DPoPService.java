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
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates Ed25519 DPoP proofs for Pudel's cookie-originated BFF sessions.
 * <p>
 * In the new architecture, proofs are minted internally by the BFF immediately before
 * processing an authenticated request. This preserves RFC 9449 request binding and
 * single-use jti freshness without exposing a signing oracle or bearer token to the SPA.
 */
@Service
public class DPoPService {
    private static final Logger log = LoggerFactory.getLogger(DPoPService.class);

    private final DPoPKeyManager dpopKeyManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Single-use ledger for DPoP proof {@code jti}s (RFC 9449 §11.1 replay defense).
     */
    private final ConcurrentHashMap<String, Long> usedJtis = new ConcurrentHashMap<>();

    private static final long JTI_SKEW_WINDOW_MS = 60_000L;

    public DPoPService(DPoPKeyManager dpopKeyManager) {
        this.dpopKeyManager = dpopKeyManager;
    }

    public record DPoPValidationResult(boolean valid, String error, String thumbprint) {
        public static DPoPValidationResult valid(String thumbprint) {
            return new DPoPValidationResult(true, null, thumbprint);
        }

        public static DPoPValidationResult invalid(String error) {
            return new DPoPValidationResult(false, error, null);
        }
    }

    public DPoPValidationResult validateProofForResource(String dpopProof,
                                                         String httpMethod,
                                                         String httpUri,
                                                         String accessToken,
                                                         String keyId) {
        try {
            if (keyId == null || keyId.isEmpty()) {
                return DPoPValidationResult.invalid("Missing DPoP key ID");
            }
            if (dpopProof == null || dpopProof.isBlank()) {
                return DPoPValidationResult.invalid("Missing DPoP proof");
            }

            Map<String, Object> publicKeyJwk = dpopKeyManager.getPublicKeyJwk(keyId);
            Key reconstructed = Jwks.parser().build()
                    .parse(objectMapper.writeValueAsString(publicKeyJwk)).toKey();
            if (!(reconstructed instanceof java.security.PublicKey publicKey)) {
                return DPoPValidationResult.invalid("Invalid stored DPoP public key");
            }

            Jws<Claims> signedProof;
            Claims claims;
            try {
                signedProof = Jwts.parser()
                        .verifyWith(publicKey)
                        .build()
                        .parseSignedClaims(dpopProof);
                claims = signedProof.getPayload();
            } catch (Exception e) {
                log.debug("DPoP proof signature verification failed: {}", e.getMessage());
                return DPoPValidationResult.invalid("Invalid DPoP proof signature");
            }

            Object algHeader = signedProof.getHeader().get("alg");
            String alg = algHeader instanceof String value ? value : null;
            if (!"EdDSA".equals(alg)) {
                return DPoPValidationResult.invalid("Only EdDSA DPoP proofs are accepted");
            }

            String htm = claims.get("htm", String.class);
            if (htm == null || !htm.equalsIgnoreCase(httpMethod)) {
                return DPoPValidationResult.invalid("Invalid htm claim");
            }

            String htu = claims.get("htu", String.class);
            if (htu == null) {
                return DPoPValidationResult.invalid("Missing htu claim");
            }
            if (!normalizeUriForComparison(htu).equals(normalizeUriForComparison(httpUri))) {
                log.debug("DPoP htu validation failed: htu='{}', request='{}'", htu, httpUri);
                return DPoPValidationResult.invalid("Invalid htu claim");
            }

            Date iat = claims.getIssuedAt();
            if (iat == null) {
                return DPoPValidationResult.invalid("Missing iat claim");
            }
            long now = System.currentTimeMillis();
            if (Math.abs(now - iat.getTime()) > JTI_SKEW_WINDOW_MS) {
                return DPoPValidationResult.invalid("iat too old or in future");
            }

            String jti = claims.getId();
            if (jti == null || jti.isEmpty()) {
                return DPoPValidationResult.invalid("Missing jti claim");
            }

            String jtiKey = keyId + ":" + jti;
            long proofExpiry = iat.getTime() + JTI_SKEW_WINDOW_MS;
            usedJtis.entrySet().removeIf(e -> e.getValue() < System.currentTimeMillis());
            Long existing = usedJtis.putIfAbsent(jtiKey, proofExpiry);
            if (existing != null) {
                log.debug("DPoP jti reuse detected (replay): key={}", jtiKey);
                return DPoPValidationResult.invalid("jti already used (replay detected)");
            }

            if (accessToken != null) {
                String ath = claims.get("ath", String.class);
                if (ath == null) {
                    return DPoPValidationResult.invalid("Missing ath claim for DPoP-bound token");
                }
                if (!calculateAccessTokenHash(accessToken).equals(ath)) {
                    return DPoPValidationResult.invalid("Invalid ath claim");
                }
            }

            return DPoPValidationResult.valid(dpopKeyManager.getPublicKeyThumbprint(keyId));
        } catch (Exception e) {
            log.warn("DPoP proof validation error", e);
            return DPoPValidationResult.invalid("Invalid DPoP proof: Unexpected Exception.");
        }
    }

    private String calculateAccessTokenHash(String accessToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(accessToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Normalizes URI comparison across reverse proxies by comparing host, port and path only.
     */
    private String normalizeUriForComparison(String uri) {
        try {
            URI parsed = new URI(uri);
            String host = parsed.getHost();
            int port = parsed.getPort();
            String scheme = parsed.getScheme();
            boolean isStandardPort = (port == -1) ||
                    ("https".equalsIgnoreCase(scheme) && port == 443) ||
                    ("http".equalsIgnoreCase(scheme) && port == 80);
            String hostPart = isStandardPort ? host : host + ":" + port;
            return hostPart + parsed.getPath();
        } catch (Exception e) {
            return uri;
        }
    }
}
