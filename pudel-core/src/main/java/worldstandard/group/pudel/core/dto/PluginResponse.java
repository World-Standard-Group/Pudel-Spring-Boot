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

import java.time.LocalDateTime;

/**
 * DTO for plugin response in the marketplace.
 * Community-driven - no approval status needed.
 */
public class PluginResponse {

    private String id;
    private String name;
    private String description;
    private String category;
    private String author;
    private String authorId;
    private String version;
    private long downloads;
    private String sourceUrl;
    private String licenseType;
    private String licenseDisplayName;
    private boolean commercial;
    private int priceCents;
    private String contactEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PluginResponse() {}

    public PluginResponse(String id, String name, String description, String category,
                          String author, String authorId, String version, long downloads,
                          String sourceUrl, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, name, description, category, author, authorId, version, downloads,
             sourceUrl, "MIT", "MIT License", false, 0, null, createdAt, updatedAt);
    }

    public PluginResponse(String id, String name, String description, String category,
                          String author, String authorId, String version, long downloads,
                          String sourceUrl, String licenseType, String licenseDisplayName,
                          boolean commercial, int priceCents, String contactEmail,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.author = author;
        this.authorId = authorId;
        this.version = version;
        this.downloads = downloads;
        this.sourceUrl = sourceUrl;
        this.licenseType = licenseType;
        this.licenseDisplayName = licenseDisplayName;
        this.commercial = commercial;
        this.priceCents = priceCents;
        this.contactEmail = contactEmail;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public long getDownloads() {
        return downloads;
    }

    public void setDownloads(long downloads) {
        this.downloads = downloads;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getLicenseType() {
        return licenseType;
    }

    public void setLicenseType(String licenseType) {
        this.licenseType = licenseType;
    }

    public String getLicenseDisplayName() {
        return licenseDisplayName;
    }

    public void setLicenseDisplayName(String licenseDisplayName) {
        this.licenseDisplayName = licenseDisplayName;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

