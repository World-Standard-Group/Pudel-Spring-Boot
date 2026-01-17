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
package worldstandard.group.pudel.api.event;

import net.dv8tion.jda.api.events.GenericEvent;

/**
 * Interface for managing plugin event listeners.
 * Provides methods for registering and unregistering event listeners.
 */
public interface EventManager {

    /**
     * Registers all event handlers in a listener object.
     * Methods annotated with {@link EventHandler} will be automatically registered.
     *
     * @param listener the listener object containing event handler methods
     * @param pluginName the name of the plugin registering the listener
     */
    void registerListener(Listener listener, String pluginName);

    /**
     * Registers a single typed event listener.
     *
     * @param listener the event listener
     * @param pluginName the name of the plugin registering the listener
     * @param <T> the event type
     */
    <T extends GenericEvent> void registerEventListener(PluginEventListener<T> listener, String pluginName);

    /**
     * Unregisters all listeners for a specific plugin.
     *
     * @param pluginName the name of the plugin
     */
    void unregisterListeners(String pluginName);

    /**
     * Unregisters a specific listener object.
     *
     * @param listener the listener to unregister
     */
    void unregisterListener(Listener listener);

    /**
     * Unregisters a specific typed event listener.
     *
     * @param listener the event listener to unregister
     * @param <T> the event type
     */
    <T extends GenericEvent> void unregisterEventListener(PluginEventListener<T> listener);

    /**
     * Gets the number of registered listeners for a plugin.
     *
     * @param pluginName the plugin name
     * @return the number of registered listeners
     */
    int getListenerCount(String pluginName);

    /**
     * Gets the total number of registered event handlers.
     *
     * @return the total number of event handlers
     */
    int getTotalHandlerCount();
}

