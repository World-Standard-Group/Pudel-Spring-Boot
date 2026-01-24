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
package worldstandard.group.pudel.core.command.builtin;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.springframework.stereotype.Component;
import worldstandard.group.pudel.api.command.CommandContext;
import worldstandard.group.pudel.api.command.TextCommandHandler;
import worldstandard.group.pudel.core.entity.GuildSettings;
import worldstandard.group.pudel.core.service.GuildInitializationService;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler for the !listen command.
 * Remove a channel from ignore list (previously !botchannel renamed for clarity).
 * <p>
 * Usage:
 * - !listen                     - Show current listening configuration
 * - !listen #channel            - Set a specific channel for Pudel to listen in
 * - !listen channelId           - Set channel by ID
 * - !listen -clear              - Clear listen restriction (listen everywhere)
 */
@Component
public class ListenCommandHandler implements TextCommandHandler {

    private static final Pattern CHANNEL_MENTION_PATTERN = Pattern.compile("<#(\\d+)>");

    private final GuildInitializationService guildInitializationService;

    public ListenCommandHandler(GuildInitializationService guildInitializationService) {
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
            // Show current listen configuration
            showListenStatus(context, settings);
            return;
        }

        String arg = context.getArgs()[0].toLowerCase();

        switch (arg) {
            case "-clear", "-reset", "-all" -> clearListenChannel(context, settings);
            default -> setListenChannel(context, settings, arg);
        }
    }

    private void showListenStatus(CommandContext context, GuildSettings settings) {
        String botChannel = settings.getBotChannel();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("👂 Listen Configuration")
                .setColor(new Color(114, 137, 218));

        if (botChannel == null || botChannel.isEmpty()) {
            embed.setDescription("""
                    Pudel is currently listening in **all channels** (except ignored ones).
                    
                    **Usage:**
                    `!listen #channel` - Only listen in a specific channel
                    `!listen -clear` - Listen in all channels
                    
                    **Tip:** Use `!ignore` to block specific channels instead.""");
        } else {
            GuildChannel channel = context.getGuild().getGuildChannelById(botChannel);
            String channelDisplay = channel != null
                    ? "<#" + botChannel + ">"
                    : "Unknown channel (`" + botChannel + "`)";

            embed.setDescription("Pudel is currently listening **only** in:\n" + channelDisplay + "\n\n" +
                    "Commands and AI responses will only work in this channel.\n" +
                    "Direct `@Pudel` mentions still work in other channels.");
            embed.setFooter("Use !listen -clear to listen everywhere");
        }

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void setListenChannel(CommandContext context, GuildSettings settings, String input) {
        String channelId = extractChannelId(input);

        if (channelId == null) {
            context.getChannel().sendMessage("❌ Invalid channel. Use: `!listen <#channel|channelId>`").queue();
            return;
        }

        // Verify channel exists
        GuildChannel channel = context.getGuild().getGuildChannelById(channelId);
        if (channel == null) {
            context.getChannel().sendMessage("❌ Channel not found in this server!").queue();
            return;
        }

        settings.setBotChannel(channelId);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("👂 Listen Channel Set")
                .setColor(new Color(67, 181, 129))
                .setDescription("Pudel will now **only** respond in <#" + channelId + ">.\n\n" +
                        "**Note:** Direct `@Pudel` mentions will still work in other channels.")
                .setFooter("Use !listen -clear to listen everywhere");

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void clearListenChannel(CommandContext context, GuildSettings settings) {
        if (settings.getBotChannel() == null || settings.getBotChannel().isEmpty()) {
            context.getChannel().sendMessage("ℹ️ Pudel is already listening in all channels.").queue();
            return;
        }

        settings.setBotChannel(null);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("👂 Listen Restriction Removed")
                .setColor(new Color(67, 181, 129))
                .setDescription("Pudel will now respond in **all channels** (except ignored ones).");

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private String extractChannelId(String input) {
        // Try channel mention pattern
        Matcher matcher = CHANNEL_MENTION_PATTERN.matcher(input);
        if (matcher.matches()) {
            return matcher.group(1);
        }

        // Try raw channel ID
        if (input.matches("\\d{17,20}")) {
            return input;
        }

        return null;
    }
}

