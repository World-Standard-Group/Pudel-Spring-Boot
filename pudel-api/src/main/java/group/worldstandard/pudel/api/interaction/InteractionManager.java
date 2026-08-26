/*
 * Pudel Plugin API (PDK) - Plugin Development Kit for Pudel Discord Bot
 * Copyright (c) 2026 World Standard Group
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package group.worldstandard.pudel.api.interaction;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Manager for Discord interactions (slash commands, buttons, modals, etc.).
 * <p>
 * Plugins use this manager to register and manage their interaction handlers.
 * <p>
 * Example usage in a plugin:
 * <pre>
 * {@code @Plugin(name = "MyPlugin", version = "1.0.0", author = "Author")
 * public class MyPlugin {
 *
 *     @OnEnable
 *     public void onEnable(PluginContext context) {
 *         InteractionManager manager = context.getInteractionManager();
 *
 *         // Register slash commands
 *         manager.registerSlashCommand("my-plugin", new PingCommand());
 *         manager.registerSlashCommand("my-plugin", new WeatherCommand());
 *
 *         // Register button handlers
 *         manager.registerButtonHandler("my-plugin", new ConfirmButtonHandler());
 *
 *         // Register modal handlers
 *         manager.registerModalHandler("my-plugin", new FeedbackModalHandler());
 *
 *         // Sync commands to Discord (call once after registering all commands)
 *         manager.syncCommands();
 *     }
 *
 *     @OnDisable
 *     public void onDisable(PluginContext context) {
 *         // Unregister all handlers for this plugin
 *         context.getInteractionManager().unregisterAll("my-plugin");
 *     }
 * }
 * }
 * </pre>
 */
public interface InteractionManager {
    // =====================================================
    // Slash Commands
    // =====================================================

    /**
     * Register a slash command handler.
     *
     * @param pluginId the plugin identifier
     * @param handler the slash command handler
     * @return true if registered successfully
     */
    boolean registerSlashCommand(String pluginId, SlashCommandHandler handler);

    /**
     * Unregister a slash command by name.
     *
     * @param commandName the command name
     * @return true if unregistered successfully
     */
    boolean unregisterSlashCommand(String commandName);

    /**
     * Get a registered slash command handler.
     *
     * @param commandName the command name
     * @return the handler or null if not found
     */
    SlashCommandHandler getSlashCommand(String commandName);

    /**
     * Get all registered slash commands.
     *
     * @return collection of slash command handlers
     */
    Collection<SlashCommandHandler> getAllSlashCommands();

    // =====================================================
    // Context Menus
    // =====================================================

    /**
     * Register a context menu handler.
     *
     * @param pluginId the plugin identifier
     * @param handler the context menu handler
     * @return true if registered successfully
     */
    boolean registerContextMenu(String pluginId, ContextMenuHandler handler);

    /**
     * Unregister a context menu by name.
     *
     * @param commandName the command name
     * @return true if unregistered successfully
     */
    boolean unregisterContextMenu(String commandName);

    /**
     * Get a registered context menu handler.
     *
     * @param commandName the command name
     * @return the handler or null if not found
     */
    ContextMenuHandler getContextMenu(String commandName);

    // =====================================================
    // Buttons
    // =====================================================

    /**
     * Register a button handler.
     *
     * @param pluginId the plugin identifier
     * @param handler the button handler
     * @return true if registered successfully
     */
    boolean registerButtonHandler(String pluginId, ButtonHandler handler);

    /**
     * Unregister a button handler by prefix.
     *
     * @param buttonIdPrefix the button ID prefix
     * @return true if unregistered successfully
     */
    boolean unregisterButtonHandler(String buttonIdPrefix);

    /**
     * Get button handler for a button ID.
     *
     * @param buttonId the full button ID
     * @return the handler or null if not found
     */
    ButtonHandler getButtonHandler(String buttonId);

    // =====================================================
    // Select Menus
    // =====================================================

    /**
     * Register a select menu handler.
     *
     * @param pluginId the plugin identifier
     * @param handler the select menu handler
     * @return true if registered successfully
     */
    boolean registerSelectMenuHandler(String pluginId, SelectMenuHandler handler);

