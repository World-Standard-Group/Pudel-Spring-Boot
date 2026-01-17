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
package worldstandard.group.pudel.core.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents loaded plugin metadata in the system.
 */
@Entity
@Table(name = "plugin_metadata", schema = "public")
public class PluginMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;

    @Column(name = "plugin_name", nullable = false, unique = true)
    private String pluginName;

    @Column(name = "plugin_version", nullable = false)
    private String pluginVersion;

    @Column(name = "plugin_author")
    private String pluginAuthor;

    @Column(name = "plugin_description")
    private String pluginDescription;

    @Column(name = "jar_file_name", nullable = false)
    private String jarFileName;

    @Column(name = "main_class", nullable = false)
    private String mainClass;

    @Column(name = "enabled")
    private boolean enabled = false;

    @Column(name = "loaded")
    private boolean loaded = false;

    @Column(name = "load_error")
    private String loadError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public PluginMetadata() {}

    public PluginMetadata(String pluginName, String pluginVersion, String jarFileName, String mainClass) {
        this.pluginName = pluginName;
        this.pluginVersion = pluginVersion;
        this.jarFileName = jarFileName;
        this.mainClass = mainClass;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }

    public String getPluginVersion() {
        return pluginVersion;
    }

    public void setPluginVersion(String pluginVersion) {
        this.pluginVersion = pluginVersion;
    }

    public String getPluginAuthor() {
        return pluginAuthor;
    }

    public void setPluginAuthor(String pluginAuthor) {
        this.pluginAuthor = pluginAuthor;
    }

    public String getPluginDescription() {
        return pluginDescription;
    }

    public void setPluginDescription(String pluginDescription) {
        this.pluginDescription = pluginDescription;
    }

    public String getJarFileName() {
        return jarFileName;
    }

    public void setJarFileName(String jarFileName) {
        this.jarFileName = jarFileName;
    }

    public String getMainClass() {
        return mainClass;
    }

    public void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    public String getLoadError() {
        return loadError;
    }

    public void setLoadError(String loadError) {
        this.loadError = loadError;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

