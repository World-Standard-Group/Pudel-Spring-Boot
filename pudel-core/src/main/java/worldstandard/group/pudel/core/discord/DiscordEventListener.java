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
package worldstandard.group.pudel.core.discord;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import worldstandard.group.pudel.api.command.TextCommandHandler;
import worldstandard.group.pudel.core.command.CommandContextImpl;
import worldstandard.group.pudel.core.command.CommandRegistry;
import worldstandard.group.pudel.core.entity.GuildSettings;
import worldstandard.group.pudel.core.event.PluginEventManager;
import worldstandard.group.pudel.core.service.ChatbotService;
import worldstandard.group.pudel.core.service.CommandExecutionService;
import worldstandard.group.pudel.core.service.GuildInitializationService;

/**
 * Listens to Discord events and dispatches commands.
 * Also handles chatbot interactions and dispatches events to plugin event handlers.
 */
@Component
public class DiscordEventListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DiscordEventListener.class);

    private final CommandRegistry commandRegistry;
    private final GuildInitializationService guildInitializationService;
    private final CommandExecutionService commandExecutionService;
    private final PluginEventManager pluginEventManager;
    private final ChatbotService chatbotService;
    private static final String DEFAULT_PREFIX = "!";

    public DiscordEventListener(CommandRegistry commandRegistry,
                               GuildInitializationService guildInitializationService,
                               @Lazy CommandExecutionService commandExecutionService,
                               PluginEventManager pluginEventManager,
                               @Lazy ChatbotService chatbotService) {
        this.commandRegistry = commandRegistry;
        this.guildInitializationService = guildInitializationService;
        this.commandExecutionService = commandExecutionService;
        this.pluginEventManager = pluginEventManager;
        this.chatbotService = chatbotService;
    }

    /**
     * Generic event handler that dispatches all events to plugin handlers.
     */
    @Override
    public void onGenericEvent(@NotNull GenericEvent event) {
        // Dispatch to plugin event handlers
        try {
            pluginEventManager.dispatchEvent(event);
        } catch (Exception e) {
            logger.error("Error dispatching event {} to plugins: {}",
                    event.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        logger.info("Bot joined guild: {} ({})", event.getGuild().getName(), event.getGuild().getIdLong());
        guildInitializationService.initializeGuild(event.getGuild());
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        logger.info("Bot left guild: {} ({})", event.getGuild().getName(), event.getGuild().getId());
        guildInitializationService.cleanupGuild(event.getGuild().getId());
        commandExecutionService.cleanupGuildCooldowns(event.getGuild().getId());
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bot messages
        if (event.getAuthor().isBot()) {
            return;
        }

        String messageContent = event.getMessage().getContentRaw().trim();
        String selfId = event.getJDA().getSelfUser().getId();

        // Determine the command prefix
        String prefix = DEFAULT_PREFIX;
        GuildSettings settings = null;
        if (event.isFromGuild()) {
            settings = guildInitializationService.getOrCreateGuildSettings(event.getGuild().getId());
            prefix = settings.getPrefix() != null ? settings.getPrefix() : DEFAULT_PREFIX;

            // Check if channel is in ignored list
            if (isChannelIgnored(event.getChannel().getId(), settings)) {
                return; // Completely ignore this channel
            }

            // Check if bot should only respond in specific channel
            if (settings.getBotChannel() != null &&
                !event.getChannel().getId().equals(settings.getBotChannel())) {
                // Still allow chatbot if directly mentioned even in other channels
                if (!chatbotService.shouldRespondAsChatbot(event, selfId)) {
                    return;
                }
            }
        }

        // Check if message starts with prefix (command mode)
        if (messageContent.startsWith(prefix)) {
            handleCommand(event, messageContent, prefix, settings);
            return;
        }

        // Check if message starts with @mention (alternative command mode)
        String mentionPrefix = "<@" + selfId + "> ";
        String mentionPrefixAlt = "<@!" + selfId + "> ";
        if (messageContent.startsWith(mentionPrefix) || messageContent.startsWith(mentionPrefixAlt)) {
            String withoutMention = messageContent.startsWith(mentionPrefix)
                    ? messageContent.substring(mentionPrefix.length()).trim()
                    : messageContent.substring(mentionPrefixAlt.length()).trim();
            if (!withoutMention.isEmpty()) {
                // Check if this looks like a command
                String[] parts = withoutMention.split("\\s+", 2);
                String potentialCommand = parts[0].toLowerCase();
                if (commandRegistry.hasCommand(potentialCommand)) {
                    handleCommand(event, prefix + withoutMention, prefix, settings);
                    return;
                }
            }
        }

        // Check AI enabled status for guild
        boolean aiEnabled = true;
        if (event.isFromGuild() && settings != null) {
            aiEnabled = settings.getAiEnabled() != null ? settings.getAiEnabled() : true;
        }

        if (!aiEnabled) {
            // AI is disabled - no chatbot responses, only @mention commands (already handled above)
            return;
        }

        // Check if this should trigger chatbot response
        if (chatbotService.shouldRespondAsChatbot(event, selfId)) {
            chatbotService.handleChatbotMessage(event);
        } else {
            // Track passive context for memory building (doesn't trigger a response)
            chatbotService.trackPassiveContext(event);
        }
    }

    /**
     * Check if a channel is in the ignored list.
     */
    private boolean isChannelIgnored(String channelId, GuildSettings settings) {
        if (settings == null || settings.getIgnoredChannels() == null || settings.getIgnoredChannels().isEmpty()) {
            return false;
        }
        String[] ignored = settings.getIgnoredChannels().split(",");
        for (String ignoredId : ignored) {
            if (ignoredId.trim().equals(channelId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handle command execution.
     */
    private void handleCommand(MessageReceivedEvent event, String messageContent, String prefix, GuildSettings settings) {
        // Remove prefix and parse command
        String withoutPrefix = messageContent.substring(prefix.length()).trim();

        if (withoutPrefix.isEmpty()) {
            return;
        }

        // Split into command and arguments
        String[] parts = withoutPrefix.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String argsString = parts.length > 1 ? parts[1] : "";
        String[] args = argsString.isEmpty() ? new String[0] : argsString.split("\\s+");

        // Dispatch to command handler
        TextCommandHandler handler = commandRegistry.getCommand(command);
        if (handler == null) {
            // Silently ignore unknown commands
            return;
        }

        // Check if command is disabled for this guild
        if (event.isFromGuild()) {
            GuildSettings guildSettings = settings != null ? settings :
                    guildInitializationService.getOrCreateGuildSettings(event.getGuild().getId());
            if (isCommandDisabled(command, guildSettings)) {
                event.getChannel().sendMessage("❌ The command `" + command + "` is disabled on this server.").queue();
                return;
            }
        }

        // Check cooldown (skip for DM and for staff in guilds)
        if (event.isFromGuild()) {
            GuildSettings guildSettings = settings != null ? settings :
                    guildInitializationService.getOrCreateGuildSettings(event.getGuild().getId());
            Float cooldownSecs = guildSettings.getCooldown();

            if (cooldownSecs != null && cooldownSecs > 0) {
                // Check if user can bypass cooldown (has MANAGE_GUILD permission)
                if (!commandExecutionService.canBypassCooldown(event.getGuild(), event.getMember())) {
                    float remainingCooldown = commandExecutionService.getRemainingCooldown(
                            event.getGuild().getId(), event.getAuthor().getId());
                    if (remainingCooldown > 0) {
                        event.getChannel().sendMessage(
                                String.format("⏳ Please wait %.2f second(s) before using another command.", remainingCooldown)
                        ).queue();
                        return;
                    }
                }
            }
        }

        try {
            CommandContextImpl context = new CommandContextImpl(event, command, args, argsString);
            handler.handle(context);

            // Apply settings after successful command execution
            if (event.isFromGuild()) {
                GuildSettings guildSettings = settings != null ? settings :
                        guildInitializationService.getOrCreateGuildSettings(event.getGuild().getId());

                // Apply verbosity (handle message deletion)
                commandExecutionService.handleVerbosity(event.getMessage(), event.getGuild().getId(),
                        guildSettings.getVerbosity());

                // Log command
                commandExecutionService.sendCommandLog(event.getGuild(), command, argsString,
                        event.getAuthor().getId(), event.getAuthor().getName(),
                        event.getChannel(), true);

                // Apply cooldown
                Float cooldownSecs = guildSettings.getCooldown();
                if (cooldownSecs != null && cooldownSecs > 0 && !commandExecutionService.canBypassCooldown(event.getGuild(), event.getMember())) {
                    commandExecutionService.applyCooldown(event.getGuild().getId(),
                            event.getAuthor().getId(), cooldownSecs);
                }
            }
        } catch (Exception e) {
            logger.error("Error executing command '{}': {}", command, e.getMessage(), e);
            try {
                event.getChannel().sendMessage("❌ An error occurred while executing the command.").queue();
                // Log failed command
                if (event.isFromGuild()) {
                    commandExecutionService.sendCommandLog(event.getGuild(), command, argsString,
                            event.getAuthor().getId(), event.getAuthor().getName(),
                            event.getChannel(), false);
                }
            } catch (Exception sendError) {
                logger.error("Failed to send error message: {}", sendError.getMessage());
            }
        }
    }

    /**
     * Check if a command is disabled for a guild.
     */
    private boolean isCommandDisabled(String command, GuildSettings settings) {
        if (settings == null || settings.getDisabledCommands() == null || settings.getDisabledCommands().isEmpty()) {
            return false;
        }

        String[] disabled = settings.getDisabledCommands().toLowerCase().split(",");
        for (String disabledCmd : disabled) {
            if (disabledCmd.trim().equals(command.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}

