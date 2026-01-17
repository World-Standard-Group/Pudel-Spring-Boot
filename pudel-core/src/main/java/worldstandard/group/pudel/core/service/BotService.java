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
import org.springframework.stereotype.Service;

/**
 * Service for managing the bot's health and status.
 */
@Service
public class BotService extends BaseService {

    private static final Logger logger = LoggerFactory.getLogger(BotService.class);

    private long startTime;

    public BotService(JDA jda) {
        super(jda);
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Get the bot's health status.
     * @return true if the bot is healthy
     */
    public boolean isHealthy() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    /**
     * Get the bot's uptime in milliseconds.
     * @return uptime in ms
     */
    public long getUptime() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Get the bot's uptime in seconds.
     * @return uptime in seconds
     */
    public long getUptimeSeconds() {
        return getUptime() / 1000;
    }

    /**
     * Get the JDA instance.
     * @return the JDA instance
     */
    public JDA getJDA() {
        return super.getJDA();
    }

    /**
     * Get the number of guilds the bot is in.
     * @return guild count
     */
    public int getGuildCount() {
        return jda != null ? jda.getGuilds().size() : 0;
    }

    /**
     * Get the number of users the bot can see.
     * @return user count
     */
    public long getUserCount() {
        return jda != null ? jda.getUserCache().size() : 0;
    }

    /**
     * Get the bot's ping to Discord.
     * @return ping in milliseconds
     */
    public long getPing() {
        return jda != null ? jda.getGatewayPing() : -1;
    }
}