    /**
     * Unregister a select menu handler by prefix.
     *
     * @param selectMenuIdPrefix the select menu ID prefix
     * @return true if unregistered successfully
     */
    boolean unregisterSelectMenuHandler(String selectMenuIdPrefix);

    /**
     * Get select menu handler for a menu ID.
     *
     * @param selectMenuId the full select menu ID
     * @return the handler or null if not found
     */
    SelectMenuHandler getSelectMenuHandler(String selectMenuId);

    // =====================================================
    // Modals
    // =====================================================

    /**
     * Register a modal handler.
     *
     * @param pluginId the plugin identifier
     * @param handler the modal handler
     * @return true if registered successfully
     */
    boolean registerModalHandler(String pluginId, ModalHandler handler);

    /**
     * Unregister a modal handler by prefix.
     *
     * @param modalIdPrefix the modal ID prefix
     * @return true if unregistered successfully
     */
    boolean unregisterModalHandler(String modalIdPrefix);

    /**
     * Get modal handler for a modal ID.
     *
     * @param modalId the full modal ID
     * @return the handler or null if not found
     */
    ModalHandler getModalHandler(String modalId);

    // =====================================================
    // Autocomplete
    // =====================================================

    /**
     * Register an autocomplete handler.
     *
     * @param pluginId the plugin identifier
     * @param handler the autocomplete handler
     * @return true if registered successfully
     */
    boolean registerAutoCompleteHandler(String pluginId, AutoCompleteHandler handler);

    /**
     * Unregister an autocomplete handler.
     *
     * @param commandName the command name
     * @param optionName the option name
     * @return true if unregistered successfully
     */
    boolean unregisterAutoCompleteHandler(String commandName, String optionName);

    /**
     * Get autocomplete handler for a command option.
     *
     * @param commandName the command name
     * @param optionName the option name
     * @return the handler or null if not found
     */
    AutoCompleteHandler getAutoCompleteHandler(String commandName, String optionName);

    // =====================================================
    // Bulk Operations
    // =====================================================

    /**
     * Unregister all handlers from a plugin.
     *
     * @param pluginId the plugin identifier
     * @return number of handlers unregistered
     */
    int unregisterAll(String pluginId);

    /**
     * Sync all registered slash commands and context menus to Discord.
     * <p>
     * Call this after registering all your commands.
     * Global commands may take up to 1 hour to appear.
     * Guild commands are instant.
     *
     * @return future that completes when sync is done
     */
    CompletableFuture<Void> syncCommands();

    /**
     * Sync commands for a specific guild.
     *
     * @param guildId the guild ID
     * @return future that completes when sync is done
     */
    CompletableFuture<Void> syncGuildCommands(long guildId);

    /**
     * Sync all commands (core + plugin) to a specific guild as guild-level commands.
     * <p>
     * This ensures commands are available instantly when the bot joins a new guild,
     * instead of waiting up to 1 hour for global command propagation.
     *
     * @param guildId the guild ID
     * @return future that completes when sync is done
     */
    CompletableFuture<Void> syncAllCommandsToGuild(long guildId);

    /**
     * Get statistics about registered handlers.
     *
     * @return interaction stats
     */
    InteractionStats getStats();

    /**
     * Statistics about registered interaction handlers.
     *
     * @param slashCommandCount        number of registered slash commands
     * @param contextMenuCount         number of registered context menus
     * @param buttonHandlerCount       number of registered button handlers
     * @param selectMenuHandlerCount   number of registered select-menu handlers
     * @param modalHandlerCount        number of registered modal handlers
     * @param autoCompleteHandlerCount number of registered auto-complete handlers
     */
    record InteractionStats(
            int slashCommandCount,
            int contextMenuCount,
            int buttonHandlerCount,
            int selectMenuHandlerCount,
            int modalHandlerCount,
            int autoCompleteHandlerCount
    ) {
        public int totalHandlers() {
            return slashCommandCount + contextMenuCount + buttonHandlerCount +
                   selectMenuHandlerCount + modalHandlerCount + autoCompleteHandlerCount;
        }
    }
}