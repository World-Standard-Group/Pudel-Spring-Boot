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

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import worldstandard.group.pudel.api.command.CommandContext;

/**
 * Implementation of CommandContext.
 */
public class CommandContextImpl implements CommandContext {

    private final MessageReceivedEvent event;
    private final String command;
    private final String[] args;
    private final String argsString;

    public CommandContextImpl(MessageReceivedEvent event, String command, String[] args, String argsString) {
        this.event = event;
        this.command = command;
        this.args = args;
        this.argsString = argsString;
    }

    @Override
    public MessageReceivedEvent getEvent() {
        return event;
    }

    @Override
    public Message getMessage() {
        return event.getMessage();
    }

    @Override
    public User getUser() {
        return event.getAuthor();
    }

    @Override
    public Member getMember() {
        return event.getMember();
    }

    @Override
    public Guild getGuild() {
        return event.getGuild();
    }

    @Override
    public MessageChannel getChannel() {
        return event.getChannel();
    }

    @Override
    public String getCommand() {
        return command;
    }

    @Override
    public String[] getArgs() {
        return args;
    }

    @Override
    public String getArgsString() {
        return argsString;
    }

    @Override
    public boolean isFromGuild() {
        return event.isFromGuild();
    }
}

