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
package worldstandard.group.pudel.core.plugin;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import worldstandard.group.pudel.api.PluginContext;
import worldstandard.group.pudel.api.audio.VoiceManager;
import worldstandard.group.pudel.api.command.TextCommandHandler;
import worldstandard.group.pudel.api.event.EventManager;
import worldstandard.group.pudel.api.event.Listener;
import worldstandard.group.pudel.api.event.PluginEventListener;
import worldstandard.group.pudel.core.command.CommandRegistry;
import worldstandard.group.pudel.core.event.PluginEventManager;

/**
 * Implementation of PluginContext.
 * Each plugin gets its own context instance with its plugin name.
 */
public class PluginContextImpl implements PluginContext {
    private static final Logger logger = LoggerFactory.getLogger(PluginContextImpl.class);

    private final String pluginName;
    private final JDA jda;
    private final CommandRegistry commandRegistry;
    private final PluginEventManager eventManager;
    private final VoiceManager voiceManager;

    public PluginContextImpl(String pluginName, JDA jda, CommandRegistry commandRegistry,
                             PluginEventManager eventManager, VoiceManager voiceManager) {
        this.pluginName = pluginName;
        this.jda = jda;
        this.commandRegistry = commandRegistry;
        this.eventManager = eventManager;
        this.voiceManager = voiceManager;
    }

    @Override
    public String getPluginName() {
        return pluginName;
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
        logger.info("[{}] Command registered: {}", pluginName, commandName);
    }

    @Override
    public void unregisterCommand(String commandName) {
        if (commandName == null) {
            return;
        }
        commandRegistry.unregisterCommand(commandName);
        logger.info("[{}] Command unregistered: {}", pluginName, commandName);
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
            logger.warn("[{}] Attempt to register null listener", pluginName);
            return;
        }
        eventManager.registerListener(listener, pluginName);
    }

    @Override
    public <T extends GenericEvent> void registerEventListener(PluginEventListener<T> listener) {
        if (listener == null) {
            logger.warn("[{}] Attempt to register null event listener", pluginName);
            return;
        }
        eventManager.registerEventListener(listener, pluginName);
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

        String formattedMessage = "[" + pluginName + "] " + message;

        switch (level.toLowerCase()) {
            case "debug" -> logger.debug(formattedMessage, throwable);
            case "info" -> logger.info(formattedMessage, throwable);
            case "warn" -> logger.warn(formattedMessage, throwable);
            case "error" -> logger.error(formattedMessage, throwable);
            default -> logger.info(formattedMessage, throwable);
        }
    }
}

