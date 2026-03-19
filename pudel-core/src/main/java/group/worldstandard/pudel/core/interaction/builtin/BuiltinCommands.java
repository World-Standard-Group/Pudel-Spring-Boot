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
package group.worldstandard.pudel.core.interaction.builtin;

import group.worldstandard.pudel.api.annotation.*;
import group.worldstandard.pudel.api.interaction.InteractionManager;
import group.worldstandard.pudel.core.command.CommandRegistry;
import group.worldstandard.pudel.core.entity.GuildSettings;
import group.worldstandard.pudel.core.entity.PluginMetadata;
import group.worldstandard.pudel.core.service.GuildInitializationService;
import group.worldstandard.pudel.core.service.GuildSettingsService;
import group.worldstandard.pudel.core.service.PluginService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Built-in Pudel commands using Components V2 interactive panels.
 * <p>
 * {@code /settings} opens a rich interactive Settings Panel with buttons,
 * modals, and select menus. Users navigate between views (General, AI,
 * Channels, Commands, Plugins) all within a single ephemeral message.
 */
@Component
@Plugin(
    name = "pudel-core",
    version = "2.2.1",
    author = "World Standard Group",
    description = "Built-in Pudel commands"
)
public class BuiltinCommands {

    // ==================== CONSTANTS ====================
    private static final String BTN = "settings:";
    private static final String MODAL_PREFIX = "settings:modal:";

    private static final Color ACCENT_MAIN = new Color(0x5865F2);
    private static final Color ACCENT_GENERAL = new Color(0x57F287);
    private static final Color ACCENT_AI = new Color(0xEB459E);
    private static final Color ACCENT_CHANNELS = new Color(0xFEE75C);
    private static final Color ACCENT_COMMANDS = new Color(0xED4245);
    private static final Color ACCENT_PLUGINS = new Color(0x00D4AA);

    private static final int PAGE_SIZE = 5;

    // ==================== DEPENDENCIES ====================
    private final GuildInitializationService guildInitializationService;
    private final CommandRegistry commandRegistry;
    private final InteractionManager interactionManager;
    private final PluginService pluginService;
    private final GuildSettingsService guildSettingsService;

    // ==================== STATE ====================
    private final Map<Long, SettingsSession> activeSessions = new ConcurrentHashMap<>();

    public BuiltinCommands(GuildInitializationService guildInitializationService,
                           CommandRegistry commandRegistry,
                           InteractionManager interactionManager,
                           PluginService pluginService,
                           GuildSettingsService guildSettingsService) {
        this.guildInitializationService = guildInitializationService;
        this.commandRegistry = commandRegistry;
        this.interactionManager = interactionManager;
        this.pluginService = pluginService;
        this.guildSettingsService = guildSettingsService;
    }

    // =====================================================
    // /settings — Components V2 Settings Panel
    // =====================================================

    @SlashCommand(
        name = "settings",
        description = "Open the Settings Panel",
        nsfw = false,
        permissions = {Permission.ADMINISTRATOR}
    )
    public void handleSettings(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild() || event.getGuild() == null) {
            event.reply("❌ This command only works in guilds!").setEphemeral(true).queue();
            return;
        }

        long userId = event.getUser().getIdLong();
        String guildId = event.getGuild().getId();

        // Clean old session
        SettingsSession old = activeSessions.get(userId);
        if (old != null && old.message != null) {
            try{
                old.message.delete().queue();
            } catch (IllegalStateException _){

            }
        }

        SettingsSession session = new SettingsSession(userId, guildId);
        activeSessions.put(userId, session);

        GuildSettings settings = guildInitializationService.getOrCreateGuildSettings(guildId);

