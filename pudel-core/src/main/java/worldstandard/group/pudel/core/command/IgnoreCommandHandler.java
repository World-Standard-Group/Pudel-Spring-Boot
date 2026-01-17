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
import org.springframework.stereotype.Component;
import worldstandard.group.pudel.api.command.CommandContext;
import worldstandard.group.pudel.api.command.TextCommandHandler;
import worldstandard.group.pudel.core.entity.GuildSettings;
import worldstandard.group.pudel.core.service.GuildInitializationService;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler for the !ignore command.
 * Add channels to ignore list where Pudel will completely ignore all input/output.
 * <p>
 * Usage:
 * - !ignore                     - Show current ignored channels
 * - !ignore #channel            - Add channel to ignore list
 * - !ignore channelId           - Add channel by ID to ignore list
 * - !ignore -clean              - Clear all ignored channels
 * - !ignore -remove #channel    - Remove specific channel from ignore list
 */
@Component
public class IgnoreCommandHandler implements TextCommandHandler {

    private static final Pattern CHANNEL_MENTION_PATTERN = Pattern.compile("<#(\\d+)>");

    private final GuildInitializationService guildInitializationService;

    public IgnoreCommandHandler(GuildInitializationService guildInitializationService) {
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
            // Show current ignored channels
            showIgnoredChannels(context, settings);
            return;
        }

        String arg = context.getArgs()[0].toLowerCase();

        switch (arg) {
            case "-clean", "-clear", "-reset" -> clearIgnoredChannels(context, settings);
            case "-remove", "-delete", "-rm" -> {
                if (context.getArgs().length < 2) {
                    context.getChannel().sendMessage("❌ Usage: `!ignore -remove <#channel|channelId>`").queue();
                    return;
                }
                removeIgnoredChannel(context, settings, context.getArgs()[1]);
            }
            default -> addIgnoredChannel(context, settings, arg);
        }
    }

    private void showIgnoredChannels(CommandContext context, GuildSettings settings) {
        Set<String> ignoredChannels = getIgnoredChannelSet(settings);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔇 Ignored Channels")
                .setColor(new Color(114, 137, 218));

        if (ignoredChannels.isEmpty()) {
            embed.setDescription("""
                    No channels are currently ignored.
                    
                    **Usage:**
                    `!ignore #channel` - Ignore a channel
                    `!ignore -remove #channel` - Stop ignoring a channel
                    `!ignore -clean` - Clear all ignored channels""");
        } else {
            StringBuilder channelList = new StringBuilder();
            for (String channelId : ignoredChannels) {
                GuildChannel channel = context.getGuild().getGuildChannelById(channelId);
                if (channel != null) {
                    channelList.append("<#").append(channelId).append("> (`").append(channelId).append("`)\n");
                } else {
                    channelList.append("Unknown channel (`").append(channelId).append("`)\n");
                }
            }
            embed.setDescription("Pudel completely ignores these channels (no AI, no commands):\n\n" + channelList);
            embed.setFooter("Total: " + ignoredChannels.size() + " channel(s) ignored");
        }

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void addIgnoredChannel(CommandContext context, GuildSettings settings, String input) {
        String channelId = extractChannelId(input);

        if (channelId == null) {
            context.getChannel().sendMessage("❌ Invalid channel. Use: `!ignore <#channel|channelId>`").queue();
            return;
        }

        // Verify channel exists
        GuildChannel channel = context.getGuild().getGuildChannelById(channelId);
        if (channel == null) {
            context.getChannel().sendMessage("❌ Channel not found in this server!").queue();
            return;
        }

        // Check if already ignored
        Set<String> ignoredChannels = getIgnoredChannelSet(settings);
        if (ignoredChannels.contains(channelId)) {
            context.getChannel().sendMessage("ℹ️ <#" + channelId + "> is already in the ignore list.").queue();
            return;
        }

        // Add to ignore list
        ignoredChannels.add(channelId);
        settings.setIgnoredChannels(String.join(",", ignoredChannels));
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔇 Channel Ignored")
                .setColor(new Color(240, 71, 71))
                .setDescription("<#" + channelId + "> has been added to the ignore list.\n\n" +
                        "Pudel will now **completely ignore** this channel:\n" +
                        "• No AI responses\n" +
                        "• No command execution\n" +
                        "• No passive context tracking")
                .setFooter("Use !ignore -remove #channel to undo");

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void removeIgnoredChannel(CommandContext context, GuildSettings settings, String input) {
        String channelId = extractChannelId(input);

        if (channelId == null) {
            context.getChannel().sendMessage("❌ Invalid channel. Use: `!ignore -remove <#channel|channelId>`").queue();
            return;
        }

        Set<String> ignoredChannels = getIgnoredChannelSet(settings);

        if (!ignoredChannels.contains(channelId)) {
            context.getChannel().sendMessage("ℹ️ <#" + channelId + "> is not in the ignore list.").queue();
            return;
        }

        // Remove from ignore list
        ignoredChannels.remove(channelId);
        settings.setIgnoredChannels(ignoredChannels.isEmpty() ? null : String.join(",", ignoredChannels));
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔊 Channel Un-ignored")
                .setColor(new Color(67, 181, 129))
                .setDescription("<#" + channelId + "> has been removed from the ignore list.\n\n" +
                        "Pudel will now respond normally in this channel.");

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void clearIgnoredChannels(CommandContext context, GuildSettings settings) {
        Set<String> ignoredChannels = getIgnoredChannelSet(settings);

        if (ignoredChannels.isEmpty()) {
            context.getChannel().sendMessage("ℹ️ No channels are currently ignored.").queue();
            return;
        }

        int count = ignoredChannels.size();
        settings.setIgnoredChannels(null);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔊 Ignore List Cleared")
                .setColor(new Color(67, 181, 129))
                .setDescription("Cleared **" + count + "** channel(s) from the ignore list.\n\n" +
                        "Pudel will now respond normally in all channels.");

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

    private Set<String> getIgnoredChannelSet(GuildSettings settings) {
        if (settings.getIgnoredChannels() == null || settings.getIgnoredChannels().isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(settings.getIgnoredChannels().split(",")));
    }
}

