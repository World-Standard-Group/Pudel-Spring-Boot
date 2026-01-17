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
 * Base interface for plugin event listeners.
 * Plugins can implement this interface to receive JDA events.
 *
 * @param <T> the event type this listener handles
 */
public interface PluginEventListener<T extends GenericEvent> {

    /**
     * Gets the event class this listener handles.
     * @return the event class
     */
    Class<T> getEventClass();

    /**
     * Called when an event of the registered type occurs.
     * @param event the JDA event
     */
    void onEvent(T event);

    /**
     * Gets the priority of this listener.
     * Higher priority listeners are called first.
     * Default priority is 0.
     * @return the listener priority
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Whether this listener should ignore cancelled events.
     * Default is false.
     * @return true to ignore cancelled events
     */
    default boolean ignoreCancelled() {
        return false;
    }
}
