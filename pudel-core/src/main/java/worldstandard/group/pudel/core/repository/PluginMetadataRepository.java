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
package worldstandard.group.pudel.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import worldstandard.group.pudel.core.entity.PluginMetadata;

import java.util.List;
import java.util.Optional;

/**
 * Repository for PluginMetadata entities.
 */
@Repository
public interface PluginMetadataRepository extends JpaRepository<PluginMetadata, Long> {

    /**
     * Find plugin by name.
     * @param pluginName the plugin name
     * @return the plugin metadata if found
     */
    Optional<PluginMetadata> findByPluginName(String pluginName);

    /**
     * Find all loaded plugins.
     * @return list of loaded plugins
     */
    List<PluginMetadata> findByLoaded(boolean loaded);

    /**
     * Find all enabled plugins.
     * @return list of enabled plugins
     */
    List<PluginMetadata> findByEnabled(boolean enabled);

    /**
     * Check if a plugin exists by name.
     * @param pluginName the plugin name
     * @return true if exists
     */
    boolean existsByPluginName(String pluginName);
}

