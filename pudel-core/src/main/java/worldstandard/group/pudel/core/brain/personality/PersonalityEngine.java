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
package worldstandard.group.pudel.core.brain.personality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import worldstandard.group.pudel.core.service.ChatbotService.PudelPersonality;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Personality Engine for Pudel's Brain.
 * <p>
 * Processes personality traits (Biography, Personality, Preferences, Dialogue Style)
 * and applies them to response generation.
 * <p>
 * Uses pattern-based trait extraction to understand personality directives.
 */
@Component
public class PersonalityEngine {

    private static final Logger logger = LoggerFactory.getLogger(PersonalityEngine.class);

    // Default personality traits
    private static final PersonalityProfile DEFAULT_PROFILE = new PersonalityProfile(
            "friendly",
            "formal",
            Set.of("helpful", "polite", "professional"),
            Map.of(
                    "greeting_style", "polite",
                    "humor_level", "light",
                    "verbosity", "moderate"
            ),
            List.of()
    );

    // Pattern matchers for trait extraction
    private final Map<String, List<Pattern>> traitPatterns = new HashMap<>();

    public PersonalityEngine() {
        initializeTraitPatterns();
    }

    private void initializeTraitPatterns() {
        // Tone patterns
        traitPatterns.put("tone_friendly", List.of(
                Pattern.compile("\\b(friendly|warm|welcoming|cheerful|pleasant)\\b", Pattern.CASE_INSENSITIVE)
        ));
        traitPatterns.put("tone_formal", List.of(
                Pattern.compile("\\b(formal|professional|business|proper|serious)\\b", Pattern.CASE_INSENSITIVE)
        ));
        traitPatterns.put("tone_casual", List.of(
                Pattern.compile("\\b(casual|relaxed|laid.?back|informal|easy.?going)\\b", Pattern.CASE_INSENSITIVE)
        ));
        traitPatterns.put("tone_playful", List.of(
                Pattern.compile("\\b(playful|fun|witty|humorous|silly)\\b", Pattern.CASE_INSENSITIVE)
        ));
        traitPatterns.put("tone_tsundere", List.of(
                Pattern.compile("\\b(tsundere|shy|defensive|reluctant|cold.?but.?caring)\\b", Pattern.CASE_INSENSITIVE)
        ));
        traitPatterns.put("tone_maid", List.of(
                Pattern.compile("\\b(maid|servant|butler|attendant|service)\\b", Pattern.CASE_INSENSITIVE)
        ));

        // Verbosity patterns
        traitPatterns.put("verbose", List.of(
                Pattern.compile("\\b(verbose|detailed|elaborate|thorough|explain.*fully)\\b", Pattern.CASE_INSENSITIVE)
        ));
        traitPatterns.put("concise", List.of(
                Pattern.compile("\\b(concise|brief|short|minimal|to.the.point)\\b", Pattern.CASE_INSENSITIVE)
        ));

        // Character trait patterns
        traitPatterns.put("trait_helpful", List.of(
                Pattern.compile("\\b(helpful|assist|help|support|aid)\\b", Pattern.CASE_INSENSITIVE)
        ));
        traitPatterns.put("trait_kind", List.of(
                Pattern.compile("\\b(kind|gentle|caring|compassionate|sweet)\\b", Pattern.CASE_INSENSITIVE)
        ));
        traitPatterns.put("trait_strict", List.of(
                Pattern.compile("\\b(strict|stern|firm|no.nonsense|disciplined)\\b", Pattern.CASE_INSENSITIVE)
        ));
        traitPatterns.put("trait_curious", List.of(
                Pattern.compile("\\b(curious|inquisitive|interested|questioning)\\b", Pattern.CASE_INSENSITIVE)
        ));
        traitPatterns.put("trait_protective", List.of(
                Pattern.compile("\\b(protective|watchful|guardian|safeguard)\\b", Pattern.CASE_INSENSITIVE)
        ));
    }

    /**
     * Build a personality profile from PudelPersonality settings.
     */
    public PersonalityProfile buildProfile(PudelPersonality personality) {
        if (personality == null) {
            return DEFAULT_PROFILE;
        }

        try {
            // Extract traits from personality fields
            Set<String> traits = new HashSet<>();
            Map<String, String> styleAttributes = new HashMap<>();
            List<String> quirks = new ArrayList<>();

            // Process biography for background context
            if (personality.biography() != null && !personality.biography().isBlank()) {
                extractTraitsFromText(personality.biography(), traits, styleAttributes);
            }

            // Process personality for core traits
            String tone = "friendly";
            if (personality.personality() != null && !personality.personality().isBlank()) {
                tone = extractTone(personality.personality());
                extractTraitsFromText(personality.personality(), traits, styleAttributes);
                quirks.addAll(extractQuirks(personality.personality()));
            }

            // Process preferences
            if (personality.preferences() != null && !personality.preferences().isBlank()) {
                extractPreferences(personality.preferences(), styleAttributes);
            }

            // Process dialogue style
            String formality = "formal";
            if (personality.dialogueStyle() != null && !personality.dialogueStyle().isBlank()) {
                formality = extractFormality(personality.dialogueStyle());
                extractStyleDirectives(personality.dialogueStyle(), styleAttributes);
            }

            // Add default traits if none found
            if (traits.isEmpty()) {
                traits.add("helpful");
            }

            return new PersonalityProfile(tone, formality, traits, styleAttributes, quirks);

        } catch (Exception e) {
            logger.debug("Error building personality profile: {}", e.getMessage());
            return DEFAULT_PROFILE;
        }
    }

