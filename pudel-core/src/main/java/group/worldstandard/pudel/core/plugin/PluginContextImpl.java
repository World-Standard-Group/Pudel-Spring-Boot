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
package group.worldstandard.pudel.core.plugin;

import group.worldstandard.pudel.api.PluginInfo;
import group.worldstandard.pudel.api.PudelProperties;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import group.worldstandard.pudel.api.PluginContext;
import group.worldstandard.pudel.api.agent.AgentToolRegistry;
import group.worldstandard.pudel.api.audio.VoiceManager;
import group.worldstandard.pudel.api.command.TextCommandHandler;
import group.worldstandard.pudel.api.database.PluginDatabaseManager;
import group.worldstandard.pudel.api.event.EventManager;
import group.worldstandard.pudel.api.event.Listener;
import group.worldstandard.pudel.api.event.PluginEventListener;
import group.worldstandard.pudel.api.interaction.InteractionManager;
import group.worldstandard.pudel.core.command.CommandRegistry;
import group.worldstandard.pudel.core.database.PluginDatabaseService;
import group.worldstandard.pudel.core.event.PluginEventManager;

/**
 * Implementation of PluginContext.
 * Each plugin gets its own context instance with its plugin name.
 */
public class PluginContextImpl implements PluginContext {
    private static final Logger logger = LoggerFactory.getLogger(PluginContextImpl.class);

    private final PluginInfo info;
    private final PudelProperties pudel;
    private final JDA jda;
    private final CommandRegistry commandRegistry;
    private final PluginEventManager eventManager;
    private final VoiceManager voiceManager;
    private final AgentToolRegistry agentToolRegistry;
    private final InteractionManager interactionManager;
    private final PluginDatabaseService databaseService;

    // Lazily initialized database manager
    private volatile PluginDatabaseManager databaseManager;

    public PluginContextImpl(PluginInfo info, PudelProperties pudel, JDA jda, CommandRegistry commandRegistry,
                             PluginEventManager eventManager, VoiceManager voiceManager,
                             AgentToolRegistry agentToolRegistry, InteractionManager interactionManager,
                             PluginDatabaseService databaseService) {
        this.info = info;
        this.pudel = pudel;
        this.jda = jda;
        this.commandRegistry = commandRegistry;
        this.eventManager = eventManager;
        this.voiceManager = voiceManager;
        this.agentToolRegistry = agentToolRegistry;
        this.interactionManager = interactionManager;
        this.databaseService = databaseService;
    }

    private String getPluginName() {
        return info.getName();
    }

    private String getPluginVersion() {
        return info.getVersion();
    }

    @Override
    public PluginInfo getInfo(){
        return info;
    }

    @Override
    public PudelProperties getPudel() {
        return pudel;
    }

    @Override
    public JDA getJDA() {
        return jda;
    }

    @Override
    public User getBotUser() {
        return jda.getSelfUser();
    }

    @Override
    public Guild getGuild(long guildId) {
        return jda.getGuildById(guildId);
    }

    @Override
    public void registerCommand(String commandName, TextCommandHandler handler) {
        if (commandName == null || handler == null) {
            logger.warn("Attempt to register command with null name or handler");
            return;
        }
        commandRegistry.registerCommand(commandName, handler);
        logger.info("[{}] Command registered: {}", getPluginName(), commandName);
    }

    @Override
    public void unregisterCommand(String commandName) {
        if (commandName == null) {
            return;
        }
        commandRegistry.unregisterCommand(commandName);
        logger.info("[{}] Command unregistered: {}", getPluginName(), commandName);
    }

    @Override
    public TextCommandHandler getCommand(String commandName) {
        if (commandName == null) {
            return null;
        }
        return commandRegistry.getCommand(commandName);
    }

    // ============== Event Management ==============

    @Override
    public EventManager getEventManager() {
        return eventManager;
    }

    @Override
    public void registerListener(Listener listener) {
        if (listener == null) {
            logger.warn("[{}] Attempt to register null listener", getPluginName());
            return;
        }
        eventManager.registerListener(listener, getPluginName());
    }

    @Override
    public <T extends GenericEvent> void registerEventListener(PluginEventListener<T> listener) {
        if (listener == null) {
            logger.warn("[{}] Attempt to register null event listener", getPluginName());
            return;
        }
        eventManager.registerEventListener(listener, getPluginName());
    }

    @Override
    public void unregisterListener(Listener listener) {
        if (listener != null) {
            eventManager.unregisterListener(listener);
        }
    }

    @Override
    public <T extends GenericEvent> void unregisterEventListener(PluginEventListener<T> listener) {
        if (listener != null) {
            eventManager.unregisterEventListener(listener);
        }
    }

    // ============== Voice Management ==============

    @Override
    public VoiceManager getVoiceManager() {
        return voiceManager;
    }

    // ============== Agent Tools ==============

    @Override
    public AgentToolRegistry getAgentToolRegistry() {
        return agentToolRegistry;
    }

    // ============== Interactions ==============

    @Override
    public InteractionManager getInteractionManager() {
        return interactionManager;
    }

    // ============== Database ==============

    @Override
    public PluginDatabaseManager getDatabaseManager() {
        // Lazy initialization to avoid database calls until actually needed
        if (databaseManager == null) {
            synchronized (this) {
                if (databaseManager == null) {
                    PluginDatabaseManager newManager = databaseService.getManagerForPlugin(getPluginName(), getPluginVersion());

                    logger.debug("[{}] Database manager initialized with prefix: {}",
                            getPluginName(), newManager.getPrefix());
                    databaseManager = newManager;
                }
            }
        }
        return databaseManager;
    }

    // ============== Logging ==============

    @Override
    public void log(String level, String message) {
        log(level, message, null);
    }

    @Override
    public void log(String level, String message, Throwable throwable) {
        if (message == null) {
            return;
        }

        String formattedMessage = "[" + getPluginName() + "] " + message;

        switch (level.toLowerCase()) {
            case "debug" -> logger.debug(formattedMessage, throwable);
            case "info" -> logger.info(formattedMessage, throwable);
            case "warn" -> logger.warn(formattedMessage, throwable);
            case "error" -> logger.error(formattedMessage, throwable);
            default -> logger.info(formattedMessage, throwable);
        }
    }
}

