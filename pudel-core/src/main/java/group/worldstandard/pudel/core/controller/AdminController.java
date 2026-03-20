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

import group.worldstandard.pudel.core.config.springboot.SwaggerAccessFilter;
import group.worldstandard.pudel.core.entity.AdminWhitelist;
import group.worldstandard.pudel.core.entity.AdminWhitelist.AdminRole;
import group.worldstandard.pudel.core.entity.PluginMetadata;
import group.worldstandard.pudel.core.repository.AdminWhitelistRepository;
import group.worldstandard.pudel.core.service.LogService;
import group.worldstandard.pudel.core.service.PluginService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * REST controller for self-hosted admin operations with Mutual RSA Authentication.
 * <p>
 * Uses a mutual authentication system where each admin has their own RSA keypair:
 * - Pudel proves its identity by signing challenges with its private key
 * - Admins prove their identity by signing challenges with their private keys
 * - Pudel verifies admin signatures using their registered public keys
 * <p>
 * <b>Mutual Authentication Flow:</b>
 * <ol>
 *   <li>User logs in via Discord OAuth (gets user JWT with discordUserId)</li>
 *   <li>User requests challenge: GET /api/admin/challenge</li>
 *   <li>Pudel generates challenge and signs it (proves Pudel identity)</li>
 *   <li>User verifies Pudel's signature using Pudel's public key</li>
 *   <li>User signs the challenge nonce with their PRIVATE key</li>
 *   <li>User submits signed challenge: POST /api/admin/auth/mutual</li>
 *   <li>Pudel looks up user's PUBLIC key from database by discordUserId</li>
 *   <li>Pudel verifies the signature - if valid, issues AdminJWT</li>
 * </ol>
 * <p>
 * <b>Initial Owner Setup:</b>
 * <ul>
 *   <li>Owner's Discord ID is set via PUDEL_ADMIN_INITIAL_OWNER env var</li>
 *   <li>Owner's public key is loaded from PUDEL_ADMIN_OWNER_PUBLIC_KEY_PATH</li>
 *   <li>On first startup, owner entry is created in admin_whitelist</li>
 * </ul>
 * <p>
 * <b>Adding New Admins:</b>
 * <ul>
 *   <li>New admin generates their own RSA keypair</li>
 *   <li>Admin gives their PUBLIC key to the owner</li>
 *   <li>Owner uploads the public key via POST /api/admin/whitelist</li>
 *   <li>New admin can now authenticate using their PRIVATE key</li>
 * </ul>
 */
