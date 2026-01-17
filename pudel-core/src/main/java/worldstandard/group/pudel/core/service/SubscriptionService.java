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
package worldstandard.group.pudel.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import worldstandard.group.pudel.core.config.SubscriptionTierConfig;
import worldstandard.group.pudel.core.config.SubscriptionTierConfig.TierDefinition;
import worldstandard.group.pudel.core.config.SubscriptionTierConfig.TierLimits;
import worldstandard.group.pudel.core.entity.Subscription;
import worldstandard.group.pudel.core.entity.Subscription.SubscriptionTier;
import worldstandard.group.pudel.core.entity.Subscription.SubscriptionType;
import worldstandard.group.pudel.core.repository.SubscriptionRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing subscriptions and checking capacity limits.
 * Uses configurable tier definitions from subscription-tiers.yml.
 */
@Service
@Transactional
public class SubscriptionService {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final SchemaManagementService schemaManagementService;
    private final SubscriptionTierConfig tierConfig;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               SchemaManagementService schemaManagementService,
                               SubscriptionTierConfig tierConfig) {
        this.subscriptionRepository = subscriptionRepository;
        this.schemaManagementService = schemaManagementService;
        this.tierConfig = tierConfig;
    }

    /**
     * Get or create subscription for a guild using configurable tier limits.
     */
    public Subscription getOrCreateGuildSubscription(String guildId) {
        return subscriptionRepository.findByTargetIdAndType(guildId, SubscriptionType.GUILD)
                .orElseGet(() -> {
                    Subscription subscription = new Subscription(guildId, SubscriptionType.GUILD, tierConfig.getDefaultTier());
                    applyConfigLimits(subscription);
                    return subscriptionRepository.save(subscription);
                });
    }

    /**
     * Get or create subscription for a user using configurable tier limits.
     */
    public Subscription getOrCreateUserSubscription(String userId) {
        return subscriptionRepository.findByTargetIdAndType(userId, SubscriptionType.USER)
                .orElseGet(() -> {
                    Subscription subscription = new Subscription(userId, SubscriptionType.USER, tierConfig.getDefaultTier());
                    applyConfigLimits(subscription);
                    return subscriptionRepository.save(subscription);
                });
    }

    /**
     * Apply limits from configuration to a subscription.
     */
    private void applyConfigLimits(Subscription subscription) {
        TierDefinition tierDef = tierConfig.getTier(subscription.getTierName());
        if (tierDef == null) {
            logger.warn("Tier {} not found in config, using defaults", subscription.getTierName());
            subscription.applyDefaultLimits();
            return;
        }

        TierLimits limits = subscription.getType() == SubscriptionType.USER
                ? tierDef.getUser()
                : tierDef.getGuild();

        if (limits != null) {
            int pluginLimit = tierDef.getFeatures() != null ? tierDef.getFeatures().getPluginLimit() : 3;
            subscription.applyLimits(
                    limits.getDialogueLimit(),
                    limits.getMemoryLimit(),
                    pluginLimit
            );
        }
    }

    /**
     * Get guild subscription.
     */
    public Optional<Subscription> getGuildSubscription(String guildId) {
        return subscriptionRepository.findByTargetIdAndType(guildId, SubscriptionType.GUILD);
    }

    /**
     * Get user subscription.
     */
    public Optional<Subscription> getUserSubscription(String userId) {
        return subscriptionRepository.findByTargetIdAndType(userId, SubscriptionType.USER);
    }

    /**
     * Upgrade subscription tier using new configurable tiers.
     */
    public Subscription upgradeSubscription(String targetId, SubscriptionType type, String newTierName) {
        if (!tierConfig.tierExists(newTierName)) {
            throw new IllegalArgumentException("Invalid tier: " + newTierName);
        }

        Subscription subscription = type == SubscriptionType.GUILD
                ? getOrCreateGuildSubscription(targetId)
                : getOrCreateUserSubscription(targetId);

        subscription.setTierName(newTierName);
        applyConfigLimits(subscription);
        return subscriptionRepository.save(subscription);
    }

    /**
     * @deprecated Use upgradeSubscription(String, SubscriptionType, String) instead
     */
    @Deprecated
    public Subscription upgradeSubscription(String targetId, SubscriptionType type, SubscriptionTier newTier) {
        return upgradeSubscription(targetId, type, newTier.name());
    }

    /**
     * Get all available subscription tiers from configuration.
     */
    public List<TierInfo> getAllTiers() {
        return tierConfig.getTierNames().stream()
                .filter(tierConfig::tierExists)
                .map(tierName -> {
                    TierDefinition def = tierConfig.getTier(tierName);
                    return new TierInfo(
                            tierName,
                            def.getName(),
                            def.getDescription(),
                            def.getUser(),
                            def.getGuild(),
                            def.getFeatures()
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Get tier info by name.
     */
    public TierInfo getTierInfo(String tierName) {
        TierDefinition def = tierConfig.getTier(tierName);
        if (def == null) return null;
        return new TierInfo(
                tierName,
                def.getName(),
                def.getDescription(),
                def.getUser(),
                def.getGuild(),
                def.getFeatures()
        );
    }

    // ===========================================
    // Capacity Checking
    // ===========================================

    /**
     * Check if guild can store more dialogue history.
     */
    public boolean canStoreGuildDialogue(long guildId) {
        Subscription subscription = getOrCreateGuildSubscription(String.valueOf(guildId));
        if (!subscription.isActive()) {
            // Expired subscriptions fall back to free tier limits
            subscription.setTierName(tierConfig.getDefaultTier());
            applyConfigLimits(subscription);
        }

        long currentCount = schemaManagementService.getGuildTableRowCount(guildId, "dialogue_history");
        boolean canStore = currentCount < subscription.getDialogueLimit();

        if (!canStore) {
            logger.warn("Guild {} has reached dialogue limit ({}/{})",
                    guildId, currentCount, subscription.getDialogueLimit());
        }

        return canStore;
    }

    /**
     * Check if guild can store more memory entries.
     */
    public boolean canStoreGuildMemory(long guildId) {
        Subscription subscription = getOrCreateGuildSubscription(String.valueOf(guildId));
        if (!subscription.isActive()) {
            subscription.setTierName(tierConfig.getDefaultTier());
            applyConfigLimits(subscription);
        }

        long currentCount = schemaManagementService.getGuildTableRowCount(guildId, "memory");
        boolean canStore = currentCount < subscription.getMemoryLimit();

        if (!canStore) {
            logger.warn("Guild {} has reached memory limit ({}/{})",
                    guildId, currentCount, subscription.getMemoryLimit());
        }

        return canStore;
    }


    /**
     * Check if user can store more dialogue history.
     */
    public boolean canStoreUserDialogue(long userId) {
        Subscription subscription = getOrCreateUserSubscription(String.valueOf(userId));
        if (!subscription.isActive()) {
            subscription.setTierName(tierConfig.getDefaultTier());
            applyConfigLimits(subscription);
        }

        long currentCount = schemaManagementService.getUserTableRowCount(userId, "dialogue_history");
        return currentCount < subscription.getDialogueLimit();
    }

    /**
     * Check if user can store more memory entries.
     */
    public boolean canStoreUserMemory(long userId) {
        Subscription subscription = getOrCreateUserSubscription(String.valueOf(userId));
        if (!subscription.isActive()) {
            subscription.setTierName(tierConfig.getDefaultTier());
            applyConfigLimits(subscription);
        }

        long currentCount = schemaManagementService.getUserTableRowCount(userId, "memory");
        return currentCount < subscription.getMemoryLimit();
    }

    // ===========================================
    // Limit Getters (for MemoryManager)
    // ===========================================

    /**
     * Get the dialogue limit for a guild.
     * Returns -1 for unlimited.
     */
    public long getGuildDialogueLimit(long guildId) {
        Subscription subscription = getOrCreateGuildSubscription(String.valueOf(guildId));
        return subscription.getDialogueLimit();
    }

    /**
     * Get the memory limit for a guild.
     * Returns -1 for unlimited.
     */
    public long getGuildMemoryLimit(long guildId) {
        Subscription subscription = getOrCreateGuildSubscription(String.valueOf(guildId));
        return subscription.getMemoryLimit();
    }

    /**
     * Get the dialogue limit for a user.
     * Returns -1 for unlimited.
     */
    public long getUserDialogueLimit(long userId) {
        Subscription subscription = getOrCreateUserSubscription(String.valueOf(userId));
        return subscription.getDialogueLimit();
    }

    /**
     * Get the memory limit for a user.
     * Returns -1 for unlimited.
     */
    public long getUserMemoryLimit(long userId) {
        Subscription subscription = getOrCreateUserSubscription(String.valueOf(userId));
        return subscription.getMemoryLimit();
    }

    // ===========================================
    // Usage Statistics
    // ===========================================

    /**
     * Get current usage for a guild.
     */
    public GuildUsage getGuildUsage(long guildId) {
        Subscription subscription = getOrCreateGuildSubscription(String.valueOf(guildId));

        return new GuildUsage(
                schemaManagementService.getGuildTableRowCount(guildId, "dialogue_history"),
                subscription.getDialogueLimit(),
                schemaManagementService.getGuildTableRowCount(guildId, "memory"),
                subscription.getMemoryLimit(),
                subscription.getTierName(),
                subscription.isActive()
        );
    }

    /**
     * Get current usage for a user.
     */
    public UserUsage getUserUsage(long userId) {
        Subscription subscription = getOrCreateUserSubscription(String.valueOf(userId));

        return new UserUsage(
                schemaManagementService.getUserTableRowCount(userId, "dialogue_history"),
                subscription.getDialogueLimit(),
                schemaManagementService.getUserTableRowCount(userId, "memory"),
                subscription.getMemoryLimit(),
                subscription.getTierName(),
                subscription.isActive()
        );
    }

    // ===========================================
    // Record Classes
    // ===========================================

    /**
     * Tier information record.
     */
    public static class TierInfo {
        public final String tierKey;
        public final String name;
        public final String description;
        public final TierLimits userLimits;
        public final TierLimits guildLimits;
        public final SubscriptionTierConfig.TierFeatures features;

        public TierInfo(String tierKey, String name, String description,
                       TierLimits userLimits, TierLimits guildLimits,
                       SubscriptionTierConfig.TierFeatures features) {
            this.tierKey = tierKey;
            this.name = name;
            this.description = description;
            this.userLimits = userLimits;
            this.guildLimits = guildLimits;
            this.features = features;
        }
    }

    public static class GuildUsage {
        public final long dialogueCount;
        public final long dialogueLimit;
        public final long memoryCount;
        public final long memoryLimit;
        public final String tierName;
        public final boolean active;

        public GuildUsage(long dialogueCount, long dialogueLimit, long memoryCount, long memoryLimit,
                          String tierName, boolean active) {
            this.dialogueCount = dialogueCount;
            this.dialogueLimit = dialogueLimit;
            this.memoryCount = memoryCount;
            this.memoryLimit = memoryLimit;
            this.tierName = tierName;
            this.active = active;
        }
    }

    public static class UserUsage {
        public final long dialogueCount;
        public final long dialogueLimit;
        public final long memoryCount;
        public final long memoryLimit;
        public final String tierName;
        public final boolean active;

        public UserUsage(long dialogueCount, long dialogueLimit, long memoryCount, long memoryLimit,
                         String tierName, boolean active) {
            this.dialogueCount = dialogueCount;
            this.dialogueLimit = dialogueLimit;
            this.memoryCount = memoryCount;
            this.memoryLimit = memoryLimit;
            this.tierName = tierName;
            this.active = active;
        }
    }
}

