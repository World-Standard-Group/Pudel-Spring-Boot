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
package worldstandard.group.pudel.core.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import worldstandard.group.pudel.core.dto.PluginResponse;
import worldstandard.group.pudel.core.dto.PublishPluginRequest;
import worldstandard.group.pudel.core.dto.UpdatePluginRequest;
import worldstandard.group.pudel.core.service.MarketPluginService;

import java.util.List;

/**
 * REST controller for plugin marketplace.
 * Community-driven marketplace - all published plugins are immediately visible.
 * Bot hosters can freely decide which plugins to install on their Pudel instance.
 */
@RestController
@RequestMapping("/api/plugins")
@CrossOrigin(origins = "*", maxAge = 3600)
public class MarketPluginController {

    private static final Logger log = LoggerFactory.getLogger(MarketPluginController.class);

    private final MarketPluginService marketPluginService;

    public MarketPluginController(MarketPluginService marketPluginService) {
        this.marketPluginService = marketPluginService;
    }

    /**
     * Get all plugins in the marketplace.
     * GET /api/plugins/market
     */
    @GetMapping("/market")
    public ResponseEntity<List<PluginResponse>> getMarketPlugins(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category) {

        List<PluginResponse> plugins;

        if (search != null && !search.isBlank()) {
            plugins = marketPluginService.searchPlugins(search);
        } else if (category != null && !category.isBlank() && !"all".equals(category)) {
            plugins = marketPluginService.getPluginsByCategory(category);
        } else {
            plugins = marketPluginService.getMarketPlugins();
        }

        return ResponseEntity.ok(plugins);
    }

    /**
     * Get paginated marketplace plugins.
     * GET /api/plugins/market/paged
     */
    @GetMapping("/market/paged")
    public ResponseEntity<Page<PluginResponse>> getMarketPluginsPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<PluginResponse> plugins = marketPluginService.getMarketPluginsPaged(page, size);
        return ResponseEntity.ok(plugins);
    }

    /**
     * Get top plugins by downloads.
     * GET /api/plugins/market/top
     */
    @GetMapping("/market/top")
    public ResponseEntity<List<PluginResponse>> getTopPlugins(
            @RequestParam(defaultValue = "10") int limit) {

        List<PluginResponse> plugins = marketPluginService.getTopPlugins(limit);
        return ResponseEntity.ok(plugins);
    }

    /**
     * Get marketplace statistics.
     * GET /api/plugins/market/stats
     */
    @GetMapping("/market/stats")
    public ResponseEntity<MarketPluginService.MarketStats> getMarketStats() {
        return ResponseEntity.ok(marketPluginService.getMarketStats());
    }

    /**
     * Get a specific plugin by ID.
     * GET /api/plugins/market/{id}
     */
    @GetMapping("/market/{id}")
    public ResponseEntity<?> getPluginById(@PathVariable("id") String id) {
        return marketPluginService.getPlugin(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Publish a new plugin.
     * Plugin is immediately visible - community-driven approach.
     * POST /api/plugins/publish
     */
    @PostMapping("/publish")
    public ResponseEntity<?> publishPlugin(@Valid @RequestBody PublishPluginRequest request) {
        String userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Authentication required"));
        }

        try {
            PluginResponse response = marketPluginService.publishPlugin(request, userId);
            log.info("Plugin published: {} by user {}", request.getName(), userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to publish plugin", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to publish plugin"));
        }
    }

    /**
     * Get current user's plugins.
     * GET /api/plugins/user/plugins
     */
    @GetMapping("/user/plugins")
    public ResponseEntity<?> getUserPlugins() {
        String userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Authentication required"));
        }

        List<PluginResponse> plugins = marketPluginService.getUserPlugins(userId);
        return ResponseEntity.ok(plugins);
    }

    /**
     * Update a plugin.
     * PATCH /api/plugins/{id}
     */
    @PatchMapping("/{id}")
    public ResponseEntity<?> updatePlugin(
            @PathVariable("id") String id,
            @Valid @RequestBody UpdatePluginRequest request) {

        String userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Authentication required"));
        }

        try {
            PluginResponse response = marketPluginService.updatePlugin(id, request, userId);
            log.info("Plugin updated: {} by user {}", id, userId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update plugin", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to update plugin"));
        }
    }

    /**
     * Delete a plugin.
     * DELETE /api/plugins/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePlugin(@PathVariable("id") String id) {
        String userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Authentication required"));
        }

        try {
            marketPluginService.deletePlugin(id, userId);
            log.info("Plugin deleted: {} by user {}", id, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete plugin", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to delete plugin"));
        }
    }

    /**
     * Increment download count for a plugin.
     * POST /api/plugins/market/{id}/download
     */
    @PostMapping("/market/{id}/download")
    public ResponseEntity<?> incrementDownloads(@PathVariable("id") String id) {
        try {
            marketPluginService.incrementDownloads(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get the current authenticated user's ID.
     */
    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return (String) auth.getPrincipal();
    }

    /**
     * Error response class.
     */
    static class ErrorResponse {
        private final String message;

        public ErrorResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}

