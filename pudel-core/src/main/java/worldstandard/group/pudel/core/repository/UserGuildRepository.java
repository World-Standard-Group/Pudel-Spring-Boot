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
import worldstandard.group.pudel.core.entity.UserGuild;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing UserGuild associations.
 */
@Repository
public interface UserGuildRepository extends JpaRepository<UserGuild, Long> {
    /**
     * Find all guilds for a specific user.
     */
    List<UserGuild> findByUserId(String userId);

    /**
     * Find all users in a specific guild.
     */
    List<UserGuild> findByGuildId(String guildId);

    /**
     * Find a specific user-guild association.
     */
    Optional<UserGuild> findByUserIdAndGuildId(String userId, String guildId);

    /**
     * Check if user has access to guild.
     */
    boolean existsByUserIdAndGuildId(String userId, String guildId);

    /**
     * Delete all guild associations for a user.
     */
    void deleteByUserId(String userId);

    /**
     * Delete all user associations for a guild.
     */
    void deleteByGuildId(String guildId);
}

