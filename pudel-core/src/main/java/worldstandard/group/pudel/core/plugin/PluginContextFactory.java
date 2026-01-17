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
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import worldstandard.group.pudel.api.PluginContext;
import worldstandard.group.pudel.api.audio.VoiceManager;
import worldstandard.group.pudel.core.command.CommandRegistry;
import worldstandard.group.pudel.core.event.PluginEventManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating plugin-specific contexts.
 * Each plugin gets its own context instance with its name.
 */
@Component
public class PluginContextFactory {

    private final JDA jda;
    private final CommandRegistry commandRegistry;
    private final PluginEventManager eventManager;
    private final VoiceManager voiceManager;
    private final Map<String, PluginContextImpl> contexts = new ConcurrentHashMap<>();

    public PluginContextFactory(@Lazy JDA jda, CommandRegistry commandRegistry,
                                PluginEventManager eventManager, @Lazy VoiceManager voiceManager) {
        this.jda = jda;
        this.commandRegistry = commandRegistry;
        this.eventManager = eventManager;
        this.voiceManager = voiceManager;
    }

    /**
     * Creates or retrieves a context for a specific plugin.
     *
     * @param pluginName the plugin name
     * @return the plugin context
     */
    public PluginContext getContext(String pluginName) {
        return contexts.computeIfAbsent(pluginName,
                name -> new PluginContextImpl(name, jda, commandRegistry, eventManager, voiceManager));
    }

    /**
     * Removes the context for a plugin.
     *
     * @param pluginName the plugin name
     */
    public void removeContext(String pluginName) {
        contexts.remove(pluginName);
    }

    /**
     * Gets the event manager.
     *
     * @return the plugin event manager
     */
    public PluginEventManager getEventManager() {
        return eventManager;
    }
}

