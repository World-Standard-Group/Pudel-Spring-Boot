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
import worldstandard.group.pudel.core.entity.BotUser;

import java.util.Optional;

/**
 * Repository for BotUser entities.
 */
@Repository
public interface BotUserRepository extends JpaRepository<BotUser, Long> {

    /**
     * Find a bot user by user ID.
     * @param userId the Discord user ID
     * @return the bot user if found
     */
    Optional<BotUser> findByUserId(long userId);

    /**
     * Check if a user exists by ID.
     * @param userId the Discord user ID
     * @return true if exists
     */
    boolean existsByUserId(long userId);
}

