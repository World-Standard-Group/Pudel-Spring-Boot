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

import org.springframework.stereotype.Component;
import worldstandard.group.pudel.api.command.TextCommandHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing text command handlers.
 */
@Component
public class CommandRegistry {

    private final Map<String, TextCommandHandler> commands = new ConcurrentHashMap<>();

    /**
     * Register a command handler.
     * @param commandName the command name (case-insensitive)
     * @param handler the command handler
     */
    public void registerCommand(String commandName, TextCommandHandler handler) {
        if (commandName == null || handler == null) {
            throw new IllegalArgumentException("Command name and handler cannot be null");
        }
        commands.put(commandName.toLowerCase(), handler);
    }

    /**
     * Unregister a command handler.
     * @param commandName the command name
     */
    public void unregisterCommand(String commandName) {
        if (commandName != null) {
            commands.remove(commandName.toLowerCase());
        }
    }

    /**
     * Get a command handler.
     * @param commandName the command name
     * @return the handler or null if not found
     */
    public TextCommandHandler getCommand(String commandName) {
        if (commandName == null) {
            return null;
        }
        return commands.get(commandName.toLowerCase());
    }

    /**
     * Check if a command is registered.
     * @param commandName the command name
     * @return true if registered
     */
    public boolean hasCommand(String commandName) {
        if (commandName == null) {
            return false;
        }
        return commands.containsKey(commandName.toLowerCase());
    }

    /**
     * Get all registered commands.
     * @return map of all commands
     */
    public Map<String, TextCommandHandler> getAllCommands() {
        return new HashMap<>(commands);
    }

    /**
     * Get the number of registered commands.
     * @return command count
     */
    public int getCommandCount() {
        return commands.size();
    }
}

