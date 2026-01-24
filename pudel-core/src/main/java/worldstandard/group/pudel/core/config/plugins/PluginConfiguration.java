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
package worldstandard.group.pudel.core.config.plugins;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import worldstandard.group.pudel.core.plugin.PluginClassLoader;

import java.io.File;

/**
 * Configuration for the plugin system.
 */
@Configuration
public class PluginConfiguration {

    @Bean
    public PluginClassLoader pluginClassLoader(PluginProperties properties) {
        File pluginsDir = new File(properties.getDirectory());
        return new PluginClassLoader(pluginsDir);
    }

    // PluginContextFactory is now a @Component that creates plugin-specific contexts
}

