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

import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import worldstandard.group.pudel.core.entity.Guild;
import worldstandard.group.pudel.core.repository.GuildRepository;
import worldstandard.group.pudel.core.service.CommandExecutionService;
import worldstandard.group.pudel.core.service.GuildInitializationService;

import java.time.LocalDateTime;

/**
 * Listener for guild-related events (join/leave).
 */
@Component
public class GuildEventListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(GuildEventListener.class);

    private final GuildInitializationService guildInitializationService;
    private final GuildRepository guildRepository;
    private final CommandExecutionService commandExecutionService;

    public GuildEventListener(GuildInitializationService guildInitializationService,
                            GuildRepository guildRepository,
                            CommandExecutionService commandExecutionService) {
        this.guildInitializationService = guildInitializationService;
        this.guildRepository = guildRepository;
        this.commandExecutionService = commandExecutionService;
    }

    /**
     * Called when the bot joins a guild.
     */
    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        String guildId = event.getGuild().getId();
        String guildName = event.getGuild().getName();
        int memberCount = event.getGuild().getMemberCount();

        log.info("Bot joined guild: {} ({}) with {} members", guildName, guildId, memberCount);

        try {
            // Create or update guild record
            Guild guild = guildRepository.findById(guildId)
                    .orElse(new Guild(guildId, guildName));

            guild.setName(guildName);
            guild.setMemberCount(memberCount);

            if (event.getGuild().getIconId() != null) {
                guild.setIcon("https://cdn.discordapp.com/icons/" + guildId + "/" +
                        event.getGuild().getIconId() + ".png");
            }

            guild.setOwnerId(event.getGuild().getOwnerId());

            // ✅ SET BOT PRESENCE TIMESTAMP
            if (guild.getBotJoinedAt() == null) {
                guild.setBotJoinedAt(LocalDateTime.now());
                log.info("Bot presence tracked for guild: {} ({})", guildName, guildId);
            }

            guildRepository.save(guild);

            // ...existing code...

            // Initialize guild settings with defaults
            guildInitializationService.initializeGuild(event.getGuild());

            // Send welcome message to system channel or first text channel
            try {
                if (event.getGuild().getSystemChannel() != null) {
                    event.getGuild().getSystemChannel().sendMessage(
                            "👋 Thanks for adding me to your server! " +
                            "Use `/settings` to configure my behavior. " +
                            "Check out `/help` for available commands."
                    ).queue();
                } else {
                    // Find first text channel where bot can send messages
                    event.getGuild().getTextChannels().stream()
                            .findFirst()
                            .ifPresent(channel -> channel.sendMessage(
                                    "👋 Thanks for adding me to your server! " +
                                    "Use `/settings` to configure my behavior. " +
                                    "Check out `/help` for available commands."
                            ).queue());
                }
            } catch (Exception e) {
                log.warn("Could not send welcome message to guild {}", guildId, e);
            }

            log.info("Guild initialized successfully: {} ({})", guildName, guildId);
        } catch (Exception e) {
            log.error("Error initializing guild: {} ({})", guildName, guildId, e);
        }
    }

    /**
     * Called when the bot leaves a guild.
     */
    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        String guildId = event.getGuild().getId();
        String guildName = event.getGuild().getName();

        log.info("Bot left guild: {} ({})", guildName, guildId);

        try {
            // ✅ CLEAR BOT PRESENCE TIMESTAMP
            Guild guild = guildRepository.findById(guildId).orElse(null);
            if (guild != null) {
                guild.setBotJoinedAt(null);
                guildRepository.save(guild);
                log.info("Bot presence cleared for guild: {} ({})", guildName, guildId);
            }

            // Clean up guild settings
            guildInitializationService.cleanupGuild(guildId);

            // Clean up cooldown data for all members in this guild
            commandExecutionService.cleanupGuildCooldowns(guildId);

            // Remove guild record (optional - can be kept for analytics)
            // guildRepository.deleteById(guildId);

            log.info("Guild cleaned up successfully: {} ({})", guildName, guildId);
        } catch (Exception e) {
            log.error("Error cleaning up guild: {} ({})", guildName, guildId, e);
        }
    }
}