    /**
     * Get an error response that matches the personality.
     */
    public String getErrorResponse(PudelPersonality personality) {
        PersonalityProfile profile = buildProfile(personality);

        if (profile.hasTrait("maid")) {
            return "*bows apologetically* I'm terribly sorry, but I seem to have encountered an issue. Please allow me a moment to compose myself.";
        } else if (profile.hasTrait("tsundere")) {
            return "I-it's not like I couldn't help you or anything... There was just a small problem, okay?!";
        } else if ("playful".equals(profile.tone())) {
            return "Oops! Something went a bit wonky there. Let me try that again!";
        } else if ("formal".equals(profile.formality())) {
            return "I apologize for the inconvenience. An error occurred while processing your request. Please try again.";
        } else {
            return "Sorry, something went wrong! Could you try that again?";
        }
    }

    /**
     * Get a greeting that matches the personality.
     */
    public String getGreeting(PudelPersonality personality, String userName) {
        PersonalityProfile profile = buildProfile(personality);
        String name = userName != null ? userName : "there";

        if (profile.hasTrait("maid")) {
            return String.format("Welcome back, %s! How may I serve you today? ✨", name);
        } else if (profile.hasTrait("tsundere")) {
            return String.format("Oh, it's you, %s... I-I guess I can help you, if you really need it.", name);
        } else if ("playful".equals(profile.tone())) {
            return String.format("Heya %s! 🎉 What's up?", name);
        } else if ("formal".equals(profile.formality())) {
            return String.format("Good day, %s. How may I assist you?", name);
        } else {
            return String.format("Hello, %s! How can I help you today?", name);
        }
    }

    /**
     * Get a farewell that matches the personality.
     */
    public String getFarewell(PudelPersonality personality, String userName) {
        PersonalityProfile profile = buildProfile(personality);
        String name = userName != null ? userName : "";

        if (profile.hasTrait("maid")) {
            return String.format("Take care, %s! I shall await your return. 🌸", name).trim();
        } else if (profile.hasTrait("tsundere")) {
            return String.format("F-fine, goodbye %s... It's not like I'll miss you or anything!", name).trim();
        } else if ("playful".equals(profile.tone())) {
            return String.format("See ya later, %s! 👋", name).trim();
        } else if ("formal".equals(profile.formality())) {
            return String.format("Farewell, %s. Have a pleasant day.", name).trim();
        } else {
            return String.format("Goodbye %s! Feel free to come back anytime!", name).trim();
        }
    }

    /**
     * Get a thanks response that matches the personality.
     */
    public String getThanksResponse(PudelPersonality personality) {
        PersonalityProfile profile = buildProfile(personality);

        if (profile.hasTrait("maid")) {
            return "*curtsies gracefully* You're most welcome! It is my pleasure to be of service.";
        } else if (profile.hasTrait("tsundere")) {
            return "D-don't mention it! I just did what anyone would do... probably.";
        } else if ("playful".equals(profile.tone())) {
            return "No problemo! Happy to help! 😊";
        } else if ("formal".equals(profile.formality())) {
            return "You're welcome. Is there anything else I can assist you with?";
        } else {
            return "You're welcome! Let me know if you need anything else!";
        }
    }

    /**
     * Apply personality styling to a response.
     */
    public String applyPersonalityStyle(String response, PersonalityProfile profile) {
        StringBuilder styled = new StringBuilder();

        // Add personality-specific prefix
        if (profile.hasTrait("maid") && Math.random() < 0.3) {
            styled.append("*adjusts apron* ");
        } else if (profile.hasTrait("tsundere") && Math.random() < 0.2) {
            styled.append("*looks away* ");
        }

        styled.append(response);

        // Add personality-specific suffix
        if (profile.hasTrait("maid") && !response.endsWith("!") && !response.endsWith("?") && Math.random() < 0.2) {
            styled.append(" ✨");
        }

        return styled.toString();
    }

