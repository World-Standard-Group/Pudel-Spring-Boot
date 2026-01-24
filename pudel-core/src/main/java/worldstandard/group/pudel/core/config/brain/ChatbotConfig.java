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
package worldstandard.group.pudel.core.config.brain;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for chatbot behavior.
 * Controls when and how Pudel responds as a chatbot.
 */
@ConfigurationProperties(prefix = "pudel.chatbot")
public class ChatbotConfig {

    private Triggers triggers = new Triggers();
    private int contextSize = 10;
    private PassiveTracking passiveTracking = new PassiveTracking();
    private Embedding embedding = new Embedding();

    public Triggers getTriggers() {
        return triggers;
    }

    public void setTriggers(Triggers triggers) {
        this.triggers = triggers;
    }

    public int getContextSize() {
        return contextSize;
    }

    public void setContextSize(int contextSize) {
        this.contextSize = contextSize;
    }

    public PassiveTracking getPassiveTracking() {
        return passiveTracking;
    }

    public void setPassiveTracking(PassiveTracking passiveTracking) {
        this.passiveTracking = passiveTracking;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
    }

    /**
     * Chatbot trigger configuration.
     */
    public static class Triggers {
        private boolean onMention = true;
        private boolean onDirectMessage = true;
        private boolean onReplyToBot = true;
        private List<String> keywords = new ArrayList<>();
        private List<String> alwaysActiveChannels = new ArrayList<>();

        public boolean isOnMention() {
            return onMention;
        }

        public void setOnMention(boolean onMention) {
            this.onMention = onMention;
        }

        public boolean isOnDirectMessage() {
            return onDirectMessage;
        }

        public void setOnDirectMessage(boolean onDirectMessage) {
            this.onDirectMessage = onDirectMessage;
        }

        public boolean isOnReplyToBot() {
            return onReplyToBot;
        }

        public void setOnReplyToBot(boolean onReplyToBot) {
            this.onReplyToBot = onReplyToBot;
        }

        public List<String> getKeywords() {
            return keywords;
        }

        public void setKeywords(List<String> keywords) {
            this.keywords = keywords;
        }

        public List<String> getAlwaysActiveChannels() {
            return alwaysActiveChannels;
        }

        public void setAlwaysActiveChannels(List<String> alwaysActiveChannels) {
            this.alwaysActiveChannels = alwaysActiveChannels;
        }
    }

    /**
     * Embedding configuration for semantic memory search.
     */
    public static class Embedding {
        private boolean enabled = true;
        private int dimension = 384;
        private int ivfProbes = 10;
        private int ivfLists = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getDimension() {
            return dimension;
        }

        public void setDimension(int dimension) {
            this.dimension = dimension;
        }

        public int getIvfProbes() {
            return ivfProbes;
        }

        public void setIvfProbes(int ivfProbes) {
            this.ivfProbes = ivfProbes;
        }

        public int getIvfLists() {
            return ivfLists;
        }

        public void setIvfLists(int ivfLists) {
            this.ivfLists = ivfLists;
        }
    }

    /**
     * Passive context tracking configuration.
     */
    public static class PassiveTracking {
        private boolean enabled = true;
        private int minMessageLength = 20;
        private boolean trackUrlMentions = true;
        private boolean trackDatesMentions = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMinMessageLength() {
            return minMessageLength;
        }

        public void setMinMessageLength(int minMessageLength) {
            this.minMessageLength = minMessageLength;
        }

        public boolean isTrackUrlMentions() {
            return trackUrlMentions;
        }

        public void setTrackUrlMentions(boolean trackUrlMentions) {
            this.trackUrlMentions = trackUrlMentions;
        }

        public boolean isTrackDatesMentions() {
            return trackDatesMentions;
        }

        public void setTrackDatesMentions(boolean trackDatesMentions) {
            this.trackDatesMentions = trackDatesMentions;
        }
    }
}

