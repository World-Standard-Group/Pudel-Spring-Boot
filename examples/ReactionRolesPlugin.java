/*
 * Example Pudel Plugin - Reaction Roles
 * This example demonstrates how to implement reaction roles using the event system.
 */
package example.pudel.plugins;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import worldstandard.group.pudel.api.PudelPlugin;
import worldstandard.group.pudel.api.PluginContext;
import worldstandard.group.pudel.api.PluginInfo;
import worldstandard.group.pudel.api.command.CommandContext;
import worldstandard.group.pudel.api.command.TextCommandHandler;
import worldstandard.group.pudel.api.event.EventHandler;
import worldstandard.group.pudel.api.event.EventPriority;
import worldstandard.group.pudel.api.event.Listener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reaction Roles plugin allowing users to get roles by reacting to a message.
 */
public class ReactionRolesPlugin implements PudelPlugin {

    private PluginInfo info;
    private PluginContext context;
    private ReactionRolesListener listener;

    // Map of messageId -> (emoji -> roleId)
    private final Map<String, Map<String, String>> reactionRoles = new ConcurrentHashMap<>();

    public ReactionRolesPlugin() {
        this.info = new PluginInfo(
                "ReactionRolesPlugin",
                "1.0.0",
                "Example Author",
                "Allows users to get roles by reacting to messages"
        );
    }

    @Override
    public PluginInfo getPluginInfo() {
        return info;
    }

    @Override
    public void initialize(PluginContext context) {
        this.context = context;
        context.log("info", "ReactionRolesPlugin initialized");
    }

    @Override
    public void onEnable(PluginContext context) {
        // Register commands for setting up reaction roles
        context.registerCommand("rr", new ReactionRolesCommandHandler(this));

        // Register event listener
        listener = new ReactionRolesListener(this);
        context.registerListener(listener);

        context.log("info", "ReactionRoles commands and listeners registered");
    }

    @Override
    public void onDisable(PluginContext context) {
        context.unregisterCommand("rr");
        if (listener != null) {
            context.unregisterListener(listener);
        }
    }

    @Override
    public void shutdown(PluginContext context) {
        reactionRoles.clear();
        context.log("info", "ReactionRolesPlugin shutting down");
    }

    /**
     * Add a reaction role mapping.
     */
    public void addReactionRole(String messageId, String emoji, String roleId) {
        reactionRoles.computeIfAbsent(messageId, k -> new ConcurrentHashMap<>())
                .put(emoji, roleId);
    }

    /**
     * Remove a reaction role mapping.
     */
    public void removeReactionRole(String messageId, String emoji) {
        Map<String, String> roles = reactionRoles.get(messageId);
        if (roles != null) {
            roles.remove(emoji);
            if (roles.isEmpty()) {
                reactionRoles.remove(messageId);
            }
        }
    }

    /**
     * Get the role ID for a reaction.
     */
    public String getRoleForReaction(String messageId, String emoji) {
        Map<String, String> roles = reactionRoles.get(messageId);
        return roles != null ? roles.get(emoji) : null;
    }

    public PluginContext getContext() {
        return context;
    }

    /**
     * Command handler for managing reaction roles.
     */
    private static class ReactionRolesCommandHandler implements TextCommandHandler {

        private final ReactionRolesPlugin plugin;

        public ReactionRolesCommandHandler(ReactionRolesPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void handle(CommandContext context) {
            if (!context.isFromGuild()) {
                context.getChannel().sendMessage("❌ This command can only be used in a server.").queue();
                return;
            }

            String[] args = context.getArgs();
            if (args.length < 1) {
                showHelp(context);
                return;
            }

            switch (args[0].toLowerCase()) {
                case "add" -> handleAdd(context, args);
                case "remove" -> handleRemove(context, args);
                case "list" -> handleList(context, args);
                default -> showHelp(context);
            }
        }

        private void handleAdd(CommandContext context, String[] args) {
            // Usage: !rr add <messageId> <emoji> <roleId>
            if (args.length < 4) {
                context.getChannel().sendMessage("Usage: `!rr add <messageId> <emoji> <roleId>`").queue();
                return;
            }

            String messageId = args[1];
            String emoji = args[2];
            String roleId = args[3];

            plugin.addReactionRole(messageId, emoji, roleId);
            context.getChannel().sendMessage(
                    String.format("✅ Added reaction role: %s -> <@&%s>", emoji, roleId)
            ).queue();
        }

        private void handleRemove(CommandContext context, String[] args) {
            if (args.length < 3) {
                context.getChannel().sendMessage("Usage: `!rr remove <messageId> <emoji>`").queue();
                return;
            }

            String messageId = args[1];
            String emoji = args[2];

            plugin.removeReactionRole(messageId, emoji);
            context.getChannel().sendMessage("✅ Removed reaction role mapping").queue();
        }

        private void handleList(CommandContext context, String[] args) {
            // List all reaction role mappings (as reminder Pudel don't provide database interface for third-party Plugin data, any persistence data plugins need might implement as local hoster of Pudel)
            context.getChannel().sendMessage("📋 Reaction roles feature - list not implemented in example").queue();
        }

        private void showHelp(CommandContext context) {
            context.getChannel().sendMessage(
                    "**Reaction Roles Commands:**\n" +
                    "`!rr add <messageId> <emoji> <roleId>` - Add reaction role\n" +
                    "`!rr remove <messageId> <emoji>` - Remove reaction role\n" +
                    "`!rr list` - List all reaction roles"
            ).queue();
        }
    }

    /**
     * Event listener for handling reactions.
     */
    private static class ReactionRolesListener implements Listener {

        private final ReactionRolesPlugin plugin;

        public ReactionRolesListener(ReactionRolesPlugin plugin) {
            this.plugin = plugin;
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void onReactionAdd(MessageReactionAddEvent event) {
            if (!event.isFromGuild() || event.getUser() == null || event.getUser().isBot()) {
                return;
            }

            String messageId = event.getMessageId();
            String emoji = event.getReaction().getEmoji().getAsReactionCode();

            String roleId = plugin.getRoleForReaction(messageId, emoji);
            if (roleId == null) {
                return;
            }

            Guild guild = event.getGuild();
            Role role = guild.getRoleById(roleId);

            if (role != null && event.getMember() != null) {
                guild.addRoleToMember(event.getMember(), role).queue(
                    success -> plugin.getContext().log("info",
                            "Added role " + role.getName() + " to " + event.getUser().getName()),
                    error -> plugin.getContext().log("error",
                            "Failed to add role: " + error.getMessage())
                );
            }
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void onReactionRemove(MessageReactionRemoveEvent event) {
            if (!event.isFromGuild() || event.getUser() == null || event.getUser().isBot()) {
                return;
            }

            String messageId = event.getMessageId();
            String emoji = event.getReaction().getEmoji().getAsReactionCode();

            String roleId = plugin.getRoleForReaction(messageId, emoji);
            if (roleId == null) {
                return;
            }

            Guild guild = event.getGuild();
            Role role = guild.getRoleById(roleId);

            if (role != null) {
                event.retrieveMember().queue(member -> {
                    if (member != null) {
                        guild.removeRoleFromMember(member, role).queue(
                            success -> plugin.getContext().log("info",
                                    "Removed role " + role.getName() + " from " + event.getUser().getName()),
                            error -> plugin.getContext().log("error",
                                    "Failed to remove role: " + error.getMessage())
                        );
                    }
                });
            }
        }
    }
}