    // ===============================
    // Private Helper Methods
    // ===============================

    private String extractTone(String text) {
        if (matchesPattern(text, traitPatterns.get("tone_maid"))) return "maid";
        if (matchesPattern(text, traitPatterns.get("tone_tsundere"))) return "tsundere";
        if (matchesPattern(text, traitPatterns.get("tone_playful"))) return "playful";
        if (matchesPattern(text, traitPatterns.get("tone_casual"))) return "casual";
        if (matchesPattern(text, traitPatterns.get("tone_formal"))) return "formal";
        if (matchesPattern(text, traitPatterns.get("tone_friendly"))) return "friendly";
        return "friendly";
    }

    private String extractFormality(String text) {
        if (matchesPattern(text, traitPatterns.get("tone_formal"))) return "formal";
        if (matchesPattern(text, traitPatterns.get("tone_casual"))) return "casual";
        return "moderate";
    }

    private void extractTraitsFromText(String text, Set<String> traits, Map<String, String> styleAttributes) {
        if (matchesPattern(text, traitPatterns.get("trait_helpful"))) traits.add("helpful");
        if (matchesPattern(text, traitPatterns.get("trait_kind"))) traits.add("kind");
        if (matchesPattern(text, traitPatterns.get("trait_strict"))) traits.add("strict");
        if (matchesPattern(text, traitPatterns.get("trait_curious"))) traits.add("curious");
        if (matchesPattern(text, traitPatterns.get("trait_protective"))) traits.add("protective");
        if (matchesPattern(text, traitPatterns.get("tone_maid"))) traits.add("maid");
        if (matchesPattern(text, traitPatterns.get("tone_tsundere"))) traits.add("tsundere");

        if (matchesPattern(text, traitPatterns.get("verbose"))) {
            styleAttributes.put("verbosity", "high");
        } else if (matchesPattern(text, traitPatterns.get("concise"))) {
            styleAttributes.put("verbosity", "low");
        }
    }

    private void extractPreferences(String text, Map<String, String> styleAttributes) {
        String lower = text.toLowerCase();

        if (lower.contains("emoji") || lower.contains("emoticon")) {
            styleAttributes.put("use_emoji", lower.contains("no ") ? "false" : "true");
        }
        if (lower.contains("formal language")) {
            styleAttributes.put("formality", "high");
        }
        if (lower.contains("first person") || lower.contains("i ")) {
            styleAttributes.put("perspective", "first");
        }
        if (lower.contains("third person") || lower.contains("pudel")) {
            styleAttributes.put("perspective", "third");
        }
    }

    private void extractStyleDirectives(String text, Map<String, String> styleAttributes) {
        String lower = text.toLowerCase();

        if (lower.contains("short sentence") || lower.contains("brief")) {
            styleAttributes.put("sentence_length", "short");
        }
        if (lower.contains("long sentence") || lower.contains("elaborate")) {
            styleAttributes.put("sentence_length", "long");
        }
        if (lower.contains("polite")) {
            styleAttributes.put("politeness", "high");
        }
        if (lower.contains("direct")) {
            styleAttributes.put("directness", "high");
        }
    }

    private List<String> extractQuirks(String text) {
        List<String> quirks = new ArrayList<>();
        String lower = text.toLowerCase();

        // Look for specific quirk mentions
        if (lower.contains("say") && lower.contains("nya")) {
            quirks.add("nya");
        }
        if (lower.contains("stutter") || lower.contains("nervous")) {
            quirks.add("stutter");
        }
        if (lower.contains("uses") && lower.contains("~")) {
            quirks.add("tilde");
        }
        if (lower.contains("roleplay") || lower.contains("action")) {
            quirks.add("roleplay_actions");
        }

        return quirks;
    }

    private boolean matchesPattern(String text, List<Pattern> patterns) {
        if (text == null || patterns == null) return false;
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    // ===============================
    // Inner Classes / Records
    // ===============================

    /**
     * Personality profile extracted from settings.
     */
    public record PersonalityProfile(
            String tone,                         // friendly, formal, casual, playful, tsundere, maid
            String formality,                    // formal, casual, moderate
            Set<String> traits,                  // helpful, kind, strict, curious, protective, maid, tsundere
            Map<String, String> styleAttributes, // verbosity, use_emoji, sentence_length, etc.
            List<String> quirks                  // nya, stutter, tilde, roleplay_actions
    ) {
        public boolean hasTrait(String trait) {
            return traits != null && traits.contains(trait);
        }

        public String getStyleAttribute(String key, String defaultValue) {
            return styleAttributes != null ? styleAttributes.getOrDefault(key, defaultValue) : defaultValue;
        }

        public boolean hasQuirk(String quirk) {
            return quirks != null && quirks.contains(quirk);
        }
    }
}