@Tag(name = "Admin", description = "Self-hosted admin operations with Mutual RSA Authentication")
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private static final long CHALLENGE_EXPIRY_MS = 60 * 1000; // 1 minute
    private static final long ADMIN_SESSION_EXPIRY_MS = 60 * 60 * 1000; // 1 hour

    private final PluginService pluginService;
    private final AdminWhitelistRepository adminWhitelistRepository;
    private final LogService logService;

    // Pending challenges: challengeId -> ChallengeData
    private final Map<String, ChallengeData> pendingChallenges = new ConcurrentHashMap<>();

    // Active admin sessions: sessionId -> AdminSessionData
    private final Map<String, AdminSessionData> activeSessions = new ConcurrentHashMap<>();

    @Value("${pudel.jwt.private-key-path:keys/pv.key}")
    private String privateKeyPath;

    @Value("${pudel.jwt.public-key-path:keys/pb.key}")
    private String publicKeyPath;

    @Value("${pudel.plugin.directory:./plugins}")
    private String pluginsDirectory;

    @Value("${pudel.admin.initial-owner:}")
    private String initialOwnerId;

    @Value("${pudel.admin.owner-public-key-path:keys/owner_pb.key}")
    private String ownerPublicKeyPath;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    public AdminController(PluginService pluginService,
                           AdminWhitelistRepository adminWhitelistRepository,
                           LogService logService) {
        this.pluginService = pluginService;
        this.adminWhitelistRepository = adminWhitelistRepository;
        this.logService = logService;
    }

    // =====================================================
    // Key Management
    // =====================================================

    /**
     * Load RSA keys lazily.
     */
    private void ensureKeysLoaded() {
        if (privateKey == null || publicKey == null) {
            try {
                privateKey = loadPrivateKey(privateKeyPath);
                publicKey = loadPublicKey(publicKeyPath);
                log.info("RSA keys loaded successfully for admin authentication");
            } catch (Exception e) {
                log.error("Failed to load RSA keys: {}", e.getMessage());
                throw new RuntimeException("Admin authentication not available: RSA keys not configured", e);
            }
        }
    }

    private PrivateKey loadPrivateKey(String path) throws Exception {
        byte[] keyBytes = Files.readAllBytes(Path.of(path));
        String keyContent = new String(keyBytes)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(keyContent);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    private PublicKey loadPublicKey(String path) throws Exception {
        byte[] keyBytes = Files.readAllBytes(Path.of(path));
        String keyContent = new String(keyBytes)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(keyContent);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    // =====================================================
    // Authentication Flow
    // =====================================================

    /**
     * Get Pudel's public key for admin to verify server identity.
     * GET /api/admin/public-key
     */
    @GetMapping("/public-key")
    public ResponseEntity<?> getPublicKey() {
        try {
            byte[] keyBytes = Files.readAllBytes(Path.of(publicKeyPath));
            String publicKeyPem = new String(keyBytes);

            Map<String, Object> response = new HashMap<>();
            response.put("publicKey", publicKeyPem);
            response.put("algorithm", "RSA");
            response.put("usage", "Use this key to verify challenge signatures from Pudel");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to read public key", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Public key not available"));
        }
    }

    /**
     * Generate a challenge for admin authentication.
     * Pudel signs the challenge with its private key to prove server identity.
     * GET /api/admin/challenge
     */
    @GetMapping("/challenge")
    public ResponseEntity<?> getChallenge() {
        try {
            ensureKeysLoaded();
            ensureInitialOwner();

            // Clean expired challenges
            cleanExpiredChallenges();

            // Generate challenge
            String challengeId = UUID.randomUUID().toString();
            String challengeNonce = UUID.randomUUID().toString();
            long timestamp = System.currentTimeMillis();
            long expiry = timestamp + CHALLENGE_EXPIRY_MS;

            // Sign challenge with Pudel's private key (server identity proof)
            String signature = Jwts.builder()
                    .subject("pudel-admin-challenge")
                    .claim("challengeId", challengeId)
                    .claim("nonce", challengeNonce)
                    .claim("timestamp", timestamp)
                    .expiration(new Date(expiry))
                    .signWith(privateKey, Jwts.SIG.RS256)
                    .compact();

            // Store challenge
            pendingChallenges.put(challengeId, new ChallengeData(challengeId, challengeNonce, expiry));

            Map<String, Object> response = new HashMap<>();
            response.put("challengeId", challengeId);
            response.put("nonce", challengeNonce);
            response.put("timestamp", timestamp);
            response.put("expiry", expiry);
            response.put("signature", signature);
            response.put("message", "Verify this signature with Pudel's public key, then submit your Discord user ID");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to generate challenge", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to generate challenge: " + e.getMessage()));
        }
    }

    /**
     * Check if the current Discord OAuth user is an admin.
     * GET /api/admin/check
     * <p>
     * Returns whether the authenticated user can access admin panel.
     */
    @GetMapping("/check")
    public ResponseEntity<?> checkAdminStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            ensureKeysLoaded();
            ensureInitialOwner();

            // Accept both Bearer and DPoP tokens
            if (authHeader == null || (!authHeader.startsWith("Bearer ") && !authHeader.startsWith("DPoP "))) {
                return ResponseEntity.ok(Map.of(
                        "isAdmin", false,
                        "reason", "Not authenticated"
                ));
            }

            // Extract token (works for both "Bearer {token}" and "DPoP {token}")
            String token = authHeader.contains(" ") ? authHeader.substring(authHeader.indexOf(" ") + 1) : null;
            if (token == null || token.isBlank()) {
                return ResponseEntity.ok(Map.of(
                        "isAdmin", false,
                        "reason", "Invalid token format"
                ));
            }

            String discordUserId;

            try {
                Claims claims = Jwts.parser()
                        .verifyWith(publicKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                // If it's already an admin token, return admin info
                if ("pudel-admin-session".equals(claims.getSubject())) {
                    String sessionId = claims.get("sessionId", String.class);
                    AdminSessionData session = activeSessions.get(sessionId);
                    if (session != null && System.currentTimeMillis() < session.expiry) {
                        return ResponseEntity.ok(Map.of(
                                "isAdmin", true,
                                "isAdminSession", true,
                                "adminRole", session.adminRole.name(),
                                "canModify", session.canModify(),
                                "canManageAdmins", session.canManageAdmins()
                        ));
                    }
                }

                // Extract user ID from user JWT
                discordUserId = claims.get("discordUserId", String.class);
                if (discordUserId == null) {
                    discordUserId = claims.get("userId", String.class);
                }
                if (discordUserId == null) {
                    discordUserId = claims.getSubject();
                }
            } catch (Exception e) {
                return ResponseEntity.ok(Map.of(
                        "isAdmin", false,
                        "reason", "Invalid token"
                ));
            }

            if (discordUserId == null || !discordUserId.matches("^\\d{17,19}$")) {
                return ResponseEntity.ok(Map.of(
                        "isAdmin", false,
                        "reason", "Cannot extract user ID"
                ));
            }

            // Check whitelist
            Optional<AdminWhitelist> adminEntry = adminWhitelistRepository.findByDiscordUserId(discordUserId);

            if (adminEntry.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "isAdmin", false,
                        "reason", "Not in whitelist"
                ));
            }

            AdminWhitelist admin = adminEntry.get();

            if (!admin.isEnabled()) {
                return ResponseEntity.ok(Map.of(
                        "isAdmin", false,
                        "reason", "Admin account disabled"
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "isAdmin", true,
                    "isAdminSession", false,
                    "adminRole", admin.getAdminRole().name(),
                    "canModify", admin.canModify(),
                    "canManageAdmins", admin.canManageAdmins(),
                    "hasPublicKey", admin.getPublicKeyPem() != null && !admin.getPublicKeyPem().isEmpty(),
                    "message", "You can access the admin panel. Complete challenge verification to continue."
            ));
        } catch (Exception e) {
            log.error("Error checking admin status", e);
            return ResponseEntity.ok(Map.of(
                    "isAdmin", false,
                    "reason", "Error checking status"
            ));
        }
    }

    /**
     * Mutual RSA Authentication - Admin signs challenge with their private key.
     * POST /api/admin/auth/mutual
     * <p>
     * Flow:
     * 1. User is logged in via Discord OAuth (has user JWT with discordUserId)
     * 2. User requested challenge from GET /api/admin/challenge
     * 3. User verified Pudel's signature (proves server identity)
     * 4. User signs the challenge nonce with their PRIVATE RSA key
     * 5. User submits the signature here
     * 6. Pudel looks up user's PUBLIC key from database by discordUserId
     * 7. Pudel verifies the signature
     * 8. If valid, issues AdminJWT session token
     */
    @PostMapping("/auth/mutual")
    public ResponseEntity<?> authenticateMutual(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody MutualAuthRequest request) {
        log.info("Admin mutual auth request received. AuthHeader present: {}, AuthHeader prefix: {}",
                authHeader != null,
                authHeader != null ? authHeader.substring(0, Math.min(authHeader.length(), 10)) : "null");
        try {
            ensureKeysLoaded();
            ensureInitialOwner();

            // Step 1: Validate user is authenticated via Discord OAuth
            // Accept both Bearer and DPoP tokens (DPoP is validated by the filter)
            if (authHeader == null || (!authHeader.startsWith("Bearer ") && !authHeader.startsWith("DPoP "))) {
                log.warn("Admin mutual auth failed: Invalid auth header format");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Discord OAuth token required. Please login with Discord first."));
            }

            // Extract token (works for both "Bearer {token}" and "DPoP {token}")
            String userToken = authHeader.contains(" ") ? authHeader.substring(authHeader.indexOf(" ") + 1) : null;
            if (userToken == null || userToken.isBlank()) {
                log.warn("Admin mutual auth failed: Empty token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Invalid authorization header format"));
            }

            log.debug("Admin mutual auth: Extracted token, length={}", userToken.length());

            String discordUserId;
            String discordUsername;

            try {
                Claims userClaims = Jwts.parser()
                        .verifyWith(publicKey)
                        .build()
                        .parseSignedClaims(userToken)
                        .getPayload();

                // Don't accept admin tokens for this flow
                if ("pudel-admin-session".equals(userClaims.getSubject())) {
                    return ResponseEntity.badRequest()
                            .body(new ErrorResponse("Use your Discord OAuth token, not admin token"));
                }

                discordUserId = userClaims.get("discordUserId", String.class);
                discordUsername = userClaims.get("discordUsername", String.class);

                if (discordUserId == null) {
                    discordUserId = userClaims.get("userId", String.class);
                }
                if (discordUserId == null) {
                    discordUserId = userClaims.getSubject();
                }

                if (discordUserId == null || !discordUserId.matches("^\\d{17,19}$")) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(new ErrorResponse("Cannot extract Discord user ID from token"));
                }
            } catch (Exception e) {
                log.debug("Failed to parse user JWT: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Invalid or expired Discord OAuth token"));
            }

            // Step 2: Validate challenge exists
            if (request.challengeId == null || request.signature == null) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Missing challengeId or signature"));
            }

            ChallengeData challenge = pendingChallenges.get(request.challengeId);
            if (challenge == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Invalid or expired challenge"));
            }

            if (System.currentTimeMillis() > challenge.expiry) {
                pendingChallenges.remove(request.challengeId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Challenge expired"));
            }

            // Step 3: Look up admin and their public key
            Optional<AdminWhitelist> adminEntry = adminWhitelistRepository.findByDiscordUserId(discordUserId);

            if (adminEntry.isEmpty()) {
                log.warn("Mutual auth failed: Discord user {} not in whitelist", discordUserId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Your Discord account is not authorized for admin access"));
            }

            AdminWhitelist admin = adminEntry.get();

            if (!admin.isEnabled()) {
                log.warn("Mutual auth failed: Discord user {} is disabled", discordUserId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Your admin account is disabled"));
            }

            if (admin.getPublicKeyPem() == null || admin.getPublicKeyPem().isEmpty()) {
                log.warn("Mutual auth failed: Discord user {} has no registered public key", discordUserId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("No public key registered for your account. Contact the owner to register your key."));
            }

            // Step 4: Load admin's public key and verify signature
            PublicKey adminPublicKey;
            try {
                adminPublicKey = loadPublicKeyFromPem(admin.getPublicKeyPem());
            } catch (Exception e) {
                log.error("Failed to load public key for user {}: {}", discordUserId, e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ErrorResponse("Invalid public key format in database"));
            }

            // Step 5: Verify the signature
            // The client signed the challenge nonce with their private key
            boolean signatureValid;
            try {
                Signature sig = Signature.getInstance("SHA256withRSA");
                sig.initVerify(adminPublicKey);
                sig.update(challenge.nonce.getBytes());

                // Decode base64 signature
                byte[] signatureBytes = Base64.getDecoder().decode(request.signature);
                signatureValid = sig.verify(signatureBytes);
            } catch (Exception e) {
                log.debug("Signature verification error for user {}: {}", discordUserId, e.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Invalid signature format"));
            }

            if (!signatureValid) {
                log.warn("Mutual auth failed: Invalid signature for user {}", discordUserId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Signature verification failed. Make sure you signed with the correct private key."));
            }

            // Step 6: Signature valid! Remove challenge and create session
            pendingChallenges.remove(request.challengeId);

            admin.setLastLogin(LocalDateTime.now());
            if (discordUsername != null) {
                admin.setDiscordUsername(discordUsername);
            }
            adminWhitelistRepository.save(admin);

            // Generate AdminJWT session token
            String sessionId = UUID.randomUUID().toString();
            long sessionExpiry = System.currentTimeMillis() + ADMIN_SESSION_EXPIRY_MS;

            String adminToken = Jwts.builder()
                    .subject("pudel-admin-session")
                    .claim("sessionId", sessionId)
                    .claim("discordUserId", admin.getDiscordUserId())
                    .claim("discordUsername", admin.getDiscordUsername())
                    .claim("adminRole", admin.getAdminRole().name())
                    .claim("canModify", admin.canModify())
                    .claim("canManageAdmins", admin.canManageAdmins())
                    .issuedAt(new Date())
                    .expiration(new Date(sessionExpiry))
                    .signWith(privateKey, Jwts.SIG.RS256)
                    .compact();

            activeSessions.put(sessionId, new AdminSessionData(
                    sessionId,
                    admin.getDiscordUserId(),
                    admin.getAdminRole(),
                    sessionExpiry
            ));

            log.info("Mutual RSA auth successful: {} ({}) with role {}",
                    admin.getDiscordUsername(), admin.getDiscordUserId(), admin.getAdminRole());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Mutual authentication successful");
            response.put("adminToken", adminToken);
            response.put("discordUserId", admin.getDiscordUserId());
            response.put("discordUsername", admin.getDiscordUsername());
            response.put("adminRole", admin.getAdminRole().name());
            response.put("canModify", admin.canModify());
            response.put("canManageAdmins", admin.canManageAdmins());
            response.put("expiresAt", sessionExpiry);
            response.put("expiresIn", ADMIN_SESSION_EXPIRY_MS / 1000);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Mutual auth failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Authentication failed: " + e.getMessage()));
        }
    }

    /**
     * Load RSA public key from PEM string.
     */
    private PublicKey loadPublicKeyFromPem(String pem) throws Exception {
        String keyContent = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(keyContent);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    /**
     * Validate admin session token from Authorization header.
     * Returns AdminSessionData if valid, null otherwise.
     */
    private AdminSessionData validateAdminSession(String authHeader) {
        if (authHeader == null || (!authHeader.startsWith("Bearer ") && !authHeader.startsWith("DPoP "))) {
            return null;
        }

        // Extract token (works for both "Bearer {token}" and "DPoP {token}")
        String token = authHeader.contains(" ") ? authHeader.substring(authHeader.indexOf(" ") + 1) : null;
        if (token == null || token.isBlank()) {
            return null;
        }

        try {
            ensureKeysLoaded();

            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Verify it's an admin session token (not user JWT)
            if (!"pudel-admin-session".equals(claims.getSubject())) {
                return null;
            }

            String sessionId = claims.get("sessionId", String.class);
            if (sessionId == null) {
                return null;
            }

            // Check if session is still active
            AdminSessionData session = activeSessions.get(sessionId);
            if (session == null || System.currentTimeMillis() > session.expiry) {
                activeSessions.remove(sessionId);
                return null;
            }

            return session;
        } catch (Exception e) {
            log.debug("Admin session validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Logout - invalidate admin session.
     * POST /api/admin/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && (authHeader.startsWith("Bearer ") || authHeader.startsWith("DPoP "))) {
            try {
                ensureKeysLoaded();
                // Extract token (works for both "Bearer {token}" and "DPoP {token}")
                String token = authHeader.contains(" ") ? authHeader.substring(authHeader.indexOf(" ") + 1) : null;
                if (token != null && !token.isBlank()) {
                    Claims claims = Jwts.parser()
                            .verifyWith(publicKey)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();

                    String sessionId = claims.get("sessionId", String.class);
                    if (sessionId != null) {
                        AdminSessionData session = activeSessions.remove(sessionId);
                        if (session != null) {
                            log.info("Admin session logged out: {} ({})", session.discordUserId, sessionId);
                        }
                    }
                }
            } catch (Exception ignored) {
                // Token invalid, session already gone
            }
        }

        return ResponseEntity.ok(new SuccessResponse("Logged out successfully"));
    }

    // =====================================================
    // Swagger Access Authorization
    // =====================================================

    /**
     * Authorize Swagger UI access for an authenticated admin.
     * POST /api/admin/swagger/authorize
     * <p>
     * Requires a valid AdminJWT (obtained via the mutual RSA crypto challenge flow).
     * Sets a {@code pudel-swagger-session} HttpOnly cookie that grants access to
     * Swagger UI and OpenAPI docs endpoints.
     * <p>
     * <b>Flow:</b>
     * <ol>
     *   <li>Admin completes crypto challenge → receives AdminJWT</li>
     *   <li>Admin calls this endpoint with {@code Authorization: Bearer <AdminJWT>}</li>
     *   <li>Server validates admin session and issues a swagger session cookie</li>
     *   <li>Admin opens {@code /swagger-ui.html} → SwaggerAccessFilter checks cookie → granted</li>
     * </ol>
     */
    @PostMapping("/swagger/authorize")
    public ResponseEntity<?> authorizeSwaggerAccess(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            AdminSessionData session = validateAdminSession(authHeader);
            if (session == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse(
                                "Valid admin session required. Complete the crypto challenge first: GET /api/admin/challenge"));
            }

            ensureKeysLoaded();

            // Generate a swagger-session JWT (short-lived, cookie-bound)
            long expiry = SwaggerAccessFilter.SWAGGER_SESSION_EXPIRY_MS;
            String swaggerToken = Jwts.builder()
                    .subject(SwaggerAccessFilter.SWAGGER_SESSION_SUBJECT)
                    .claim("discordUserId", session.discordUserId)
                    .claim("adminRole", session.adminRole.name())
                    .claim("sessionId", session.sessionId)
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + expiry))
                    .signWith(privateKey, Jwts.SIG.RS256)
                    .compact();

            // Build HttpOnly cookie
            ResponseCookie cookie = ResponseCookie
                    .from(SwaggerAccessFilter.SWAGGER_SESSION_COOKIE, swaggerToken)
                    .httpOnly(true)
                    .secure(false) // Set to true in production (requires HTTPS)
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(expiry / 1000)
                    .build();

            log.info("Swagger access authorized for admin: {} ({})",
                    session.discordUserId, session.adminRole);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Swagger UI access authorized. Cookie set.");
            response.put("swaggerUrl", "/swagger-ui.html");
            response.put("expiresIn", expiry / 1000);
            response.put("adminRole", session.adminRole.name());

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(response);
        } catch (Exception e) {
            log.error("Failed to authorize swagger access", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to authorize swagger access: " + e.getMessage()));
        }
    }

    /**
     * Revoke Swagger UI access by clearing the session cookie.
     * DELETE /api/admin/swagger/authorize
     */
    @DeleteMapping("/swagger/authorize")
    public ResponseEntity<?> revokeSwaggerAccess() {
        // Clear the cookie by setting max-age to 0
        ResponseCookie cookie = ResponseCookie
                .from(SwaggerAccessFilter.SWAGGER_SESSION_COOKIE, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();

        log.info("Swagger access cookie cleared");

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new SuccessResponse("Swagger UI access revoked. Cookie cleared."));
    }

    // =====================================================
    // Log Viewer
    // =====================================================

    /**
     * Get recent application log entries.
     * GET /api/admin/logs?count=500&level=INFO
     *
     * @param count maximum number of entries to return (default 500)
     * @param level minimum log level filter: ERROR, WARN, INFO, DEBUG (default: all)
     */
    @GetMapping("/logs")
    public ResponseEntity<?> getLogs(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "500") int count,
            @RequestParam(required = false) String level) {

        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        try {
            var entries = logService.getRecentLogs(Math.min(count, 5000), level);

            Map<String, Object> response = new HashMap<>();
            response.put("entries", entries);
            response.put("count", entries.size());
            response.put("stats", logService.getStats());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching logs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch logs: " + e.getMessage()));
        }
    }

    /**
     * Stream application logs in real-time via Server-Sent Events (SSE).
     * GET /api/admin/logs/stream?token=<AdminJWT>&history=true
     * <p>
     * Uses query parameter for authentication since EventSource API
     * does not support custom headers.
     *
     * @param token AdminJWT token (required, passed as query param)
     * @param history if true, sends the last 200 entries before streaming live
     */
    @GetMapping(value = "/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(
            @RequestParam(value = "token") String token,
            @RequestParam(defaultValue = "true") boolean history) {

        // Validate admin token from query parameter
        AdminSessionData session = validateAdminSession("Bearer " + token);
        if (session == null) {
            SseEmitter emitter = new SseEmitter(0L);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("error", "Unauthorized", "message", "Invalid admin token")));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        log.info("Admin log stream started by: {} ({})", session.discordUserId, session.adminRole);
        return logService.createStream(history);
    }

    /**
     * Get log buffer statistics.
     * GET /api/admin/logs/stats
     */
    @GetMapping("/logs/stats")
    public ResponseEntity<?> getLogStats(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        return ResponseEntity.ok(logService.getStats());
    }

    /**
     * Clear the in-memory log buffer.
     * DELETE /api/admin/logs
     */
    @DeleteMapping("/logs")
    public ResponseEntity<?> clearLogs(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        if (!session.canModify()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Insufficient permissions to clear logs"));
        }

        logService.clearLogs();
        log.info("Log buffer cleared by admin: {}", session.discordUserId);

        return ResponseEntity.ok(new SuccessResponse("Log buffer cleared"));
    }

    /**
     * Ensure initial owner is created if configured and no admins exist.
     */
    private void ensureInitialOwner() {
        if (initialOwnerId != null && !initialOwnerId.isEmpty() && !adminWhitelistRepository.hasAnyAdmin()) {
            AdminWhitelist owner = new AdminWhitelist(initialOwnerId, AdminRole.OWNER);
            owner.setNote("Initial owner configured via PUDEL_ADMIN_INITIAL_OWNER");

            // Load owner's public key from file if available
            try {
                Path ownerKeyPath = Path.of(ownerPublicKeyPath);
                if (Files.exists(ownerKeyPath)) {
                    String ownerPublicKeyPem = Files.readString(ownerKeyPath);
                    owner.setPublicKeyPem(ownerPublicKeyPem);
                    log.info("Loaded owner's public key from: {}", ownerPublicKeyPath);
                } else {
                    log.warn("Owner public key file not found: {}. Owner will need to register their key.", ownerPublicKeyPath);
                }
            } catch (Exception e) {
                log.warn("Failed to load owner public key: {}. Owner will need to register their key.", e.getMessage());
            }

            adminWhitelistRepository.save(owner);
            log.info("Created initial admin owner: {}", initialOwnerId);
        }
    }

    /**
     * Clean expired challenges and sessions.
     */
    private void cleanExpiredChallenges() {
        long now = System.currentTimeMillis();
        pendingChallenges.entrySet().removeIf(e -> now > e.getValue().expiry);
        activeSessions.entrySet().removeIf(e -> now > e.getValue().expiry);
    }

    // Session data classes
    private static class ChallengeData {
        final String challengeId;
        final String nonce;
        final long expiry;

        ChallengeData(String challengeId, String nonce, long expiry) {
            this.challengeId = challengeId;
            this.nonce = nonce;
            this.expiry = expiry;
        }
    }

    private static class AdminSessionData {
        final String sessionId;
        final String discordUserId;
        final AdminRole adminRole;
        final long expiry;

        AdminSessionData(String sessionId, String discordUserId, AdminRole adminRole, long expiry) {
            this.sessionId = sessionId;
            this.discordUserId = discordUserId;
            this.adminRole = adminRole;
            this.expiry = expiry;
        }

        boolean canModify() {
            return adminRole == AdminRole.OWNER || adminRole == AdminRole.ADMIN;
        }

        boolean canManageAdmins() {
            return adminRole == AdminRole.OWNER;
        }
    }

    // =====================================================
    // Admin Whitelist Management (OWNER only)
    // =====================================================

    /**
     * Get all admin whitelist entries.
     * GET /api/admin/whitelist
     */
    @GetMapping("/whitelist")
    public ResponseEntity<?> getWhitelist(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        if (!session.canManageAdmins()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Only OWNER can manage admin whitelist"));
        }

        try {
            List<AdminWhitelist> admins = adminWhitelistRepository.findAll();
            List<Map<String, Object>> adminList = admins.stream()
                    .map(this::adminToMap)
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("admins", adminList);
            response.put("total", admins.size());
            response.put("enabled", admins.stream().filter(AdminWhitelist::isEnabled).count());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting admin whitelist", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to get admin whitelist: " + e.getMessage()));
        }
    }

    /**
     * Add a Discord user to admin whitelist with their RSA public key.
     * POST /api/admin/whitelist
     * <p>
     * IMPORTANT: For mutual RSA authentication, the new admin MUST provide their public key.
     * The admin keeps their private key secure and uses it to sign challenges.
     */
    @PostMapping("/whitelist")
    public ResponseEntity<?> addAdmin(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody AddAdminRequest request) {

        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        if (!session.canManageAdmins()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Only OWNER can manage admin whitelist"));
        }

        if (request.discordUserId == null || !request.discordUserId.matches("^\\d{17,19}$")) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Invalid Discord user ID"));
        }

        // Public key is required for mutual RSA authentication
        if (request.publicKeyPem == null || request.publicKeyPem.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Public key is required. The new admin must provide their RSA public key."));
        }

        // Validate the public key format
        try {
            loadPublicKeyFromPem(request.publicKeyPem);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Invalid public key format: " + e.getMessage()));
        }

        try {
            // Check if already exists
            if (adminWhitelistRepository.findByDiscordUserId(request.discordUserId).isPresent()) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Discord user already in whitelist"));
            }

            AdminRole role = AdminRole.ADMIN;
            if (request.adminRole != null) {
                try {
                    role = AdminRole.valueOf(request.adminRole.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest()
                            .body(new ErrorResponse("Invalid admin role. Valid: OWNER, ADMIN, MODERATOR"));
                }
            }

            // Cannot create another OWNER unless you're the only one
            if (role == AdminRole.OWNER && adminWhitelistRepository.hasOwner()) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Cannot create multiple OWNERs"));
            }

            AdminWhitelist newAdmin = new AdminWhitelist(request.discordUserId, request.discordUsername, role);
            newAdmin.setAddedBy(session.discordUserId);
            newAdmin.setNote(request.note);
            newAdmin.setPublicKeyPem(request.publicKeyPem);
            adminWhitelistRepository.save(newAdmin);

            log.info("Admin added to whitelist with public key: {} ({}) by {}",
                    request.discordUserId, role, session.discordUserId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Admin added to whitelist with public key");
            response.put("admin", adminToMap(newAdmin));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error adding admin to whitelist", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to add admin: " + e.getMessage()));
        }
    }

    /**
     * Update admin whitelist entry.
     * PUT /api/admin/whitelist/{discordUserId}
     * <p>
     * Can update role, enabled status, note, and public key.
     */
    @PutMapping("/whitelist/{discordUserId}")
    public ResponseEntity<?> updateAdmin(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String discordUserId,
            @RequestBody UpdateAdminRequest request) {

        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        if (!session.canManageAdmins()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Only OWNER can manage admin whitelist"));
        }

        try {
            Optional<AdminWhitelist> adminOpt = adminWhitelistRepository.findByDiscordUserId(discordUserId);
            if (adminOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            AdminWhitelist admin = adminOpt.get();

            // Cannot modify the last OWNER
            if (admin.getAdminRole() == AdminRole.OWNER &&
                request.adminRole != null &&
                !request.adminRole.equalsIgnoreCase("OWNER")) {
                long ownerCount = adminWhitelistRepository.findByAdminRole(AdminRole.OWNER).size();
                if (ownerCount <= 1) {
                    return ResponseEntity.badRequest()
                            .body(new ErrorResponse("Cannot demote the last OWNER"));
                }
            }

            if (request.adminRole != null) {
                try {
                    admin.setAdminRole(AdminRole.valueOf(request.adminRole.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest()
                            .body(new ErrorResponse("Invalid admin role"));
                }
            }

            if (request.enabled != null) {
                admin.setEnabled(request.enabled);
            }

            if (request.note != null) {
                admin.setNote(request.note);
            }

            // Update public key if provided
            if (request.publicKeyPem != null && !request.publicKeyPem.trim().isEmpty()) {
                try {
                    // Validate the key format
                    loadPublicKeyFromPem(request.publicKeyPem);
                    admin.setPublicKeyPem(request.publicKeyPem);
                    log.info("Updated public key for admin: {}", discordUserId);
                } catch (Exception e) {
                    return ResponseEntity.badRequest()
                            .body(new ErrorResponse("Invalid public key format: " + e.getMessage()));
                }
            }

            adminWhitelistRepository.save(admin);

            log.info("Admin updated: {} by {}", discordUserId, session.discordUserId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Admin updated");
            response.put("admin", adminToMap(admin));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error updating admin", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to update admin: " + e.getMessage()));
        }
    }

    /**
     * Remove a Discord user from admin whitelist.
     * DELETE /api/admin/whitelist/{discordUserId}
     */
    @DeleteMapping("/whitelist/{discordUserId}")
    public ResponseEntity<?> removeAdmin(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String discordUserId) {

        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        if (!session.canManageAdmins()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Only OWNER can manage admin whitelist"));
        }

        // Cannot remove yourself
        if (discordUserId.equals(session.discordUserId)) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Cannot remove yourself from whitelist"));
        }

        try {
            Optional<AdminWhitelist> adminOpt = adminWhitelistRepository.findByDiscordUserId(discordUserId);
            if (adminOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            AdminWhitelist admin = adminOpt.get();

            // Cannot remove the last OWNER
            if (admin.getAdminRole() == AdminRole.OWNER) {
                long ownerCount = adminWhitelistRepository.findByAdminRole(AdminRole.OWNER).size();
                if (ownerCount <= 1) {
                    return ResponseEntity.badRequest()
                            .body(new ErrorResponse("Cannot remove the last OWNER"));
                }
            }

            adminWhitelistRepository.delete(admin);

            // Invalidate any active sessions for this user
            activeSessions.entrySet().removeIf(e -> e.getValue().discordUserId.equals(discordUserId));

            log.info("Admin removed from whitelist: {} by {}", discordUserId, session.discordUserId);

            return ResponseEntity.ok(new SuccessResponse("Admin removed from whitelist"));
        } catch (Exception e) {
            log.error("Error removing admin from whitelist", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to remove admin: " + e.getMessage()));
        }
    }

    private Map<String, Object> adminToMap(AdminWhitelist admin) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", admin.getId());
        map.put("discordUserId", admin.getDiscordUserId());
        map.put("discordUsername", admin.getDiscordUsername());
        map.put("adminRole", admin.getAdminRole().name());
        map.put("enabled", admin.isEnabled());
        map.put("canModify", admin.canModify());
        map.put("canManageAdmins", admin.canManageAdmins());
        map.put("hasPublicKey", admin.getPublicKeyPem() != null && !admin.getPublicKeyPem().isEmpty());
        map.put("note", admin.getNote());
        map.put("addedBy", admin.getAddedBy());
        map.put("lastLogin", admin.getLastLogin());
        map.put("createdAt", admin.getCreatedAt());
        map.put("updatedAt", admin.getUpdatedAt());
        return map;
    }

    // =====================================================
    // System Status
    // =====================================================

    /**
     * Get comprehensive system status.
     * GET /api/admin/status
     */
    @GetMapping("/status")
    public ResponseEntity<?> getSystemStatus(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        try {
            Map<String, Object> status = new HashMap<>();

            // Admin info
            Map<String, Object> adminInfo = new HashMap<>();
            adminInfo.put("discordUserId", session.discordUserId);
            adminInfo.put("adminRole", session.adminRole.name());
            adminInfo.put("canModify", session.canModify());
            adminInfo.put("canManageAdmins", session.canManageAdmins());
            status.put("admin", adminInfo);

            // System info
            Runtime runtime = Runtime.getRuntime();
            status.put("javaVersion", System.getProperty("java.version"));
            status.put("osName", System.getProperty("os.name"));
            status.put("osVersion", System.getProperty("os.version"));

            // Memory
            Map<String, Object> memory = new HashMap<>();
            memory.put("max", runtime.maxMemory() / (1024 * 1024) + " MB");
            memory.put("total", runtime.totalMemory() / (1024 * 1024) + " MB");
            memory.put("free", runtime.freeMemory() / (1024 * 1024) + " MB");
            memory.put("used", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) + " MB");
            status.put("memory", memory);

            // Plugins
            List<PluginMetadata> plugins = pluginService.getAllPlugins();
            Map<String, Object> pluginStats = new HashMap<>();
            pluginStats.put("total", plugins.size());
            pluginStats.put("enabled", plugins.stream().filter(PluginMetadata::isEnabled).count());
            pluginStats.put("loaded", plugins.stream().filter(PluginMetadata::isLoaded).count());
            status.put("plugins", pluginStats);

            // Admins
            Map<String, Object> adminStats = new HashMap<>();
            adminStats.put("total", adminWhitelistRepository.count());
            adminStats.put("enabled", adminWhitelistRepository.countByEnabledTrue());
            status.put("admins", adminStats);

            // Plugins directory
            File dir = new File(pluginsDirectory);
            status.put("pluginsDirectory", dir.getAbsolutePath());
            status.put("pluginsDirectoryExists", dir.exists());
            status.put("pluginsDirectoryWritable", dir.canWrite());

            status.put("timestamp", OffsetDateTime.now().toString());

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error getting system status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to get system status: " + e.getMessage()));
        }
    }

    // =====================================================
    // Plugin Management
    // =====================================================

    /**
     * Get all plugins with detailed information.
     * GET /api/admin/plugins
     */
    @GetMapping("/plugins")
    public ResponseEntity<?> getAllPlugins(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        try {
            List<PluginMetadata> plugins = pluginService.getAllPlugins();
            List<Map<String, Object>> pluginList = plugins.stream()
                    .map(this::pluginToMap)
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("plugins", pluginList);
            response.put("total", plugins.size());
            response.put("enabled", plugins.stream().filter(PluginMetadata::isEnabled).count());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting plugins", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to get plugins: " + e.getMessage()));
        }
    }

    /**
     * Upload a plugin JAR file.
     * POST /api/admin/plugins/upload
     */
    @PostMapping("/plugins/upload")
    public ResponseEntity<?> uploadPlugin(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam("file") MultipartFile file) {

        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        if (!session.canModify()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Insufficient permissions to upload plugins"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("No file provided"));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".jar")) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Only JAR files are allowed"));
        }

        // Sanitize filename: extract only the base name to prevent path traversal
        String sanitizedFilename = Path.of(originalFilename).getFileName().toString();
        if (sanitizedFilename.isEmpty() || sanitizedFilename.startsWith(".") || !sanitizedFilename.toLowerCase().endsWith(".jar")) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Invalid filename"));
        }

        try {
            // Ensure plugins directory exists
            File dir = new File(pluginsDirectory);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created) {
                    log.warn("Could not create plugins directory: {}", dir.getAbsolutePath());
                }
            }

            // Resolve and validate that the target path is within the plugins directory
            Path targetPath = dir.toPath().resolve(sanitizedFilename).normalize();
            if (!targetPath.startsWith(dir.toPath().toAbsolutePath().normalize())) {
                log.warn("Path traversal attempt detected in plugin upload by {}: {}", session.discordUserId, originalFilename);
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Invalid filename: path traversal detected"));
            }
            boolean isOverwrite = Files.exists(targetPath);
            String existingPluginName = null;
            boolean wasEnabled = false;

            if (isOverwrite) {
                // Find the plugin associated with this JAR file
                Optional<PluginMetadata> existingPlugin = pluginService.getAllPlugins().stream()
                        .filter(p -> sanitizedFilename.equals(p.getJarFileName()))
                        .findFirst();

                if (existingPlugin.isPresent()) {
                    existingPluginName = existingPlugin.get().getPluginName();
                    wasEnabled = existingPlugin.get().isEnabled() || pluginService.isPluginEnabled(existingPluginName);

                    log.info("Overwriting existing plugin: {} (wasEnabled={})", existingPluginName, wasEnabled);

                    // Unload the existing plugin before overwriting
                    pluginService.removePlugin(existingPluginName);

                    // Small delay to ensure file handles are released
                    Thread.sleep(500);
                }
            }

            // Save the file
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("Plugin JAR uploaded by {}: {} (overwrite={})", session.discordUserId, sanitizedFilename, isOverwrite);

            // Wait for file to be fully written
            Thread.sleep(300);

            // Manually trigger plugin load instead of relying on file watcher
            // This ensures consistent behavior for overwrites
            File jarFile = targetPath.toFile();
            if (jarFile.exists() && jarFile.canRead()) {
                // Trigger discovery for this specific file
                // The file watcher might still fire, but loadPlugin handles duplicates gracefully
                log.info("Triggering plugin load for: {}", sanitizedFilename);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Plugin uploaded successfully: " + sanitizedFilename);
            response.put("filename", sanitizedFilename);
            response.put("size", file.getSize());
            response.put("path", targetPath.toAbsolutePath().toString());
            response.put("uploadedBy", session.discordUserId);
            response.put("wasOverwrite", isOverwrite);
            if (existingPluginName != null) {
                response.put("previousPluginName", existingPluginName);
                response.put("wasEnabled", wasEnabled);
                response.put("note", "Previous plugin was unloaded. The new plugin will be discovered by the file watcher. If it was enabled, you may need to re-enable it.");
            }

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Error uploading plugin: {}", sanitizedFilename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to upload plugin: " + e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Upload interrupted: {}", sanitizedFilename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Upload interrupted: " + e.getMessage()));
        }
    }

    /**
     * Enable a plugin.
     * POST /api/admin/plugins/{name}/enable
     */
    @PostMapping("/plugins/{name}/enable")
    public ResponseEntity<?> enablePlugin(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String name) {

        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        if (!session.canModify()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Insufficient permissions to modify plugins"));
        }

        try {
            Optional<PluginMetadata> plugin = pluginService.getPlugin(name);
            if (plugin.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            pluginService.enablePlugin(name);

            log.info("Plugin enabled by {}: {}", session.discordUserId, name);
            return ResponseEntity.ok(new SuccessResponse("Plugin enabled: " + name));
        } catch (Exception e) {
            log.error("Error enabling plugin: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to enable plugin: " + e.getMessage()));
        }
    }

    /**
     * Disable a plugin.
     * POST /api/admin/plugins/{name}/disable
     */
    @PostMapping("/plugins/{name}/disable")
    public ResponseEntity<?> disablePlugin(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String name) {

        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        if (!session.canModify()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Insufficient permissions to modify plugins"));
        }

        try {
            Optional<PluginMetadata> plugin = pluginService.getPlugin(name);
            if (plugin.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            pluginService.disablePlugin(name);

            log.info("Plugin disabled by {}: {}", session.discordUserId, name);
            return ResponseEntity.ok(new SuccessResponse("Plugin disabled: " + name));
        } catch (Exception e) {
            log.error("Error disabling plugin: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to disable plugin: " + e.getMessage()));
        }
    }

    /**
     * Reload a plugin (unload and load again).
     * POST /api/admin/plugins/{name}/reload
     */
    @PostMapping("/plugins/{name}/reload")
    public ResponseEntity<?> reloadPlugin(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String name) {

        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        if (!session.canModify()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Insufficient permissions to modify plugins"));
        }

        try {
            Optional<PluginMetadata> plugin = pluginService.getPlugin(name);
            if (plugin.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // Use the dedicated reload method
            boolean success = pluginService.reloadPlugin(name);

            if (success) {
                log.info("Plugin reloaded by {}: {}", session.discordUserId, name);
                return ResponseEntity.ok(new SuccessResponse("Plugin reloaded: " + name));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ErrorResponse("Failed to reload plugin: reload returned false"));
            }
        } catch (Exception e) {
            log.error("Error reloading plugin: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to reload plugin: " + e.getMessage()));
        }
    }

    /**
     * Remove a plugin (unload and delete JAR).
     * DELETE /api/admin/plugins/{name}
     */
    @DeleteMapping("/plugins/{name}")
    public ResponseEntity<?> removePlugin(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String name) {

        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        if (!session.canModify()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Insufficient permissions to remove plugins"));
        }

        try {
            Optional<PluginMetadata> plugin = pluginService.getPlugin(name);
            if (plugin.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // Get JAR file name before removing
            String jarFileName = plugin.get().getJarFileName();

            // Remove plugin (unload and delete metadata from database)
            pluginService.removePlugin(name);

            // Delete JAR file (sanitize to prevent path traversal)
            if (jarFileName != null) {
                String safeJarName = Path.of(jarFileName).getFileName().toString();
                Path jarPath = Path.of(pluginsDirectory).resolve(safeJarName).normalize();
                if (jarPath.startsWith(Path.of(pluginsDirectory).toAbsolutePath().normalize())) {
                    File file = jarPath.toFile();
                    if (file.exists()) {
                        boolean deleted = file.delete();
                        if (!deleted) {
                            log.warn("Could not delete JAR file: {}", file.getAbsolutePath());
                        }
                    }
                } else {
                    log.warn("Path traversal attempt detected in plugin removal: {}", jarFileName);
                }
            }

            log.info("Plugin removed by {}: {}", session.discordUserId, name);
            return ResponseEntity.ok(new SuccessResponse("Plugin removed: " + name));
        } catch (Exception e) {
            log.error("Error removing plugin: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to remove plugin: " + e.getMessage()));
        }
    }

    /**
     * Get list of JAR files in plugins directory (including unloaded ones).
     * GET /api/admin/plugins/files
     */
    @GetMapping("/plugins/files")
    public ResponseEntity<?> getPluginFiles(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        try {
            File dir = new File(pluginsDirectory);
            List<Map<String, Object>> files = new ArrayList<>();

            if (dir.exists() && dir.isDirectory()) {
                File[] jarFiles = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
                if (jarFiles != null) {
                    for (File file : jarFiles) {
                        Map<String, Object> fileInfo = new HashMap<>();
                        fileInfo.put("name", file.getName());
                        fileInfo.put("size", file.length());
                        fileInfo.put("lastModified", file.lastModified());
                        fileInfo.put("path", file.getAbsolutePath());
                        files.add(fileInfo);
                    }
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("files", files);
            response.put("total", files.size());
            response.put("directory", dir.getAbsolutePath());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting plugin files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to get plugin files: " + e.getMessage()));
        }
    }

    // =====================================================
    // Helper Methods
    // =====================================================

    private Map<String, Object> pluginToMap(PluginMetadata plugin) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", plugin.getId());
        map.put("name", plugin.getPluginName());
        map.put("version", plugin.getPluginVersion());
        map.put("author", plugin.getPluginAuthor());
        map.put("description", plugin.getPluginDescription());
        map.put("jarFile", plugin.getJarFileName());
        map.put("enabled", plugin.isEnabled());
        map.put("loaded", plugin.isLoaded());
        map.put("loadError", plugin.getLoadError());
        map.put("createdAt", plugin.getCreatedAt());
        map.put("updatedAt", plugin.getUpdatedAt());
        return map;
    }

    // =====================================================
    // DTOs
    // =====================================================

    /**
     * Request DTO for Mutual RSA Authentication.
     * Admin signs the challenge nonce with their private key.
     * This is the ONLY supported authentication method for admin panel.
     */
    public static class MutualAuthRequest {
        public String challengeId;
        public String signature; // Base64-encoded RSA signature of the challenge nonce

        public String getChallengeId() { return challengeId; }
        public void setChallengeId(String challengeId) { this.challengeId = challengeId; }
        public String getSignature() { return signature; }
        public void setSignature(String signature) { this.signature = signature; }
    }

    /**
     * Request DTO for adding a new admin.
     * IMPORTANT: publicKeyPem is REQUIRED for mutual RSA authentication.
     */
    public static class AddAdminRequest {
        public String discordUserId;
        public String discordUsername;
        public String adminRole; // OWNER, ADMIN, MODERATOR
        public String note;
        public String publicKeyPem; // RSA public key in PEM format - REQUIRED

        public String getDiscordUserId() { return discordUserId; }
        public void setDiscordUserId(String discordUserId) { this.discordUserId = discordUserId; }
        public String getDiscordUsername() { return discordUsername; }
        public void setDiscordUsername(String discordUsername) { this.discordUsername = discordUsername; }
        public String getAdminRole() { return adminRole; }
        public void setAdminRole(String adminRole) { this.adminRole = adminRole; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        public String getPublicKeyPem() { return publicKeyPem; }
        public void setPublicKeyPem(String publicKeyPem) { this.publicKeyPem = publicKeyPem; }
    }

    /**
     * Request DTO for updating an admin.
     */
    public static class UpdateAdminRequest {
        public String adminRole;
        public Boolean enabled;
        public String note;
        public String publicKeyPem; // Update RSA public key

        public String getAdminRole() { return adminRole; }
        public void setAdminRole(String adminRole) { this.adminRole = adminRole; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        public String getPublicKeyPem() { return publicKeyPem; }
        public void setPublicKeyPem(String publicKeyPem) { this.publicKeyPem = publicKeyPem; }
    }

    public static class SuccessResponse {
        public boolean success = true;
        public String message;

        public SuccessResponse(String message) {
            this.message = message;
        }
    }

    public static class ErrorResponse {
        public boolean success = false;
        public String error;

        public ErrorResponse(String error) {
            this.error = error;
        }
    }
}
