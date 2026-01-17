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
package worldstandard.group.pudel.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import net.dv8tion.jda.api.JDA;
import worldstandard.group.pudel.api.PluginContext;
import worldstandard.group.pudel.api.PudelPlugin;
import worldstandard.group.pudel.core.entity.PluginMetadata;
import worldstandard.group.pudel.core.plugin.PluginClassLoader;
import worldstandard.group.pudel.core.plugin.PluginContextFactory;
import worldstandard.group.pudel.core.repository.PluginMetadataRepository;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing plugins.
 */
@Service
@Transactional
public class PluginService extends BaseService {

    private static final Logger logger = LoggerFactory.getLogger(PluginService.class);

    private final PluginMetadataRepository pluginMetadataRepository;
    private final PluginClassLoader pluginClassLoader;
    private final PluginContextFactory pluginContextFactory;
    private final Map<String, PluginContext> pluginContexts = new ConcurrentHashMap<>();

    public PluginService(JDA jda,
                        PluginMetadataRepository pluginMetadataRepository,
                        PluginClassLoader pluginClassLoader,
                        PluginContextFactory pluginContextFactory) {
        super(jda);
        this.pluginMetadataRepository = pluginMetadataRepository;
        this.pluginClassLoader = pluginClassLoader;
        this.pluginContextFactory = pluginContextFactory;
    }

    /**
     * Scan for and load all plugins in the plugins directory.
     */
    public void discoverPlugins() {
        File pluginsDir = pluginClassLoader.getPluginsDirectory();
        File[] files = pluginsDir.listFiles((dir, name) -> name.endsWith(".jar"));

        if (files == null || files.length == 0) {
            logger.info("No plugins found in directory: {}", pluginsDir.getAbsolutePath());
            return;
        }

        logger.info("Found {} JAR files in plugins directory", files.length);

        for (File jarFile : files) {
            try {
                PudelPlugin plugin = pluginClassLoader.loadPlugin(jarFile);
                if (plugin != null) {
                    String pluginName = plugin.getPluginInfo().getName();
                    registerPluginMetadata(plugin, jarFile.getName());

                    // Create plugin-specific context
                    PluginContext context = pluginContextFactory.getContext(pluginName);
                    pluginContexts.put(pluginName, context);

                    plugin.initialize(context);
                    logger.info("Plugin initialized: {}", pluginName);
                }
            } catch (Exception e) {
                logger.error("Error discovering plugin {}: {}", jarFile.getName(), e.getMessage(), e);
            }
        }
    }

    /**
     * Register plugin metadata in the database.
     * @param plugin the plugin
     * @param jarFileName the JAR file name
     */
    private void registerPluginMetadata(PudelPlugin plugin, String jarFileName) {
        String pluginName = plugin.getPluginInfo().getName();

        Optional<PluginMetadata> existing = pluginMetadataRepository.findByPluginName(pluginName);

        PluginMetadata metadata;
        if (existing.isPresent()) {
            metadata = existing.get();
            metadata.setPluginVersion(plugin.getPluginInfo().getVersion());
            metadata.setPluginAuthor(plugin.getPluginInfo().getAuthor());
            metadata.setPluginDescription(plugin.getPluginInfo().getDescription());
            metadata.setLoaded(true);
            metadata.setLoadError(null);
        } else {
            metadata = new PluginMetadata(
                    pluginName,
                    plugin.getPluginInfo().getVersion(),
                    jarFileName,
                    plugin.getClass().getName()
            );
            metadata.setPluginAuthor(plugin.getPluginInfo().getAuthor());
            metadata.setPluginDescription(plugin.getPluginInfo().getDescription());
            metadata.setLoaded(true);
        }

        pluginMetadataRepository.save(metadata);
    }

