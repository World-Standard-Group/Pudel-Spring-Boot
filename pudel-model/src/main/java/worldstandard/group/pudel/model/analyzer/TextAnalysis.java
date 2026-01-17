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
package worldstandard.group.pudel.model.analyzer;

import java.util.List;
import java.util.Map;

/**
 * Text analysis result - unified output from any analyzer.
 * Replaces the old NlpProcessor.NlpAnalysis with a cleaner interface.
 */
public record TextAnalysis(
        String language,
        String intent,
        double confidence,
        String sentiment,
        Map<String, List<String>> entities,
        List<String> keywords,
        boolean isQuestion,
        boolean isCommand,
        boolean isGreeting,
        boolean isFarewell
) {
    /**
     * Check if the analysis contains interesting information worth storing.
     */
    public boolean containsInterestingInfo() {
        // Has entities
        if (entities != null && !entities.isEmpty()) {
            return true;
        }
        // Has specific intent (not just chat)
        if (intent != null && !intent.equals("chat") && !intent.equals("unknown")) {
            return true;
        }
        // Has keywords
        if (keywords != null && keywords.size() >= 2) {
            return true;
        }
        return false;
    }

    /**
     * Create an empty analysis (fallback).
     */
    public static TextAnalysis empty() {
        return new TextAnalysis(
                "eng",
                "unknown",
                0.0,
                "neutral",
                Map.of(),
                List.of(),
                false,
                false,
                false,
                false
        );
    }

    /**
     * Builder for TextAnalysis.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String language = "eng";
        private String intent = "chat";
        private double confidence = 0.5;
        private String sentiment = "neutral";
        private Map<String, List<String>> entities = Map.of();
        private List<String> keywords = List.of();
        private boolean isQuestion = false;
        private boolean isCommand = false;
        private boolean isGreeting = false;
        private boolean isFarewell = false;

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder intent(String intent) {
            this.intent = intent;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder sentiment(String sentiment) {
            this.sentiment = sentiment;
            return this;
        }

        public Builder entities(Map<String, List<String>> entities) {
            this.entities = entities;
            return this;
        }

        public Builder keywords(List<String> keywords) {
            this.keywords = keywords;
            return this;
        }

        public Builder isQuestion(boolean isQuestion) {
            this.isQuestion = isQuestion;
            return this;
        }

        public Builder isCommand(boolean isCommand) {
            this.isCommand = isCommand;
            return this;
        }

        public Builder isGreeting(boolean isGreeting) {
            this.isGreeting = isGreeting;
            return this;
        }

        public Builder isFarewell(boolean isFarewell) {
            this.isFarewell = isFarewell;
            return this;
        }

        public TextAnalysis build() {
            return new TextAnalysis(
                    language, intent, confidence, sentiment, entities, keywords,
                    isQuestion, isCommand, isGreeting, isFarewell
            );
        }
    }
}

