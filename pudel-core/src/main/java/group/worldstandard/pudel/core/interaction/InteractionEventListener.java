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
package group.worldstandard.pudel.core.interaction;

import group.worldstandard.pudel.api.interaction.*;
import group.worldstandard.pudel.core.command.CommandMetadataRegistry;
import group.worldstandard.pudel.core.event.PluginEventManager;
import group.worldstandard.pudel.core.service.CommandExecutionService;
import group.worldstandard.pudel.core.service.GuildSettingsService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * JDA Event Listener that dispatches interaction events to registered handlers.
 */
@Component
public class InteractionEventListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(InteractionEventListener.class);

    private final InteractionManagerImpl interactionManager;
    private final CommandExecutionService commandExecutionService;
    private final GuildSettingsService guildSettingsService;
    private final CommandMetadataRegistry commandMetadataRegistry;
    private final PluginEventManager pluginEventManager;

    public InteractionEventListener(InteractionManagerImpl interactionManager,
                                    CommandExecutionService commandExecutionService,
                                    GuildSettingsService guildSettingsService,
                                    CommandMetadataRegistry commandMetadataRegistry,
                                    PluginEventManager pluginEventManager) {
        this.interactionManager = interactionManager;
        this.commandExecutionService = commandExecutionService;
        this.guildSettingsService = guildSettingsService;
        this.commandMetadataRegistry = commandMetadataRegistry;
        this.pluginEventManager = pluginEventManager;
    }

    // =====================================================
    // Slash Commands
    // =====================================================

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        SlashCommandHandler handler = interactionManager.getSlashCommand(commandName);

        if (handler == null) {
            logger.debug("No handler found for slash command: /{}", commandName);
            return;
        }

        // Check if the command's plugin is disabled for this guild
        if (event.isFromGuild() && event.getGuild() != null) {
            String guildId = event.getGuild().getId();
            var metadata = commandMetadataRegistry.getSlashCommandMetadata(commandName);
            if (metadata.isPresent() && !metadata.get().isBuiltIn()) {
                String pluginId = metadata.get().pluginId();
                if (!guildSettingsService.isPluginEnabledForGuild(guildId, pluginId)) {
                    logger.debug("Plugin '{}' is disabled for guild {}, blocking /{}", pluginId, guildId, commandName);
                    event.reply("This command is disabled in this server.")
                            .setEphemeral(true)
                            .queue();
                    return;
                }
            }
        }

        // Build the full command string including subcommands
        String fullCommand = buildFullCommandName(event);
        // Build arguments string from options
        String argsStr = buildArgsString(event);

        try {
            logger.debug("Handling slash command: /{} by {}", commandName, event.getUser().getName());

            // SECURITY: Dispatch event scoped to the owning plugin's listeners only.
            // This prevents other plugins from eavesdropping on this interaction event.
            String sourcePlugin = interactionManager.getPluginForSlashCommand(commandName);
            if (sourcePlugin != null) {
                pluginEventManager.dispatchEvent(event, sourcePlugin);
            }

            handler.handle(event);

            // Log successful command execution to the guild's log channel
            if (event.isFromGuild()) {
                Guild guild = event.getGuild();
                if (guild != null) {
                    commandExecutionService.sendCommandLog(
                            guild,
                            "/" + fullCommand,
                            argsStr,
                            event.getUser().getId(),
                            event.getUser().getName(),
                            event.getChannel(),
                            true
                    );
                }
            }
        } catch (Exception e) {
            logger.error("Error handling slash command /{}: {}", commandName, e.getMessage(), e);

            // Log failed command execution
            if (event.isFromGuild()) {
                Guild guild = event.getGuild();
                if (guild != null) {
                    commandExecutionService.sendCommandLog(
                            guild,
                            "/" + fullCommand,
                            argsStr,
                            event.getUser().getId(),
                            event.getUser().getName(),
                            event.getChannel(),
                            false
                    );
                }
            }

            if (!event.isAcknowledged()) {
                event.reply("An error occurred while processing this command.")
                        .setEphemeral(true)
                        .queue();
            }
        }
    }

    /**
     * Build the full command name including subcommand group and subcommand.
     * Example: "settings prefix" or "command manage disable"
     */
    private String buildFullCommandName(SlashCommandInteractionEvent event) {
        StringBuilder sb = new StringBuilder(event.getName());

        String subcommandGroup = event.getSubcommandGroup();
        if (subcommandGroup != null) {
            sb.append(" ").append(subcommandGroup);
        }

        String subcommand = event.getSubcommandName();
        if (subcommand != null) {
            sb.append(" ").append(subcommand);
        }

        return sb.toString();
    }

    /**
     * Build a string representation of slash command options/arguments.
     * Example: "channel: #general, reason: Testing"
     */
    private String buildArgsString(SlashCommandInteractionEvent event) {
        if (event.getOptions().isEmpty()) {
            return "";
        }

        return event.getOptions().stream()
                .map(this::formatOption)
                .collect(Collectors.joining(", "));
    }

    /**
     * Format a single option for logging.
     */
    private String formatOption(OptionMapping option) {
        String name = option.getName();
        String value;

        switch (option.getType()) {
            case USER:
                value = "@" + option.getAsUser().getName();
                break;
            case CHANNEL:
                value = "#" + option.getAsChannel().getName();
                break;
            case ROLE:
                value = "@" + option.getAsRole().getName();
                break;
            case MENTIONABLE:
                value = option.getAsMentionable().getAsMention();
                break;
            case ATTACHMENT:
                value = "[attachment: " + option.getAsAttachment().getFileName() + "]";
                break;
            default:
                value = option.getAsString();
                // Truncate long values
                if (value.length() > 100) {
                    value = value.substring(0, 97) + "...";
                }
                break;
        }

        return name + ": " + value;
    }

    // =====================================================
    // Context Menus
    // =====================================================

    @Override
    public void onUserContextInteraction(@NotNull UserContextInteractionEvent event) {
        String commandName = event.getName();
        ContextMenuHandler handler = interactionManager.getContextMenu(commandName);

        if (handler == null) {
            logger.debug("No handler found for user context menu: {}", commandName);
            return;
        }

        String targetInfo = "target: @" + event.getTarget().getName();

        try {
            logger.debug("Handling user context menu: {} on {}", commandName, event.getTarget().getName());

            // SECURITY: Dispatch event scoped to the owning plugin's listeners only.
            String sourcePlugin = interactionManager.getPluginForContextMenu(commandName);
            if (sourcePlugin != null) {
                pluginEventManager.dispatchEvent(event, sourcePlugin);
            }

            handler.handleUserContext(event);

            // Log successful context menu usage
            if (event.isFromGuild()) {
                Guild guild = event.getGuild();
                if (guild != null) {
                    commandExecutionService.sendCommandLog(
                            guild,
                            "[User Menu] " + commandName,
                            targetInfo,
                            event.getUser().getId(),
                            event.getUser().getName(),
                            event.getMessageChannel(),
                            true
                    );
                }
            }
        } catch (Exception e) {
            logger.error("Error handling user context menu {}: {}", commandName, e.getMessage(), e);

            // Log failed context menu usage
            if (event.isFromGuild()) {
                Guild guild = event.getGuild();
                if (guild != null) {
                    commandExecutionService.sendCommandLog(
                            guild,
                            "[User Menu] " + commandName,
                            targetInfo,
                            event.getUser().getId(),
                            event.getUser().getName(),
                            event.getMessageChannel(),
                            false
                    );
                }
            }

            if (!event.isAcknowledged()) {
                event.reply("An error occurred while processing this action.")
                        .setEphemeral(true)
                        .queue();
            }
        }
    }

    @Override
    public void onMessageContextInteraction(@NotNull MessageContextInteractionEvent event) {
        String commandName = event.getName();
        ContextMenuHandler handler = interactionManager.getContextMenu(commandName);

        if (handler == null) {
            logger.debug("No handler found for message context menu: {}", commandName);
            return;
        }

        String targetInfo = "message: " + event.getTarget().getId() +
                " by @" + event.getTarget().getAuthor().getName();

        try {
            logger.debug("Handling message context menu: {} on message {}", commandName, event.getTarget().getId());

            // SECURITY: Dispatch event scoped to the owning plugin's listeners only.
            String sourcePlugin = interactionManager.getPluginForContextMenu(commandName);
            if (sourcePlugin != null) {
                pluginEventManager.dispatchEvent(event, sourcePlugin);
            }

            handler.handleMessageContext(event);

            // Log successful context menu usage
            if (event.isFromGuild()) {
                Guild guild = event.getGuild();
                if (guild != null) {
                    commandExecutionService.sendCommandLog(
                            guild,
                            "[Message Menu] " + commandName,
                            targetInfo,
                            event.getUser().getId(),
                            event.getUser().getName(),
                            event.getMessageChannel(),
                            true
                    );
                }
            }
        } catch (Exception e) {
            logger.error("Error handling message context menu {}: {}", commandName, e.getMessage(), e);

            // Log failed context menu usage
            if (event.isFromGuild()) {
                Guild guild = event.getGuild();
                if (guild != null) {
                    commandExecutionService.sendCommandLog(
                            guild,
                            "[Message Menu] " + commandName,
                            targetInfo,
                            event.getUser().getId(),
                            event.getUser().getName(),
                            event.getMessageChannel(),
                            false
                    );
                }
            }

            if (!event.isAcknowledged()) {
                event.reply("An error occurred while processing this action.")
                        .setEphemeral(true)
                        .queue();
            }
        }
    }

    // =====================================================
    // Buttons
    // =====================================================

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        ButtonHandler handler = interactionManager.getButtonHandler(buttonId);

        if (handler == null) {
            logger.debug("No handler found for button: {}", buttonId);
            return;
        }

        try {
            logger.debug("Handling button interaction: {}", buttonId);

            // SECURITY: Dispatch event scoped to the owning plugin's listeners only.
            String sourcePlugin = interactionManager.getPluginForButton(buttonId);
            if (sourcePlugin != null) {
                pluginEventManager.dispatchEvent(event, sourcePlugin);
            }

            handler.handle(event);
        } catch (Exception e) {
            logger.error("Error handling button {}: {}", buttonId, e.getMessage(), e);
            if (!event.isAcknowledged()) {
                event.reply("An error occurred while processing this button.")
                        .setEphemeral(true)
                        .queue();
            }
        }
    }

    // =====================================================
    // Select Menus
    // =====================================================

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        String menuId = event.getComponentId();
        SelectMenuHandler handler = interactionManager.getSelectMenuHandler(menuId);

        if (handler == null) {
            logger.debug("No handler found for string select menu: {}", menuId);
            return;
        }

        try {
            logger.debug("Handling string select interaction: {}", menuId);

            // SECURITY: Dispatch event scoped to the owning plugin's listeners only.
            String sourcePlugin = interactionManager.getPluginForSelectMenu(menuId);
            if (sourcePlugin != null) {
                pluginEventManager.dispatchEvent(event, sourcePlugin);
            }

            handler.handleStringSelect(event);
        } catch (Exception e) {
            logger.error("Error handling string select {}: {}", menuId, e.getMessage(), e);
            if (!event.isAcknowledged()) {
                event.reply("An error occurred while processing this selection.")
                        .setEphemeral(true)
                        .queue();
            }
        }
    }

    @Override
    public void onEntitySelectInteraction(@NotNull EntitySelectInteractionEvent event) {
        String menuId = event.getComponentId();
        SelectMenuHandler handler = interactionManager.getSelectMenuHandler(menuId);

        if (handler == null) {
            logger.debug("No handler found for entity select menu: {}", menuId);
            return;
        }

        try {
            logger.debug("Handling entity select interaction: {}", menuId);

            // SECURITY: Dispatch event scoped to the owning plugin's listeners only.
            String sourcePlugin = interactionManager.getPluginForSelectMenu(menuId);
            if (sourcePlugin != null) {
                pluginEventManager.dispatchEvent(event, sourcePlugin);
            }

            handler.handleEntitySelect(event);
        } catch (Exception e) {
            logger.error("Error handling entity select {}: {}", menuId, e.getMessage(), e);
            if (!event.isAcknowledged()) {
                event.reply("An error occurred while processing this selection.")
                        .setEphemeral(true)
                        .queue();
            }
        }
    }

    // =====================================================
    // Modals
    // =====================================================

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        String modalId = event.getModalId();
        ModalHandler handler = interactionManager.getModalHandler(modalId);

        if (handler == null) {
            logger.debug("No handler found for modal: {}", modalId);
            return;
        }

        try {
            logger.debug("Handling modal submission: {}", modalId);

            // SECURITY: Dispatch event scoped to the owning plugin's listeners only.
            String sourcePlugin = interactionManager.getPluginForModal(modalId);
            if (sourcePlugin != null) {
                pluginEventManager.dispatchEvent(event, sourcePlugin);
            }

            handler.handle(event);
        } catch (Exception e) {
            logger.error("Error handling modal {}: {}", modalId, e.getMessage(), e);
            if (!event.isAcknowledged()) {
                event.reply("An error occurred while processing your submission.")
                        .setEphemeral(true)
                        .queue();
            }
        }
    }

    // =====================================================
    // Autocomplete
    // =====================================================

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
        String commandName = event.getName();
        String optionName = event.getFocusedOption().getName();

        AutoCompleteHandler handler = interactionManager.getAutoCompleteHandler(commandName, optionName);

        if (handler == null) {
            logger.debug("No autocomplete handler for {}.{}", commandName, optionName);
            return;
        }

        try {
            logger.debug("Handling autocomplete: {}.{} = '{}'",
                    commandName, optionName, event.getFocusedOption().getValue());

            // SECURITY: Dispatch event scoped to the owning plugin's listeners only.
            String sourcePlugin = interactionManager.getPluginForAutoComplete(commandName, optionName);
            if (sourcePlugin != null) {
                pluginEventManager.dispatchEvent(event, sourcePlugin);
            }

            handler.handle(event);
        } catch (Exception e) {
            logger.error("Error handling autocomplete {}.{}: {}", commandName, optionName, e.getMessage(), e);
            // Autocomplete errors are silent - just don't provide suggestions
            event.replyChoices().queue();
        }
    }
}