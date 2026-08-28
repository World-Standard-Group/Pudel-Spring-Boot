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
import group.worldstandard.pudel.core.service.GuildSettingsService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of InteractionManager for managing Discord interactions.
 */
@Component
public class InteractionManagerImpl implements InteractionManager {
    private static final Logger logger = LoggerFactory.getLogger(InteractionManagerImpl.class);

    private final JDA jda;
    private final GuildSettingsService guildSettingsService;

    // Slash commands: commandName -> handler
    private final Map<String, SlashCommandHandler> slashCommands = new ConcurrentHashMap<>();

    // Context menus: commandName -> handler
    private final Map<String, ContextMenuHandler> contextMenus = new ConcurrentHashMap<>();

    // Buttons: prefix -> handler
    private final Map<String, ButtonHandler> buttonHandlers = new ConcurrentHashMap<>();

    // Select menus: prefix -> handler
    private final Map<String, SelectMenuHandler> selectMenuHandlers = new ConcurrentHashMap<>();

    // Modals: prefix -> handler
    private final Map<String, ModalHandler> modalHandlers = new ConcurrentHashMap<>();

    // Autocomplete: "command:option" -> handler
    private final Map<String, AutoCompleteHandler> autoCompleteHandlers = new ConcurrentHashMap<>();

    // Track plugin ownership: pluginId -> Set<handlerKey>
    private final Map<String, Set<String>> pluginHandlers = new ConcurrentHashMap<>();

    public InteractionManagerImpl(@Lazy JDA jda,
                                  @Lazy GuildSettingsService guildSettingsService) {
        this.jda = jda;
        this.guildSettingsService = guildSettingsService;
    }

    // =====================================================
    // Slash Commands
    // =====================================================

    @Override
    public boolean registerSlashCommand(String pluginId, SlashCommandHandler handler) {
        String name = handler.getCommandData().getName();

        if (slashCommands.containsKey(name)) {
            logger.warn("Slash command '{}' already registered", name);
            return false;
        }

        slashCommands.put(name, handler);
        trackHandler(pluginId, "slash:" + name);
        logger.info("[{}] Registered slash command: /{}", pluginId, name);
        return true;
    }

    @Override
    public boolean unregisterSlashCommand(String commandName) {
        SlashCommandHandler removed = slashCommands.remove(commandName);
        if (removed != null) {
            untrackHandler("slash:" + commandName);
            logger.info("Unregistered slash command: /{}", commandName);
            return true;
        }
        return false;
    }

    @Override
    public SlashCommandHandler getSlashCommand(String commandName) {
        return slashCommands.get(commandName);
    }

    @Override
    public Collection<SlashCommandHandler> getAllSlashCommands() {
        return Collections.unmodifiableCollection(slashCommands.values());
    }

    // =====================================================
    // Context Menus
    // =====================================================

    @Override
    public boolean registerContextMenu(String pluginId, ContextMenuHandler handler) {
        String name = handler.getCommandData().getName();

        if (contextMenus.containsKey(name)) {
            logger.warn("Context menu '{}' already registered", name);
            return false;
        }

        contextMenus.put(name, handler);
        trackHandler(pluginId, "context:" + name);
        logger.info("[{}] Registered context menu: {}", pluginId, name);
        return true;
    }

    @Override
    public boolean unregisterContextMenu(String commandName) {
        ContextMenuHandler removed = contextMenus.remove(commandName);
        if (removed != null) {
            untrackHandler("context:" + commandName);
            logger.info("Unregistered context menu: {}", commandName);
            return true;
        }
        return false;
    }

    @Override
    public ContextMenuHandler getContextMenu(String commandName) {
        return contextMenus.get(commandName);
    }

    // =====================================================
    // Buttons
    // =====================================================

    @Override
    public boolean registerButtonHandler(String pluginId, ButtonHandler handler) {
        String prefix = handler.getButtonIdPrefix();

        if (buttonHandlers.containsKey(prefix)) {
            logger.warn("Button handler with prefix '{}' already registered", prefix);
            return false;
        }

        buttonHandlers.put(prefix, handler);
        trackHandler(pluginId, "button:" + prefix);
        logger.info("[{}] Registered button handler: {}", pluginId, prefix);
        return true;
    }

    @Override
    public boolean unregisterButtonHandler(String buttonIdPrefix) {
        ButtonHandler removed = buttonHandlers.remove(buttonIdPrefix);
        if (removed != null) {
            untrackHandler("button:" + buttonIdPrefix);
            logger.info("Unregistered button handler: {}", buttonIdPrefix);
            return true;
        }
        return false;
    }

