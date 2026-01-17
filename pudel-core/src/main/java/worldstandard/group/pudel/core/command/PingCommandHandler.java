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
package worldstandard.group.pudel.core.command;

import net.dv8tion.jda.api.JDA;
import org.springframework.stereotype.Component;
import worldstandard.group.pudel.api.command.CommandContext;
import worldstandard.group.pudel.api.command.TextCommandHandler;

/**
 * Handler for the ping command.
 * Checks if Pudel is online and displays response latency.
 */
@Component
public class PingCommandHandler implements TextCommandHandler {

    private final JDA jda;

    public PingCommandHandler(JDA jda) {
        this.jda = jda;
    }

    @Override
    public void handle(CommandContext context) {
        long startTime = System.currentTimeMillis();

        context.getChannel().sendMessage("🏓 Pong!").queue(message -> {
            long latency = System.currentTimeMillis() - startTime;
            long gatewayPing = jda.getGatewayPing();

            message.editMessage(String.format("🏓 Pong! Latency: %dms | Gateway: %dms",
                    latency, gatewayPing)).queue();
        });
    }
}

