/*
 * Pudel Plugin API (PDK) - Plugin Development Kit for Pudel Discord Bot
 * Copyright (c) 2026 World Standard Group
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
package group.worldstandard.pudel.api.annotation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a context menu handler.
 * <p>
 * Context menus appear when right-clicking on users or messages in Discord.
 * The method will be called with either a {@link net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent}
 * or {@link net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent}.
 *
 * <h2>User Context Menu Example:</h2>
 * <pre>{@code
 * @ContextMenu(name = "Get User Info", type = Command.Type.USER)
 * public void getUserInfo(UserContextInteractionEvent event) {
 *     User target = event.getTarget();
 *     event.reply("User: " + target.getAsTag() + "\nID: " + target.getId()).queue();
 * }
 * }</pre>
 *
 * <h2>Message Context Menu Example:</h2>
 * <pre>{@code
 * @ContextMenu(name = "Bookmark Message", type = Command.Type.MESSAGE)
 * public void bookmarkMessage(MessageContextInteractionEvent event) {
 *     Message message = event.getTarget();
 *     // Save bookmark...
 *     event.reply("Message bookmarked!").setEphemeral(true).queue();
 * }
 * }</pre>
 *
 * <h2>With Permissions, IntegrationType &amp; InteractionContextType:</h2>
 * <pre>{@code
 * @ContextMenu(
 *     name = "Ban User",
 *     type = Command.Type.USER,
 *     permissions = {Permission.BAN_MEMBERS},
 *     integrationTo = {IntegrationType.GUILD_INSTALL},
 *     integrationContext = {InteractionContextType.GUILD}
 * )
 * public void banUser(UserContextInteractionEvent event) {
 *     // only members with BAN_MEMBERS can see/use this menu in guilds
 * }
 * }</pre>
 *
 * <p>The core automatically:</p>
 * <ul>
 *   <li>Registers the command when plugin is enabled</li>
 *   <li>Applies default member permissions, integration types and interaction contexts</li>
 *   <li>Syncs to Discord (globally by default)</li>
 *   <li>Unregisters and re-syncs when plugin is disabled</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ContextMenu {
    /**
     * Returns the base name of the context menu.
     * This name is in format of 'baseName > funcName' to charity mixed plugin may had mismatch display name.
     *
     * @return the base name of the context menu (the format result must not excess 32 characters)
     */
    String baseName();

    /**
     * Returns the function name of the context menu.
     * This name is in format of 'baseName > funcName' to charity mixed plugin may had mismatch display name.
     *
     * @return the function name of the context menu (the format result must not excess 32 characters)
     */
    String funcName();

    /**
     * Returns the type of context menu this command represents.
     * <p>
     * The type determines whether the context menu appears when right-clicking on a user or a message.
     * For user context menus, the handler receives a {@link net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent}.
     * For message context menus, the handler receives a {@link net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent}.
     *
     * @return the context menu type (USER or MESSAGE)
     */
    Command.Type type();

    /**
     * Indicates whether this context menu should be registered globally or per-guild.
     * <p>
     * When {@code true}, the context menu will be available across all guilds the bot is in.
     * When {@code false}, the context menu must be explicitly registered per guild using {@link #guildIds()}.
     *
     * @return {@code true} if the context menu should be registered globally, {@code false} otherwise; Default {@code true}
     */
    boolean global() default true;

    /**
     * Returns an array of guild IDs where this context menu should be registered.
     * <p>
     * This method is used when {@link #global()} is set to {@code false} to specify
     * the exact guilds where the context menu should appear. If {@link #global()}
     * is {@code true}, this setting has no effect and the context menu will be
     * registered globally across all guilds.
     *
     * @return an array of guild IDs, or an empty array if not specified or if global registration is enabled
     */
    long[] guildIds() default {};

    /**
     * Indicates whether the context menu is NSFW (Not Safe For Work).
     * If set to true, the menu will only be available in channels marked as NSFW.
     * Defaults to {@code true}.
     *
     * @return {@code true} if the context menu is NSFW, {@code false} otherwise
     */
    boolean nsfw() default true;

    /**
     * Returns the default member permissions required to see and use this context menu.
     * <p>
     * These permissions define the minimum set of privileges that a member must have
     * in the guild to have this context menu visible and executable. Discord enforces
     * these as <i>default member permissions</i> — members lacking them will not see
     * the menu in their client.
     * <p>
     * If no permissions are specified, the context menu is available to every member
     * who can normally use it in the given {@link #integrationContext()}.
     *
     * @return an array of {@link Permission} enums representing the required permissions; defaults to an empty array
     */
    Permission[] permissions() default {};

    /**
     * Returns the integration types for which this context menu is available.
     * Integration types determine how and where the command can be installed and used.
     * By default, the context menu is integrated as a guild install type.
     *
     * @return an array of {@link IntegrationType} enums indicating the supported integration types; defaults to {@link IntegrationType#GUILD_INSTALL}
     */
    IntegrationType[] integrationTo() default {IntegrationType.GUILD_INSTALL};

    /**
     * Returns the interaction context types in which this context menu is available.
     * Interaction contexts define where the command can be used, such as in guilds,
     * direct messages, or other specific environments. This setting controls the
     * visibility and accessibility of the menu based on the user's current context.
     *
     * @return an array of {@link InteractionContextType} enums indicating the supported interaction contexts;
     *         defaults to {@link InteractionContextType#GUILD}
     */
    InteractionContextType[] integrationContext() default {InteractionContextType.GUILD};
}