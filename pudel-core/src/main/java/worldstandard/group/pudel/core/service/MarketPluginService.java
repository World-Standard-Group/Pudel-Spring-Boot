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
package worldstandard.group.pudel.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import worldstandard.group.pudel.core.dto.PluginResponse;
import worldstandard.group.pudel.core.dto.PublishPluginRequest;
import worldstandard.group.pudel.core.dto.UpdatePluginRequest;
import worldstandard.group.pudel.core.entity.MarketPlugin;
import worldstandard.group.pudel.core.entity.PluginLicenseType;
import worldstandard.group.pudel.core.entity.User;
import worldstandard.group.pudel.core.repository.MarketPluginRepository;
import worldstandard.group.pudel.core.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing plugins in the marketplace.
 * Community-driven marketplace - all published plugins are immediately visible.
 * Bot hosters can freely decide which plugins to install on their Pudel instance.
 */
@Service
@Transactional
public class MarketPluginService {

    private static final Logger logger = LoggerFactory.getLogger(MarketPluginService.class);

    private final MarketPluginRepository marketPluginRepository;
    private final UserRepository userRepository;

    public MarketPluginService(MarketPluginRepository marketPluginRepository, UserRepository userRepository) {
        this.marketPluginRepository = marketPluginRepository;
        this.userRepository = userRepository;
    }

