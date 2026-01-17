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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import worldstandard.group.pudel.api.PudelPlugin;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads and manages plugins from JAR files.
 */
public class PluginClassLoader {

    private static final Logger logger = LoggerFactory.getLogger(PluginClassLoader.class);

    private final File pluginsDirectory;
    private final Map<String, PudelPlugin> loadedPlugins = new ConcurrentHashMap<>();
    private final Map<String, URLClassLoader> classLoaders = new ConcurrentHashMap<>();

    public PluginClassLoader(File pluginsDirectory) {
        this.pluginsDirectory = pluginsDirectory;
        if (!pluginsDirectory.exists()) {
            if (pluginsDirectory.mkdirs()) {
                logger.info("Created plugins directory: {}", pluginsDirectory.getAbsolutePath());
            }
        }
    }

    /**
     * Load a plugin from a JAR file.
     * @param jarFile the JAR file to load
     * @return the loaded plugin or null if failed
     */
    public PudelPlugin loadPlugin(File jarFile) {
        if (!jarFile.exists() || !jarFile.getName().endsWith(".jar")) {
            logger.warn("Invalid JAR file: {}", jarFile.getAbsolutePath());
            return null;
        }

        URLClassLoader classLoader = null;
        String pluginName = null;

        try {
            String mainClassName = findMainClass(jarFile);
            if (mainClassName == null) {
                logger.error("Could not find main plugin class in {}", jarFile.getName());
                return null;
            }

            // 1. Create the ClassLoader
            classLoader = new URLClassLoader(
                    new URL[]{jarFile.toURI().toURL()},
                    Thread.currentThread().getContextClassLoader()
            );

            // 2. Load and Verify
            Class<?> mainClass = classLoader.loadClass(mainClassName);
            if (!PudelPlugin.class.isAssignableFrom(mainClass)) {
                logger.error("Main class {} does not implement PudelPlugin", mainClassName);
                classLoader.close();
                return null;
            }

            // 3. Instantiate
            PudelPlugin plugin = (PudelPlugin) mainClass.getDeclaredConstructor().newInstance();

            // 4. Register
            pluginName = plugin.getPluginInfo().getName();

            // Safety: Check if plugin with same name already exists
            if (loadedPlugins.containsKey(pluginName)) {
                logger.warn("Plugin {} is already loaded. Skipping.", pluginName);
                classLoader.close();
                return loadedPlugins.get(pluginName);
            }

            classLoaders.put(pluginName, classLoader);
            loadedPlugins.put(pluginName, plugin);

            logger.info("Successfully loaded plugin: {} v{}", pluginName, plugin.getPluginInfo().getVersion());
            return plugin;

        } catch (Exception e) {
            logger.error("Failed to load plugin from {}: {}", jarFile.getName(), e.getMessage(), e);

            // CLEANUP LOGIC:
            // If we have a plugin name, use the existing unload method
            if (pluginName != null) {
                unloadPlugin(pluginName);
            } else if (classLoader != null) {
                // If it failed before we even got a name, just close the loader
                try {
                    classLoader.close();
                } catch (IOException ioEx) {
                    logger.error("Failed to close classloader after failed load", ioEx);
                }
            }
            return null;
        }
    }

    /**
     * Find the main plugin class in a JAR file.
     * Reads from MANIFEST.MF entry "Plugin-Main" or falls back to plugin.yml or class scanning.
     * @param jarFile the JAR file to inspect
     * @return the main class name or null if not found
     */
    private String findMainClass(File jarFile) {
        logger.info("Searching for plugin main class in: {}", jarFile.getName());

        try (JarFile jar = new JarFile(jarFile)) {
            // First, try to read from MANIFEST.MF
            java.util.jar.Manifest manifest = jar.getManifest();
            if (manifest != null) {
                java.util.jar.Attributes attributes = manifest.getMainAttributes();
                String pluginMain = attributes.getValue("Plugin-Main");
                if (pluginMain != null && !pluginMain.isEmpty()) {
                    logger.info("Found Plugin-Main in manifest: {}", pluginMain);
                    return pluginMain;
                }
                logger.debug("No Plugin-Main in manifest, checking plugin.yml");
            } else {
                logger.debug("No manifest found in JAR");
            }

            // Second, try to read from plugin.yml
            JarEntry pluginYml = jar.getJarEntry("plugin.yml");
            if (pluginYml != null) {
                logger.debug("Found plugin.yml, parsing...");
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(jar.getInputStream(pluginYml)))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("main:")) {
                            String mainClass = line.substring(5).trim();
                            logger.info("Found main class in plugin.yml: {}", mainClass);
                            return mainClass;
                        }
                    }
                }
                logger.warn("plugin.yml found but no 'main:' entry");
            }

            // Third, scan for classes with plugin naming patterns (fallback)
            logger.info("Scanning JAR for PudelPlugin implementations...");
            java.util.List<String> candidateClasses = new java.util.ArrayList<>();

            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
                JarEntry entry = entries.nextElement();

                if (entry.getName().endsWith(".class") && !entry.getName().contains("$")) {
                    String className = entry.getName()
                            .replace("/", ".")
                            .replace(".class", "");

                    // Look for classes with common plugin naming patterns
                    String simpleName = className.substring(className.lastIndexOf('.') + 1);
                    if (simpleName.endsWith("Plugin") || simpleName.endsWith("Bundle") ||
                        simpleName.equals("Main") || simpleName.equals("PluginMain")) {
                        logger.debug("Found candidate plugin class: {}", className);
                        candidateClasses.add(className);
                    }
                }
            }

            if (!candidateClasses.isEmpty()) {
                // Prefer classes with "Bundle" or "Default" in name
                for (String className : candidateClasses) {
                    if (className.contains("Bundle") || className.contains("Default")) {
                        logger.info("Selected plugin class (priority match): {}", className);
                        return className;
                    }
                }
                // Return first candidate if no priority match
                String selected = candidateClasses.getFirst();
                logger.info("Selected plugin class (first match): {}", selected);
                return selected;
            }

            logger.warn("No plugin class found in JAR: {}", jarFile.getName());
        } catch (IOException e) {
            logger.error("Error reading JAR file: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * Unload a plugin.
     * @param pluginName the plugin name
     */
    public void unloadPlugin(String pluginName) {
        loadedPlugins.remove(pluginName);

        URLClassLoader classLoader = classLoaders.remove(pluginName);
        if (classLoader != null) {
            try {
                classLoader.close();
                logger.info("Plugin unloaded: {}", pluginName);
            } catch (IOException e) {
                logger.error("Error closing class loader for plugin {}: {}", pluginName, e.getMessage());
            }
        }
    }

    /**
     * Get a loaded plugin.
     * @param pluginName the plugin name
     * @return the plugin or null if not found
     */
    public PudelPlugin getPlugin(String pluginName) {
        return loadedPlugins.get(pluginName);
    }

    /**
     * Get all loaded plugins.
     * @return map of loaded plugins
     */
    public Map<String, PudelPlugin> getAllPlugins() {
        return new HashMap<>(loadedPlugins);
    }

    /**
     * Check if a plugin is loaded.
     * @param pluginName the plugin name
     * @return true if loaded
     */
    public boolean isPluginLoaded(String pluginName) {
        return loadedPlugins.containsKey(pluginName);
    }

    /**
     * Get the plugins directory.
     * @return the plugins directory
     */
    public File getPluginsDirectory() {
        return pluginsDirectory;
    }
}

