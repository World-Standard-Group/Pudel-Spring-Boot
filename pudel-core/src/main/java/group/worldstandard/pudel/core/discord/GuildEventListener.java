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
package group.worldstandard.pudel.core.discord;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import group.worldstandard.pudel.api.interaction.InteractionManager;
import group.worldstandard.pudel.core.entity.Guild;
import group.worldstandard.pudel.core.repository.GuildRepository;
import group.worldstandard.pudel.core.repository.UserGuildRepository;
import group.worldstandard.pudel.core.service.CommandExecutionService;
import group.worldstandard.pudel.core.service.GuildInitializationService;

import java.time.LocalDateTime;

/**
 * Listener for guild-related events (join/leave).
 */
@Component
public class GuildEventListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(GuildEventListener.class);

    private final GuildInitializationService guildInitializationService;
    private final GuildRepository guildRepository;
    private final UserGuildRepository userGuildRepository;
    private final CommandExecutionService commandExecutionService;
    private final InteractionManager interactionManager;

    public GuildEventListener(GuildInitializationService guildInitializationService,
                            GuildRepository guildRepository,
                            UserGuildRepository userGuildRepository,
                            CommandExecutionService commandExecutionService,
                            @Lazy InteractionManager interactionManager) {
        this.guildInitializationService = guildInitializationService;
        this.guildRepository = guildRepository;
        this.userGuildRepository = userGuildRepository;
        this.commandExecutionService = commandExecutionService;
        this.interactionManager = interactionManager;
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

            // ✅ SYNC ALL SLASH COMMANDS TO GUILD IMMEDIATELY
            // Global commands can take up to 1 hour to propagate in Discord.
            // By registering them as guild commands on join, they appear instantly.
            try {
                interactionManager.syncAllCommandsToGuild(event.getGuild().getIdLong())
                        .thenRun(() -> log.info("Slash commands synced to guild: {} ({})", guildName, guildId))
                        .exceptionally(e -> {
                            log.warn("Failed to sync slash commands to guild {}: {}", guildId, e.getMessage());
                            return null;
                        });
            } catch (Exception e) {
                log.warn("Could not sync slash commands to guild {}: {}", guildId, e.getMessage());
            }

            String introductionText = """
                            👋 Thanks for adding me to your server!
                            Use `/settings` to configure my behavior.
                            Check out `!help` for available commands.
                            """;

            // Send welcome message to system channel or first text channel
            try {
                if (event.getGuild().getSystemChannel() != null && event.getGuild().getSystemChannel().canTalk()) {
                    event.getGuild().getSystemChannel().sendMessage(introductionText).queue();
                } else {
                    // Find first text channel where bot can send messages
                    for(TextChannel ch : event.getGuild().getTextChannels()){
                        if(ch.canTalk()){
                            ch.sendMessage(introductionText).queue();
                            break;
                        }
                    }
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
     * Completely removes all guild data: settings, schema, user associations, and guild record.
     */
    @Override
    @Transactional
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        String guildId = event.getGuild().getId();
        String guildName = event.getGuild().getName();

        log.info("Bot left guild: {} ({})", guildName, guildId);

        try {
            // Clean up guild settings and drop per-guild schema
            guildInitializationService.cleanupGuild(guildId);

            // Clean up cooldown data for all members in this guild
            commandExecutionService.cleanupGuildCooldowns(guildId);

            // Delete user-guild associations for this guild
            try {
                userGuildRepository.deleteByGuildId(guildId);
                log.info("Deleted user-guild associations for guild: {} ({})", guildName, guildId);
            } catch (Exception e) {
                log.warn("Failed to delete user-guild associations for guild {}: {}", guildId, e.getMessage());
            }

            // Delete the guild record completely
            try {
                guildRepository.deleteById(guildId);
                log.info("Deleted guild record: {} ({})", guildName, guildId);
            } catch (Exception e) {
                log.warn("Failed to delete guild record for {}: {}", guildId, e.getMessage());
            }

            log.info("Guild cleaned up successfully: {} ({})", guildName, guildId);
        } catch (Exception e) {
            log.error("Error cleaning up guild: {} ({})", guildName, guildId, e);
        }
    }
}

