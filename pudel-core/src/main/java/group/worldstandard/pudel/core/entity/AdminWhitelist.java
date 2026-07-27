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
 * Entity representing an admin-whitelisted Discord user.
 * <p>
 * Users in this table can authenticate to the admin panel
 * using their Discord credentials.
 */
@Entity
@Table(name = "admin_whitelist")
public class AdminWhitelist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Discord user ID (snowflake).
     */
    @Column(name = "discord_user_id", nullable = false, unique = true)
    private String discordUserId;

    /**
     * Discord username for display/logging purposes.
     */
    @Column(name = "discord_username")
    private String discordUsername;

    /**
     * Admin role/permission level.
     * OWNER - Full access, can manage other admins
     * ADMIN - Full access to plugins and settings
     * MODERATOR - Limited access, can view but not modify
     */
    @Column(name = "admin_role", nullable = false)
    @Enumerated(EnumType.STRING)
    private AdminRole adminRole = AdminRole.ADMIN;

    /**
     * Whether this admin entry is currently active.
     */
    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * Optional note about why this user was whitelisted.
     */
    @Column
    private String note;

    /**
     * RSA public key in PEM format for mutual authentication.
     * Each admin has their own keypair - they sign challenges with their private key,
     * Pudel verifies with this public key.
     */
    @Column(name = "public_key_pem", columnDefinition = "TEXT")
    private String publicKeyPem;

    /**
     * Who added this admin (Discord user ID).
     */
    @Column(name = "added_by")
    private String addedBy;

    /**
     * Last successful login timestamp.
     */
    @Column(name = "last_login")
    private Instant lastLogin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // Enum for admin roles
    public enum AdminRole {
        OWNER,      // Can manage other admins
        ADMIN,      // Full plugin/settings access
        MODERATOR   // View-only access
    }

    // Constructors
    public AdminWhitelist() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public AdminWhitelist(String discordUserId, AdminRole adminRole) {
        this();
        this.discordUserId = discordUserId;
        this.adminRole = adminRole;
    }

    public AdminWhitelist(String discordUserId, String discordUsername, AdminRole adminRole) {
        this(discordUserId, adminRole);
        this.discordUsername = discordUsername;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDiscordUserId() {
        return discordUserId;
    }

    public void setDiscordUserId(String discordUserId) {
        this.discordUserId = discordUserId;
    }

    public String getDiscordUsername() {
        return discordUsername;
    }

    public void setDiscordUsername(String discordUsername) {
        this.discordUsername = discordUsername;
    }

    public AdminRole getAdminRole() {
        return adminRole;
    }

    public void setAdminRole(AdminRole adminRole) {
        this.adminRole = adminRole;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public void setAddedBy(String addedBy) {
        this.addedBy = addedBy;
    }

    public Instant getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(Instant lastLogin) {
        this.lastLogin = lastLogin;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Check if this admin can manage other admins.
     */
    public boolean canManageAdmins() {
        return adminRole == AdminRole.OWNER;
    }

    /**
     * Check if this admin can modify settings/plugins.
     */
    public boolean canModify() {
        return adminRole == AdminRole.OWNER || adminRole == AdminRole.ADMIN;
    }

    @Override
    public String toString() {
        return "AdminWhitelist{" +
                "id=" + id +
                ", discordUserId='" + discordUserId + '\'' +
                ", discordUsername='" + discordUsername + '\'' +
                ", adminRole=" + adminRole +
                ", enabled=" + enabled +
                '}';
    }
}