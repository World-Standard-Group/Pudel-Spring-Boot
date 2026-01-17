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
package worldstandard.group.pudel.core.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * DTO for updating a plugin in the marketplace.
 */
public class UpdatePluginRequest {

    @Size(min = 2, max = 50, message = "Plugin name must be between 2 and 50 characters")
    private String name;

    @Size(min = 10, max = 500, message = "Description must be between 10 and 500 characters")
    private String description;

    @Size(max = 50, message = "Category must not exceed 50 characters")
    private String category;

    @URL(message = "Source URL must be a valid URL")
    @Size(max = 255, message = "Source URL must not exceed 255 characters")
    private String sourceUrl;

    @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "Version must follow semantic versioning (e.g., 1.0.0)")
    @Size(max = 20, message = "Version must not exceed 20 characters")
    private String version;

    public UpdatePluginRequest() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}

