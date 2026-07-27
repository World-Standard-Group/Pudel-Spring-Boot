/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 World Standard Group
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
package group.worldstandard.pudel.core.brain.context;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents a single passive context entry collected from a Discord message.
 * <p>
 * Passive context is gathered when Pudel observes messages it doesn't directly
 * respond to, building understanding of ongoing conversations.
 * <p>
 * Entities are extracted from the message and can include:
 * - Users mentioned (@user)
 * - Channels referenced (#channel)
 * - Emojis used (:emoji:)
 * - Roles mentioned (@role)
 * - Attachment URLs (Discord CDN links)
 * - Reply references (message_id of the replied-to message)
 * - Forwarded message references
 *
 * @param messageId   the Discord message ID this context came from
 * @param userId      the user who sent the message
 * @param channelId   the channel the message was sent in
 * @param content     the raw message content
 * @param entities    extracted entities (users, channels, emojis, roles, etc.)
 * @param attachmentUrls Discord CDN URLs for attachments (images, videos, etc.)
 * @param replyToMessageId the message ID this message replies to (if any)
 * @param forwardedMessages list of forwarded message data (if any)
 * @param timestamp   when the context was collected
 */
public record PassiveContextEntry(
        long messageId,
        long userId,
        long channelId,
        String content,
        Map<String, List<String>> entities,
        List<String> attachmentUrls,
        Long replyToMessageId,
        List<ForwardedMessageRef> forwardedMessages,
        Instant timestamp
) {
    /**
     * Creates a minimal passive context entry with just the essential fields.
     */
    public static PassiveContextEntry minimal(long messageId, long userId, long channelId, String content) {
        return new PassiveContextEntry(messageId, userId, channelId, content,
                Map.of(), List.of(), null, List.of(), Instant.now());
    }

    /**
     * Reference to a forwarded message.
     * <p>
     * Discord {@link net.dv8tion.jda.api.entities.messages.MessageSnapshot} does not expose the
     * original message author or id, so {@code messageId} is best-effort (0 when unknown) and
     * {@code authorId}/{@code authorName} are intentionally left empty. Forwarded messages are
     * recorded in the dedicated {@code forwarded_messages} table; this record only carries the
     * extracted content for inline display and retrieval.
     *
     * @param messageId the forwarded message's id if known (0 when Discord does not expose it)
     * @param content   the forwarded message's content
     */
    public record ForwardedMessageRef(
            long messageId,
            String content
    ) {
        public ForwardedMessageRef(String content) {
            this(0L, content);
        }
    }
}

