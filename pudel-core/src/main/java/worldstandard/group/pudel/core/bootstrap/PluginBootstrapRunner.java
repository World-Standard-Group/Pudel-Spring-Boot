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
package worldstandard.group.pudel.core.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import worldstandard.group.pudel.core.config.PluginProperties;
import worldstandard.group.pudel.core.service.PluginService;

/**
 * Bootstrap runner for initializing the bot on startup.
 */
@Component
public class PluginBootstrapRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(PluginBootstrapRunner.class);

    private final PluginService pluginService;
    private final PluginProperties pluginProperties;

    public PluginBootstrapRunner(PluginService pluginService, PluginProperties pluginProperties) {
        this.pluginService = pluginService;
        this.pluginProperties = pluginProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Starting Pudel plugin bootstrap...");

        if (pluginProperties.isEnableAutoDiscovery()) {
            logger.info("Auto-discovery enabled, scanning for plugins...");
            pluginService.discoverPlugins();
        } else {
            logger.info("Auto-discovery disabled");
        }

        logger.info("Plugin bootstrap completed");
    }
}

