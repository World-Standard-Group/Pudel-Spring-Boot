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

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.springframework.stereotype.Component;
import worldstandard.group.pudel.api.command.CommandContext;
import worldstandard.group.pudel.api.command.TextCommandHandler;
import worldstandard.group.pudel.core.entity.GuildSettings;
import worldstandard.group.pudel.core.service.GuildInitializationService;

/**
 * Handler for the botchannel command.
 * Sets the channel where Pudel should respond (or all channels if none).
 */
@Component
public class BotChannelCommandHandler implements TextCommandHandler {

    private final GuildInitializationService guildInitializationService;

    public BotChannelCommandHandler(GuildInitializationService guildInitializationService) {
        this.guildInitializationService = guildInitializationService;
    }

    @Override
    public void handle(CommandContext context) {
        if (!context.isFromGuild()) {
            context.getChannel().sendMessage("❌ This command only works in guilds!").queue();
            return;
        }

        if (!context.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            context.getChannel().sendMessage("❌ You need ADMINISTRATOR permission to use this command!").queue();
            return;
        }

        GuildSettings settings = guildInitializationService.getOrCreateGuildSettings(context.getGuild().getId());

        if (context.getArgs().length < 1) {
            String currentChannel = settings.getBotChannel() != null ? "<#" + settings.getBotChannel() + ">" : "All channels";
            context.getChannel().sendMessage("Current bot channel: " + currentChannel + "\n" +
                    "Usage: `!botchannel <#channel>` or `!botchannel all`").queue();
            return;
        }

        if (context.getArgs()[0].equalsIgnoreCase("all")) {
            settings.setBotChannel(null);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
            context.getChannel().sendMessage("✅ Bot will respond in all channels").queue();
            return;
        }

        if (context.getMessage().getMentions().getChannels().isEmpty()) {
            context.getChannel().sendMessage("❌ Please mention a channel: `!botchannel #channel`").queue();
            return;
        }

        GuildChannel channel = context.getMessage().getMentions().getChannels().getFirst();
        settings.setBotChannel(channel.getId());
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);

        context.getChannel().sendMessage("✅ Bot channel set to: <#" + channel.getId() + ">").queue();
    }
}
