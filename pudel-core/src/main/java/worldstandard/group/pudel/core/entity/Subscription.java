/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 Napapon Kamanee
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
package worldstandard.group.pudel.core.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a subscription tier for users/guilds.
 * Controls capacity limits for schema data (dialogue history, memory, etc.)
 * <p>
 * Note: Tier limits are now configurable via subscription-tiers.yml.
 * The tierName field stores the tier key (e.g., "FREE", "TIER_1", "TIER_2").
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    /**
     * Legacy enum for backward compatibility.
     * New code should use tierName string field.
     */
    public enum SubscriptionTier {
        FREE,       // Basic free tier
        BASIC,      // Paid basic tier (mapped to TIER_1)
        PREMIUM,    // Premium tier (mapped to TIER_2)
        UNLIMITED   // Unlimited (for special cases)
    }

    public enum SubscriptionType {
        USER,   // User subscription (for DM)
        GUILD   // Guild subscription
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_id", nullable = false)
    private String targetId; // User ID or Guild ID

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_type", nullable = false)
    private SubscriptionType type;

    /**
     * Tier name from configuration file (e.g., "FREE", "TIER_1", "TIER_2", "TIER_3", "UNLIMITED").
     * This allows for expandable tiers via YAML configuration.
     */
    @Column(name = "tier_name", nullable = false)
    private String tierName = "FREE";

    /**
     * Legacy enum field for backward compatibility.
     * @deprecated Use tierName instead
     */
    @Deprecated
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionTier tier = SubscriptionTier.FREE;

    @Column(name = "dialogue_limit")
    private Long dialogueLimit = 1000L; // Default limit

    @Column(name = "memory_limit")
    private Long memoryLimit = 100L; // Default limit

    @Column(name = "plugin_limit")
    private Integer pluginLimit = 3; // Default plugin limit

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Constructors
    public Subscription() {
    }

    public Subscription(String targetId, SubscriptionType type) {
        this.targetId = targetId;
        this.type = type;
        this.tierName = "FREE";
        this.tier = SubscriptionTier.FREE;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        applyDefaultLimits();
    }

    public Subscription(String targetId, SubscriptionType type, String tierName) {
        this.targetId = targetId;
        this.type = type;
        this.tierName = tierName;
        this.tier = mapTierNameToEnum(tierName);
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        // Limits should be set by SubscriptionService using config
    }

    /**
     * @deprecated Use constructor with tierName string
     */
    @Deprecated
    public Subscription(String targetId, SubscriptionType type, SubscriptionTier tier) {
        this.targetId = targetId;
        this.type = type;
        this.tier = tier;
        this.tierName = tier.name();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        applyDefaultLimits();
    }

    /**
     * Map tier name to legacy enum for backward compatibility.
     */
    private SubscriptionTier mapTierNameToEnum(String tierName) {
        return switch (tierName) {
            case "TIER_1", "BASIC" -> SubscriptionTier.BASIC;
            case "TIER_2", "TIER_3", "PREMIUM" -> SubscriptionTier.PREMIUM;
            case "UNLIMITED" -> SubscriptionTier.UNLIMITED;
            default -> SubscriptionTier.FREE;
        };
    }

    /**
     * Apply default limits (fallback when config is not available).
     */
    public void applyDefaultLimits() {
        if (type == SubscriptionType.USER) {
            this.dialogueLimit = 1000L;
            this.memoryLimit = 100L;
        } else {
            this.dialogueLimit = 5000L;
            this.memoryLimit = 500L;
        }
        this.pluginLimit = 3;
    }

    /**
     * Apply limits from configuration values.
     */
    public void applyLimits(long dialogueLimit, long memoryLimit, int pluginLimit) {
        this.dialogueLimit = dialogueLimit == -1 ? Long.MAX_VALUE : dialogueLimit;
        this.memoryLimit = memoryLimit == -1 ? Long.MAX_VALUE : memoryLimit;
        this.pluginLimit = pluginLimit == -1 ? Integer.MAX_VALUE : pluginLimit;
    }

    /**
     * @deprecated Use tierName-based configuration
     */
    @Deprecated
    public void applyTierLimits() {
        applyDefaultLimits();
    }

    /**
     * Check if subscription is active (not expired).
     */
    public boolean isActive() {
        if ("FREE".equals(tierName) || tier == SubscriptionTier.FREE) {
            return true; // Free tier never expires
        }
        return expiresAt == null || expiresAt.isAfter(LocalDateTime.now());
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public SubscriptionType getType() {
        return type;
    }

    public void setType(SubscriptionType type) {
        this.type = type;
    }

    public String getTierName() {
        return tierName;
    }

    public void setTierName(String tierName) {
        this.tierName = tierName;
        this.tier = mapTierNameToEnum(tierName);
    }

    /**
     * @deprecated Use getTierName() instead
     */
    @Deprecated
    public SubscriptionTier getTier() {
        return tier;
    }

    /**
     * @deprecated Use setTierName() instead
     */
    @Deprecated
    public void setTier(SubscriptionTier tier) {
        this.tier = tier;
        this.tierName = tier.name();
    }

    public Long getDialogueLimit() {
        return dialogueLimit;
    }

    public void setDialogueLimit(Long dialogueLimit) {
        this.dialogueLimit = dialogueLimit;
    }

    public Long getMemoryLimit() {
        return memoryLimit;
    }

    public void setMemoryLimit(Long memoryLimit) {
        this.memoryLimit = memoryLimit;
    }


    public Integer getPluginLimit() {
        return pluginLimit;
    }

    public void setPluginLimit(Integer pluginLimit) {
        this.pluginLimit = pluginLimit;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

