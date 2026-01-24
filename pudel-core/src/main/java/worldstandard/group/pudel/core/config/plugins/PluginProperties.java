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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the plugin system.
 */
@Component
@ConfigurationProperties(prefix = "pudel.plugin")
public class PluginProperties {

    private String directory = "./plugins";
    private boolean enableAutoDiscovery = true;
    private boolean enableAutoLoad = true;

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public boolean isEnableAutoDiscovery() {
        return enableAutoDiscovery;
    }

    public void setEnableAutoDiscovery(boolean enableAutoDiscovery) {
        this.enableAutoDiscovery = enableAutoDiscovery;
    }

    public boolean isEnableAutoLoad() {
        return enableAutoLoad;
    }

    public void setEnableAutoLoad(boolean enableAutoLoad) {
        this.enableAutoLoad = enableAutoLoad;
    }
}

