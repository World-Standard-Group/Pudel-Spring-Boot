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
package worldstandard.group.pudel.core.util;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Discord message parsing utility for detecting bot mentions, replies, and other patterns.
 * Uses JDA-native approach for efficient message parsing.
 */
@Component
public class DiscordMessageParser {

    /**
     * Parse result containing information about bot mentions and reply context.
     */
    public record ParseResult(
            boolean mentionsBotById,
            boolean mentionsBotByName,
            boolean isReplyToBot,
            String referencedContent
    ) {
        public boolean mentionsBot() {
            return mentionsBotById || mentionsBotByName;
        }
    }

    /**
     * Parse a message to detect bot mentions and reply context.
     */
    public ParseResult parseMessage(MessageReceivedEvent event) {
        Message message = event.getMessage();
        String content = message.getContentRaw().toLowerCase();
        String selfId = event.getJDA().getSelfUser().getId();

        // Check for bot mention by ID (using JDA)
        boolean mentionedById = message.getMentions().isMentioned(event.getJDA().getSelfUser());

        // Check for bot mention by name/nickname
        boolean mentionedByName = false;
        String botName = event.getJDA().getSelfUser().getName().toLowerCase();

        // If in guild, also check for nickname
        if (event.isFromGuild()) {
            Member selfMember = event.getGuild().getSelfMember();
            String nickname = selfMember.getNickname();
            if (nickname != null) {
                String nickLower = nickname.toLowerCase();
                mentionedByName = containsWord(content, nickLower) || containsWord(content, botName);
            } else {
                mentionedByName = containsWord(content, botName);
            }
        } else {
            mentionedByName = containsWord(content, botName);
        }

        // Check if replying to bot
        boolean isReplyToBot = false;
        String referencedContent = null;
        Message referenced = message.getReferencedMessage();
        if (referenced != null) {
            isReplyToBot = referenced.getAuthor().getId().equals(selfId);
            referencedContent = referenced.getContentRaw();
        }

        return new ParseResult(mentionedById, mentionedByName, isReplyToBot, referencedContent);
    }

    /**
     * Check if content contains word as a whole word (not just substring).
     */
    private boolean containsWord(String content, String word) {
        if (word == null || word.isEmpty()) return false;
        String pattern = "\\b" + Pattern.quote(word) + "\\b";
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(content).find();
    }
}

