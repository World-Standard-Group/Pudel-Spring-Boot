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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import worldstandard.group.pudel.api.PudelPlugin;
import worldstandard.group.pudel.core.config.PluginProperties;
import worldstandard.group.pudel.core.entity.PluginMetadata;
import worldstandard.group.pudel.core.repository.PluginMetadataRepository;
import worldstandard.group.pudel.core.service.PluginService;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that watches the plugins directory for changes and handles hot-reload.
 *
 * Features:
 * - Auto-detect new plugins
 * - Hash-based change detection for updates
 * - Loads plugins from temp copies for safe hot-reload
 * - Pending updates list for enabled plugins
 * - Warning notifications for pending updates (every 1 min)
 */
@Service
public class PluginWatcherService {

    private static final Logger logger = LoggerFactory.getLogger(PluginWatcherService.class);
    private static final long UPDATE_CHECK_INTERVAL_MS = 60_000; // 1 minute

    private final PluginProperties pluginProperties;
    private final PluginClassLoader pluginClassLoader;
    private final PluginMetadataRepository pluginMetadataRepository;
    private final PluginService pluginService;

    // Track JAR hashes: pluginName -> hash
    private final Map<String, String> jarHashes = new ConcurrentHashMap<>();

    // Track JAR file -> plugin name mapping
    private final Map<String, String> jarToPlugin = new ConcurrentHashMap<>();

    // Pending updates for enabled plugins: pluginName -> (newJarPath, detectedTime)
    private final Map<String, PendingUpdate> pendingUpdates = new ConcurrentHashMap<>();

    // Temp directory for loaded plugin copies
    private Path tempPluginDir;

    private volatile boolean watcherRunning = false;

    public PluginWatcherService(PluginProperties pluginProperties,
                                 PluginClassLoader pluginClassLoader,
                                 PluginMetadataRepository pluginMetadataRepository,
                                 PluginService pluginService) {
        this.pluginProperties = pluginProperties;
        this.pluginClassLoader = pluginClassLoader;
        this.pluginMetadataRepository = pluginMetadataRepository;
        this.pluginService = pluginService;
    }

