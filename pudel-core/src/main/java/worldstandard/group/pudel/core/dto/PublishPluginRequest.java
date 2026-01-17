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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * DTO for publishing a new plugin to the marketplace.
 */
public class PublishPluginRequest {

    @NotBlank(message = "Plugin name is required")
    @Size(min = 2, max = 50, message = "Plugin name must be between 2 and 50 characters")
    private String name;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 500, message = "Description must be between 10 and 500 characters")
    private String description;

    @Size(max = 50, message = "Category must not exceed 50 characters")
    private String category;

    @NotBlank(message = "Source URL is required")
    @URL(message = "Source URL must be a valid URL")
    @Size(max = 255, message = "Source URL must not exceed 255 characters")
    private String sourceUrl;

    @NotBlank(message = "Version is required")
    @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "Version must follow semantic versioning (e.g., 1.0.0)")
    @Size(max = 20, message = "Version must not exceed 20 characters")
    private String version;

    /**
     * License type: MIT, APACHE_2, GPL_3, AGPL_3, PROPRIETARY, CUSTOM, EXCLUSIVE
     */
    private String licenseType = "MIT";

    /**
     * Whether this is a commercial plugin (may require payment).
     */
    private boolean commercial = false;

    /**
     * Price in cents (0 for free). Only relevant if commercial = true.
     */
    private int priceCents = 0;

    /**
     * Contact email for commercial/exclusive plugin inquiries.
     */
    @Size(max = 100, message = "Contact email must not exceed 100 characters")
    private String contactEmail;

    public PublishPluginRequest() {
    }

    public PublishPluginRequest(String name, String description, String category, String sourceUrl, String version) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.sourceUrl = sourceUrl;
        this.version = version;
    }

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

    public String getLicenseType() {
        return licenseType;
    }

    public void setLicenseType(String licenseType) {
        this.licenseType = licenseType;
    }

    public boolean isCommercial() {
        return commercial;
    }

    public void setCommercial(boolean commercial) {
        this.commercial = commercial;
    }

    public int getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(int priceCents) {
        this.priceCents = priceCents;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }
}

