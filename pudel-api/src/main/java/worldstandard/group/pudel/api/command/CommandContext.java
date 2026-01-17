/*
 * Pudel Plugin API (PDK) - Plugin Development Kit for Pudel Discord Bot
 * Copyright (c) 2026 Napapon Kamanee
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package worldstandard.group.pudel.api.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

/**
 * Context provided to command handlers.
 * Contains all necessary information about the command execution.
 */
public interface CommandContext {

    /**
     * Gets the original message event.
     * @return the message event
     */
    MessageReceivedEvent getEvent();

    /**
     * Gets the message that triggered the command.
     * @return the message
     */
    Message getMessage();

    /**
     * Gets the user who issued the command.
     * @return the user
     */
    User getUser();

    /**
     * Gets the member who issued the command (guild only).
     * @return the member or null if in DM
     */
    Member getMember();

    /**
     * Gets the guild where the command was issued.
     * @return the guild or null if in DM
     */
    Guild getGuild();

    /**
     * Gets the channel where the command was issued.
     * @return the message channel
     */
    MessageChannel getChannel();

    /**
     * Gets the command name.
     * @return the command name
     */
    String getCommand();

    /**
     * Gets the command arguments.
     * @return the arguments array
     */
    String[] getArgs();

    /**
     * Gets the full command input after the command name.
     * @return the raw arguments string
     */
    String getArgsString();

    /**
     * Checks if the command was issued in a guild.
     * @return true if in a guild, false if in DM
     */
    boolean isFromGuild();

    // ============== Convenience Methods ==============

    /**
     * Reply to the command message.
     * @param message the message to send
     */
    default void reply(String message) {
        getChannel().sendMessage(message).queue();
    }

    /**
     * Reply with formatted string.
     * @param format the format string
     * @param args the format arguments
     */
    default void replyFormat(String format, Object... args) {
        reply(String.format(format, args));
    }

    /**
     * Gets the author of the command (alias for getUser).
     * @return the user who sent the command
     */
    default User getAuthor() {
        return getUser();
    }

    /**
     * Gets a specific argument by index.
     * @param index the argument index (0-based)
     * @return the argument or empty string if out of bounds
     */
    default String getArg(int index) {
        String[] args = getArgs();
        if (index >= 0 && index < args.length) {
            return args[index];
        }
        return "";
    }

    /**
     * Checks if there are any arguments.
     * @return true if there are arguments
     */
    default boolean hasArgs() {
        return getArgs().length > 0;
    }

    /**
     * Gets the number of arguments.
     * @return the argument count
     */
    default int getArgCount() {
        return getArgs().length;
    }
}

