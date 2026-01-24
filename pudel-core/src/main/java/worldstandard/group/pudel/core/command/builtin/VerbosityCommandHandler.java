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
import org.springframework.stereotype.Component;
import worldstandard.group.pudel.api.command.CommandContext;
import worldstandard.group.pudel.api.command.TextCommandHandler;
import worldstandard.group.pudel.core.entity.GuildSettings;
import worldstandard.group.pudel.core.service.GuildInitializationService;

/**
 * Handler for the verbosity command.
 * Sets the command prompt deletion verbosity level (1-3).
 */
@Component
public class VerbosityCommandHandler implements TextCommandHandler {

    private final GuildInitializationService guildInitializationService;

    public VerbosityCommandHandler(GuildInitializationService guildInitializationService) {
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

        if (context.getArgs().length < 1) {
            GuildSettings settings = guildInitializationService.getOrCreateGuildSettings(context.getGuild().getId());
            String desc = switch (settings.getVerbosity()) {
                case 1 -> "Delete all command prompts after execution";
                case 2 -> "Delete all command prompts unless it pings a role or user";
                case 3 -> "Keep all command prompts (default)";
                default -> "Unknown";
            };
            context.getChannel().sendMessage("Current verbosity: Level " + settings.getVerbosity() + " (" + desc + ")\n" +
                    "Usage: `!verbosity <1|2|3>`\n" +
                    "**1:** Delete all command prompts\n" +
                    "**2:** Delete unless it pings a role/user\n" +
                    "**3:** Keep all prompts").queue();
            return;
        }

        try {
            int level = Integer.parseInt(context.getArgs()[0]);
            if (level < 1 || level > 3) {
                context.getChannel().sendMessage("❌ Verbosity must be 1, 2, or 3!").queue();
                return;
            }

            GuildSettings settings = guildInitializationService.getOrCreateGuildSettings(context.getGuild().getId());
            settings.setVerbosity(level);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);

            String description = switch (level) {
                case 1 -> "Delete all command prompts after execution";
                case 2 -> "Delete all command prompts unless it pings a role or user";
                case 3 -> "Keep all command prompts (default)";
                default -> "";
            };

            context.getChannel().sendMessage("✅ Verbosity set to level " + level + ": " + description).queue();
        } catch (NumberFormatException e) {
            context.getChannel().sendMessage("❌ Invalid number!").queue();
        }
    }
}

