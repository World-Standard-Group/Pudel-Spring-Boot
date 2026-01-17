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
package worldstandard.group.pudel.core.discord;

import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import worldstandard.group.pudel.core.command.CommandRegistry;
import worldstandard.group.pudel.core.command.HelpSessionManager;

/**
 * Listener for handling reaction-based navigation in help menus and other interactive embeds.
 */
@Component
public class ReactionNavigationListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ReactionNavigationListener.class);

    private final HelpSessionManager helpSessionManager;
    private final CommandRegistry commandRegistry;

    public ReactionNavigationListener(HelpSessionManager helpSessionManager, CommandRegistry commandRegistry) {
        this.helpSessionManager = helpSessionManager;
        this.commandRegistry = commandRegistry;
    }

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        // Ignore bot's own reactions
        if (event.getUser() == null || event.getUser().isBot()) {
            return;
        }

        long messageId = event.getMessageIdLong();
        long userId = event.getUserIdLong();

        // Check if this is a help session message
        if (!helpSessionManager.hasSession(messageId)) {
            return;
        }

        HelpSessionManager.HelpSession session = helpSessionManager.getSession(messageId);

        // Verify the reactor is the session owner
        if (session.getUserId() != userId) {
            // Remove the reaction if it's not from the session owner
            try {
                event.getReaction().removeReaction(event.getUser()).queue(
                        success -> {},
                        error -> {} // Ignore permission errors
                );
            } catch (Exception e) {
                // Ignore - might not have permission
            }
            return;
        }

        // Get the emoji name (Unicode emoji)
        String unicodeEmoji = event.getReaction().getEmoji().getName();

        // Determine new page
        int newPage = helpSessionManager.handleNavigation(unicodeEmoji, session);

        if (newPage == -1) {
            // Close requested - delete message and remove session
            event.getChannel().deleteMessageById(messageId).queue(
                    success -> helpSessionManager.removeSession(messageId),
                    error -> {
                        logger.debug("Could not delete help message: {}", error.getMessage());
                        helpSessionManager.removeSession(messageId);
                    }
            );
            return;
        }

        // Only update if page changed
        if (newPage != session.getCurrentPage()) {
            session.setCurrentPage(newPage);

            // Update the embed
            event.getChannel().retrieveMessageById(messageId).queue(message -> message.editMessageEmbeds(
                    helpSessionManager.buildHelpEmbed(session, newPage, commandRegistry)
            ).queue(), error -> {
                logger.debug("Could not retrieve help message: {}", error.getMessage());
                helpSessionManager.removeSession(messageId);
            });
        }

        // Remove the user's reaction to allow re-clicking
        try {
            event.getReaction().removeReaction(event.getUser()).queue(
                    success -> {},
                    error -> {} // Ignore permission errors
            );
        } catch (Exception e) {
            // Ignore - might not have permission to manage messages
        }
    }
}