        event.reply(
                new MessageCreateBuilder()
                        .useComponentsV2(true)
                        .setComponents(buildMainView(settings))
                        .build()
        ).setEphemeral(true).queue(hook -> hook.retrieveOriginal().queue(msg -> session.message = msg));
    }

    // =====================================================
    // Button Handler
    // =====================================================

    @ButtonHandler(BTN)
    public void onSettingsButton(ButtonInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        SettingsSession session = activeSessions.get(userId);

        if (session == null) {
            event.reply("❌ Session expired! Use `/settings` to open a new panel.")
                    .setEphemeral(true).queue();
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) return;

        String id = event.getComponentId().substring(BTN.length());
        GuildSettings settings = guildInitializationService.getOrCreateGuildSettings(session.guildId);

        switch (id) {
            // ---- Navigation ----
            case "nav:main" -> {
                session.view = SettingsView.MAIN;
                editView(event, buildMainView(settings));
            }
            case "nav:general" -> {
                session.view = SettingsView.GENERAL;
                editView(event, buildGeneralView(settings));
            }
            case "nav:ai" -> {
                session.view = SettingsView.AI;
                editView(event, buildAIView(settings));
            }
            case "nav:ai_advanced" -> {
                session.view = SettingsView.AI_ADVANCED;
                editView(event, buildAIAdvancedView(settings));
            }
            case "nav:channels" -> {
                session.view = SettingsView.CHANNELS;
                editView(event, buildChannelsView(settings));
            }
            case "nav:commands" -> {
                session.view = SettingsView.COMMANDS;
                session.page = 0;
                editView(event, buildCommandsView(settings, session));
            }
            case "nav:plugins" -> {
                session.view = SettingsView.PLUGINS;
                session.page = 0;
                editView(event, buildPluginsView(settings, session));
            }

            // ---- General Settings ----
            case "general:prefix" -> event.replyModal(buildModal("prefix", "Set Prefix",
                    "prefix", "New prefix (max 5 chars)", settings.getPrefix(), 1, 5, true)).queue();
            case "general:cooldown" -> event.replyModal(buildModal("cooldown", "Set Cooldown",
                    "seconds", "Cooldown in seconds (0 to disable)",
                    settings.getCooldown() != null ? String.valueOf(settings.getCooldown()) : "0",
                    1, 10, true)).queue();
            case "general:verbosity:1", "general:verbosity:2", "general:verbosity:3" -> {
                int level = Integer.parseInt(id.substring(id.length() - 1));
                settings.setVerbosity(level);
                guildInitializationService.updateGuildSettings(session.guildId, settings);
                editView(event, buildGeneralView(settings));
            }

            // ---- AI Settings ----
            case "ai:toggle" -> {
                settings.setAiEnabled(!Boolean.TRUE.equals(settings.getAiEnabled()));
                guildInitializationService.updateGuildSettings(session.guildId, settings);
                editView(event, buildAIView(settings));
            }
            case "ai:personality_bio" -> event.replyModal(buildPersonalityBioModal(settings)).queue();
            case "ai:nickname" -> event.replyModal(buildModal("nickname", "Set Nickname",
                    "nickname", "Bot nickname", settings.getNickname(), 1, 100, true)).queue();
            case "ai:language" -> event.replyModal(buildModal("language", "Set Language",
                    "language", "Language code (e.g. en, th, ja)",
                    settings.getLanguage() != null ? settings.getLanguage() : "en", 2, 5, true)).queue();

            // ---- AI Advanced Settings ----
            case "ai:prefs_dialogue" -> event.replyModal(buildPrefsDialogueModal(settings)).queue();
            case "ai:quirks_prompt" -> event.replyModal(buildQuirksPromptModal(settings)).queue();
            case "ai:topics" -> event.replyModal(buildTopicsModal(settings)).queue();

            // ---- AI Behavior Settings ----
            case "ai:resplength:short", "ai:resplength:medium", "ai:resplength:long" -> {
                String value = id.substring("ai:resplength:".length());
                settings.setResponseLength(value);
                guildInitializationService.updateGuildSettings(session.guildId, settings);
                editView(event, buildAIView(settings));
            }
            case "ai:formality:casual", "ai:formality:balanced", "ai:formality:formal" -> {
                String value = id.substring("ai:formality:".length());
                settings.setFormality(value);
                guildInitializationService.updateGuildSettings(session.guildId, settings);
                editView(event, buildAIView(settings));
            }
            case "ai:emote:none", "ai:emote:minimal", "ai:emote:moderate", "ai:emote:frequent" -> {
                String value = id.substring("ai:emote:".length());
                settings.setEmoteUsage(value);
                guildInitializationService.updateGuildSettings(session.guildId, settings);
                editView(event, buildAIView(settings));
            }

            // ---- Channel Actions ----
            case "channels:setlog" -> showChannelSelect(event, "log");
            case "channels:setbot" -> showChannelSelect(event, "bot");
            case "channels:clearlog" -> {
                settings.setLogChannel(null);
                guildInitializationService.updateGuildSettings(session.guildId, settings);
                editView(event, buildChannelsView(settings));
            }
            case "channels:clearbot" -> {
                settings.setBotChannel(null);
                guildInitializationService.updateGuildSettings(session.guildId, settings);
                editView(event, buildChannelsView(settings));
            }
            case "channels:ignore" -> showChannelSelect(event, "ignore");
            case "channels:unignore" -> showChannelSelect(event, "unignore");

            // ---- Command Pagination ----
            case "cmd:prev" -> {
                session.page = Math.max(0, session.page - 1);
                editView(event, buildCommandsView(settings, session));
            }
            case "cmd:next" -> {
                session.page++;
                editView(event, buildCommandsView(settings, session));
            }

            // ---- Plugin Pagination ----
            case "plugin:prev" -> {
                session.page = Math.max(0, session.page - 1);
                editView(event, buildPluginsView(settings, session));
            }
            case "plugin:next" -> {
                session.page++;
                editView(event, buildPluginsView(settings, session));
            }

            default -> {
                if (id.startsWith("plugin:toggle:")) {
                    String pluginName = id.substring("plugin:toggle:".length());
                    handlePluginToggle(event, session, settings, pluginName);
                } else if (id.startsWith("cmd:toggle:")) {
                    String cmdName = id.substring("cmd:toggle:".length());
                    handleCommandToggle(event, session, settings, cmdName);
                } else {
                    event.deferEdit().queue();
                }
            }
        }
    }

    // =====================================================
    // Modal Handler
    // =====================================================

    @ModalHandler(MODAL_PREFIX)
    public void onSettingsModal(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        SettingsSession session = activeSessions.get(userId);

        if (session == null) {
            event.reply("❌ Session expired!").setEphemeral(true).queue();
            return;
        }

        String modalId = event.getModalId().substring(MODAL_PREFIX.length());
        GuildSettings settings = guildInitializationService.getOrCreateGuildSettings(session.guildId);

        switch (modalId) {
            case "prefix" -> {
                String value = getModalValue(event, "prefix");
                if (value.isEmpty() || value.length() > 5) {
                    event.reply("❌ Prefix must be 1–5 characters").setEphemeral(true).queue();
                    return;
                }
                settings.setPrefix(value);
                guildInitializationService.updateGuildSettings(session.guildId, settings);
                session.view = SettingsView.GENERAL;
                editModalView(event, session, buildGeneralView(settings));
            }
            case "cooldown" -> {
                String value = getModalValue(event, "seconds");
                try {
                    float seconds = Math.max(0, Float.parseFloat(value));
                    settings.setCooldown(seconds);
                    guildInitializationService.updateGuildSettings(session.guildId, settings);
                    session.view = SettingsView.GENERAL;
                    editModalView(event, session, buildGeneralView(settings));
                } catch (NumberFormatException e) {
                    event.reply("❌ Invalid number").setEphemeral(true).queue();
                }
            }
            case "personality_bio" -> {
                String personality = getModalValue(event, "personality");
                String biography = getModalValue(event, "biography");
                settings.setPersonality(personality.isEmpty() ? null : personality);
                settings.setBiography(biography.isEmpty() ? null : biography);
                guildInitializationService.updateGuildSettings(session.guildId, settings);
                session.view = SettingsView.AI;
                editModalView(event, session, buildAIView(settings));
            }
            case "nickname" -> {
                String value = getModalValue(event, "nickname");
                settings.setNickname(value.isEmpty() ? null : value);
                guildInitializationService.updateGuildSettings(session.guildId, settings);
                session.view = SettingsView.AI;
                editModalView(event, session, buildAIView(settings));
            }
            case "language" -> {
                String value = getModalValue(event, "language").toLowerCase().trim();
                if (value.isEmpty() || value.length() > 5) {
                    event.reply("❌ Language code must be 2–5 characters (e.g., `en`, `th`, `ja`)").setEphemeral(true).queue();
                    return;
                }
                settings.setLanguage(value);
                guildInitializationService.updateGuildSettings(session.guildId, settings);
                session.view = SettingsView.AI;
                editModalView(event, session, buildAIView(settings));
            }
            case "prefs_dialogue" -> {
                String preferences = getModalValue(event, "preferences");
                String dialoguestyle = getModalValue(event, "dialoguestyle");
                settings.setPreferences(preferences.isEmpty() ? null : preferences);
                settings.setDialogueStyle(dialoguestyle.isEmpty() ? null : dialoguestyle);
                guildInitializationService.updateGuildSettings(session.guildId, settings);
                session.view = SettingsView.AI_ADVANCED;
                editModalView(event, session, buildAIAdvancedView(settings));
            }
            case "quirks_prompt" -> {
                String quirks = getModalValue(event, "quirks");
                String systemprompt = getModalValue(event, "systemprompt");
                settings.setQuirks(quirks.isEmpty() ? null : quirks);
                settings.setSystemPromptPrefix(systemprompt.isEmpty() ? null : systemprompt);
                guildInitializationService.updateGuildSettings(session.guildId, settings);
                session.view = SettingsView.AI_ADVANCED;
                editModalView(event, session, buildAIAdvancedView(settings));
            }
            case "topics" -> {
                String interest = getModalValue(event, "topics_interest");
                String avoid = getModalValue(event, "topics_avoid");
                settings.setTopicsInterest(interest.isEmpty() ? null : interest);
                settings.setTopicsAvoid(avoid.isEmpty() ? null : avoid);
                guildInitializationService.updateGuildSettings(session.guildId, settings);
                session.view = SettingsView.AI_ADVANCED;
                editModalView(event, session, buildAIAdvancedView(settings));
            }
            default -> {
                // Channel selection modals: channel:log, channel:bot, channel:ignore, channel:unignore
                if (modalId.startsWith("channel:")) {
                    handleChannelModal(event, session, settings, modalId);
                } else {
                    event.deferEdit().queue();
                }
            }
        }
    }

    // =====================================================
    // View Builders
    // =====================================================

    private Container buildMainView(GuildSettings settings) {
        List<ContainerChildComponent> c = new ArrayList<>();

        c.add(TextDisplay.of("# ⚙️ Settings Panel"));
        c.add(Separator.create(false, Separator.Spacing.SMALL));

        boolean aiOn = Boolean.TRUE.equals(settings.getAiEnabled());
        long enabledCount = pluginService.getEnabledPlugins().size();
        String prefix = settings.getPrefix() != null ? settings.getPrefix() : "!";

        c.add(TextDisplay.of(
                "**Prefix:** `" + prefix + "`"
                + "\u2003**Verbosity:** " + settings.getVerbosity()
                + "\u2003**Cooldown:** " + (settings.getCooldown() != null && settings.getCooldown() > 0 ? settings.getCooldown() + "s" : "Off")
                + "\n**AI:** " + (aiOn ? "✅ Enabled" : "❌ Disabled")
                + "\u2003**Nickname:** " + (settings.getNickname() != null ? settings.getNickname() : "Pudel")
                + "\u2003**Plugins:** " + enabledCount + " active"
        ));

        c.add(Separator.create(true, Separator.Spacing.SMALL));

        c.add(ActionRow.of(
                Button.primary(BTN + "nav:general", "⚙ General"),
                Button.primary(BTN + "nav:ai", "🤖 AI"),
                Button.primary(BTN + "nav:channels", "📢 Channels")
        ));
        c.add(ActionRow.of(
                Button.primary(BTN + "nav:commands", "📝 Commands"),
                Button.primary(BTN + "nav:plugins", "🧩 Plugins")
        ));

        return Container.of(c).withAccentColor(ACCENT_MAIN);
    }

    private Container buildGeneralView(GuildSettings settings) {
        List<ContainerChildComponent> c = new ArrayList<>();

        c.add(TextDisplay.of("# ⚙️ General Settings"));
        c.add(Separator.create(false, Separator.Spacing.SMALL));

        String prefix = settings.getPrefix() != null ? settings.getPrefix() : "!";
        String cooldown = settings.getCooldown() != null && settings.getCooldown() > 0
                ? settings.getCooldown() + "s" : "Off";
        int verbosity = settings.getVerbosity() != null ? settings.getVerbosity() : 3;

        c.add(TextDisplay.of("**Prefix:** `" + prefix + "`\u2003\u2003**Cooldown:** " + cooldown));

        c.add(ActionRow.of(
                Button.secondary(BTN + "general:prefix", "✏ Change Prefix"),
                Button.secondary(BTN + "general:cooldown", "⏱ Set Cooldown")
        ));

        c.add(Separator.create(false, Separator.Spacing.SMALL));

        c.add(TextDisplay.of("**Verbosity:** Level " + verbosity
                + "\n-# 1 = Delete cmd msgs · 2 = Keep pings · 3 = Keep all"));

        c.add(ActionRow.of(
                Button.of(verbosity == 1 ? ButtonStyle.SUCCESS : ButtonStyle.SECONDARY,
                        BTN + "general:verbosity:1", verbosity == 1 ? "▸ Level 1" : "Level 1"),
                Button.of(verbosity == 2 ? ButtonStyle.SUCCESS : ButtonStyle.SECONDARY,
                        BTN + "general:verbosity:2", verbosity == 2 ? "▸ Level 2" : "Level 2"),
                Button.of(verbosity == 3 ? ButtonStyle.SUCCESS : ButtonStyle.SECONDARY,
                        BTN + "general:verbosity:3", verbosity == 3 ? "▸ Level 3" : "Level 3")
        ));

        c.add(Separator.create(true, Separator.Spacing.SMALL));
        c.add(ActionRow.of(Button.primary(BTN + "nav:main", "🔙 Back")));

        return Container.of(c).withAccentColor(ACCENT_GENERAL);
    }

    private Container buildAIView(GuildSettings settings) {
        List<ContainerChildComponent> c = new ArrayList<>();
        boolean aiOn = Boolean.TRUE.equals(settings.getAiEnabled());

        String lang = settings.getLanguage() != null ? settings.getLanguage() : "en";
        String respLen = settings.getResponseLength() != null ? settings.getResponseLength() : "medium";
        String formality = settings.getFormality() != null ? settings.getFormality() : "balanced";
        String emoteUsage = settings.getEmoteUsage() != null ? settings.getEmoteUsage() : "moderate";

        c.add(TextDisplay.of("# 🤖 AI Settings"));
        c.add(Separator.create(false, Separator.Spacing.SMALL));

        c.add(TextDisplay.of("**Status:** " + (aiOn ? "✅ Enabled" : "❌ Disabled")
                + "\u2003**Nickname:** " + (settings.getNickname() != null ? settings.getNickname() : "Pudel")
                + "\u2003**Language:** " + getLanguageDisplay(lang)));

        c.add(TextDisplay.of("**Response Length:** " + capitalize(respLen)
                + "\u2003**Formality:** " + capitalize(formality)
                + "\u2003**Emote Usage:** " + capitalize(emoteUsage)));

        c.add(Separator.create(false, Separator.Spacing.SMALL));

        c.add(ActionRow.of(
                Button.of(aiOn ? ButtonStyle.DANGER : ButtonStyle.SUCCESS,
                        BTN + "ai:toggle", aiOn ? "🔴 Disable AI" : "🟢 Enable AI"),
                Button.secondary(BTN + "ai:nickname", "✏ Nickname"),
                Button.secondary(BTN + "ai:language", "🌐 Language")
        ));

        c.add(Separator.create(false, Separator.Spacing.SMALL));

        c.add(TextDisplay.of("-# Response Length"));
        c.add(ActionRow.of(
                Button.of("short".equals(respLen) ? ButtonStyle.SUCCESS : ButtonStyle.SECONDARY,
                        BTN + "ai:resplength:short", "short".equals(respLen) ? "▸ Short" : "Short"),
                Button.of("medium".equals(respLen) ? ButtonStyle.SUCCESS : ButtonStyle.SECONDARY,
                        BTN + "ai:resplength:medium", "medium".equals(respLen) ? "▸ Medium" : "Medium"),
                Button.of("long".equals(respLen) ? ButtonStyle.SUCCESS : ButtonStyle.SECONDARY,
                        BTN + "ai:resplength:long", "long".equals(respLen) ? "▸ Long" : "Long")
        ));

        c.add(TextDisplay.of("-# Formality"));
        c.add(ActionRow.of(
                Button.of("casual".equals(formality) ? ButtonStyle.SUCCESS : ButtonStyle.SECONDARY,
                        BTN + "ai:formality:casual", "casual".equals(formality) ? "▸ Casual" : "Casual"),
                Button.of("balanced".equals(formality) ? ButtonStyle.SUCCESS : ButtonStyle.SECONDARY,
                        BTN + "ai:formality:balanced", "balanced".equals(formality) ? "▸ Balanced" : "Balanced"),
                Button.of("formal".equals(formality) ? ButtonStyle.SUCCESS : ButtonStyle.SECONDARY,
                        BTN + "ai:formality:formal", "formal".equals(formality) ? "▸ Formal" : "Formal")
        ));

        c.add(TextDisplay.of("-# Emote Usage"));
        c.add(ActionRow.of(
                Button.of("none".equals(emoteUsage) ? ButtonStyle.SUCCESS : ButtonStyle.SECONDARY,
                        BTN + "ai:emote:none", "none".equals(emoteUsage) ? "▸ None" : "None"),
                Button.of("minimal".equals(emoteUsage) ? ButtonStyle.SUCCESS : ButtonStyle.SECONDARY,
                        BTN + "ai:emote:minimal", "minimal".equals(emoteUsage) ? "▸ Minimal" : "Minimal"),
                Button.of("moderate".equals(emoteUsage) ? ButtonStyle.SUCCESS : ButtonStyle.SECONDARY,
                        BTN + "ai:emote:moderate", "moderate".equals(emoteUsage) ? "▸ Moderate" : "Moderate"),
                Button.of("frequent".equals(emoteUsage) ? ButtonStyle.SUCCESS : ButtonStyle.SECONDARY,
                        BTN + "ai:emote:frequent", "frequent".equals(emoteUsage) ? "▸ Frequent" : "Frequent")
        ));

        c.add(Separator.create(true, Separator.Spacing.SMALL));
        c.add(ActionRow.of(
                Button.primary(BTN + "nav:main", "🔙 Back"),
                Button.secondary(BTN + "nav:ai_advanced", "🔧 Advanced ▸")
        ));

        return Container.of(c).withAccentColor(ACCENT_AI);
    }

    private Container buildAIAdvancedView(GuildSettings settings) {
        List<ContainerChildComponent> c = new ArrayList<>();

        c.add(TextDisplay.of("# 🔧 AI Advanced Settings"));
        c.add(TextDisplay.of("-# Extended personality, dialogue style, and LLM tuning"));
        c.add(Separator.create(false, Separator.Spacing.SMALL));

        c.add(TextDisplay.of("**System Prompt Prefix:** " + truncate(settings.getSystemPromptPrefix(), 100)
                + "\n-# Prepended to the LLM system prompt. Use for custom instructions."
                + "\n**Quirks:** " + truncate(settings.getQuirks(), 100)
        ));

        c.add(ActionRow.of(
                Button.secondary(BTN + "ai:quirks_prompt", "✏ System Prompt & Quirks")
        ));

        c.add(Separator.create(false, Separator.Spacing.SMALL));

        c.add(TextDisplay.of("**Personality:** " + truncate(settings.getPersonality(), 120)
                + "\n**Biography:** " + truncate(settings.getBiography(), 120)));

        c.add(ActionRow.of(
                Button.secondary(BTN + "ai:personality_bio", "✏ Personality & Biography")
        ));

        c.add(Separator.create(false, Separator.Spacing.SMALL));

        c.add(TextDisplay.of("**Preferences:** " + truncate(settings.getPreferences(), 100)
                + "\n**Dialogue Style:** " + truncate(settings.getDialogueStyle(), 100)));

        c.add(ActionRow.of(
                Button.secondary(BTN + "ai:prefs_dialogue", "✏ Preferences & Dialogue Style")
        ));

        c.add(Separator.create(false, Separator.Spacing.SMALL));

        c.add(TextDisplay.of("**Topics of Interest:** " + truncate(settings.getTopicsInterest(), 100)
                + "\n**Topics to Avoid:** " + truncate(settings.getTopicsAvoid(), 100)));

        c.add(ActionRow.of(
                Button.secondary(BTN + "ai:topics", "✏ Topics Interest & Avoid")
        ));

        c.add(Separator.create(true, Separator.Spacing.SMALL));
        c.add(ActionRow.of(
                Button.primary(BTN + "nav:ai", "🔙 AI Settings"),
                Button.primary(BTN + "nav:main", "🏠 Main")
        ));

        return Container.of(c).withAccentColor(ACCENT_AI);
    }

    private Container buildChannelsView(GuildSettings settings) {
        List<ContainerChildComponent> c = new ArrayList<>();

        c.add(TextDisplay.of("# 📢 Channel Settings"));
        c.add(Separator.create(false, Separator.Spacing.SMALL));

        String logCh = settings.getLogChannel();
        String botCh = settings.getBotChannel();

        c.add(TextDisplay.of("**Log Channel:** " + (logCh != null ? "<#" + logCh + ">" : "None")
                + "\n**Bot Channel:** " + (botCh != null ? "<#" + botCh + ">" : "All channels")));

        c.add(ActionRow.of(
                Button.secondary(BTN + "channels:setlog", "📋 Set Log"),
                Button.secondary(BTN + "channels:setbot", "🤖 Set Bot Ch"),
                Button.danger(BTN + "channels:clearlog", "✖ Clear Log"),
                Button.danger(BTN + "channels:clearbot", "✖ Clear Bot")
        ));

        c.add(Separator.create(false, Separator.Spacing.SMALL));

        Set<String> ignored = parseCsv(settings.getIgnoredChannels());
        if (ignored.isEmpty()) {
            c.add(TextDisplay.of("**Ignored Channels:** _None_"));
        } else {
            StringBuilder sb = new StringBuilder("**Ignored Channels:**\n");
            for (String chId : ignored) {
                sb.append("• <#").append(chId).append(">\n");
            }
            c.add(TextDisplay.of(sb.toString()));
        }

        c.add(ActionRow.of(
                Button.success(BTN + "channels:ignore", "➕ Ignore"),
                Button.danger(BTN + "channels:unignore", "➖ Unignore")
        ));

        c.add(Separator.create(true, Separator.Spacing.SMALL));
        c.add(ActionRow.of(Button.primary(BTN + "nav:main", "🔙 Back")));

        return Container.of(c).withAccentColor(ACCENT_CHANNELS);
    }

    private Container buildCommandsView(GuildSettings settings, SettingsSession session) {
        List<ContainerChildComponent> c = new ArrayList<>();

        c.add(TextDisplay.of("# 📝 Command Management"));
        c.add(Separator.create(false, Separator.Spacing.SMALL));

        List<String> cmdList = new ArrayList<>(new TreeSet<>(commandRegistry.getAllCommands().keySet()));
        Set<String> disabled = parseCsv(settings.getDisabledCommands());

        int totalItems = cmdList.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / PAGE_SIZE));
        session.page = Math.min(session.page, totalPages - 1);

        if (cmdList.isEmpty()) {
            c.add(TextDisplay.of("_No text commands registered._"));
        } else {
            int start = session.page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, totalItems);

            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                String cmd = cmdList.get(i);
                boolean off = disabled.contains(cmd);
                sb.append(off ? "🔴" : "🟢").append(" `").append(cmd).append("`\n");
            }
            c.add(TextDisplay.of(sb.toString()));
            c.add(TextDisplay.of("-# Page " + (session.page + 1) + "/" + totalPages
                    + " • " + totalItems + " commands • " + disabled.size() + " disabled"));

            // Toggle buttons for this page (max 5 per ActionRow)
            List<Button> btns = new ArrayList<>();
            for (int i = start; i < end && btns.size() < 5; i++) {
                String cmd = cmdList.get(i);
                boolean off = disabled.contains(cmd);
                btns.add(Button.of(off ? ButtonStyle.SUCCESS : ButtonStyle.DANGER,
                        BTN + "cmd:toggle:" + cmd,
                        (off ? "✅ " : "❌ ") + cmd));
            }
            if (!btns.isEmpty()) c.add(ActionRow.of(btns));
        }

        c.add(Separator.create(true, Separator.Spacing.SMALL));
        c.add(ActionRow.of(
                Button.secondary(BTN + "cmd:prev", "◀").withDisabled(session.page <= 0),
                Button.secondary(BTN + "cmd:next", "▶").withDisabled(session.page >= totalPages - 1),
                Button.primary(BTN + "nav:main", "🔙 Back")
        ));

        return Container.of(c).withAccentColor(ACCENT_COMMANDS);
    }

    private Container buildPluginsView(GuildSettings settings, SettingsSession session) {
        List<ContainerChildComponent> c = new ArrayList<>();

        c.add(TextDisplay.of("# 🧩 Plugin Management"));
        c.add(TextDisplay.of("-# Toggle plugins for this server. Disabled plugins' commands won't appear."));
        c.add(Separator.create(false, Separator.Spacing.SMALL));

        List<PluginMetadata> plugins = pluginService.getEnabledPlugins();
        List<String> disabledForGuild = settings.getDisabledPluginsList();

        if (plugins.isEmpty()) {
            c.add(TextDisplay.of("_No plugins are globally enabled._"));
        } else {
            int totalItems = plugins.size();
            int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / PAGE_SIZE));
            session.page = Math.min(session.page, totalPages - 1);

            int start = session.page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, totalItems);

            StringBuilder sb = new StringBuilder();
            List<Button> btns = new ArrayList<>();

            for (int i = start; i < end; i++) {
                PluginMetadata p = plugins.get(i);
                boolean on = !disabledForGuild.contains(p.getPluginName());

                sb.append(on ? "🟢" : "🔴")
                  .append(" **").append(p.getPluginName()).append("**")
                  .append(" v").append(p.getPluginVersion());
                if (p.getPluginAuthor() != null) sb.append(" _by ").append(p.getPluginAuthor()).append("_");
                if (p.getPluginDescription() != null) sb.append("\n-# ").append(truncate(p.getPluginDescription(), 60));
                sb.append("\n");

                if (btns.size() < 5) {
                    btns.add(Button.of(on ? ButtonStyle.DANGER : ButtonStyle.SUCCESS,
                            BTN + "plugin:toggle:" + p.getPluginName(),
                            (on ? "❌ " : "✅ ") + truncate(p.getPluginName(), 20)));
                }
            }

            c.add(TextDisplay.of(sb.toString()));
            c.add(TextDisplay.of("-# Page " + (session.page + 1) + "/" + totalPages + " • " + totalItems + " plugins"));
            if (!btns.isEmpty()) c.add(ActionRow.of(btns));

            c.add(Separator.create(true, Separator.Spacing.SMALL));
            c.add(ActionRow.of(
                    Button.secondary(BTN + "plugin:prev", "◀").withDisabled(session.page <= 0),
                    Button.secondary(BTN + "plugin:next", "▶").withDisabled(session.page >= totalPages - 1),
                    Button.primary(BTN + "nav:main", "🔙 Back")
            ));
        }

        if (plugins.isEmpty()) {
            c.add(Separator.create(true, Separator.Spacing.SMALL));
            c.add(ActionRow.of(Button.primary(BTN + "nav:main", "🔙 Back")));
        }

        return Container.of(c).withAccentColor(ACCENT_PLUGINS);
    }

    // =====================================================
    // Action Handlers
    // =====================================================

    private void handlePluginToggle(ButtonInteractionEvent event, SettingsSession session,
                                     GuildSettings settings, String pluginName) {
        if (settings.isPluginDisabled(pluginName)) {
            guildSettingsService.enablePluginForGuild(session.guildId, pluginName);
        } else {
            guildSettingsService.disablePluginForGuild(session.guildId, pluginName);
        }

        // Re-sync guild commands so slash commands appear/disappear instantly
        try {
            interactionManager.syncGuildCommands(Long.parseLong(session.guildId));
        } catch (NumberFormatException ignored) {}

        GuildSettings refreshed = guildInitializationService.getOrCreateGuildSettings(session.guildId);
        editView(event, buildPluginsView(refreshed, session));
    }

    private void handleCommandToggle(ButtonInteractionEvent event, SettingsSession session,
                                      GuildSettings settings, String cmdName) {
        Set<String> disabled = parseCsv(settings.getDisabledCommands());
        if (disabled.contains(cmdName)) {
            disabled.remove(cmdName);
        } else {
            disabled.add(cmdName);
        }
        settings.setDisabledCommands(disabled.isEmpty() ? null : String.join(",", disabled));
        guildInitializationService.updateGuildSettings(session.guildId, settings);

        GuildSettings refreshed = guildInitializationService.getOrCreateGuildSettings(session.guildId);
        editView(event, buildCommandsView(refreshed, session));
    }

    private void showChannelSelect(ButtonInteractionEvent event, String type) {
        String title = switch (type) {
            case "log" -> "Set Log Channel";
            case "bot" -> "Set Bot Channel";
            case "ignore" -> "Ignore Channel";
            case "unignore" -> "Unignore Channel";
            default -> "Select Channel";
        };

        EntitySelectMenu channelMenu = EntitySelectMenu.create("channel", EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)
                .setPlaceholder("Select a channel...")
                .setMaxValues(1)
                .build();

        event.replyModal(Modal.create(MODAL_PREFIX + "channel:" + type, title)
                .addComponents(Label.of("📢 " + title, channelMenu))
                .build()
        ).queue();
    }

    private void handleChannelModal(ModalInteractionEvent event, SettingsSession session,
                                     GuildSettings settings, String modalId) {
        // modalId is "channel:log", "channel:bot", "channel:ignore", or "channel:unignore"
        var channelMapping = event.getValue("channel");
        if (channelMapping == null || channelMapping.getAsStringList().isEmpty()) {
            event.reply("❌ Please select a channel!").setEphemeral(true).queue();
            return;
        }
        String channelId = channelMapping.getAsStringList().getFirst();

        String type = modalId.substring("channel:".length());

        switch (type) {
            case "log" -> settings.setLogChannel(channelId);
            case "bot" -> settings.setBotChannel(channelId);
            case "ignore" -> {
                Set<String> ignored = parseCsv(settings.getIgnoredChannels());
                ignored.add(channelId);
                settings.setIgnoredChannels(String.join(",", ignored));
            }
            case "unignore" -> {
                Set<String> ignored = parseCsv(settings.getIgnoredChannels());
                ignored.remove(channelId);
                settings.setIgnoredChannels(ignored.isEmpty() ? null : String.join(",", ignored));
            }
        }

        guildInitializationService.updateGuildSettings(session.guildId, settings);
        session.view = SettingsView.CHANNELS;
        editModalView(event, session, buildChannelsView(settings));
    }

    // =====================================================
    // Utilities
    // =====================================================

    private void editView(ButtonInteractionEvent event, Container container) {
        event.editMessage(
                new MessageEditBuilder()
                        .useComponentsV2(true)
                        .setComponents(container)
                        .build()
        ).queue();
    }

    private void editModalView(ModalInteractionEvent event, SettingsSession session, Container container) {
        event.deferEdit().queue();
        if (session.message != null) {
            session.message.editMessage(
                    new MessageEditBuilder()
                            .useComponentsV2(true)
                            .setComponents(container)
                            .build()
            ).queue();
        }
    }

    private Modal buildModal(String id, String title, String inputId, String placeholder,
                              String defaultValue, int minLen, int maxLen, boolean required) {
        TextInput.Builder input = TextInput.create(inputId, TextInputStyle.SHORT)
                .setPlaceholder(placeholder)
                .setMinLength(minLen)
                .setMaxLength(maxLen)
                .setRequired(required);
        if (defaultValue != null && !defaultValue.isEmpty()) input.setValue(defaultValue);
        String labelText = placeholder.length() > 45 ? placeholder.substring(0, 42) + "..." : placeholder;
        return Modal.create(MODAL_PREFIX + id, title)
                .addComponents(Label.of(labelText, input.build()))
                .build();
    }


    private Modal buildPersonalityBioModal(GuildSettings settings) {
        TextInput.Builder personality = TextInput.create("personality", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Describe the bot's personality traits")
                .setMinLength(0).setMaxLength(2000).setRequired(false);
        if (settings.getPersonality() != null && !settings.getPersonality().isEmpty())
            personality.setValue(settings.getPersonality());

        TextInput.Builder biography = TextInput.create("biography", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Describe the bot's backstory/biography")
                .setMinLength(0).setMaxLength(2000).setRequired(false);
        if (settings.getBiography() != null && !settings.getBiography().isEmpty())
            biography.setValue(settings.getBiography());

        return Modal.create(MODAL_PREFIX + "personality_bio", "Personality & Biography")
                .addComponents(
                        Label.of("Personality", personality.build()),
                        Label.of("Biography", biography.build())
                ).build();
    }

    private Modal buildPrefsDialogueModal(GuildSettings settings) {
        TextInput.Builder prefs = TextInput.create("preferences", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Style preferences for the bot")
                .setMinLength(0).setMaxLength(2000).setRequired(false);
        if (settings.getPreferences() != null && !settings.getPreferences().isEmpty())
            prefs.setValue(settings.getPreferences());

        TextInput.Builder dialogue = TextInput.create("dialoguestyle", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Speech patterns and style")
                .setMinLength(0).setMaxLength(2000).setRequired(false);
        if (settings.getDialogueStyle() != null && !settings.getDialogueStyle().isEmpty())
            dialogue.setValue(settings.getDialogueStyle());

        return Modal.create(MODAL_PREFIX + "prefs_dialogue", "Preferences & Dialogue Style")
                .addComponents(
                        Label.of("Preferences", prefs.build()),
                        Label.of("Dialogue Style", dialogue.build())
                ).build();
    }

    private Modal buildQuirksPromptModal(GuildSettings settings) {
        TextInput.Builder quirks = TextInput.create("quirks", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Catchphrases or speech quirks")
                .setMinLength(0).setMaxLength(2000).setRequired(false);
        if (settings.getQuirks() != null && !settings.getQuirks().isEmpty())
            quirks.setValue(settings.getQuirks());

        TextInput.Builder prompt = TextInput.create("systemprompt", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Custom LLM system prompt prefix")
                .setMinLength(0).setMaxLength(2000).setRequired(false);
        if (settings.getSystemPromptPrefix() != null && !settings.getSystemPromptPrefix().isEmpty())
            prompt.setValue(settings.getSystemPromptPrefix());

        return Modal.create(MODAL_PREFIX + "quirks_prompt", "Quirks & System Prompt")
                .addComponents(
                        Label.of("System Prompt Prefix", prompt.build()),
                        Label.of("Quirks", quirks.build())
                ).build();
    }

    private Modal buildTopicsModal(GuildSettings settings) {
        TextInput.Builder interest = TextInput.create("topics_interest", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Topics the bot is interested in")
                .setMinLength(0).setMaxLength(2000).setRequired(false);
        if (settings.getTopicsInterest() != null && !settings.getTopicsInterest().isEmpty())
            interest.setValue(settings.getTopicsInterest());

        TextInput.Builder avoid = TextInput.create("topics_avoid", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Topics the bot should avoid")
                .setMinLength(0).setMaxLength(2000).setRequired(false);
        if (settings.getTopicsAvoid() != null && !settings.getTopicsAvoid().isEmpty())
            avoid.setValue(settings.getTopicsAvoid());

        return Modal.create(MODAL_PREFIX + "topics", "Topics Interest & Avoid")
                .addComponents(
                        Label.of("Topics of Interest", interest.build()),
                        Label.of("Topics to Avoid", avoid.build())
                ).build();
    }

    private String getModalValue(ModalInteractionEvent event, String id) {
        var v = event.getValue(id);
        return v != null ? v.getAsString() : "";
    }

    private Set<String> parseCsv(String csv) {
        if (csv == null || csv.isEmpty()) return new LinkedHashSet<>();
        return Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "_Not set_";
        return text.length() > maxLen ? text.substring(0, maxLen - 3) + "..." : text;
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    private String getLanguageDisplay(String code) {
        return switch (code) {
            case "th" -> "🇹🇭 Thai";
            case "ja" -> "🇯🇵 Japanese";
            case "ko" -> "🇰🇷 Korean";
            case "zh" -> "🇨🇳 Chinese";
            case "de" -> "🇩🇪 German";
            case "fr" -> "🇫🇷 French";
            case "es" -> "🇪🇸 Spanish";
            case "pt" -> "🇵🇹 Portuguese";
            case "ru" -> "🇷🇺 Russian";
            case "it" -> "🇮🇹 Italian";
            case "nl" -> "🇳🇱 Dutch";
            case "pl" -> "🇵🇱 Polish";
            case "vi" -> "🇻🇳 Vietnamese";
            case "id" -> "🇮🇩 Indonesian";
            default -> "🇬🇧 English";
        };
    }

    // =====================================================
    // Inner Types
    // =====================================================

    private enum SettingsView { MAIN, GENERAL, AI, AI_ADVANCED, CHANNELS, COMMANDS, PLUGINS }

    private static class SettingsSession {
        final long userId;
        final String guildId;
        Message message;
        SettingsView view = SettingsView.MAIN;
        int page = 0;

        SettingsSession(long userId, String guildId) {
            this.userId = userId;
            this.guildId = guildId;
        }
    }
}
