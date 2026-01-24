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
import worldstandard.group.pudel.core.command.CommandRegistry;
import worldstandard.group.pudel.core.entity.GuildSettings;
import worldstandard.group.pudel.core.service.GuildInitializationService;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handler for the enable command.
 * Re-enable a previously disabled command so people can use it again like normal.
 */
@Component
public class EnableCommandHandler implements TextCommandHandler {

    private final GuildInitializationService guildInitializationService;
    private final CommandRegistry commandRegistry;

    public EnableCommandHandler(GuildInitializationService guildInitializationService,
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
            // Show list of disabled commands
            String guildId = context.getGuild().getId();
            GuildSettings settings = guildInitializationService.getOrCreateGuildSettings(guildId);

            if (settings.getDisabledCommands() == null || settings.getDisabledCommands().isEmpty()) {
                context.getChannel().sendMessage("ℹ️ No commands are currently disabled.\n" +
                        "Usage: `!enable [command]`").queue();
                return;
            }

            String[] disabled = settings.getDisabledCommands().split(",");
            StringBuilder sb = new StringBuilder("**Disabled commands:**\n");
            for (String cmd : disabled) {
                sb.append("• `").append(cmd.trim()).append("`\n");
            }
            sb.append("\nUsage: `!enable [command]`");
            context.getChannel().sendMessage(sb.toString()).queue();
            return;
        }

        String commandName = context.getArgs()[0].toLowerCase();

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

        // Check if command is actually disabled
        if (!disabledCommands.contains(commandName)) {
            context.getChannel().sendMessage("⚠️ Command `" + commandName + "` is not disabled!").queue();
            return;
        }

        // Remove from disabled list
        disabledCommands.remove(commandName);
        String newDisabledCommands = disabledCommands.isEmpty() ? null : String.join(",", disabledCommands);
        settings.setDisabledCommands(newDisabledCommands);
        guildInitializationService.updateGuildSettings(guildId, settings);

        context.getChannel().sendMessage("✅ Command `" + commandName + "` has been re-enabled.\n" +
                "Users can now use this command again.").queue();
    }
}

