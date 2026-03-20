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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages dynamic Logback logger registration for plugin packages.
 * <p>
 * When a plugin is loaded, its main class package is detected and a
 * Logback logger is programmatically registered at DEBUG level. This
 * ensures that third-party plugin developers using arbitrary package
 * names (e.g., {@code com.myplugin.bot}, {@code org.example.discord})
 * have their DEBUG-level logs captured by the {@link group.worldstandard.pudel.core.service.InMemoryLogAppender}
 * and streamed to the admin log viewer.
 * <p>
 * Without this, plugin packages would inherit the root logger's INFO
 * level and all DEBUG logs from plugins would be silently dropped.
 * <p>
 * Loggers are automatically cleaned up when the plugin is unloaded.
 *
 * @see group.worldstandard.pudel.core.service.InMemoryLogAppender
 */
public final class PluginLoggerManager {

    private static final Logger logger = LoggerFactory.getLogger(PluginLoggerManager.class);

    /** Default log level for dynamically registered plugin loggers. */
    private static final Level DEFAULT_PLUGIN_LOG_LEVEL = Level.DEBUG;

    /**
     * Tracks registered plugin loggers: pluginName → set of package names.
     * A plugin may have multiple packages registered (e.g., base package + sub-packages).
     */
    private static final Map<String, Set<String>> PLUGIN_LOGGERS = new ConcurrentHashMap<>();

    private PluginLoggerManager() {
        // Utility class
    }

    /**
     * Register a dynamic logger for a plugin's package based on its main class.
     * <p>
     * Extracts the package name from the fully-qualified main class name
     * and creates a Logback logger at DEBUG level for that package.
     * This allows all classes within the plugin's package hierarchy to
     * have their logs captured at DEBUG level.
     *
     * @param pluginName    the plugin's display name (used for tracking)
     * @param mainClassName the fully-qualified main class name (e.g., {@code com.myplugin.MyPlugin})
     */
    public static void registerPluginLogger(String pluginName, String mainClassName) {
        if (pluginName == null || mainClassName == null) {
            return;
        }

        String packageName = extractPackageName(mainClassName);
        if (packageName == null || packageName.isEmpty()) {
            logger.warn("Could not extract package name from main class '{}' for plugin '{}'",
                    mainClassName, pluginName);
            return;
        }

        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

            // Register logger for the plugin's package (covers direct SLF4J usage within plugin code)
            ch.qos.logback.classic.Logger pluginLogger = loggerContext.getLogger(packageName);
            pluginLogger.setLevel(DEFAULT_PLUGIN_LOG_LEVEL);

            // Also register the "Plugin.<name>" logger used by PluginContext.log()
            // so that context.log("debug", ...) calls are not silently dropped.
            String contextLoggerName = "Plugin." + pluginName;
            ch.qos.logback.classic.Logger contextLogger = loggerContext.getLogger(contextLoggerName);
            contextLogger.setLevel(DEFAULT_PLUGIN_LOG_LEVEL);

            Set<String> packages = PLUGIN_LOGGERS.computeIfAbsent(pluginName, k -> ConcurrentHashMap.newKeySet());
            packages.add(packageName);
            packages.add(contextLoggerName);

            logger.info("Registered dynamic logger for plugin '{}': package '{}' + context logger '{}' at level {}",
                    pluginName, packageName, contextLoggerName, DEFAULT_PLUGIN_LOG_LEVEL);

        } catch (Exception e) {
            logger.error("Failed to register dynamic logger for plugin '{}' (package '{}'): {}",
                    pluginName, packageName, e.getMessage(), e);
        }
    }

    /**
     * Deregister all dynamic loggers associated with a plugin.
     * <p>
     * Resets the logger level to null so it falls back to the parent
     * (root) logger's level, effectively cleaning up the override.
     *
     * @param pluginName the plugin's display name
     */
    public static void deregisterPluginLogger(String pluginName) {
        if (pluginName == null) {
            return;
        }

        Set<String> packages = PLUGIN_LOGGERS.remove(pluginName);
        if (packages == null || packages.isEmpty()) {
            return;
        }

        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

            for (String packageName : packages) {
                ch.qos.logback.classic.Logger pluginLogger = loggerContext.getLogger(packageName);
                pluginLogger.setLevel(null); // Reset to inherit from parent/root

                logger.info("Deregistered dynamic logger for plugin '{}': package '{}'",
                        pluginName, packageName);
            }
        } catch (Exception e) {
            logger.error("Failed to deregister dynamic loggers for plugin '{}': {}",
                    pluginName, e.getMessage(), e);
        }
    }

    /**
     * Get all currently registered plugin logger packages.
     *
     * @return unmodifiable view of plugin name → package names mapping
     */
    public static Map<String, Set<String>> getRegisteredLoggers() {
        return Map.copyOf(PLUGIN_LOGGERS);
    }

    /**
     * Check if a plugin has a registered dynamic logger.
     *
     * @param pluginName the plugin name
     * @return true if the plugin has at least one registered logger
     */
    public static boolean hasRegisteredLogger(String pluginName) {
        Set<String> packages = PLUGIN_LOGGERS.get(pluginName);
        return packages != null && !packages.isEmpty();
    }

    /**
     * Extract the package name from a fully-qualified class name.
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code com.myplugin.MyPlugin} → {@code com.myplugin}</li>
     *   <li>{@code org.example.discord.bot.Main} → {@code org.example.discord.bot}</li>
     *   <li>{@code MyPlugin} (default package) → {@code null}</li>
     * </ul>
     *
     * @param className the fully-qualified class name
     * @return the package name, or null if in the default package
     */
    private static String extractPackageName(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot <= 0) {
            return null;
        }
        return className.substring(0, lastDot);
    }
}

