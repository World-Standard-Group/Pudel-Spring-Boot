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

import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base abstract service class that provides access to the JDA instance.
 * All service classes should extend this to have protected access to JDA.
 */
public abstract class BaseService {

    protected static final Logger logger = LoggerFactory.getLogger(BaseService.class);

    protected final JDA jda;

    public BaseService(JDA jda) {
        this.jda = jda;
    }

    /**
     * Check if the bot is currently in a specific guild.
     * Uses JDA instance to verify actual bot presence.
     *
     * @param guildId the Discord guild ID
     * @return true if the bot is in the guild, false otherwise
     */
    protected boolean isBotInGuild(String guildId) {
        try {
            if (jda == null) {
                logger.warn("JDA instance is null");
                return false;
            }

            net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(guildId);
            return guild != null;
        } catch (Exception e) {
            logger.error("Error checking if bot is in guild {}", guildId, e);
            return false;
        }
    }

    /**
     * Check if the bot is connected to Discord.
     *
     * @return true if connected, false otherwise
     */
    protected boolean isBotConnected() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    /**
     * Get the JDA instance.
     *
     * @return the JDA instance
     */
    protected JDA getJDA() {
        return jda;
    }
}