    @PostConstruct
    public void init() {
        try {
            // Create temp directory for plugin copies
            tempPluginDir = Files.createTempDirectory("pudel-plugins-");
            logger.info("Plugin temp directory: {}", tempPluginDir);

            // Register shutdown hook to clean temp dir
            Runtime.getRuntime().addShutdownHook(new Thread(this::cleanupTempDir));

            // Initial scan
            scanPluginsDirectory();

        } catch (IOException e) {
            logger.error("Failed to create temp plugin directory: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        watcherRunning = false;
        cleanupTempDir();
    }

    /**
     * Scheduled task to check for plugin updates.
     */
    @Scheduled(fixedRate = UPDATE_CHECK_INTERVAL_MS)
    public void checkForUpdates() {
        if (!pluginProperties.isEnableAutoDiscovery()) {
            return;
        }

        scanPluginsDirectory();
        warnPendingUpdates();
    }

    /**
     * Scan the plugins directory for new/updated plugins.
     */
    public void scanPluginsDirectory() {
        File pluginsDir = pluginClassLoader.getPluginsDirectory();
        File[] jarFiles = pluginsDir.listFiles((dir, name) -> name.endsWith(".jar"));

        if (jarFiles == null) {
            return;
        }

        Set<String> currentJars = new HashSet<>();

        for (File jarFile : jarFiles) {
            currentJars.add(jarFile.getName());
            processJarFile(jarFile);
        }

        // Check for removed JARs
        Set<String> removedJars = new HashSet<>(jarToPlugin.keySet());
        removedJars.removeAll(currentJars);

        for (String removedJar : removedJars) {
            String pluginName = jarToPlugin.remove(removedJar);
            if (pluginName != null) {
                logger.info("Plugin JAR removed: {} ({})", removedJar, pluginName);
                jarHashes.remove(pluginName);
            }
        }
    }

    /**
     * Process a single JAR file.
     */
    private void processJarFile(File jarFile) {
        try {
            String currentHash = computeFileHash(jarFile);
            String jarName = jarFile.getName();

            // Check if this is a known plugin
            String existingPluginName = jarToPlugin.get(jarName);

            if (existingPluginName != null) {
                // Check if hash changed (update detected)
                String storedHash = jarHashes.get(existingPluginName);

                if (storedHash != null && !storedHash.equals(currentHash)) {
                    handlePluginUpdate(existingPluginName, jarFile, currentHash);
                }
            } else {
                // New plugin - try to load it
                loadNewPlugin(jarFile, currentHash);
            }

        } catch (Exception e) {
            logger.error("Error processing JAR file {}: {}", jarFile.getName(), e.getMessage());
        }
    }

    /**
     * Handle a plugin update.
     */
    private void handlePluginUpdate(String pluginName, File newJarFile, String newHash) {
        Optional<PluginMetadata> metadataOpt = pluginMetadataRepository.findByPluginName(pluginName);

        if (metadataOpt.isEmpty()) {
            return;
        }

        PluginMetadata metadata = metadataOpt.get();

        if (metadata.isEnabled()) {
            // Plugin is enabled - queue update and warn
            pendingUpdates.put(pluginName, new PendingUpdate(newJarFile.getAbsolutePath(), newHash, Instant.now()));
            logger.warn("Update detected for enabled plugin '{}'. Restart or disable plugin to apply update.", pluginName);
        } else {
            // Plugin is disabled - apply update immediately
            applyPluginUpdate(pluginName, newJarFile, newHash);
        }
    }

    /**
     * Apply a plugin update (for disabled plugins).
     */
    private void applyPluginUpdate(String pluginName, File newJarFile, String newHash) {
        logger.info("Applying update for plugin: {}", pluginName);

        try {
            // Unload old plugin
            pluginService.unloadPlugin(pluginName);

            // Delete old temp copy
            Path oldTempCopy = tempPluginDir.resolve(pluginName + ".jar");
            Files.deleteIfExists(oldTempCopy);

            // Copy new JAR to temp
            Path newTempCopy = tempPluginDir.resolve(pluginName + ".jar");
            Files.copy(newJarFile.toPath(), newTempCopy, StandardCopyOption.REPLACE_EXISTING);

            // Load from temp copy
            PudelPlugin plugin = pluginClassLoader.loadPlugin(newTempCopy.toFile());

            if (plugin != null) {
                jarHashes.put(pluginName, newHash);
                pendingUpdates.remove(pluginName);
                logger.info("Plugin '{}' updated successfully", pluginName);
            }

        } catch (Exception e) {
            logger.error("Failed to apply update for plugin {}: {}", pluginName, e.getMessage(), e);
        }
    }

    /**
     * Load a new plugin.
     */
    private void loadNewPlugin(File jarFile, String hash) {
        try {
            // Copy to temp directory
            String tempName = jarFile.getName().replace(".jar", "-" + hash.substring(0, 8) + ".jar");
            Path tempCopy = tempPluginDir.resolve(tempName);
            Files.copy(jarFile.toPath(), tempCopy, StandardCopyOption.REPLACE_EXISTING);

            // Load from temp copy
            PudelPlugin plugin = pluginClassLoader.loadPlugin(tempCopy.toFile());

            if (plugin != null) {
                String pluginName = plugin.getPluginInfo().getName();
                jarHashes.put(pluginName, hash);
                jarToPlugin.put(jarFile.getName(), pluginName);

                logger.info("New plugin discovered: {} v{}", pluginName, plugin.getPluginInfo().getVersion());
            }

        } catch (Exception e) {
            logger.error("Failed to load new plugin from {}: {}", jarFile.getName(), e.getMessage(), e);
        }
    }

    /**
     * Warn about pending updates for enabled plugins.
     */
    private void warnPendingUpdates() {
        for (Map.Entry<String, PendingUpdate> entry : pendingUpdates.entrySet()) {
            String pluginName = entry.getKey();
            PendingUpdate update = entry.getValue();

            long minutesAgo = java.time.Duration.between(update.detectedTime, Instant.now()).toMinutes();

            logger.warn("[HOT-RELOAD] Plugin '{}' has pending update (detected {} min ago). " +
                       "Disable plugin or restart bot to apply.", pluginName, minutesAgo);
        }
    }

    /**
     * Force apply all pending updates (for use during shutdown/restart).
     */
    public void applyAllPendingUpdates() {
        for (Map.Entry<String, PendingUpdate> entry : pendingUpdates.entrySet()) {
            String pluginName = entry.getKey();
            PendingUpdate update = entry.getValue();

            File jarFile = new File(update.jarPath);
            if (jarFile.exists()) {
                applyPluginUpdate(pluginName, jarFile, update.newHash);
            }
        }
    }

    /**
     * Get list of pending updates.
     */
    public Map<String, PendingUpdate> getPendingUpdates() {
        return new HashMap<>(pendingUpdates);
    }

    /**
     * Check if a plugin has pending update.
     */
    public boolean hasPendingUpdate(String pluginName) {
        return pendingUpdates.containsKey(pluginName);
    }

    /**
     * Compute SHA-256 hash of a file.
     */
    private String computeFileHash(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream is = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Cleanup temp directory.
     */
    private void cleanupTempDir() {
        if (tempPluginDir != null) {
            try {
                Files.walk(tempPluginDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            logger.debug("Failed to delete temp file: {}", path);
                        }
                    });
            } catch (IOException e) {
                logger.debug("Failed to cleanup temp directory: {}", e.getMessage());
            }
        }
    }

    /**
     * Pending update record.
     */
    public static class PendingUpdate {
        public final String jarPath;
        public final String newHash;
        public final Instant detectedTime;

        public PendingUpdate(String jarPath, String newHash, Instant detectedTime) {
            this.jarPath = jarPath;
            this.newHash = newHash;
            this.detectedTime = detectedTime;
        }
    }
}

