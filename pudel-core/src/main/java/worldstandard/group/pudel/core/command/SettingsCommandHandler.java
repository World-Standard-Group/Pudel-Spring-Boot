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
package worldstandard.group.pudel.core.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import worldstandard.group.pudel.api.command.CommandContext;
import worldstandard.group.pudel.api.command.TextCommandHandler;
import worldstandard.group.pudel.core.entity.GuildSettings;
import worldstandard.group.pudel.core.service.GuildInitializationService;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Handler for the !settings command.
 * Manages guild-level settings like prefix, verbosity, cooldown, channels.
 * AI personality settings are handled by AICommandHandler (!ai command).
 */
@Component
public class SettingsCommandHandler extends ListenerAdapter implements TextCommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(SettingsCommandHandler.class);

    private final GuildInitializationService guildInitializationService;

    // Track active wizard sessions: guildId_userId -> WizardSession
    private final Map<String, SettingsWizardSession> activeWizards = new ConcurrentHashMap<>();

    public SettingsCommandHandler(GuildInitializationService guildInitializationService) {
        this.guildInitializationService = guildInitializationService;
    }

    @Override
    public void handle(CommandContext context) {
        if (!context.isFromGuild()) {
            context.getChannel().sendMessage("❌ This command only works in guilds!").queue();
            return;
        }

        // Check for admin permission
        if (!context.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            context.getChannel().sendMessage("❌ You need ADMINISTRATOR permission to use this command!").queue();
            return;
        }

        String guildId = context.getGuild().getId();
        GuildSettings settings = guildInitializationService.getOrCreateGuildSettings(guildId);

        if (context.getArgs().length == 0) {
            // Show current settings overview
            showSettings(context, settings);
            return;
        }

        String subcommand = context.getArgs()[0].toLowerCase();

        switch (subcommand) {
            case "setup", "wizard" -> startWizard(context, settings);
            case "prefix" -> handlePrefix(context, settings);
            case "verbosity" -> handleVerbosity(context, settings);
            case "cooldown" -> handleCooldown(context, settings);
            case "logchannel" -> handleLogChannel(context, settings);
            case "botchannel" -> handleBotChannel(context, settings);
            default -> showHelp(context);
        }
    }

    private void showSettings(CommandContext context, GuildSettings settings) {
        boolean aiEnabled = settings.getAiEnabled() != null ? settings.getAiEnabled() : true;
        String ignoredCount = "None";
        if (settings.getIgnoredChannels() != null && !settings.getIgnoredChannels().isEmpty()) {
            ignoredCount = settings.getIgnoredChannels().split(",").length + " channel(s)";
        }

        String nickname = settings.getNickname() != null ? settings.getNickname() : "Pudel";

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⚙️ Guild Settings")
                .setColor(new Color(114, 137, 218))
                .setDescription("Use `!settings setup` for first-time configuration wizard.\n" +
                        "Use `!ai` to configure AI personality settings.")
                .addField("Prefix", "`" + settings.getPrefix() + "`", true)
                .addField("Verbosity", getVerbosityDescription(settings.getVerbosity()), true)
                .addField("Cooldown", settings.getCooldown() + "s", true)
                .addField("Log Channel", settings.getLogChannel() != null ? "<#" + settings.getLogChannel() + ">" : "None", true)
                .addField("Bot Channel", settings.getBotChannel() != null ? "<#" + settings.getBotChannel() + ">" : "All channels", true)
                .addField("Ignored Channels", ignoredCount, true)
                .addField("AI Status", aiEnabled ? "✅ Enabled" : "❌ Disabled", true)
                .addField("Bot Name", nickname, true)
                .setFooter("!settings <option> <value> | !settings setup for wizard | !ai for AI settings");

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private String getVerbosityDescription(int level) {
        return switch (level) {
            case 1 -> "Level 1 (Delete all)";
            case 2 -> "Level 2 (Keep pings)";
            case 3 -> "Level 3 (Keep all)";
            default -> "Level " + level;
        };
    }

    private void showHelp(CommandContext context) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⚙️ Settings Command Help")
                .setColor(new Color(114, 137, 218))
                .setDescription("Configure guild-level settings for Pudel.")
                .addField("First-Time Setup", "`!settings setup` - Interactive configuration wizard", false)
                .addField("Individual Settings", """
                        `!settings prefix <prefix>` - Set command prefix
                        `!settings verbosity <1|2|3>` - Message cleanup level
                        `!settings cooldown <seconds>` - Command cooldown
                        `!settings logchannel <#channel|none>` - Command log channel
                        `!settings botchannel <#channel|all>` - Restrict to channel
                        """, false)
                .addField("Related Commands", """
                        `!ai` - Configure AI personality settings
                        `!ignore` - Manage ignored channels
                        `!listen` - Set bot listen channel
                        `!enable` / `!disable` - Toggle commands
                        """, false)
                .setFooter("AI settings (biography, personality, etc.) are now under !ai command");

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    // ==================== WIZARD FUNCTIONALITY ====================

    private void startWizard(CommandContext context, GuildSettings settings) {
        String sessionKey = context.getGuild().getId() + "_" + context.getUser().getId();

        // Cancel any existing wizard
        if (activeWizards.containsKey(sessionKey)) {
            activeWizards.remove(sessionKey);
        }

        SettingsWizardSession session = new SettingsWizardSession(
                context.getGuild().getId(),
                context.getUser().getId(),
                context.getChannel().getId(),
                settings
        );
        activeWizards.put(sessionKey, session);

        // Welcome message
        EmbedBuilder welcome = new EmbedBuilder()
                .setTitle("⚙️ Pudel Setup Wizard")
                .setColor(new Color(114, 137, 218))
                .setDescription("""
                        Welcome to the Pudel setup wizard!
                        
                        I'll guide you through configuring the basic guild settings. \
                        For AI personality settings (biography, personality, etc.), use `!ai setup` after this wizard.
                        
                        Type your response or `skip` to keep current values. Type `cancel` to exit.""");

        context.getChannel().sendMessageEmbeds(welcome.build()).queue(msg -> {
            // Send first step
            sendWizardStep(context, session);
        });

        // Auto-cleanup after 10 minutes
        context.getChannel().sendMessage("").queueAfter(10, TimeUnit.MINUTES, msg -> {
            activeWizards.remove(sessionKey);
        }, error -> {});
    }

    private void sendWizardStep(CommandContext context, SettingsWizardSession session) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(114, 137, 218))
                .setFooter("Type 'skip' to keep current | 'cancel' to exit | Step " + (session.currentStep + 1) + "/5");

        switch (session.currentStep) {
            case 0 -> embed.setTitle("📝 Step 1: Command Prefix")
                    .setDescription("What prefix should trigger my commands?\n\n" +
                            "**Current:** `" + session.settings.getPrefix() + "`\n\n" +
                            "Examples: `!`, `?`, `p!`, `>>`, `.`\n\nType your preferred prefix:");
            case 1 -> embed.setTitle("🔊 Step 2: Verbosity Level")
                    .setDescription("How should I handle command messages after execution?\n\n" +
                            "**Current:** Level " + session.settings.getVerbosity() + "\n\n" +
                            "**Options:**\n" +
                            "• `1` - Delete ALL command messages after execution\n" +
                            "• `2` - Delete commands UNLESS they ping someone (prevents ghost pings)\n" +
                            "• `3` - Keep all command messages (default)\n\nEnter 1, 2, or 3:");
            case 2 -> embed.setTitle("⏱️ Step 3: Command Cooldown")
                    .setDescription("How long should users wait between commands?\n\n" +
                            "**Current:** " + session.settings.getCooldown() + " seconds\n\n" +
                            "Enter seconds (0 to disable, e.g., `5` for 5 seconds).\n" +
                            "Staff with Manage Messages permission bypass cooldowns.");
            case 3 -> embed.setTitle("📋 Step 4: Log Channel")
                    .setDescription("Where should I log command usage?\n\n" +
                            "**Current:** " + (session.settings.getLogChannel() != null ? "<#" + session.settings.getLogChannel() + ">" : "Not set") +
                            "\n\nMention a channel (e.g., `#bot-logs`) or type `none` to disable:");
            case 4 -> embed.setTitle("🎯 Step 5: Bot Channel")
                    .setDescription("Should I only respond in a specific channel?\n\n" +
                            "**Current:** " + (session.settings.getBotChannel() != null ? "<#" + session.settings.getBotChannel() + ">" : "All channels") +
                            "\n\nMention a channel to restrict, or type `all` to respond everywhere:");
            default -> {
                // Wizard complete
                completeWizard(context, session);
                return;
            }
        }

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void completeWizard(CommandContext context, SettingsWizardSession session) {
        String sessionKey = session.guildId + "_" + session.userId;
        activeWizards.remove(sessionKey);

        // Save all settings
        guildInitializationService.updateGuildSettings(session.guildId, session.settings);

        boolean aiEnabled = session.settings.getAiEnabled() != null ? session.settings.getAiEnabled() : true;

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("✅ Setup Complete!")
                .setColor(new Color(67, 181, 129))
                .setDescription("Guild settings have been configured!\n\n" +
                        "**Next Steps:**\n" +
                        "• Use `!ai setup` to configure my AI personality\n" +
                        "• Use `!help` to see all available commands\n" +
                        "• Use `!settings` to review or change settings")
                .addField("Prefix", "`" + session.settings.getPrefix() + "`", true)
                .addField("Verbosity", "Level " + session.settings.getVerbosity(), true)
                .addField("Cooldown", session.settings.getCooldown() + "s", true)
                .addField("Log Channel", session.settings.getLogChannel() != null ? "<#" + session.settings.getLogChannel() + ">" : "None", true)
                .addField("Bot Channel", session.settings.getBotChannel() != null ? "<#" + session.settings.getBotChannel() + ">" : "All", true)
                .addField("AI Status", aiEnabled ? "✅ Enabled" : "❌ Disabled", true);

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild()) return;

        String sessionKey = event.getGuild().getId() + "_" + event.getAuthor().getId();
        SettingsWizardSession session = activeWizards.get(sessionKey);

        if (session == null || !session.channelId.equals(event.getChannel().getId())) {
            return;
        }

        String input = event.getMessage().getContentRaw().trim();

        // Handle cancel
        if (input.equalsIgnoreCase("cancel")) {
            activeWizards.remove(sessionKey);
            event.getChannel().sendMessage("❌ Setup wizard cancelled. No changes were saved.").queue();
            return;
        }

        // Handle skip
        boolean skipped = input.equalsIgnoreCase("skip");

        if (!skipped) {
            // Process the input for current step
            switch (session.currentStep) {
                case 0 -> { // Prefix
                    if (input.length() > 5) {
                        event.getChannel().sendMessage("❌ Prefix must be 5 characters or less. Try again:").queue();
                        return;
                    }
                    session.settings.setPrefix(input);
                }
                case 1 -> { // Verbosity
                    try {
                        int level = Integer.parseInt(input);
                        if (level < 1 || level > 3) {
                            event.getChannel().sendMessage("❌ Enter 1, 2, or 3. Try again:").queue();
                            return;
                        }
                        session.settings.setVerbosity(level);
                    } catch (NumberFormatException e) {
                        event.getChannel().sendMessage("❌ Enter a number (1, 2, or 3). Try again:").queue();
                        return;
                    }
                }
                case 2 -> { // Cooldown
                    try {
                        float cooldown = Float.parseFloat(input);
                        if (cooldown < 0) {
                            event.getChannel().sendMessage("❌ Cooldown cannot be negative. Try again:").queue();
                            return;
                        }
                        session.settings.setCooldown(cooldown);
                    } catch (NumberFormatException e) {
                        event.getChannel().sendMessage("❌ Enter a number (e.g., 5 or 0). Try again:").queue();
                        return;
                    }
                }
                case 3 -> { // Log Channel
                    if (input.equalsIgnoreCase("none")) {
                        session.settings.setLogChannel(null);
                    } else if (!event.getMessage().getMentions().getChannels().isEmpty()) {
                        GuildChannel channel = event.getMessage().getMentions().getChannels().get(0);
                        session.settings.setLogChannel(channel.getId());
                    } else {
                        event.getChannel().sendMessage("❌ Please mention a channel (e.g., #bot-logs) or type 'none'. Try again:").queue();
                        return;
                    }
                }
                case 4 -> { // Bot Channel
                    if (input.equalsIgnoreCase("all")) {
                        session.settings.setBotChannel(null);
                    } else if (!event.getMessage().getMentions().getChannels().isEmpty()) {
                        GuildChannel channel = event.getMessage().getMentions().getChannels().get(0);
                        session.settings.setBotChannel(channel.getId());
                    } else {
                        event.getChannel().sendMessage("❌ Please mention a channel or type 'all'. Try again:").queue();
                        return;
                    }
                }
            }
        }

        // Move to next step
        session.currentStep++;

        // Create a context for sending the next step
        CommandContext mockContext = new CommandContextImpl(event, "settings", new String[0], "");

        sendWizardStep(mockContext, session);
    }

    // ==================== INDIVIDUAL SETTING HANDLERS ====================

    private void handlePrefix(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Prefix:** `" + settings.getPrefix() + "`\n\n" +
                    "*Usage:* `!settings prefix <prefix>`").queue();
            return;
        }

        String newPrefix = context.getArgs()[1];
        if (newPrefix.length() > 5) {
            context.getChannel().sendMessage("❌ Prefix must be 5 characters or less!").queue();
            return;
        }

        settings.setPrefix(newPrefix);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);

        context.getChannel().sendMessage("✅ Prefix changed to: `" + newPrefix + "`").queue();
    }

    private void handleVerbosity(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Verbosity:** Level " + settings.getVerbosity() + "\n\n" +
                    "*Usage:* `!settings verbosity <1|2|3>`\n" +
                    "• **1** - Delete all command messages\n" +
                    "• **2** - Keep messages with pings\n" +
                    "• **3** - Keep all messages").queue();
            return;
        }

        try {
            int level = Integer.parseInt(context.getArgs()[1]);
            if (level < 1 || level > 3) {
                context.getChannel().sendMessage("❌ Verbosity must be 1, 2, or 3!").queue();
                return;
            }

            settings.setVerbosity(level);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);

            String description = switch (level) {
                case 1 -> "Delete all command prompts after execution";
                case 2 -> "Delete prompts unless they ping someone";
                case 3 -> "Keep all command prompts (default)";
                default -> "";
            };

            context.getChannel().sendMessage("✅ Verbosity set to level " + level + ": " + description).queue();
        } catch (NumberFormatException e) {
            context.getChannel().sendMessage("❌ Invalid number! Use 1, 2, or 3.").queue();
        }
    }

    private void handleCooldown(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Cooldown:** " + settings.getCooldown() + " seconds\n\n" +
                    "*Usage:* `!settings cooldown <seconds>` (0 to disable)").queue();
            return;
        }

        try {
            float cooldown = Float.parseFloat(context.getArgs()[1]);
            if (cooldown < 0) {
                context.getChannel().sendMessage("❌ Cooldown cannot be negative!").queue();
                return;
            }

            settings.setCooldown(cooldown);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);

            String message = cooldown == 0 ? "disabled" : cooldown + " seconds";
            context.getChannel().sendMessage("✅ Command cooldown set to: " + message).queue();
        } catch (NumberFormatException e) {
            context.getChannel().sendMessage("❌ Invalid number!").queue();
        }
    }

    private void handleLogChannel(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Log Channel:** " +
                    (settings.getLogChannel() != null ? "<#" + settings.getLogChannel() + ">" : "Not set") +
                    "\n\n*Usage:* `!settings logchannel <#channel>` or `!settings logchannel none`").queue();
            return;
        }

        if (context.getArgs()[1].equalsIgnoreCase("none")) {
            settings.setLogChannel(null);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
            context.getChannel().sendMessage("✅ Log channel disabled").queue();
            return;
        }

        if (context.getMessage().getMentions().getChannels().isEmpty()) {
            context.getChannel().sendMessage("❌ Please mention a channel: `!settings logchannel #channel`").queue();
            return;
        }

        GuildChannel channel = context.getMessage().getMentions().getChannels().get(0);
        settings.setLogChannel(channel.getId());
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);

        context.getChannel().sendMessage("✅ Log channel set to: <#" + channel.getId() + ">").queue();
    }

    private void handleBotChannel(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Bot Channel:** " +
                    (settings.getBotChannel() != null ? "<#" + settings.getBotChannel() + ">" : "All channels") +
                    "\n\n*Usage:* `!settings botchannel <#channel>` or `!settings botchannel all`").queue();
            return;
        }

        if (context.getArgs()[1].equalsIgnoreCase("all")) {
            settings.setBotChannel(null);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
            context.getChannel().sendMessage("✅ Bot will respond in all channels").queue();
            return;
        }

        if (context.getMessage().getMentions().getChannels().isEmpty()) {
            context.getChannel().sendMessage("❌ Please mention a channel: `!settings botchannel #channel`").queue();
            return;
        }

        GuildChannel channel = context.getMessage().getMentions().getChannels().getFirst();
        settings.setBotChannel(channel.getId());
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);

        context.getChannel().sendMessage("✅ Bot channel set to: <#" + channel.getId() + ">").queue();
    }

    // ==================== WIZARD SESSION CLASS ====================

    private static class SettingsWizardSession {
        final String guildId;
        final String userId;
        final String channelId;
        final GuildSettings settings;
        int currentStep = 0;

        SettingsWizardSession(String guildId, String userId, String channelId, GuildSettings settings) {
            this.guildId = guildId;
            this.userId = userId;
            this.channelId = channelId;
            this.settings = settings;
        }
    }
}
