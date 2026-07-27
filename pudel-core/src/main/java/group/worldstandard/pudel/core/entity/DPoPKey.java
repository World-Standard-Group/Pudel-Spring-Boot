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
package group.worldstandard.pudel.core.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Entity for storing DPoP keypairs persistently.
 * <p>
 * This solves the issue where DPoP keys were stored in HttpSession, which is lost
 * in stateless architectures or when sessions expire. By storing keys in the database
 * indexed by a stable key identifier, keys persist across:
 * - Page refreshes
 * - Session timeouts
 * - Server restarts (if using persistent database)
 * - Multiple requests in stateless architectures
 * <p>
 * The private key is stored in serialized JWK format (encrypted at rest by the database).
 * Each user can have multiple DPoP keys (one per device/browser), identified by keyId.
 */
@Entity
@Table(name = "dpop_keys", indexes = {
    @Index(name = "idx_dpop_key_id", columnList = "keyId", unique = true),
    @Index(name = "idx_dpop_user_id", columnList = "userId"),
    @Index(name = "idx_dpop_token_thumbprint", columnList = "tokenThumbprint")
})
public class DPoPKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier for this DPoP key.
     * Generated when the key is created and sent to the frontend.
     * The frontend includes this in requests to identify which key to use.
     */
    @Column(name = "key_id", nullable = false, unique = true, length = 64)
    private String keyId;

    /**
     * The user ID this key belongs to.
     * Links the DPoP key to a specific user for cleanup and management.
     */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /**
     * Thumbprint of the access token this key is bound to.
     * Used to validate that the DPoP proof matches the token being used.
     */
    @Column(name = "token_thumbprint", length = 64)
    private String tokenThumbprint;

    /**
     * The public key in JWK format (stored as JSON string).
     * Safe to share with frontend for DPoP proof creation.
     */
    @Column(name = "public_key_jwk", nullable = false, columnDefinition = "TEXT")
    private String publicKeyJwk;

    /**
     * The private key in JWK format (stored as JSON string).
     * NEVER exposed to the frontend. Used only for signing DPoP proofs.
     * Should be encrypted at rest by database or application-level encryption.
     */
    @Column(name = "private_key_jwk", nullable = false, columnDefinition = "TEXT")
    private String privateKeyJwk;

    /**
     * The JWK thumbprint (RFC 7638) of the public key.
     * Used for quick lookup and validation.
     */
    @Column(name = "public_key_thumbprint", nullable = false, length = 64)
    private String publicKeyThumbprint;

    /**
     * When this key was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * When this key was last used.
     * Used for cleanup of stale keys.
     */
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    /**
     * When this key expires.
     * DPoP keys should have a limited lifetime for security.
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Whether this key is still active.
     * Keys can be revoked without deleting them for audit purposes.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /**
     * Optional: User agent or device identifier for debugging.
     */
    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    // Constructors
    public DPoPKey() {
        this.createdAt = Instant.now();
        this.isActive = true;
    }

    public DPoPKey(String keyId, String userId, String publicKeyJwk, String privateKeyJwk, 
                   String publicKeyThumbprint, Instant expiresAt) {
        this();
        this.keyId = keyId;
        this.userId = userId;
        this.publicKeyJwk = publicKeyJwk;
        this.privateKeyJwk = privateKeyJwk;
        this.publicKeyThumbprint = publicKeyThumbprint;
        this.expiresAt = expiresAt;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTokenThumbprint() {
        return tokenThumbprint;
    }

    public void setTokenThumbprint(String tokenThumbprint) {
        this.tokenThumbprint = tokenThumbprint;
    }

    public String getPublicKeyJwk() {
        return publicKeyJwk;
    }

    public void setPublicKeyJwk(String publicKeyJwk) {
        this.publicKeyJwk = publicKeyJwk;
    }

    public String getPrivateKeyJwk() {
        return privateKeyJwk;
    }

    public void setPrivateKeyJwk(String privateKeyJwk) {
        this.privateKeyJwk = privateKeyJwk;
    }

    public String getPublicKeyThumbprint() {
        return publicKeyThumbprint;
    }

    public void setPublicKeyThumbprint(String publicKeyThumbprint) {
        this.publicKeyThumbprint = publicKeyThumbprint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(String deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastUsedAt = Instant.now();
    }

    @Override
    public String toString() {
        return "DPoPKey{" +
                "keyId='" + keyId + '\'' +
                ", userId='" + userId + '\'' +
                ", publicKeyThumbprint='" + publicKeyThumbprint + '\'' +
                ", isActive=" + isActive +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
