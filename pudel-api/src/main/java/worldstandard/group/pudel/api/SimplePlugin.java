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

import worldstandard.group.pudel.api.command.TextCommandHandler;
import worldstandard.group.pudel.api.event.Listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for simplified plugin development.
 *
 * <p>This class handles common boilerplate like command/listener registration
 * and provides convenient helper methods.</p>
 *
 * <h2>Example - Minimal Plugin:</h2>
 * <pre>{@code
 * public class PingPlugin extends SimplePlugin {
 *
 *     public PingPlugin() {
 *         super("PingPlugin", "1.0", "Author", "A simple ping command");
 *     }
 *
 *     @Override
 *     protected void setup() {
 *         command("ping", ctx -> ctx.reply("Pong! 🏓"));
 *         command("echo", ctx -> ctx.reply(ctx.getArgs()));
 *     }
 * }
 * }</pre>
 *
 * <h2>Example - With Listeners:</h2>
 * <pre>{@code
 * public class WelcomePlugin extends SimplePlugin implements Listener {
 *
 *     public WelcomePlugin() {
 *         super("WelcomePlugin", "1.0", "Author", "Welcome new members");
 *     }
 *
 *     @Override
 *     protected void setup() {
 *         listener(this);
 *     }
 *
 *     @EventHandler
 *     public void onMemberJoin(GuildMemberJoinEvent event) {
 *         event.getGuild().getDefaultChannel()
 *             .sendMessage("Welcome " + event.getMember().getAsMention() + "!")
 *             .queue();
 *     }
 * }
 * }</pre>
 */
public abstract class SimplePlugin implements PudelPlugin {

    private final PluginInfo info;
    private PluginContext context;
    private final Map<String, TextCommandHandler> commands = new HashMap<>();
    private final List<Listener> listeners = new ArrayList<>();
    private boolean enabled = false;

    /**
     * Create a simple plugin with the given metadata.
     *
     * @param name        Plugin name
     * @param version     Plugin version
     * @param author      Plugin author
     * @param description Plugin description
     */
    protected SimplePlugin(String name, String version, String author, String description) {
        this.info = new PluginInfo(name, version, author, description);
    }

    @Override
    public final PluginInfo getPluginInfo() {
        return info;
    }

    @Override
    public final void onEnable(PluginContext context) {
        this.context = context;
        this.enabled = true;

        // Let subclass register commands/listeners
        setup();

        // Register all commands
        for (Map.Entry<String, TextCommandHandler> entry : commands.entrySet()) {
            context.registerCommand(entry.getKey(), entry.getValue());
        }

        // Register all listeners
        for (Listener listener : listeners) {
            context.registerListener(listener);
        }

        // Call optional post-setup hook
        onEnabled();
    }

    @Override
    public final void onDisable(PluginContext context) {
        // Unregister all commands
        for (String cmd : commands.keySet()) {
            context.unregisterCommand(cmd);
        }

        // Unregister all listeners
        for (Listener listener : listeners) {
            context.unregisterListener(listener);
        }

        this.enabled = false;

        // Call optional cleanup hook
        onDisabled();
    }

    // ============== Override These ==============

    /**
     * Called during enable to set up commands and listeners.
     * Use {@link #command(String, TextCommandHandler)} and {@link #listener(Listener)}.
     */
    protected abstract void setup();

    /**
     * Optional hook called after setup completes.
     * Override for additional enable logic.
     */
    protected void onEnabled() {
        // Override if needed
    }

    /**
     * Optional hook called during disable.
     * Override for additional cleanup logic.
     */
    protected void onDisabled() {
        // Override if needed
    }

    // ============== Helper Methods ==============

    /**
     * Register a command. Call this in {@link #setup()}.
     *
     * @param name    Command name (without prefix)
     * @param handler Command handler
     */
    protected final void command(String name, TextCommandHandler handler) {
        commands.put(name.toLowerCase(), handler);
    }

    /**
     * Register a command with aliases. Call this in {@link #setup()}.
     *
     * @param handler Command handler
     * @param names   Command name and aliases
     */
    protected final void command(TextCommandHandler handler, String... names) {
        for (String name : names) {
            commands.put(name.toLowerCase(), handler);
        }
    }

    /**
     * Register an event listener. Call this in {@link #setup()}.
     *
     * @param listener The listener (often 'this')
     */
    protected final void listener(Listener listener) {
        listeners.add(listener);
    }

    /**
     * Get the plugin context.
     *
     * @return plugin context, or null if not yet enabled
     */
    protected final PluginContext getContext() {
        return context;
    }

    /**
     * Log a message.
     *
     * @param level   Log level (info, warn, error, debug)
     * @param message Log message
     */
    protected final void log(String level, String message) {
        if (context != null) {
            context.log(level, message);
        }
    }

    /**
     * Log a message with throwable.
     *
     * @param level   Log level (info, warn, error, debug)
     * @param message Log message
     * @param ex Throwable object
     */
    protected final void log(String level, String message, Throwable ex) {
        if (context != null) {
            context.log(level, message, ex);
        }
    }

    /**
     * Check if the plugin is currently enabled.
     *
     * @return true if enabled
     */
    protected final boolean isEnabled() {
        return enabled;
    }

    /**
     * Get the plugin name.
     *
     * @return plugin name
     */
    protected final String getName() {
        return info.getName();
    }
}

