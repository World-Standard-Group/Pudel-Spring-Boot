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
package worldstandard.group.pudel.model.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discord Syntax Processor for handling Discord-specific formatting.
 * <p>
 * Handles:
 * - User mentions (<@123456789> or <@!123456789>)
 * - Role mentions (<@&123456789>)
 * - Channel mentions (<#123456789>)
 * - Custom emojis (<:name:123456789> or <a:name:123456789>)
 * - Reply/forward message context
 * - Code blocks and inline code
 * - Markdown formatting
 * - URLs and attachments
 * <p>
 * Two modes:
 * 1. Preprocessing for LLM: Convert Discord syntax to readable text
 * 2. Preprocessing for Embedding: Clean noise for better semantic matching
 */
@Component
public class DiscordSyntaxProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DiscordSyntaxProcessor.class);

    // Discord mention patterns
    private static final Pattern USER_MENTION = Pattern.compile("<@!?(\\d+)>");
    private static final Pattern ROLE_MENTION = Pattern.compile("<@&(\\d+)>");
    private static final Pattern CHANNEL_MENTION = Pattern.compile("<#(\\d+)>");
    private static final Pattern CUSTOM_EMOJI = Pattern.compile("<a?:(\\w+):(\\d+)>");
    private static final Pattern TIMESTAMP = Pattern.compile("<t:(\\d+)(?::[tTdDfFR])?>");

    // URL and attachment patterns
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\"]+");
    private static final Pattern ATTACHMENT_URL = Pattern.compile("https://cdn\\.discordapp\\.com/attachments/[^\\s]+");
    private static final Pattern TENOR_GIF = Pattern.compile("https://tenor\\.com/view/[^\\s]+");
    private static final Pattern GIPHY_GIF = Pattern.compile("https://giphy\\.com/gifs/[^\\s]+");

    // Code blocks
    private static final Pattern CODE_BLOCK = Pattern.compile("```(?:\\w+)?\\n?([\\s\\S]*?)```");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");

    // Markdown patterns
    private static final Pattern BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("\\*([^*]+)\\*");
    private static final Pattern UNDERLINE = Pattern.compile("__([^_]+)__");
    private static final Pattern STRIKETHROUGH = Pattern.compile("~~([^~]+)~~");
    private static final Pattern SPOILER = Pattern.compile("\\|\\|([^|]+)\\|\\|");
    private static final Pattern QUOTE = Pattern.compile("^>\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern BLOCK_QUOTE = Pattern.compile("^>>>\\s*([\\s\\S]+)$", Pattern.MULTILINE);

    /**
     * Context for resolving Discord IDs to readable names.
     * Optional - if provided, mentions will be resolved to actual names.
     */
    public record DiscordContext(
            String selfBotId,
            String selfBotName,
            String selfBotNickname,
            Map<String, String> userIdToName,
            Map<String, String> roleIdToName,
            Map<String, String> channelIdToName
    ) {
        public static DiscordContext empty() {
            return new DiscordContext(null, "Pudel", null,
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String selfBotId;
            private String selfBotName = "Pudel";
            private String selfBotNickname;
            private Map<String, String> userIdToName = new HashMap<>();
            private Map<String, String> roleIdToName = new HashMap<>();
            private Map<String, String> channelIdToName = new HashMap<>();

            public Builder selfBotId(String id) { this.selfBotId = id; return this; }
            public Builder selfBotName(String name) { this.selfBotName = name; return this; }
            public Builder selfBotNickname(String nickname) { this.selfBotNickname = nickname; return this; }
            public Builder addUser(String id, String name) { this.userIdToName.put(id, name); return this; }
            public Builder addRole(String id, String name) { this.roleIdToName.put(id, name); return this; }
            public Builder addChannel(String id, String name) { this.channelIdToName.put(id, name); return this; }
            public Builder users(Map<String, String> users) { this.userIdToName.putAll(users); return this; }
            public Builder roles(Map<String, String> roles) { this.roleIdToName.putAll(roles); return this; }
            public Builder channels(Map<String, String> channels) { this.channelIdToName.putAll(channels); return this; }

            public DiscordContext build() {
                return new DiscordContext(selfBotId, selfBotName, selfBotNickname,
                        userIdToName, roleIdToName, channelIdToName);
            }
        }
    }

    /**
     * Preprocess message for LLM input.
     * Converts Discord syntax to human-readable text while preserving semantic meaning.
     *
     * @param content Raw Discord message content
     * @param context Discord context for resolving mentions
     * @return Processed text suitable for LLM
     */
    public String preprocessForLLM(String content, DiscordContext context) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String processed = content;

        // Resolve user mentions
        processed = resolveUserMentions(processed, context);

        // Resolve role mentions
        processed = resolveRoleMentions(processed, context);

        // Resolve channel mentions
        processed = resolveChannelMentions(processed, context);

        // Convert custom emojis to their names
        processed = processCustomEmojis(processed);

        // Convert timestamps to readable format
        processed = processTimestamps(processed);

        // Handle URLs - keep but annotate
        processed = processURLs(processed);

        // Handle code blocks - keep but mark as code
        processed = processCodeBlocks(processed);

        // Strip markdown but keep text content
        processed = stripMarkdown(processed);

        // Clean up excessive whitespace
        processed = cleanWhitespace(processed);

        return processed.trim();
    }

    /**
     * Preprocess message for LLM using empty context.
     */
    public String preprocessForLLM(String content) {
        return preprocessForLLM(content, DiscordContext.empty());
    }

    /**
     * Preprocess message for embedding/semantic search.
     * More aggressive cleaning - removes noise to focus on semantic content.
     *
     * @param content Raw Discord message content
     * @return Cleaned text optimized for embedding
     */
    public String preprocessForEmbedding(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String processed = content;

        // Remove all mentions (replace with generic placeholders or nothing)
        processed = USER_MENTION.matcher(processed).replaceAll("[user]");
        processed = ROLE_MENTION.matcher(processed).replaceAll("[role]");
        processed = CHANNEL_MENTION.matcher(processed).replaceAll("[channel]");

        // Remove custom emojis entirely (they don't contribute to semantics)
        processed = CUSTOM_EMOJI.matcher(processed).replaceAll("");

        // Remove timestamps
        processed = TIMESTAMP.matcher(processed).replaceAll("");

        // Remove URLs entirely
        processed = URL_PATTERN.matcher(processed).replaceAll("[link]");

        // Extract code block content (programming context is relevant)
        processed = CODE_BLOCK.matcher(processed).replaceAll("$1");
        processed = INLINE_CODE.matcher(processed).replaceAll("$1");

        // Strip all markdown
        processed = stripMarkdown(processed);

        // Remove quotes markers
        processed = QUOTE.matcher(processed).replaceAll("$1");
        processed = BLOCK_QUOTE.matcher(processed).replaceAll("$1");

        // Remove spoiler markers
        processed = SPOILER.matcher(processed).replaceAll("$1");

        // Clean whitespace aggressively
        processed = processed.replaceAll("\\s+", " ");

        return processed.trim();
    }

    /**
     * Format LLM response for Discord output.
     * Ensures response follows Discord message limits and formatting.
     *
     * @param response Raw LLM response
     * @param maxLength Maximum message length (Discord limit is 2000)
     * @return Discord-ready formatted message
     */
    public String formatForDiscord(String response, int maxLength) {
        if (response == null || response.isBlank()) {
            return "";
        }

        String formatted = response;

        // Escape any raw Discord mention patterns that shouldn't ping
        formatted = escapeUnintendedMentions(formatted);

        // Ensure code blocks are properly closed
        formatted = ensureCodeBlocksClosed(formatted);

        // Truncate if too long, but try to break at a natural point
        if (formatted.length() > maxLength) {
            formatted = truncateNaturally(formatted, maxLength);
        }

        return formatted;
    }

    /**
     * Format response for Discord with default limit.
     */
    public String formatForDiscord(String response) {
        return formatForDiscord(response, 2000);
    }

    // ================================
    // Internal processing methods
    // ================================

    private String resolveUserMentions(String content, DiscordContext context) {
        Matcher matcher = USER_MENTION.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String userId = matcher.group(1);
            String replacement;

            // Check if it's the bot itself
            if (userId.equals(context.selfBotId())) {
                replacement = context.selfBotNickname() != null ?
                        context.selfBotNickname() : context.selfBotName();
            } else if (context.userIdToName().containsKey(userId)) {
                replacement = "@" + context.userIdToName().get(userId);
            } else {
                replacement = "@user";
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private String resolveRoleMentions(String content, DiscordContext context) {
        Matcher matcher = ROLE_MENTION.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String roleId = matcher.group(1);
            String replacement = context.roleIdToName().containsKey(roleId) ?
                    "@" + context.roleIdToName().get(roleId) : "@role";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private String resolveChannelMentions(String content, DiscordContext context) {
        Matcher matcher = CHANNEL_MENTION.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String channelId = matcher.group(1);
            String replacement = context.channelIdToName().containsKey(channelId) ?
                    "#" + context.channelIdToName().get(channelId) : "#channel";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private String processCustomEmojis(String content) {
        // Replace custom emojis with their name in colons :name:
        Matcher matcher = CUSTOM_EMOJI.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String emojiName = matcher.group(1);
            matcher.appendReplacement(sb, ":" + emojiName + ":");
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private String processTimestamps(String content) {
        // Replace Discord timestamps with [timestamp] placeholder
        // Full timestamp parsing would require more context
        return TIMESTAMP.matcher(content).replaceAll("[timestamp]");
    }

    private String processURLs(String content) {
        String processed = content;

        // Annotate attachment URLs
        processed = ATTACHMENT_URL.matcher(processed).replaceAll("[attachment]");

        // Annotate GIF URLs
        processed = TENOR_GIF.matcher(processed).replaceAll("[gif]");
        processed = GIPHY_GIF.matcher(processed).replaceAll("[gif]");

        // Keep other URLs but could annotate as [link: url] if needed

        return processed;
    }

    private String processCodeBlocks(String content) {
        // Keep code blocks but could mark them as [code: content]
        // For LLM, we want to preserve code context
        return content;
    }

    private String stripMarkdown(String content) {
        String processed = content;

        // Strip formatting but keep the text
        processed = BOLD.matcher(processed).replaceAll("$1");
        processed = ITALIC.matcher(processed).replaceAll("$1");
        processed = UNDERLINE.matcher(processed).replaceAll("$1");
        processed = STRIKETHROUGH.matcher(processed).replaceAll("$1");

        return processed;
    }

    private String cleanWhitespace(String content) {
        // Replace multiple spaces/newlines with single space
        return content.replaceAll("\\s+", " ").trim();
    }

    private String escapeUnintendedMentions(String content) {
        // Escape @ mentions that LLM might accidentally generate
        // Only escape if it looks like an unintended mention (not @user type response)

        // Escape @everyone and @here if not intentional
        String processed = content;
        processed = processed.replace("@everyone", "@\u200Beveryone");
        processed = processed.replace("@here", "@\u200Bhere");

        return processed;
    }

    private String ensureCodeBlocksClosed(String content) {
        // Count code block markers
        int openBlocks = countOccurrences(content, "```");

        // If odd number, add closing block
        if (openBlocks % 2 != 0) {
            return content + "\n```";
        }

        return content;
    }

    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private String truncateNaturally(String content, int maxLength) {
        if (content.length() <= maxLength) {
            return content;
        }

        // Reserve space for truncation indicator
        int targetLength = maxLength - 3; // "..."

        // Try to break at sentence boundary
        String truncated = content.substring(0, targetLength);

        int lastSentence = Math.max(
                truncated.lastIndexOf(". "),
                Math.max(truncated.lastIndexOf("! "), truncated.lastIndexOf("? "))
        );

        if (lastSentence > targetLength * 0.5) {
            return truncated.substring(0, lastSentence + 1) + "...";
        }

        // Try to break at word boundary
        int lastSpace = truncated.lastIndexOf(' ');
        if (lastSpace > targetLength * 0.7) {
            return truncated.substring(0, lastSpace) + "...";
        }

        return truncated + "...";
    }

    // ================================
    // Extraction methods
    // ================================

    /**
     * Extract all user mentions from content.
     */
    public List<String> extractUserMentions(String content) {
        List<String> mentions = new ArrayList<>();
        Matcher matcher = USER_MENTION.matcher(content);
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }
        return mentions;
    }

    /**
     * Extract all channel mentions from content.
     */
    public List<String> extractChannelMentions(String content) {
        List<String> mentions = new ArrayList<>();
        Matcher matcher = CHANNEL_MENTION.matcher(content);
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }
        return mentions;
    }

    /**
     * Extract all role mentions from content.
     */
    public List<String> extractRoleMentions(String content) {
        List<String> mentions = new ArrayList<>();
        Matcher matcher = ROLE_MENTION.matcher(content);
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }
        return mentions;
    }

    /**
     * Extract all URLs from content.
     */
    public List<String> extractURLs(String content) {
        List<String> urls = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(content);
        while (matcher.find()) {
            urls.add(matcher.group());
        }
        return urls;
    }

    /**
     * Check if content mentions a specific user ID.
     */
    public boolean mentionsUser(String content, String userId) {
        return extractUserMentions(content).contains(userId);
    }

    /**
     * Check if content contains any mention of the bot.
     */
    public boolean mentionsBot(String content, String botId, String botName, String botNickname) {
        // Check ID mention
        if (mentionsUser(content, botId)) {
            return true;
        }

        // Check name mention (case insensitive, word boundary)
        String lowerContent = content.toLowerCase();
        if (botName != null && containsWord(lowerContent, botName.toLowerCase())) {
            return true;
        }
        return botNickname != null && containsWord(lowerContent, botNickname.toLowerCase());
    }

    private boolean containsWord(String content, String word) {
        if (word == null || word.isEmpty()) return false;
        String pattern = "\\b" + Pattern.quote(word) + "\\b";
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(content).find();
    }
}

