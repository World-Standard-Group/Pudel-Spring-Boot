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
package worldstandard.group.pudel.core.bootstrap;

import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import worldstandard.group.pudel.core.command.*;
import worldstandard.group.pudel.core.command.builtin.*;
import worldstandard.group.pudel.core.service.GuildInitializationService;

/**
 * Bootstrap runner for initializing built-in commands and guild discovery on startup.
 */
@Component
public class CommandBootstrapRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(CommandBootstrapRunner.class);

    private final CommandRegistry commandRegistry;
    private final SettingsCommandHandler settingsCommandHandler;
    private final PrefixCommandHandler prefixCommandHandler;
    private final VerbosityCommandHandler verbosityCommandHandler;
    private final CooldownCommandHandler cooldownCommandHandler;
    private final LogChannelCommandHandler logChannelCommandHandler;
    private final BotChannelCommandHandler botChannelCommandHandler;
    private final BiographyCommandHandler biographyCommandHandler;
    private final PersonalityCommandHandler personalityCommandHandler;
    private final PreferencesCommandHandler preferencesCommandHandler;
    private final DialogueStyleCommandHandler dialogueStyleCommandHandler;
    private final HelpCommandHandler helpCommandHandler;
    private final EnableCommandHandler enableCommandHandler;
    private final DisableCommandHandler disableCommandHandler;
    private final PingCommandHandler pingCommandHandler;
    private final AICommandHandler aiCommandHandler;
    private final IgnoreCommandHandler ignoreCommandHandler;
    private final ListenCommandHandler listenCommandHandler;
    private final GuildInitializationService guildInitializationService;
    private final JDA jda;

    public CommandBootstrapRunner(CommandRegistry commandRegistry,
                                 SettingsCommandHandler settingsCommandHandler,
                                 PrefixCommandHandler prefixCommandHandler,
                                 VerbosityCommandHandler verbosityCommandHandler,
                                 CooldownCommandHandler cooldownCommandHandler,
                                 LogChannelCommandHandler logChannelCommandHandler,
                                 BotChannelCommandHandler botChannelCommandHandler,
                                 BiographyCommandHandler biographyCommandHandler,
                                 PersonalityCommandHandler personalityCommandHandler,
                                 PreferencesCommandHandler preferencesCommandHandler,
                                 DialogueStyleCommandHandler dialogueStyleCommandHandler,
                                 HelpCommandHandler helpCommandHandler,
                                 EnableCommandHandler enableCommandHandler,
                                 DisableCommandHandler disableCommandHandler,
                                 PingCommandHandler pingCommandHandler,
                                 AICommandHandler aiCommandHandler,
                                 IgnoreCommandHandler ignoreCommandHandler,
                                 ListenCommandHandler listenCommandHandler,
                                 GuildInitializationService guildInitializationService,
                                 JDA jda) {
        this.commandRegistry = commandRegistry;
        this.settingsCommandHandler = settingsCommandHandler;
        this.prefixCommandHandler = prefixCommandHandler;
        this.verbosityCommandHandler = verbosityCommandHandler;
        this.cooldownCommandHandler = cooldownCommandHandler;
        this.logChannelCommandHandler = logChannelCommandHandler;
        this.botChannelCommandHandler = botChannelCommandHandler;
        this.biographyCommandHandler = biographyCommandHandler;
        this.personalityCommandHandler = personalityCommandHandler;
        this.preferencesCommandHandler = preferencesCommandHandler;
        this.dialogueStyleCommandHandler = dialogueStyleCommandHandler;
        this.helpCommandHandler = helpCommandHandler;
        this.enableCommandHandler = enableCommandHandler;
        this.disableCommandHandler = disableCommandHandler;
        this.pingCommandHandler = pingCommandHandler;
        this.aiCommandHandler = aiCommandHandler;
        this.ignoreCommandHandler = ignoreCommandHandler;
        this.listenCommandHandler = listenCommandHandler;
        this.guildInitializationService = guildInitializationService;
        this.jda = jda;
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Initializing built-in commands...");

        // Register built-in commands
        commandRegistry.registerCommand("help", helpCommandHandler);
        commandRegistry.registerCommand("enable", enableCommandHandler);
        commandRegistry.registerCommand("disable", disableCommandHandler);
        commandRegistry.registerCommand("ping", pingCommandHandler);
        commandRegistry.registerCommand("settings", settingsCommandHandler);
        commandRegistry.registerCommand("prefix", prefixCommandHandler);
        commandRegistry.registerCommand("verbosity", verbosityCommandHandler);
        commandRegistry.registerCommand("cooldown", cooldownCommandHandler);
        commandRegistry.registerCommand("logchannel", logChannelCommandHandler);
        commandRegistry.registerCommand("botchannel", botChannelCommandHandler);
        commandRegistry.registerCommand("biography", biographyCommandHandler);
        commandRegistry.registerCommand("personality", personalityCommandHandler);
        commandRegistry.registerCommand("preferences", preferencesCommandHandler);
        commandRegistry.registerCommand("dialoguestyle", dialogueStyleCommandHandler);
        commandRegistry.registerCommand("ai", aiCommandHandler);
        commandRegistry.registerCommand("ignore", ignoreCommandHandler);
        commandRegistry.registerCommand("listen", listenCommandHandler);

        logger.info("Registered {} built-in commands", commandRegistry.getCommandCount());

        // Initialize all existing guilds
        logger.info("Initializing {} guilds...", jda.getGuilds().size());
        jda.getGuilds().forEach(guild -> {
            try {
                guildInitializationService.initializeGuild(guild);
            } catch (Exception e) {
                logger.error("Error initializing guild {}: {}", guild.getId(), e.getMessage());
            }
        });

        logger.info("Command bootstrap completed");
    }
}

