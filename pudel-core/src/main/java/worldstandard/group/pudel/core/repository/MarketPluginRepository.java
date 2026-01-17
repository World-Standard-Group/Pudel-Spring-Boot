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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import worldstandard.group.pudel.core.entity.MarketPlugin;

import java.util.List;
import java.util.Optional;

/**
 * Repository for MarketPlugin entities.
 * Community-driven marketplace - all published plugins are immediately visible.
 */
@Repository
public interface MarketPluginRepository extends JpaRepository<MarketPlugin, String> {

    /**
     * Find all plugins by author.
     * @param authorId the author's Discord ID
     * @return list of plugins by the author
     */
    List<MarketPlugin> findByAuthorId(String authorId);

    /**
     * Find all plugins by category.
     * @param category the category
     * @return list of plugins in the category
     */
    List<MarketPlugin> findByCategory(String category);

    /**
     * Find plugins by name containing search term (case insensitive).
     * @param name the search term
     * @return list of matching plugins
     */
    List<MarketPlugin> findByNameContainingIgnoreCase(String name);

    /**
     * Search plugins by name or description (case insensitive).
     * @param nameQuery search term for name
     * @param descQuery search term for description
     * @return list of matching plugins
     */
    @Query("SELECT p FROM MarketPlugin p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :nameQuery, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :descQuery, '%'))")
    List<MarketPlugin> searchByNameOrDescription(@Param("nameQuery") String nameQuery, @Param("descQuery") String descQuery);

    /**
     * Find by name (exact match).
     * @param name the plugin name
     * @return the plugin if found
     */
    Optional<MarketPlugin> findByName(String name);

    /**
     * Find by author and name.
     * @param authorId the author's Discord ID
     * @param name the plugin name
     * @return the plugin if found
     */
    Optional<MarketPlugin> findByAuthorIdAndName(String authorId, String name);

    /**
     * Count plugins by author.
     * @param authorId the author's Discord ID
     * @return count of plugins
     */
    long countByAuthorId(String authorId);

    /**
     * Get top plugins by downloads.
     * @param pageable pagination info
     * @return list of top plugins
     */
    @Query("SELECT p FROM MarketPlugin p ORDER BY p.downloads DESC")
    List<MarketPlugin> findTopByDownloads(Pageable pageable);

    /**
     * Get all plugins ordered by creation date (newest first).
     * @param pageable pagination info
     * @return page of plugins
     */
    @Query("SELECT p FROM MarketPlugin p ORDER BY p.createdAt DESC")
    Page<MarketPlugin> findAllOrderByCreatedAtDesc(Pageable pageable);
}

