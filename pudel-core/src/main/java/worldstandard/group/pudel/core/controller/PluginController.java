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
package worldstandard.group.pudel.core.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import worldstandard.group.pudel.core.entity.PluginMetadata;
import worldstandard.group.pudel.core.service.PluginService;

import java.util.List;
import java.util.Optional;

/**
 * REST controller for plugin management.
 */
@RestController
@RequestMapping("/api/plugins")
public class PluginController {

    private final PluginService pluginService;

    public PluginController(PluginService pluginService) {
        this.pluginService = pluginService;
    }

    /**
     * Get all plugins.
     * @return list of plugins
     */
    @GetMapping
    public ResponseEntity<PluginListResponse> getAllPlugins() {
        List<PluginMetadata> plugins = pluginService.getAllPlugins();
        return ResponseEntity.ok(new PluginListResponse(plugins, plugins.size()));
    }

    /**
     * Get a specific plugin by name.
     * @param name the plugin name
     * @return the plugin or 404 if not found
     */
    @GetMapping("/{name}")
    public ResponseEntity<PluginMetadata> getPlugin(@PathVariable String name) {
        Optional<PluginMetadata> plugin = pluginService.getPlugin(name);
        return plugin.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Enable a plugin.
     * @param name the plugin name
     * @return 200 OK or 404 if not found
     */
    @PostMapping("/{name}/enable")
    public ResponseEntity<String> enablePlugin(@PathVariable String name) {
        if (pluginService.getPlugin(name).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        pluginService.enablePlugin(name);
        return ResponseEntity.ok("Plugin enabled: " + name);
    }

    /**
     * Disable a plugin.
     * @param name the plugin name
     * @return 200 OK or 404 if not found
     */
    @PostMapping("/{name}/disable")
    public ResponseEntity<String> disablePlugin(@PathVariable String name) {
        if (pluginService.getPlugin(name).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        pluginService.disablePlugin(name);
        return ResponseEntity.ok("Plugin disabled: " + name);
    }

    /**
     * Unload a plugin completely.
     * @param name the plugin name
     * @return 200 OK or 404 if not found
     */
    @PostMapping("/{name}/unload")
    public ResponseEntity<String> unloadPlugin(@PathVariable String name) {
        if (pluginService.getPlugin(name).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        pluginService.unloadPlugin(name);
        return ResponseEntity.ok("Plugin unloaded: " + name);
    }

    /**
     * Get all enabled plugins.
     * @return list of enabled plugins
     */
    @GetMapping("/enabled")
    public ResponseEntity<PluginListResponse> getEnabledPlugins() {
        List<PluginMetadata> plugins = pluginService.getEnabledPlugins();
        return ResponseEntity.ok(new PluginListResponse(plugins, plugins.size()));
    }

    /**
     * Response class for plugin list.
     */
    static class PluginListResponse {
        public List<PluginMetadata> plugins;
        public int total;

        public PluginListResponse(List<PluginMetadata> plugins, int total) {
            this.plugins = plugins;
            this.total = total;
        }
    }
}

