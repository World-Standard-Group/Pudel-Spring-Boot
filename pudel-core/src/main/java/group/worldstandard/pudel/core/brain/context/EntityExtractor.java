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

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts entities from Discord messages for passive context collection.
 * <p>
 * Extracts:
 * - User mentions (&lt;@id&gt;, &lt;@!id&gt;)
 * - Channel mentions (&lt;#id&gt;)
 * - Role mentions (&lt;@&amp;id&gt;)
 * - Custom emojis (&lt;:name:id&gt;, &lt;a:name:id&gt; for animated)
 * - Standard Unicode emojis
 * - URLs (http/https links)
 * - Reply references
 * - Attachment URLs (Discord CDN links)
 * - Forwarded message indicators
 */
@Component
public class EntityExtractor {

    private static final Logger logger = LoggerFactory.getLogger(EntityExtractor.class);

    // Discord mention patterns
    private static final Pattern USER_MENTION = Pattern.compile("<@!?(\\d+)>");
    private static final Pattern CHANNEL_MENTION = Pattern.compile("<#(\\d+)>");
    private static final Pattern ROLE_MENTION = Pattern.compile("<@&(\\d+)>");
    private static final Pattern CUSTOM_EMOJI = Pattern.compile("<a?:(\\w+):(\\d+)>");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");

    /**
     * Extract all entities from a Discord message event.
     *
     * @param event the message received event
     * @return map of entity type -> list of entity values
     */
    public Map<String, List<String>> extractEntities(MessageReceivedEvent event) {
        Message message = event.getMessage();
        Map<String, List<String>> entities = new HashMap<>();

        String content = message.getContentRaw();

        // Extract user mentions
        List<String> users = extractUserMentions(content, message);
        if (!users.isEmpty()) {
            entities.put("users", users);
        }

        // Extract channel mentions
        List<String> channels = extractChannelMentions(content, message);
        if (!channels.isEmpty()) {
            entities.put("channels", channels);
        }

        // Extract role mentions
        List<String> roles = extractRoleMentions(content, message);
        if (!roles.isEmpty()) {
            entities.put("roles", roles);
        }

        // Extract custom emojis
        List<String> emojis = extractCustomEmojis(content);
        if (!emojis.isEmpty()) {
            entities.put("emojis", emojis);
        }

        // Extract Unicode emojis (kept in entities for AI reference; not stored as separate column)
        List<String> unicodeEmojis = extractUnicodeEmojis(content);
        if (!unicodeEmojis.isEmpty()) {
            entities.put("unicode_emojis", unicodeEmojis);
        }

        // Extract stickers from the message
        List<String> stickers = extractStickers(message);
        if (!stickers.isEmpty()) {
            entities.put("stickers", stickers);
        }

        // Extract URLs
        List<String> urls = extractUrls(content);
        if (!urls.isEmpty()) {
            entities.put("urls", urls);
        }

        // Extract attachment URLs (Discord CDN links)
        List<String> attachmentUrls = extractAttachmentUrls(message);
        if (!attachmentUrls.isEmpty()) {
            entities.put("attachments", attachmentUrls);
        }

        // Check for reply reference
        Message referenced = message.getReferencedMessage();
        if (referenced != null) {
            List<String> replyRefs = new ArrayList<>();
            replyRefs.add(String.valueOf(referenced.getIdLong()));
            entities.put("reply_to", replyRefs);
        }

        // Check for forwarded messages (embeds that look like forwards)
        List<String> forwarded = extractForwardedIndicators(message);
        if (!forwarded.isEmpty()) {
            entities.put("forwarded", forwarded);
        }

        return entities;
    }

    /**
     * Extract attachment URLs from a message, filtering for Discord CDN links.
     *
     * @param message the Discord message
     * @return list of Discord CDN attachment URLs
     */
    public List<String> extractAttachmentUrls(Message message) {
        List<String> urls = new ArrayList<>();
        for (Message.Attachment attachment : message.getAttachments()) {
            String url = attachment.getUrl();
            if (url != null && url.contains("discordapp.com") || url != null && url.contains("discord.com")) {
                urls.add(url);
            } else if (url != null) {
                // Include non-discord URLs too (e.g., image links)
                urls.add(url);
            }
        }
        return urls;
    }

    /**
     * Check if a message contains only text attachments (no binary files).
     * Text attachments can be read for context.
     *
     * @param message the Discord message
     * @return true if the message has readable text attachments
     */
    public boolean hasTextAttachments(Message message) {
        return message.getAttachments().stream().anyMatch(a ->
                a.getContentType() != null && a.getContentType().startsWith("text/"));
    }

    /**
     * Get the content type of attachments for filtering.
     *
     * @param message the Discord message
     * @return map of filename -> content type for each attachment
     */
    public Map<String, String> getAttachmentInfo(Message message) {
        Map<String, String> info = new LinkedHashMap<>();
        for (Message.Attachment attachment : message.getAttachments()) {
            info.put(attachment.getFileName(),
                    attachment.getContentType() != null ? attachment.getContentType() : "unknown");
        }
        return info;
    }

    // ===============================
    // Private extraction methods
    // ===============================

    private List<String> extractUserMentions(String content, Message message) {
        List<String> users = new ArrayList<>();

        // Use JDA's built-in mention extraction for accuracy
        for (User user : message.getMentions().getUsers()) {
            users.add(user.getId());
        }

        // Also check raw content for any missed mentions
        Matcher matcher = USER_MENTION.matcher(content);
        while (matcher.find()) {
            String id = matcher.group(1);
            if (!users.contains(id)) {
                users.add(id);
            }
        }

        return users;
    }

    private List<String> extractChannelMentions(String content, Message message) {
        List<String> channels = new ArrayList<>();
        for (var channel : message.getMentions().getChannels()) {
            channels.add(channel.getId());
        }

        Matcher matcher = CHANNEL_MENTION.matcher(content);
        while (matcher.find()) {
            String id = matcher.group(1);
            if (!channels.contains(id)) {
                channels.add(id);
            }
        }

        return channels;
    }

    private List<String> extractRoleMentions(String content, Message message) {
        List<String> roles = new ArrayList<>();
        for (Role role : message.getMentions().getRoles()) {
            roles.add(role.getId());
        }

        Matcher matcher = ROLE_MENTION.matcher(content);
        while (matcher.find()) {
            String id = matcher.group(1);
            if (!roles.contains(id)) {
                roles.add(id);
            }
        }

        return roles;
    }

    private List<String> extractCustomEmojis(String content) {
        List<String> emojis = new ArrayList<>();
        Matcher matcher = CUSTOM_EMOJI.matcher(content);
        while (matcher.find()) {
            emojis.add(matcher.group(1) + ":" + matcher.group(2));
        }
        return emojis;
    }

    private List<String> extractUnicodeEmojis(String content) {
        List<String> emojis = new ArrayList<>();
        content.codePoints().forEach(cp -> {
            if (Character.isEmoji(cp) && cp != 0x200d && cp != 0xfe0f && cp != 0x20e3) {
                emojis.add(new String(Character.toChars(cp)));
            }
        });
        return emojis;
    }

    private List<String> extractStickers(Message message) {
        List<String> stickers = new ArrayList<>();
        for (var sticker : message.getStickers()) {
            // Record id + name so the AI can reference which sticker was used.
            stickers.add(sticker.getId() + ":" + (sticker.getName() != null ? sticker.getName() : "unknown"));
        }
        return stickers;
    }

    private List<String> extractUrls(String content) {
        List<String> urls = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(content);
        while (matcher.find()) {
            urls.add(matcher.group());
        }
        return urls;
    }

    private List<String> extractForwardedIndicators(Message message) {
        List<String> forwarded = new ArrayList<>();
        // Forwarded messages typically come as embeds
        for (MessageEmbed embed : message.getEmbeds()) {
            if (embed.getAuthor() != null && embed.getAuthor().getName() != null) {
                forwarded.add("embed:" + embed.getAuthor().getName());
            }
        }
        return forwarded;
    }
}