    /**
     * Enable a plugin.
     * @param pluginName the plugin name
     */
    public void enablePlugin(String pluginName) {
        PudelPlugin plugin = pluginClassLoader.getPlugin(pluginName);
        if (plugin == null) {
            logger.warn("Plugin not found: {}", pluginName);
            return;
        }

        try {
            PluginContext context = pluginContexts.computeIfAbsent(pluginName,
                    pluginContextFactory::getContext);
            plugin.onEnable(context);

            Optional<PluginMetadata> metadata = pluginMetadataRepository.findByPluginName(pluginName);
            if (metadata.isPresent()) {
                PluginMetadata m = metadata.get();
                m.setEnabled(true);
                pluginMetadataRepository.save(m);
            }

            logger.info("Plugin enabled: {}", pluginName);
        } catch (Exception e) {
            logger.error("Error enabling plugin {}: {}", pluginName, e.getMessage(), e);
        }
    }

    /**
     * Disable a plugin.
     * @param pluginName the plugin name
     */
    public void disablePlugin(String pluginName) {
        PudelPlugin plugin = pluginClassLoader.getPlugin(pluginName);
        if (plugin == null) {
            logger.warn("Plugin not found: {}", pluginName);
            return;
        }

        try {
            PluginContext context = pluginContexts.get(pluginName);
            if (context != null) {
                plugin.onDisable(context);
            }

            // Unregister all event listeners for this plugin
            pluginContextFactory.getEventManager().unregisterListeners(pluginName);

            Optional<PluginMetadata> metadata = pluginMetadataRepository.findByPluginName(pluginName);
            if (metadata.isPresent()) {
                PluginMetadata m = metadata.get();
                m.setEnabled(false);
                pluginMetadataRepository.save(m);
            }

            logger.info("Plugin disabled: {}", pluginName);
        } catch (Exception e) {
            logger.error("Error disabling plugin {}: {}", pluginName, e.getMessage(), e);
        }
    }

    /**
     * Unload a plugin completely.
     * @param pluginName the plugin name
     */
    public void unloadPlugin(String pluginName) {
        PudelPlugin plugin = pluginClassLoader.getPlugin(pluginName);
        if (plugin == null) {
            logger.warn("Plugin not found for unloading: {}", pluginName);
            return;
        }

        try {
            PluginContext context = pluginContexts.get(pluginName);
            if (context != null) {
                plugin.shutdown(context);
            }

            // Unregister all event listeners
            pluginContextFactory.getEventManager().unregisterListeners(pluginName);

            // Remove context
            pluginContexts.remove(pluginName);
            pluginContextFactory.removeContext(pluginName);

            // Unload from class loader
            pluginClassLoader.unloadPlugin(pluginName);

            Optional<PluginMetadata> metadata = pluginMetadataRepository.findByPluginName(pluginName);
            if (metadata.isPresent()) {
                PluginMetadata m = metadata.get();
                m.setLoaded(false);
                m.setEnabled(false);
                pluginMetadataRepository.save(m);
            }

            logger.info("Plugin unloaded: {}", pluginName);
        } catch (Exception e) {
            logger.error("Error unloading plugin {}: {}", pluginName, e.getMessage(), e);
        }
    }

    /**
     * Shutdown all plugins.
     * Called when the bot is shutting down.
     */
    public void shutdownAllPlugins() {
        logger.info("Shutting down all plugins...");

        for (String pluginName : pluginClassLoader.getAllPlugins().keySet()) {
            try {
                PudelPlugin plugin = pluginClassLoader.getPlugin(pluginName);
                PluginContext context = pluginContexts.get(pluginName);

                if (plugin != null && context != null) {
                    plugin.shutdown(context);
                }

                pluginContextFactory.getEventManager().unregisterListeners(pluginName);
            } catch (Exception e) {
                logger.error("Error shutting down plugin {}: {}", pluginName, e.getMessage(), e);
            }
        }

        pluginContexts.clear();
        logger.info("All plugins shut down");
    }

    /**
     * Get all loaded plugins.
     * @return list of plugin metadata
     */
    public List<PluginMetadata> getAllPlugins() {
        return pluginMetadataRepository.findAll();
    }

    /**
     * Get a plugin by name.
     * @param pluginName the plugin name
     * @return the plugin metadata
     */
    public Optional<PluginMetadata> getPlugin(String pluginName) {
        return pluginMetadataRepository.findByPluginName(pluginName);
    }

    /**
     * Get all enabled plugins.
     * @return list of enabled plugins
     */
    public List<PluginMetadata> getEnabledPlugins() {
        return pluginMetadataRepository.findByEnabled(true);
    }
}

