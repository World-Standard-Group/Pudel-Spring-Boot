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

/**
 * Base interface for all Pudel plugins.
 * Plugins must implement this interface to be recognized and loaded by the plugin system.
 *
 * <h2>Quick Start - Minimal Plugin:</h2>
 * <pre>{@code
 * public class MyPlugin implements PudelPlugin {
 *     @Override
 *     public PluginInfo getPluginInfo() {
 *         return new PluginInfo("MyPlugin", "1.0", "Author", "Description");
 *     }
 *
 *     @Override
 *     public void onEnable(PluginContext ctx) {
 *         ctx.registerCommand("hello", c -> c.reply("Hello!"));
 *     }
 * }
 * }</pre>
 *
 * <p>All lifecycle methods have default empty implementations except {@link #getPluginInfo()}.
 * Only override the methods you need.</p>
 *
 * @see SimplePlugin For an even simpler approach using annotations
 */
public interface PudelPlugin {

    /**
     * Gets the plugin information.
     * This is the only required method.
     *
     * @return plugin metadata
     */
    PluginInfo getPluginInfo();

    /**
     * Initializes the plugin.
     * Called when the plugin is first loaded, before enable.
     * Use this for one-time setup (database connections, heavy resources).
     *
     * @param context the plugin context containing access to bot services
     */
    default void initialize(PluginContext context) {
        // Default: no initialization needed
    }

    /**
     * Called when the plugin is enabled at runtime.
     * Register commands, listeners, and start services here.
     *
     * @param context the plugin context
     */
    default void onEnable(PluginContext context) {
        // Default: no enable logic
    }

    /**
     * Called when the plugin is disabled at runtime.
     * Unregister commands and pause services here.
     * Plugin may be re-enabled later.
     *
     * @param context the plugin context
     */
    default void onDisable(PluginContext context) {
        // Default: no disable logic
    }

    /**
     * Called when the plugin is being unloaded.
     * Clean up all resources here (close connections, stop threads).
     * Plugin will NOT be re-enabled after this.
     *
     * @param context the plugin context
     */
    default void shutdown(PluginContext context) {
        // Default: no cleanup needed
    }
}

