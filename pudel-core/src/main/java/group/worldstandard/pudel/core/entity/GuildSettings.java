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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents guild settings for Pudel bot configuration.
 */
@Entity
@Table(name = "guild_settings")
public class GuildSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false, unique = true)
    private String guildId;

    @Column
    private String prefix = "!";

    @Column
    private Integer verbosity = 3;

    @Column
    private Float cooldown = 0f;

    @Column(name = "log_channel")
    private String logChannel;

    @Column(name = "bot_channel")
    private String botChannel;

    @Column(columnDefinition = "TEXT")
    private String biography;

    @Column(columnDefinition = "TEXT")
    private String personality;

    @Column(columnDefinition = "TEXT")
    private String preferences;

    @Column(name = "dialogue_style", columnDefinition = "TEXT")
    private String dialogueStyle;

    @Column(name = "nickname")
    private String nickname;

    @Column(name = "language")
    private String language = "en";

    @Column(name = "response_length")
    private String responseLength = "medium";

    @Column(name = "formality")
    private String formality = "balanced";

    @Column(name = "emote_usage")
    private String emoteUsage = "moderate"; // none, minimal, moderate, frequent

    @Column(name = "quirks", columnDefinition = "TEXT")
    private String quirks; // Speech quirks/catchphrases

    @Column(name = "topics_interest", columnDefinition = "TEXT")
    private String topicsInterest; // Topics Pudel is interested in

    @Column(name = "topics_avoid", columnDefinition = "TEXT")
    private String topicsAvoid; // Topics Pudel should avoid

    @Column(name = "system_prompt_prefix", columnDefinition = "TEXT")
    private String systemPromptPrefix; // Custom system prompt prefix for LLM

    @Column(name = "disabled_commands", columnDefinition = "TEXT")
    private String disabledCommands;

    @Column(name = "disabled_plugins", columnDefinition = "TEXT")
    private String disabledPlugins;

    @Column(name = "ai_enabled")
    private Boolean aiEnabled = true;

    @Column(name = "ignored_channels", columnDefinition = "TEXT")
    private String ignoredChannels;

    @Column(name = "schema_created")
    private Boolean schemaCreated = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // Constructors
    public GuildSettings() {
    }

    public GuildSettings(String guildId) {
        this.guildId = guildId;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Integer getVerbosity() {
        return verbosity;
    }

    public void setVerbosity(Integer verbosity) {
        this.verbosity = verbosity;
    }

    public Float getCooldown() {
        return cooldown;
    }

    public void setCooldown(Float cooldown) {
        this.cooldown = cooldown;
    }

    public String getLogChannel() {
        return logChannel;
    }

    public void setLogChannel(String logChannel) {
        this.logChannel = logChannel;
    }

    public String getBotChannel() {
        return botChannel;
    }

    public void setBotChannel(String botChannel) {
        this.botChannel = botChannel;
    }

    public String getBiography() {
        return biography;
    }

    public void setBiography(String biography) {
        this.biography = biography;
    }

    public String getPersonality() {
        return personality;
    }

    public void setPersonality(String personality) {
        this.personality = personality;
    }

    public String getPreferences() {
        return preferences;
    }

    public void setPreferences(String preferences) {
        this.preferences = preferences;
    }

    public String getDialogueStyle() {
        return dialogueStyle;
    }

    public void setDialogueStyle(String dialogueStyle) {
        this.dialogueStyle = dialogueStyle;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getResponseLength() {
        return responseLength;
    }

    public void setResponseLength(String responseLength) {
        this.responseLength = responseLength;
    }

    public String getFormality() {
        return formality;
    }

    public void setFormality(String formality) {
        this.formality = formality;
    }

    public String getEmoteUsage() {
        return emoteUsage;
    }

    public void setEmoteUsage(String emoteUsage) {
        this.emoteUsage = emoteUsage;
    }

    public String getQuirks() {
        return quirks;
    }

    public void setQuirks(String quirks) {
        this.quirks = quirks;
    }

    public String getTopicsInterest() {
        return topicsInterest;
    }

    public void setTopicsInterest(String topicsInterest) {
        this.topicsInterest = topicsInterest;
    }

    public String getTopicsAvoid() {
        return topicsAvoid;
    }

    public void setTopicsAvoid(String topicsAvoid) {
        this.topicsAvoid = topicsAvoid;
    }

    public String getSystemPromptPrefix() {
        return systemPromptPrefix;
    }

    public void setSystemPromptPrefix(String systemPromptPrefix) {
        this.systemPromptPrefix = systemPromptPrefix;
    }

    public String getDisabledCommands() {
        return disabledCommands;
    }

    public void setDisabledCommands(String disabledCommands) {
        this.disabledCommands = disabledCommands;
    }

    public String getDisabledPlugins() {
        return disabledPlugins;
    }

    public void setDisabledPlugins(String disabledPlugins) {
        this.disabledPlugins = disabledPlugins;
    }

    /**
     * Get disabled plugins as a list.
     * The field stores comma-separated plugin names.
     */
    public List<String> getDisabledPluginsList() {
        if (disabledPlugins == null || disabledPlugins.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(disabledPlugins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Check if a specific plugin is disabled for this guild.
     */
    public boolean isPluginDisabled(String pluginName) {
        return getDisabledPluginsList().contains(pluginName);
    }

    public Boolean getAiEnabled() {
        return aiEnabled;
    }

    public void setAiEnabled(Boolean aiEnabled) {
        this.aiEnabled = aiEnabled;
    }

    public String getIgnoredChannels() {
        return ignoredChannels;
    }

    public void setIgnoredChannels(String ignoredChannels) {
        this.ignoredChannels = ignoredChannels;
    }

    public Boolean getSchemaCreated() {
        return schemaCreated;
    }

    public void setSchemaCreated(Boolean schemaCreated) {
        this.schemaCreated = schemaCreated;
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

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}