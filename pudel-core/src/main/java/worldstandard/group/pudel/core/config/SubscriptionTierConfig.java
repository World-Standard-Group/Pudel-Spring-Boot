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
package worldstandard.group.pudel.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for subscription tiers.
 * Loaded from application.yml, allowing hosters to customize
 * subscription limits without modifying code.
 */
@ConfigurationProperties(prefix = "pudel.subscription")
public class SubscriptionTierConfig {

    private Map<String, TierDefinition> tiers = new HashMap<>();
    private String defaultTier = "FREE";
    private boolean enableExpiration = true;

    // Getters and Setters
    public Map<String, TierDefinition> getTiers() {
        return tiers;
    }

    public void setTiers(Map<String, TierDefinition> tiers) {
        this.tiers = tiers;
    }

    public String getDefaultTier() {
        return defaultTier;
    }

    public void setDefaultTier(String defaultTier) {
        this.defaultTier = defaultTier;
    }

    public boolean isEnableExpiration() {
        return enableExpiration;
    }

    public void setEnableExpiration(boolean enableExpiration) {
        this.enableExpiration = enableExpiration;
    }

    /**
     * Get tier definition by name.
     */
    public TierDefinition getTier(String tierName) {
        return tiers.getOrDefault(tierName, tiers.get(defaultTier));
    }

    /**
     * Get all tier names in order.
     */
    public List<String> getTierNames() {
        return List.of("FREE", "TIER_1", "TIER_2", "TIER_3", "UNLIMITED");
    }

    /**
     * Check if tier exists.
     */
    public boolean tierExists(String tierName) {
        return tiers.containsKey(tierName);
    }

    /**
     * Definition of a subscription tier.
     */
    public static class TierDefinition {
        private String name;
        private String description;
        private TierLimits user;
        private TierLimits guild;
        private TierFeatures features;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public TierLimits getUser() {
            return user;
        }

        public void setUser(TierLimits user) {
            this.user = user;
        }

        public TierLimits getGuild() {
            return guild;
        }

        public void setGuild(TierLimits guild) {
            this.guild = guild;
        }

        public TierFeatures getFeatures() {
            return features;
        }

        public void setFeatures(TierFeatures features) {
            this.features = features;
        }
    }

    /**
     * Limits for user or guild.
     */
    public static class TierLimits {
        private long dialogueLimit = 1000;
        private long memoryLimit = 100;

        public long getDialogueLimit() {
            return dialogueLimit;
        }

        public void setDialogueLimit(long dialogueLimit) {
            this.dialogueLimit = dialogueLimit;
        }

        public long getMemoryLimit() {
            return memoryLimit;
        }

        public void setMemoryLimit(long memoryLimit) {
            this.memoryLimit = memoryLimit;
        }

        /**
         * Check if limit is unlimited (-1 means unlimited).
         */
        public boolean isUnlimited(String limitType) {
            return switch (limitType) {
                case "dialogue" -> dialogueLimit == -1;
                case "memory" -> memoryLimit == -1;
                default -> false;
            };
        }
    }

    /**
     * Features enabled for a tier.
     */
    public static class TierFeatures {
        private boolean chatbot = true;
        private boolean customPersonality = true;
        private int pluginLimit = 3;
        private boolean voiceEnabled = false;
        private boolean prioritySupport = false;

        public boolean isChatbot() {
            return chatbot;
        }

        public void setChatbot(boolean chatbot) {
            this.chatbot = chatbot;
        }

        public boolean isCustomPersonality() {
            return customPersonality;
        }

        public void setCustomPersonality(boolean customPersonality) {
            this.customPersonality = customPersonality;
        }

        public int getPluginLimit() {
            return pluginLimit;
        }

        public void setPluginLimit(int pluginLimit) {
            this.pluginLimit = pluginLimit;
        }

        public boolean isVoiceEnabled() {
            return voiceEnabled;
        }

        public void setVoiceEnabled(boolean voiceEnabled) {
            this.voiceEnabled = voiceEnabled;
        }

        public boolean isPrioritySupport() {
            return prioritySupport;
        }

        public void setPrioritySupport(boolean prioritySupport) {
            this.prioritySupport = prioritySupport;
        }
    }
}

