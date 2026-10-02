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

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * AES-GCM encrypted browser session cookie.
 * <p>
 * The browser never sees an access token, DPoP key id, or JWT. The cookie value is an
 * opaque ciphertext that decrypts to a short JSON envelope containing only the database
 * key id and the cookie expiry. The backing key material never leaves the server.
 */
@Service
public class SessionCookieService {
    private static final Logger log = LoggerFactory.getLogger(SessionCookieService.class);
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final String COOKIE_VERSION = "1";

    public static final class InvalidSessionCookieException extends RuntimeException {
        public InvalidSessionCookieException(String message) {
            super(message);
        }

        public InvalidSessionCookieException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final ObjectMapper objectMapper;
    private final String cookieName;
    private final SecretKeySpec encryptionKey;
    private final long maxAgeSeconds;
    private final SecureRandom random = new SecureRandom();

    public SessionCookieService(ObjectMapper objectMapper,
                                @Value("${pudel.session.cookie-name:pudel_session}") String cookieName,
                                @Value("${pudel.session.cookie-secret:}") String configuredSecret,
                                @Value("${pudel.session.cookie-key-path:}") String keyPath,
                                @Value("${pudel.jwt.expiration:604800000}") long jwtExpirationMillis) {
        this.objectMapper = objectMapper;
        this.cookieName = cookieName;
        this.maxAgeSeconds = Math.max(60L, jwtExpirationMillis / 1000L);
        this.encryptionKey = new SecretKeySpec(loadOrCreateKey(configuredSecret, keyPath), "AES");
        log.info("Initialized encrypted session cookie service (cookie={}, maxAgeSeconds={})",
                cookieName, maxAgeSeconds);
    }

    private byte[] loadOrCreateKey(String configuredSecret, String keyPath) {
        try {
            if (configuredSecret != null && !configuredSecret.isBlank()) {
                return sha256(configuredSecret.getBytes(StandardCharsets.UTF_8));
            }

            Path path = Path.of(keyPath);
            if (Files.exists(path)) {
                byte[] encoded = Base64.getDecoder().decode(Files.readString(path).strip());
                if (encoded.length < 32) {
                    throw new IllegalStateException("Session cookie key file is too short: " + path);
                }
                return encoded;
            }

            byte[] key = new byte[32];
            random.nextBytes(key);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp-" + System.nanoTime());
            Files.writeString(tmp, Base64.getEncoder().encodeToString(key), StandardCharsets.US_ASCII);
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnsupported) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return key;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize the session cookie encryption key", e);
        }
    }

    private static byte[] sha256(byte[] value) throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256").digest(value);
    }

    public String encrypt(String keyId, Instant expiresAt) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("v", COOKIE_VERSION);
            envelope.put("keyId", keyId);
            envelope.put("exp", expiresAt.getEpochSecond());

            byte[] iv = new byte[GCM_IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(
                    objectMapper.writeValueAsString(envelope).getBytes(StandardCharsets.UTF_8));

            return B64.encodeToString(iv) + "." + B64.encodeToString(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encrypt the session cookie", e);
        }
    }

    public String decrypt(String cookieValue) {
        if (cookieValue == null || cookieValue.isBlank()) {
            throw new InvalidSessionCookieException("Missing session cookie value");
        }

        String[] parts = cookieValue.split("\\.", -1);
        if (parts.length != 2) {
            throw new InvalidSessionCookieException("Malformed session cookie value");
        }

        try {
            byte[] iv = B64D.decode(parts[0]);
            byte[] ciphertext = B64D.decode(parts[1]);
            if (iv.length != GCM_IV_BYTES) {
                throw new InvalidSessionCookieException("Invalid session cookie IV");
            }

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            String json = new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
            JsonNode envelope = objectMapper.readTree(json);

            if (!"1".equals(envelope.path("v").asString())) {
                throw new InvalidSessionCookieException("Unsupported session cookie version");
            }
            String keyId = envelope.path("keyId").asString();
            long exp = envelope.path("exp").asLong(-1L);
            if (keyId == null || keyId.isBlank() || exp <= 0) {
                throw new InvalidSessionCookieException("Incomplete session cookie");
            }
            if (Instant.ofEpochSecond(exp).isBefore(Instant.now())) {
                throw new InvalidSessionCookieException("Session cookie expired");
            }
            return keyId;
        } catch (InvalidSessionCookieException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidSessionCookieException("Invalid session cookie", e);
        }
    }

    public Optional<String> readKeyId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(decrypt(cookie.getValue()));
            }
        }
        return Optional.empty();
    }

    public void writeCookie(HttpServletResponse response, String keyId, Instant expiresAt) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, encrypt(keyId, expiresAt))
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public long getMaxAgeSeconds() {
        return maxAgeSeconds;
    }
}
