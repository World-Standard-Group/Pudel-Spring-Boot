/*
 * Pudel Plugin API (PDK) - Plugin Development Kit for Pudel Discord Bot
 * Copyright (c) 2026 Napapon Kamanee
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
package worldstandard.group.pudel.api;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import worldstandard.group.pudel.api.audio.VoiceManager;
import worldstandard.group.pudel.api.command.TextCommandHandler;
import worldstandard.group.pudel.api.event.EventManager;
import worldstandard.group.pudel.api.event.Listener;
import worldstandard.group.pudel.api.event.PluginEventListener;

/**
 * Context provided to plugins for accessing bot services and Discord API.
 * Plugins should use this context to interact with the bot and access shared services.
 */
public interface PluginContext {

    /**
     * Gets the JDA instance.
     * @return the JDA instance
     */
    JDA getJDA();

    /**
     * Gets the bot's JDA user.
     * @return the bot user
     */
    User getBotUser();

    /**
     * Gets a guild by ID.
     * @param guildId the guild ID
     * @return the guild or null if not found
     */
    Guild getGuild(long guildId);

    /**
     * Registers a command handler.
     * @param commandName the command name
     * @param handler the command handler
     */
    void registerCommand(String commandName, TextCommandHandler handler);

    /**
     * Unregisters a command handler.
     * @param commandName the command name
     */
    void unregisterCommand(String commandName);

    /**
     * Gets a registered command handler.
     * @param commandName the command name
     * @return the handler or null if not found
     */
    TextCommandHandler getCommand(String commandName);

    /**
     * Logs a message.
     * @param level the log level
     * @param message the message
     */
    void log(String level, String message);

    /**
     * Logs a message with an exception.
     * @param level the log level
     * @param message the message
     * @param throwable the exception
     */
    void log(String level, String message, Throwable throwable);

    // ============== Event Management ==============

    /**
     * Gets the event manager for registering event listeners.
     * @return the event manager
     */
    EventManager getEventManager();

    /**
     * Registers a listener object with annotated event handlers.
     * Convenience method for getEventManager().registerListener().
     * @param listener the listener object
     */
    void registerListener(Listener listener);

    /**
     * Registers a typed event listener.
     * Convenience method for getEventManager().registerEventListener().
     * @param listener the event listener
     * @param <T> the event type
     */
    <T extends GenericEvent> void registerEventListener(PluginEventListener<T> listener);

    /**
     * Unregisters a listener object.
     * @param listener the listener to unregister
     */
    void unregisterListener(Listener listener);

    /**
     * Unregisters a typed event listener.
     * @param listener the event listener to unregister
     * @param <T> the event type
     */
    <T extends GenericEvent> void unregisterEventListener(PluginEventListener<T> listener);

    /**
     * Gets the plugin name associated with this context.
     * @return the plugin name
     */
    String getPluginName();

    // ============== Voice/Audio Management ==============

    /**
     * Gets the voice manager for handling voice connections and audio.
     *
     * @return the voice manager
     */
    VoiceManager getVoiceManager();
}

