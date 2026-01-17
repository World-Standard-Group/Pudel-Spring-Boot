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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handler for the disable command.
 * Disable a command so nobody can use them anymore in guild.
 */
@Component
public class DisableCommandHandler implements TextCommandHandler {

    private static final Set<String> PROTECTED_COMMANDS = Set.of("help", "enable", "disable", "settings");

    private final GuildInitializationService guildInitializationService;
    private final CommandRegistry commandRegistry;

    public DisableCommandHandler(GuildInitializationService guildInitializationService,
                                 CommandRegistry commandRegistry) {
        this.guildInitializationService = guildInitializationService;
        this.commandRegistry = commandRegistry;
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
            context.getChannel().sendMessage("❌ Usage: `!disable [command]`\n" +
                    "Example: `!disable ping`").queue();
            return;
        }

        String commandName = context.getArgs()[0].toLowerCase();

        // Check if command is protected
        if (PROTECTED_COMMANDS.contains(commandName)) {
            context.getChannel().sendMessage("❌ Cannot disable protected command: `" + commandName + "`\n" +
                    "Protected commands: " + String.join(", ", PROTECTED_COMMANDS)).queue();
            return;
        }

        // Check if command exists
        if (!commandRegistry.hasCommand(commandName)) {
            context.getChannel().sendMessage("❌ Unknown command: `" + commandName + "`").queue();
            return;
        }

        String guildId = context.getGuild().getId();
        GuildSettings settings = guildInitializationService.getOrCreateGuildSettings(guildId);

        // Get current disabled commands
        Set<String> disabledCommands = new HashSet<>();
        if (settings.getDisabledCommands() != null && !settings.getDisabledCommands().isEmpty()) {
            disabledCommands = Arrays.stream(settings.getDisabledCommands().split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
        }

        // Check if already disabled
        if (disabledCommands.contains(commandName)) {
            context.getChannel().sendMessage("⚠️ Command `" + commandName + "` is already disabled!").queue();
            return;
        }

        // Add to disabled list
        disabledCommands.add(commandName);
        settings.setDisabledCommands(String.join(",", disabledCommands));
        guildInitializationService.updateGuildSettings(guildId, settings);

        context.getChannel().sendMessage("✅ Command `" + commandName + "` has been disabled.\n" +
                "Use `!enable " + commandName + "` to re-enable it.").queue();
    }
}