    @Override
    public ButtonHandler getButtonHandler(String buttonId) {
        // Find handler by prefix match
        for (Map.Entry<String, ButtonHandler> entry : buttonHandlers.entrySet()) {
            if (buttonId.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    // =====================================================
    // Select Menus
    // =====================================================

    @Override
    public boolean registerSelectMenuHandler(String pluginId, SelectMenuHandler handler) {
        String prefix = handler.getSelectMenuIdPrefix();

        if (selectMenuHandlers.containsKey(prefix)) {
            logger.warn("Select menu handler with prefix '{}' already registered", prefix);
            return false;
        }

        selectMenuHandlers.put(prefix, handler);
        trackHandler(pluginId, "select:" + prefix);
        logger.info("[{}] Registered select menu handler: {}", pluginId, prefix);
        return true;
    }

    @Override
    public boolean unregisterSelectMenuHandler(String selectMenuIdPrefix) {
        SelectMenuHandler removed = selectMenuHandlers.remove(selectMenuIdPrefix);
        if (removed != null) {
            untrackHandler("select:" + selectMenuIdPrefix);
            logger.info("Unregistered select menu handler: {}", selectMenuIdPrefix);
            return true;
        }
        return false;
    }

    @Override
    public SelectMenuHandler getSelectMenuHandler(String selectMenuId) {
        for (Map.Entry<String, SelectMenuHandler> entry : selectMenuHandlers.entrySet()) {
            if (selectMenuId.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    // =====================================================
    // Modals
    // =====================================================

    @Override
    public boolean registerModalHandler(String pluginId, ModalHandler handler) {
        String prefix = handler.getModalIdPrefix();

        if (modalHandlers.containsKey(prefix)) {
            logger.warn("Modal handler with prefix '{}' already registered", prefix);
            return false;
        }

        modalHandlers.put(prefix, handler);
        trackHandler(pluginId, "modal:" + prefix);
        logger.info("[{}] Registered modal handler: {}", pluginId, prefix);
        return true;
    }

    @Override
    public boolean unregisterModalHandler(String modalIdPrefix) {
        ModalHandler removed = modalHandlers.remove(modalIdPrefix);
        if (removed != null) {
            untrackHandler("modal:" + modalIdPrefix);
            logger.info("Unregistered modal handler: {}", modalIdPrefix);
            return true;
        }
        return false;
    }

    @Override
    public ModalHandler getModalHandler(String modalId) {
        for (Map.Entry<String, ModalHandler> entry : modalHandlers.entrySet()) {
            if (modalId.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    // =====================================================
    // Autocomplete
    // =====================================================

    @Override
    public boolean registerAutoCompleteHandler(String pluginId, AutoCompleteHandler handler) {
        String key = handler.getCommandName() + ":" + handler.getOptionName();

        if (autoCompleteHandlers.containsKey(key)) {
            logger.warn("Autocomplete handler for '{}.{}' already registered",
                    handler.getCommandName(), handler.getOptionName());
            return false;
        }

        autoCompleteHandlers.put(key, handler);
        trackHandler(pluginId, "autocomplete:" + key);
        logger.info("[{}] Registered autocomplete handler: {}.{}",
                pluginId, handler.getCommandName(), handler.getOptionName());
        return true;
    }

    @Override
    public boolean unregisterAutoCompleteHandler(String commandName, String optionName) {
        String key = commandName + ":" + optionName;
        AutoCompleteHandler removed = autoCompleteHandlers.remove(key);
        if (removed != null) {
            untrackHandler("autocomplete:" + key);
            logger.info("Unregistered autocomplete handler: {}.{}", commandName, optionName);
            return true;
        }
        return false;
    }

    @Override
    public AutoCompleteHandler getAutoCompleteHandler(String commandName, String optionName) {
        return autoCompleteHandlers.get(commandName + ":" + optionName);
    }

    // =====================================================
    // Bulk Operations
    // =====================================================

    @Override
    public int unregisterAll(String pluginId) {
        Set<String> handlers = pluginHandlers.remove(pluginId);
        if (handlers == null || handlers.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (String key : handlers) {
            if (key.startsWith("slash:")) {
                if (slashCommands.remove(key.substring(6)) != null) count++;
            } else if (key.startsWith("context:")) {
                if (contextMenus.remove(key.substring(8)) != null) count++;
            } else if (key.startsWith("button:")) {
                if (buttonHandlers.remove(key.substring(7)) != null) count++;
            } else if (key.startsWith("select:")) {
                if (selectMenuHandlers.remove(key.substring(7)) != null) count++;
            } else if (key.startsWith("modal:")) {
                if (modalHandlers.remove(key.substring(6)) != null) count++;
            } else if (key.startsWith("autocomplete:")) {
                if (autoCompleteHandlers.remove(key.substring(13)) != null) count++;
            }
        }

        logger.info("Unregistered {} interaction handlers from plugin: {}", count, pluginId);
        return count;
    }

    @Override
    public CompletableFuture<Void> syncCommands() {
        if (jda == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("JDA not available"));
        }

        // Separate commands into global vs per-guild.
        // Global commands (core + plugins with global=true) → jda.updateCommands()
        // Non-global plugin commands → per-guild registration (supports guild-disable filtering)
        List<CommandData> globalCommands = new ArrayList<>();
        List<SlashCommandHandler> pluginSlashHandlers = new ArrayList<>();
        List<ContextMenuHandler> pluginContextHandlers = new ArrayList<>();

        for (SlashCommandHandler handler : slashCommands.values()) {
            if (handler.isGlobal()) {
                // Global commands: core commands AND plugin commands with global=true
                globalCommands.add(handler.getCommandData());
            } else {
                String cmdName = handler.getCommandData().getName();
                String pluginId = getPluginIdForCommand("slash:" + cmdName);
                if (!"core".equals(pluginId)) {
                    pluginSlashHandlers.add(handler);
                }
            }
        }

        for (ContextMenuHandler handler : contextMenus.values()) {
            if (handler.isGlobal()) {
                globalCommands.add(handler.getCommandData());
            } else {
                String cmdName = handler.getCommandData().getName();
                String pluginId = getPluginIdForCommand("context:" + cmdName);
                if (!"core".equals(pluginId)) {
                    pluginContextHandlers.add(handler);
                }
            }
        }

        // Register global commands (core + global plugins) via jda.updateCommands()
        CompletableFuture<Void> globalFuture;
        if (!globalCommands.isEmpty()) {
            logger.info("Syncing {} global commands to Discord...", globalCommands.size());
            globalFuture = jda.updateCommands()
                    .addCommands(globalCommands)
                    .submit()
                    .thenAccept(commands ->
                            logger.info("Synced {} global commands", commands.size()))
                    .exceptionally(e -> {
                        logger.error("Failed to sync global commands: {}", e.getMessage());
                        return null;
                    });
        } else {
            globalFuture = CompletableFuture.completedFuture(null);
        }

        // Register non-global plugin commands per-guild, filtering out disabled plugins
        List<CompletableFuture<Void>> guildFutures = new ArrayList<>();
        for (Guild guild : jda.getGuilds()) {
            long guildId = guild.getIdLong();
            String guildIdStr = guild.getId();

            // Get disabled plugins for this guild
            Set<String> disabledPlugins = getDisabledPluginsForGuild(guildIdStr);

            // Build command list for this guild
            List<CommandData> guildCmds = new ArrayList<>();

            for (SlashCommandHandler handler : pluginSlashHandlers) {
                String cmdName = handler.getCommandData().getName();
                String pluginId = getPluginIdForCommand("slash:" + cmdName);

                // Skip if this plugin is disabled for this guild
                if (pluginId != null && disabledPlugins.contains(pluginId)) {
                    continue;
                }

                // Check if this handler targets this specific guild
                long[] guildIds = handler.getGuildIds();
                if (guildIds != null && guildIds.length > 0 && !contains(guildIds, guildId)) {
                    continue;
                }

                guildCmds.add(handler.getCommandData());
            }

            for (ContextMenuHandler handler : pluginContextHandlers) {
                String cmdName = handler.getCommandData().getName();
                String pluginId = getPluginIdForCommand("context:" + cmdName);

                if (pluginId != null && disabledPlugins.contains(pluginId)) {
                    continue;
                }

                long[] guildIds = handler.getGuildIds();
                if (guildIds != null && guildIds.length > 0 && !contains(guildIds, guildId)) {
                    continue;
                }

                guildCmds.add(handler.getCommandData());
            }

            CompletableFuture<Void> future;
            if (!guildCmds.isEmpty()) {
                future = guild.updateCommands()
                        .addCommands(guildCmds)
                        .submit()
                        .thenAccept(commands ->
                                logger.debug("Synced {} plugin commands to guild {}", commands.size(), guild.getName()))
                        .exceptionally(e -> {
                            logger.error("Failed to sync plugin commands to guild {}: {}", guildId, e.getMessage());
                            return null;
                        });
            } else {
                // Clear any previously registered guild commands if no plugin commands remain
                future = guild.updateCommands()
                        .submit()
                        .thenAccept(commands ->
                                logger.debug("Cleared guild commands for {} (no active plugin commands)", guild.getName()))
                        .exceptionally(e -> {
                            logger.error("Failed to clear guild commands for {}: {}", guildId, e.getMessage());
                            return null;
                        });
            }
            guildFutures.add(future);
        }

        return CompletableFuture.allOf(
                globalFuture,
                CompletableFuture.allOf(guildFutures.toArray(new CompletableFuture[0]))
        );
    }

    @Override
    public CompletableFuture<Void> syncGuildCommands(long guildId) {
        if (jda == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("JDA not available"));
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Guild not found: " + guildId));
        }

        String guildIdStr = String.valueOf(guildId);
        Set<String> disabledPlugins = getDisabledPluginsForGuild(guildIdStr);

        List<CommandData> commands = new ArrayList<>();

        // Collect non-global plugin slash commands for this guild, filtering disabled plugins.
        // Global commands (core + global plugins) are already registered via jda.updateCommands().
        for (SlashCommandHandler handler : slashCommands.values()) {
            // Skip global commands — already registered globally
            if (handler.isGlobal()) {
                continue;
            }

            String cmdName = handler.getCommandData().getName();
            String pluginId = getPluginIdForCommand("slash:" + cmdName);

            // Skip if this plugin is disabled for this guild
            if (pluginId != null && disabledPlugins.contains(pluginId)) {
                continue;
            }

            // For guild-specific handlers, check targeting
            long[] guildIds = handler.getGuildIds();
            if (guildIds != null && guildIds.length > 0 && !contains(guildIds, guildId)) {
                continue;
            }

            commands.add(handler.getCommandData());
        }

        // Collect non-global plugin context menus for this guild
        for (ContextMenuHandler handler : contextMenus.values()) {
            // Skip global commands — already registered globally
            if (handler.isGlobal()) {
                continue;
            }

            String cmdName = handler.getCommandData().getName();
            String pluginId = getPluginIdForCommand("context:" + cmdName);

            if (pluginId != null && disabledPlugins.contains(pluginId)) {
                continue;
            }

            long[] guildIds = handler.getGuildIds();
            if (guildIds != null && guildIds.length > 0 && !contains(guildIds, guildId)) {
                continue;
            }

            commands.add(handler.getCommandData());
        }

        logger.info("Syncing {} non-global plugin commands to guild {} (disabled plugins: {})...",
                commands.size(), guild.getName(), disabledPlugins);

        return guild.updateCommands()
                .addCommands(commands)
                .submit()
                .thenAccept(cmds -> logger.info("Synced {} plugin commands to guild {}", cmds.size(), guild.getName()))
                .exceptionally(e -> {
                    logger.error("Failed to sync commands to guild {}: {}", guildId, e.getMessage());
                    return null;
                });
    }

    @Override
    public InteractionStats getStats() {
        return new InteractionStats(
                slashCommands.size(),
                contextMenus.size(),
                buttonHandlers.size(),
                selectMenuHandlers.size(),
                modalHandlers.size(),
                autoCompleteHandlers.size()
        );
    }

    @Override
    public CompletableFuture<Void> syncAllCommandsToGuild(long guildId) {
        if (jda == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("JDA not available"));
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Guild not found: " + guildId));
        }

        String guildIdStr = String.valueOf(guildId);
        Set<String> disabledPlugins = getDisabledPluginsForGuild(guildIdStr);

        List<CommandData> commands = new ArrayList<>();

        // Include ALL slash commands (both global and non-global) for guild-level registration.
        // Global commands can take up to 1 hour to propagate via Discord's global registration.
        // By also registering them as guild commands on join, they appear instantly.
        // Guild-level commands take priority over global commands when both exist.
        for (SlashCommandHandler handler : slashCommands.values()) {
            String cmdName = handler.getCommandData().getName();
            String pluginId = getPluginIdForCommand("slash:" + cmdName);

            // Skip if this plugin is disabled for this guild (but never skip core)
            if (pluginId != null && !"core".equals(pluginId) && disabledPlugins.contains(pluginId)) {
                continue;
            }

            // For guild-specific handlers, check targeting
            long[] guildIds = handler.getGuildIds();
            if (guildIds != null && guildIds.length > 0 && !contains(guildIds, guildId)) {
                continue;
            }

            commands.add(handler.getCommandData());
        }

        // Include ALL context menus (both global and non-global)
        for (ContextMenuHandler handler : contextMenus.values()) {
            String cmdName = handler.getCommandData().getName();
            String pluginId = getPluginIdForCommand("context:" + cmdName);

            if (pluginId != null && !"core".equals(pluginId) && disabledPlugins.contains(pluginId)) {
                continue;
            }

            long[] guildIds = handler.getGuildIds();
            if (guildIds != null && guildIds.length > 0 && !contains(guildIds, guildId)) {
                continue;
            }

            commands.add(handler.getCommandData());
        }

        logger.info("Syncing all {} commands (global + non-global) to newly joined guild {} ...",
                commands.size(), guild.getName());

        return guild.updateCommands()
                .addCommands(commands)
                .submit()
                .thenAccept(cmds -> logger.info("Synced all {} commands to guild {}",
                        cmds.size(), guild.getName()))
                .exceptionally(e -> {
                    logger.error("Failed to sync commands to guild {}: {}", guildId, e.getMessage());
                    return null;
                });
    }

    // =====================================================
    // Helper Methods
    // =====================================================

    private void trackHandler(String pluginId, String handlerKey) {
        pluginHandlers.computeIfAbsent(pluginId, k -> ConcurrentHashMap.newKeySet()).add(handlerKey);
    }

    private void untrackHandler(String handlerKey) {
        pluginHandlers.values().forEach(set -> set.remove(handlerKey));
    }

    /**
     * Get the plugin ID that owns a given handler key (e.g., "slash:music" or "context:Ban User").
     */
    private String getPluginIdForCommand(String handlerKey) {
        for (Map.Entry<String, Set<String>> entry : pluginHandlers.entrySet()) {
            if (entry.getValue().contains(handlerKey)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // =====================================================
    // Plugin Owner Resolution (for event source scoping)
    // =====================================================

    /**
     * Resolves the plugin that owns a slash command handler.
     *
     * @param commandName the slash command name
     * @return the plugin ID, or null if not found or built-in
     */
    public String getPluginForSlashCommand(String commandName) {
        return getPluginIdForCommand("slash:" + commandName);
    }

    /**
     * Resolves the plugin that owns a context menu handler.
     *
     * @param commandName the context menu command name
     * @return the plugin ID, or null if not found or built-in
     */
    public String getPluginForContextMenu(String commandName) {
        return getPluginIdForCommand("context:" + commandName);
    }

    /**
     * Resolves the plugin that owns a button handler by matching the button ID prefix.
     *
     * @param buttonId the full button component ID
     * @return the plugin ID, or null if not found
     */
    public String getPluginForButton(String buttonId) {
        for (Map.Entry<String, ButtonHandler> entry : buttonHandlers.entrySet()) {
            if (buttonId.startsWith(entry.getKey())) {
                String prefix = entry.getKey();
                return getPluginIdForCommand("button:" + prefix);
            }
        }
        return null;
    }

    /**
     * Resolves the plugin that owns a select menu handler by matching the menu ID prefix.
     *
     * @param selectMenuId the full select menu component ID
     * @return the plugin ID, or null if not found
     */
    public String getPluginForSelectMenu(String selectMenuId) {
        for (Map.Entry<String, SelectMenuHandler> entry : selectMenuHandlers.entrySet()) {
            if (selectMenuId.startsWith(entry.getKey())) {
                String prefix = entry.getKey();
                return getPluginIdForCommand("select:" + prefix);
            }
        }
        return null;
    }

    /**
     * Resolves the plugin that owns a modal handler by matching the modal ID prefix.
     *
     * @param modalId the full modal ID
     * @return the plugin ID, or null if not found
     */
    public String getPluginForModal(String modalId) {
        for (Map.Entry<String, ModalHandler> entry : modalHandlers.entrySet()) {
            if (modalId.startsWith(entry.getKey())) {
                String prefix = entry.getKey();
                return getPluginIdForCommand("modal:" + prefix);
            }
        }
        return null;
    }

    /**
     * Resolves the plugin that owns an autocomplete handler.
     *
     * @param commandName the command name
     * @param optionName  the autocomplete option name
     * @return the plugin ID, or null if not found
     */
    public String getPluginForAutoComplete(String commandName, String optionName) {
        return getPluginIdForCommand("autocomplete:" + commandName + ":" + optionName);
    }

    /**
     * Get the set of plugin names disabled for a given guild.
     */
    private Set<String> getDisabledPluginsForGuild(String guildId) {
        try {
            var settings = guildSettingsService.getGuildSettings(guildId);
            if (settings.isPresent()) {
                List<String> disabled = settings.get().getDisabledPluginsList();
                return new HashSet<>(disabled);
            }
        } catch (Exception e) {
            logger.warn("Failed to get disabled plugins for guild {}: {}", guildId, e.getMessage());
        }
        return Collections.emptySet();
    }

    private boolean contains(long[] array, long value) {
        for (long l : array) {
            if (l == value) return true;
        }
        return false;
    }
}