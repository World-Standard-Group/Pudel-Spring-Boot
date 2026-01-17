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

/**
 * Configuration properties for memory management.
 * Controls auto-cleanup and semantic search behavior.
 */
@ConfigurationProperties(prefix = "pudel.memory")
public class MemoryConfig {

    private AutoCleanup autoCleanup = new AutoCleanup();
    private SemanticSearch semanticSearch = new SemanticSearch();

    public AutoCleanup getAutoCleanup() {
        return autoCleanup;
    }

    public void setAutoCleanup(AutoCleanup autoCleanup) {
        this.autoCleanup = autoCleanup;
    }

    public SemanticSearch getSemanticSearch() {
        return semanticSearch;
    }

    public void setSemanticSearch(SemanticSearch semanticSearch) {
        this.semanticSearch = semanticSearch;
    }

    /**
     * Auto-cleanup configuration for memory management.
     */
    public static class AutoCleanup {
        private boolean enabled = true;
        private int keepPercentage = 80;
        private int minAgeDays = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getKeepPercentage() {
            return keepPercentage;
        }

        public void setKeepPercentage(int keepPercentage) {
            this.keepPercentage = keepPercentage;
        }

        public int getMinAgeDays() {
            return minAgeDays;
        }

        public void setMinAgeDays(int minAgeDays) {
            this.minAgeDays = minAgeDays;
        }
    }

    /**
     * Semantic search configuration.
     */
    public static class SemanticSearch {
        private boolean enabled = true;
        private double minSimilarity = 0.7;
        private int maxResults = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getMinSimilarity() {
            return minSimilarity;
        }

        public void setMinSimilarity(double minSimilarity) {
            this.minSimilarity = minSimilarity;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }
    }
}

