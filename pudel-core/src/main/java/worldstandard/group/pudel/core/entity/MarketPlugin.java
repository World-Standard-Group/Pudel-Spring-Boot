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
 * Represents a plugin published in the marketplace.
 */
@Entity
@Table(name = "market_plugins", schema = "public")
public class MarketPlugin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "author_id", nullable = false)
    private String authorId;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    @Column(name = "downloads")
    private long downloads = 0;

    @Column(name = "source_url", nullable = false, length = 255)
    private String sourceUrl;

    /**
     * License type for the plugin.
     * Possible values:
     * - MIT: Open source, permissive
     * - APACHE_2: Open source, Apache 2.0
     * - GPL_3: Open source, copyleft
     * - AGPL_3: Open source, network copyleft
     * - PROPRIETARY: Commercial/closed source (source_url is for documentation only)
     * - CUSTOM: Custom license (check source_url for details)
     */
    @Column(name = "license_type", length = 20)
    @Enumerated(EnumType.STRING)
    private PluginLicenseType licenseType = PluginLicenseType.MIT;

    /**
     * Indicates if this is a commercial plugin (may require payment).
     */
    @Column(name = "is_commercial")
    private boolean commercial = false;

    /**
     * Price in cents (0 for free plugins). Only relevant if commercial = true.
     */
    @Column(name = "price_cents")
    private int priceCents = 0;

    /**
     * Contact email for commercial/exclusive plugin inquiries.
     */
    @Column(name = "contact_email", length = 100)
    private String contactEmail;

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

    public MarketPlugin() {}

    public MarketPlugin(String name, String description, String category, String authorId, String version, String sourceUrl) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.authorId = authorId;
        this.version = version;
        this.sourceUrl = sourceUrl;
    }

    // Getters and Setters

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

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
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

    public PluginLicenseType getLicenseType() {
        return licenseType;
    }

    public void setLicenseType(PluginLicenseType licenseType) {
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

