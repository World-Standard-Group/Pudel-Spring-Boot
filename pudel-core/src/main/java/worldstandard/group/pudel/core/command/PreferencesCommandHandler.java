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

import net.dv8tion.jda.api.Permission;
import org.springframework.stereotype.Component;
import worldstandard.group.pudel.api.command.CommandContext;
import worldstandard.group.pudel.api.command.TextCommandHandler;
import worldstandard.group.pudel.core.entity.GuildSettings;
import worldstandard.group.pudel.core.service.GuildInitializationService;

/**
 * Handler for the preferences command.
 * Sets Pudel's preferences for this guild.
 */
@Component
public class PreferencesCommandHandler implements TextCommandHandler {

    private final GuildInitializationService guildInitializationService;

    public PreferencesCommandHandler(GuildInitializationService guildInitializationService) {
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

        if (context.getArgs().length == 0) {
            String preferences = settings.getPreferences() != null ? settings.getPreferences() : "Not set";
            context.getChannel().sendMessage("Current preferences:\n" + preferences + "\n\n" +
                    "Usage: `!preferences <text>`").queue();
            return;
        }

        String newPreferences = String.join(" ", context.getArgs());
        settings.setPreferences(newPreferences);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);

        context.getChannel().sendMessage("✅ Preferences updated!").queue();
    }
}

