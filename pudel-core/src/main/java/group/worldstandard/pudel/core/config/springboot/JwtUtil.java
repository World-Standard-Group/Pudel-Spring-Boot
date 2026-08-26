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
 * Utility class for handling JSON Web Tokens (JWT).
 * Provides methods for generating, validating, and extracting information from JWT tokens.
 * Supports both standard-bearer tokens and DPoP (Demonstrating Proof-of-Possession) bound tokens.
 * Automatically loads RSA public and private keys from PEM files during initialization.
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
     * Loads an RSA private key from a PEM file located at the specified path.
     * The method reads the PEM-encoded private key, strips the headers and footers,
     * and decodes the key content into a byte array. It then generates a {@link PrivateKey}
     * instance using the RSA algorithm and PKCS#8 encoding specification.
     *
     * @param path the file system path to the PEM-encoded private key file
     * @return the loaded {@link PrivateKey} instance
     * @throws IOException if an I/O error occurs while reading the file
     * @throws NoSuchAlgorithmException if the RSA algorithm is not available
     * @throws InvalidKeySpecException if the key specification is invalid
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
     * Loads an RSA public key from a PEM file located at the specified path.
     * The method reads the PEM-encoded public key, strips the headers and footers,
     * and decodes the key content into a byte array. It then generates a {@link PublicKey}
     * instance using the RSA algorithm and X.509 encoding specification.
     *
     * @param path the file system path to the PEM-encoded public key file
     * @return the loaded {@link PublicKey} instance
     * @throws IOException if an I/O error occurs while reading the file
     * @throws NoSuchAlgorithmException if the RSA algorithm is not available
     * @throws InvalidKeySpecException if the key specification is invalid
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
     * Reads a PEM-formatted key from the specified file path and extracts the base64-encoded body
     * by removing the provided headers and whitespace characters.
     *
     * @param path the file system path to the PEM key file
     * @param headers an array of header strings to remove from the key content
     * @return the decoded byte array representing the key body
     * @throws IOException if an I/O error occurs while reading the file
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
     * Generates a JWT token for the specified user ID with default claims.
     * This method delegates to the overloaded generateToken method with an empty claims map.
     *
     * @param userId the unique identifier of the user for whom the token is generated
     * @return a compact, URL-safe JWT string representation of the token
     */
    public String generateToken(String userId) {
        return generateToken(userId, new HashMap<>());
    }

    /**
     * Generates a JWT token for the specified user ID with the provided claims.
     *
     * @param userId the unique identifier of the user for whom the token is generated
     * @param claims a map of additional claims to include in the token
     * @return a compact, URL-safe JWT string representation of the token
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
     * @param subject the user ID
     * @param claims additional claims
     * @param dpopThumbprint the JWK thumbprint from the DPoP proof
     * @return the DPoP-bound JWT token
     */
    public String generateDPoPBoundToken(String subject, Map<String, Object> claims, String dpopThumbprint) {
        long now = System.currentTimeMillis();
        Date expiryDate = new Date(now + jwtExpiration);

        // Create the confirmation claim with JWK thumbprint
        // Per RFC 9449, this is: "cnf": {"jkt": "<thumbprint>"}
        Map<String, Object> cnf = new HashMap<>();
        cnf.put(DPOP_JKT_CLAIM, dpopThumbprint);

        Map<String, Object> allClaims = new HashMap<>(claims);
        allClaims.put(DPOP_THUMBPRINT_CLAIM, cnf);

        return Jwts.builder()
                .subject(subject)
                .claims(allClaims)
                .issuedAt(new Date(now))
                .expiration(expiryDate)
                .signWith(privateKey, Jwts.SIG.RS512)
                .compact();
    }

    /**
     * Checks if the provided JWT token is bound to a DPoP (Demonstrating Proof-of-Possession) key.
     * This method verifies the token signature using the configured public key and checks
     * for the presence of a DPoP thumbprint claim.
     *
     * @param token the JWT token string to check
     * @return true if the token contains a DPoP thumbprint claim and is valid, false otherwise
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
     * Extracts the user ID from the subject claim of a JWT token.
     * The method parses and verifies the token using the configured public key.
     * If the token is invalid or does not contain a subject claim, the method returns null.
     *
     * @param token the JWT token string from which to extract the user ID
     * @return the user ID extracted from the token's subject claim, or null if extraction fails
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
     * Parses and retrieves the claims from the provided JWT token.
     * This method uses the configured public key to verify the token's signature
     * and extract its payload as a {@link Claims} object.
     *
     * @param token the JWT token string from which to extract claims
     * @return the {@link Claims} object containing the token's payload, or null if parsing fails
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
     * Validates the integrity and authenticity of a JWT token.
     * This method attempts to parse and verify the provided token using the configured public key.
     * It returns true if the token is successfully validated, otherwise false.
     *
     * @param token the JWT token string to validate
     * @return true if the token is valid, false otherwise
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
     * Retrieves the expiration date from the provided JWT token.
     * This method parses and verifies the token using the configured public key,
     * then extracts the expiration claim from the token's payload.
     * If the token is invalid or the expiration claim is not present, the method returns null.
     *
     * @param token the JWT token string from which to extract the expiration date
     * @return the expiration date extracted from the token, or null if extraction fails
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
     * Checks whether the provided JWT token has expired.
     * This method retrieves the expiration date from the token and compares it
     * with the current date. If the expiration date is before the current date,
     * the token is considered expired.
     *
     * @param token the JWT token string to check for expiration
     * @return true if the token has expired or if the expiration date cannot be retrieved, false otherwise
     */
    public boolean isTokenExpired(String token) {
        Date expirationDate = getExpirationDateFromToken(token);
        if (expirationDate == null) {
            return true;
        }
        return expirationDate.before(new Date());
    }

    /**
     * Retrieves the public key used for JWT token verification.
     *
     * @return the {@link PublicKey} instance used for verifying JWT signatures
     */
    public PublicKey getPublicKey() {
        return publicKey;
    }

    /**
     * Retrieves the private key used for JWT token generation.
     *
     * @return the {@link PrivateKey} instance used for signing JWT tokens
     */
    protected PrivateKey getPrivateKey() {
        return privateKey;
    }
}
