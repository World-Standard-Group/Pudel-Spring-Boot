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

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import worldstandard.group.pudel.core.entity.GuildSettings;
import worldstandard.group.pudel.core.repository.GuildSettingsRepository;

import java.awt.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for handling command execution features like cooldowns, logging, and verbosity.
 */
@Service
public class CommandExecutionService extends BaseService {

    private static final Logger logger = LoggerFactory.getLogger(CommandExecutionService.class);

    private final GuildSettingsRepository guildSettingsRepository;

    // Track user cooldowns: key = "guildId:userId", value = cooldown end time in milliseconds
    private final ConcurrentHashMap<String, Float> userCooldowns = new ConcurrentHashMap<>();

    // Track message IDs to delete: key = "guildId:userId:messageId", value = true
    private final ConcurrentHashMap<String, Message> messagesToManage = new ConcurrentHashMap<>();

    public CommandExecutionService(JDA jda, GuildSettingsRepository guildSettingsRepository) {
        super(jda);
        this.guildSettingsRepository = guildSettingsRepository;
    }


    /**
     * Check if a user is on cooldown and return the remaining cooldown time.
     *
     * @param guildId the guild ID
     * @param userId  the user ID
     * @return remaining cooldown time in seconds, or 0 if no cooldown
     */
    public float getRemainingCooldown(String guildId, String userId) {
        String key = guildId + ":" + userId;
        Float cooldownEndTime = userCooldowns.get(key);

        if (cooldownEndTime == null) {
            return 0;
        }

        float remainingMs = cooldownEndTime - System.currentTimeMillis();
        if (remainingMs <= 0) {
            userCooldowns.remove(key);
            return 0;
        }

        return remainingMs / 1000; // Round up to seconds
    }

    /**
     * Apply cooldown to a user.
     *
     * @param guildId      the guild ID
     * @param userId       the user ID
     * @param cooldownSecs cooldown duration in seconds
     */
    public void applyCooldown(String guildId, String userId, Float cooldownSecs) {
        if (cooldownSecs <= 0) {
            return;
        }

        String key = guildId + ":" + userId;
        float cooldownEndTime = System.currentTimeMillis() + (cooldownSecs * 1000);
        userCooldowns.put(key, cooldownEndTime);
    }

    /**
     * Check if a user should be subject to cooldown restrictions.
     * Staff (users with ADMINISTRATOR) bypass cooldowns.
     *
     * @param guild  the guild
     * @param member the member
     * @return true if user should bypass cooldown
     */
    public boolean canBypassCooldown(Guild guild, Member member) {
        if (member == null) {
            return false;
        }
        return member.hasPermission(Permission.ADMINISTRATOR);
    }

    /**
     * Log a command execution.
     *
     * @param guildId  the guild ID
     * @param command  the command name
     * @param args     the command arguments
     * @param userId   the user ID
     * @param userName the user name
     * @param success  whether the command succeeded
     */
    public void logCommand(String guildId, String command, String args, String userId, String userName, boolean success) {
        GuildSettings settings = guildSettingsRepository.findByGuildId(guildId).orElse(null);
        if (settings == null || settings.getLogChannel() == null) {
            return;
        }

        logger.debug("Logging command '{}' from user '{}' in guild {}", command, userName, guildId);
    }

    /**
     * Handle message deletion based on verbosity level.
     * Verbosity levels:
     * 1 - Delete all command prompts after execution
     * 2 - Delete prompts unless they ping a role or user
     * 3 - Keep all prompts (default)
     *
     * @param message    the command message
     * @param guildId    the guild ID
     * @param verbosity  the verbosity level
     */
    public void handleVerbosity(Message message, String guildId, int verbosity) {
        if (message == null || verbosity == 3) {
            return; // Don't delete anything for level 3
        }

        if (verbosity == 1) {
            // Always delete
            scheduleMessageDeletion(message, 100); // Delete after 100ms to ensure command is processed
        } else if (verbosity == 2) {
            // Delete unless message contains mentions
            if (message.getMentions().getUsers().isEmpty() &&
                message.getMentions().getRoles().isEmpty() &&
                message.getMentions().getChannels().isEmpty()) {
                scheduleMessageDeletion(message, 100);
            }
        }
    }

    /**
     * Schedule a message for deletion after a delay.
     *
     * @param message the message to delete
     * @param delayMs delay in milliseconds
     */
    private void scheduleMessageDeletion(Message message, long delayMs) {
        try {
            message.delete().queueAfter(delayMs, TimeUnit.MILLISECONDS,
                    success -> logger.debug("Command prompt deleted"),
                    error -> logger.debug("Could not delete command prompt: {}", error.getMessage())
            );
        } catch (Exception e) {
            logger.debug("Error scheduling message deletion: {}", e.getMessage());
        }
    }

    /**
     * Send a command log to the designated log channel.
     *
     * @param guild   the guild
     * @param command the command name
     * @param argsStr the arguments string
     * @param userId   the user ID
     * @param userName the user name
     * @param success whether the command was successful
     */
    public void sendCommandLog(Guild guild, String command, String argsStr, String userId, String userName,
                               MessageChannel commandChannel, boolean success) {
        GuildSettings settings = guildSettingsRepository.findByGuildId(guild.getId()).orElse(null);
        if (settings == null || settings.getLogChannel() == null) {
            return;
        }

        try {
            net.dv8tion.jda.api.entities.channel.concrete.TextChannel logChannel =
                guild.getTextChannelById(settings.getLogChannel());
            if (logChannel == null) {
                logger.warn("Log channel {} not found in guild {}", settings.getLogChannel(), guild.getId());
                return;
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Command Executed", null)
                    .setColor(success ? Color.GREEN : Color.RED)
                    .addField("Command", "`" + command + "`", true)
                    .addField("User", "<@" + userId + "> (" + userName + ")", true)
                    .addField("Arguments", argsStr.isEmpty() ? "None" : "`" + argsStr + "`", false)
                    .addField("Channel", commandChannel.getAsMention(), true)
                    .addField("Status", success ? "✅ Success" : "❌ Failed", true)
                    .setTimestamp(java.time.Instant.now());

            logChannel.sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            logger.error("Error sending command log: {}", e.getMessage());
        }
    }

    /**
     * Clean up cooldowns for a guild (called when bot leaves).
     *
     * @param guildId the guild ID
     */
    public void cleanupGuildCooldowns(String guildId) {
        userCooldowns.keySet().removeIf(key -> key.startsWith(guildId + ":"));
    }
}