    /**
     * Get all plugins in the marketplace.
     * @return list of all plugins
     */
    @Transactional(readOnly = true)
    public List<PluginResponse> getMarketPlugins() {
        List<MarketPlugin> plugins = marketPluginRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        return plugins.stream()
                .map(this::toPluginResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get marketplace plugins with pagination.
     * @param page page number
     * @param size page size
     * @return page of plugins
     */
    @Transactional(readOnly = true)
    public Page<PluginResponse> getMarketPluginsPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MarketPlugin> pluginPage = marketPluginRepository.findAll(pageable);
        return pluginPage.map(this::toPluginResponse);
    }

    /**
     * Search plugins in the marketplace.
     * @param query search query
     * @return list of matching plugins
     */
    @Transactional(readOnly = true)
    public List<PluginResponse> searchPlugins(String query) {
        List<MarketPlugin> plugins = marketPluginRepository.searchByNameOrDescription(query, query);
        return plugins.stream()
                .map(this::toPluginResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get plugins by category.
     * @param category the category
     * @return list of plugins in the category
     */
    @Transactional(readOnly = true)
    public List<PluginResponse> getPluginsByCategory(String category) {
        List<MarketPlugin> plugins = marketPluginRepository.findByCategory(category);
        return plugins.stream()
                .map(this::toPluginResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get top plugins by downloads.
     * @param limit maximum number of plugins to return
     * @return list of top plugins
     */
    @Transactional(readOnly = true)
    public List<PluginResponse> getTopPlugins(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<MarketPlugin> plugins = marketPluginRepository.findTopByDownloads(pageable);
        return plugins.stream()
                .map(this::toPluginResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all plugins by the current user.
     * @param userId the user's Discord ID
     * @return list of user's plugins
     */
    @Transactional(readOnly = true)
    public List<PluginResponse> getUserPlugins(String userId) {
        List<MarketPlugin> plugins = marketPluginRepository.findByAuthorId(userId);
        return plugins.stream()
                .map(this::toPluginResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific plugin by ID.
     * @param pluginId the plugin ID
     * @return the plugin if found
     */
    @Transactional(readOnly = true)
    public Optional<PluginResponse> getPlugin(String pluginId) {
        return marketPluginRepository.findById(pluginId)
                .map(this::toPluginResponse);
    }

    /**
     * Publish a new plugin to the marketplace.
     * Plugin is immediately visible - community-driven approach.
     * @param request the publish request
     * @param userId the author's Discord ID
     * @return the created plugin response
     */
    public PluginResponse publishPlugin(PublishPluginRequest request, String userId) {
        // Check if plugin name already exists for this user
        Optional<MarketPlugin> existing = marketPluginRepository.findByAuthorIdAndName(userId, request.getName());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("You already have a plugin with this name");
        }

        // Get author name from user repository
        String authorName = "Unknown";
        Optional<User> user = userRepository.findById(userId);
        if (user.isPresent()) {
            authorName = user.get().getUsername();
        }

        MarketPlugin plugin = new MarketPlugin(
                request.getName(),
                request.getDescription(),
                request.getCategory() != null ? request.getCategory() : "other",
                userId,
                request.getVersion(),
                request.getSourceUrl()
        );
        plugin.setAuthorName(authorName);

        // Set license fields
        if (request.getLicenseType() != null) {
            try {
                plugin.setLicenseType(PluginLicenseType.valueOf(request.getLicenseType().toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.setLicenseType(PluginLicenseType.MIT);
            }
        }
        plugin.setCommercial(request.isCommercial());
        plugin.setPriceCents(request.getPriceCents());
        plugin.setContactEmail(request.getContactEmail());

        MarketPlugin saved = marketPluginRepository.save(plugin);
        logger.info("Plugin published: {} by user {}", saved.getName(), userId);

        return toPluginResponse(saved);
    }

    /**
     * Update an existing plugin.
     * @param pluginId the plugin ID
     * @param request the update request
     * @param userId the current user's ID (for authorization)
     * @return the updated plugin response
     */
    public PluginResponse updatePlugin(String pluginId, UpdatePluginRequest request, String userId) {
        MarketPlugin plugin = marketPluginRepository.findById(pluginId)
                .orElseThrow(() -> new IllegalArgumentException("Plugin not found"));

        // Check ownership
        if (!plugin.getAuthorId().equals(userId)) {
            throw new SecurityException("You do not have permission to update this plugin");
        }

        // Update fields if provided
        if (request.getName() != null && !request.getName().isBlank()) {
            plugin.setName(request.getName());
        }
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            plugin.setDescription(request.getDescription());
        }
        if (request.getCategory() != null) {
            plugin.setCategory(request.getCategory());
        }
        if (request.getSourceUrl() != null && !request.getSourceUrl().isBlank()) {
            plugin.setSourceUrl(request.getSourceUrl());
        }
        if (request.getVersion() != null && !request.getVersion().isBlank()) {
            plugin.setVersion(request.getVersion());
        }

        MarketPlugin saved = marketPluginRepository.save(plugin);
        logger.info("Plugin updated: {} by user {}", saved.getName(), userId);

        return toPluginResponse(saved);
    }

    /**
     * Delete a plugin.
     * @param pluginId the plugin ID
     * @param userId the current user's ID (for authorization)
     */
    public void deletePlugin(String pluginId, String userId) {
        MarketPlugin plugin = marketPluginRepository.findById(pluginId)
                .orElseThrow(() -> new IllegalArgumentException("Plugin not found"));

        // Check ownership
        if (!plugin.getAuthorId().equals(userId)) {
            throw new SecurityException("You do not have permission to delete this plugin");
        }

        marketPluginRepository.delete(plugin);
        logger.info("Plugin deleted: {} by user {}", plugin.getName(), userId);
    }

    /**
     * Increment download count.
     * @param pluginId the plugin ID
     */
    public void incrementDownloads(String pluginId) {
        MarketPlugin plugin = marketPluginRepository.findById(pluginId)
                .orElseThrow(() -> new IllegalArgumentException("Plugin not found"));

        plugin.setDownloads(plugin.getDownloads() + 1);
        marketPluginRepository.save(plugin);
    }

    /**
     * Get marketplace statistics.
     * @return statistics
     */
    @Transactional(readOnly = true)
    public MarketStats getMarketStats() {
        long total = marketPluginRepository.count();

        // Get unique authors count
        List<MarketPlugin> allPlugins = marketPluginRepository.findAll();
        long uniqueAuthors = allPlugins.stream()
                .map(MarketPlugin::getAuthorId)
                .distinct()
                .count();

        // Total downloads
        long totalDownloads = allPlugins.stream()
                .mapToLong(MarketPlugin::getDownloads)
                .sum();

        return new MarketStats(total, uniqueAuthors, totalDownloads);
    }

    /**
     * Convert entity to response DTO.
     */
    private PluginResponse toPluginResponse(MarketPlugin plugin) {
        return new PluginResponse(
                plugin.getId(),
                plugin.getName(),
                plugin.getDescription(),
                plugin.getCategory(),
                plugin.getAuthorName(),
                plugin.getAuthorId(),
                plugin.getVersion(),
                plugin.getDownloads(),
                plugin.getSourceUrl(),
                plugin.getLicenseType() != null ? plugin.getLicenseType().name() : "MIT",
                plugin.getLicenseType() != null ? plugin.getLicenseType().getDisplayName() : "MIT License",
                plugin.isCommercial(),
                plugin.getPriceCents(),
                plugin.getContactEmail(),
                plugin.getCreatedAt(),
                plugin.getUpdatedAt()
        );
    }

    /**
     * Inner class for market statistics.
     */
    public static class MarketStats {
        private final long totalPlugins;
        private final long uniqueAuthors;
        private final long totalDownloads;

        public MarketStats(long totalPlugins, long uniqueAuthors, long totalDownloads) {
            this.totalPlugins = totalPlugins;
            this.uniqueAuthors = uniqueAuthors;
            this.totalDownloads = totalDownloads;
        }

        public long getTotalPlugins() {
            return totalPlugins;
        }

        public long getUniqueAuthors() {
            return uniqueAuthors;
        }

        public long getTotalDownloads() {
            return totalDownloads;
        }
    }
}

