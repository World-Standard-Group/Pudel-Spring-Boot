/*
 * Example Pudel Plugin - Advanced Event Handler
 * This example demonstrates how to create a plugin with various event handlers
 * for moderation, audit logging, and role reactions.
 */
package example.pudel.plugins;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateNameEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import worldstandard.group.pudel.api.PudelPlugin;
import worldstandard.group.pudel.api.PluginContext;
import worldstandard.group.pudel.api.PluginInfo;
import worldstandard.group.pudel.api.event.EventHandler;
import worldstandard.group.pudel.api.event.EventPriority;
import worldstandard.group.pudel.api.event.Listener;

import java.awt.Color;
import java.time.Instant;

/**
 * Advanced audit logging plugin demonstrating various JDA events.
 */
public class AuditLogPlugin implements PudelPlugin {

    private PluginInfo info;
    private AuditEventListener auditListener;

    // Configuration - in a real plugin this would come from config file
    private String auditChannelId = null; // Set via command or config

    public AuditLogPlugin() {
        this.info = new PluginInfo(
                "AuditLogPlugin",
                "1.0.0",
                "Example Author",
                "Complete audit logging for guild moderation events"
        );
    }

    @Override
    public PluginInfo getPluginInfo() {
        return info;
    }

    @Override
    public void initialize(PluginContext context) {
        context.log("info", "AuditLogPlugin initialized");
    }

    @Override
    public void onEnable(PluginContext context) {
        auditListener = new AuditEventListener(context, this);
        context.registerListener(auditListener);
        context.log("info", "Audit log listeners registered");
    }

    @Override
    public void onDisable(PluginContext context) {
        if (auditListener != null) {
            context.unregisterListener(auditListener);
        }
    }

    @Override
    public void shutdown(PluginContext context) {
        context.log("info", "AuditLogPlugin shutting down");
    }

    public void setAuditChannelId(String channelId) {
        this.auditChannelId = channelId;
    }

    public String getAuditChannelId() {
        return auditChannelId;
    }

    /**
     * Audit event listener demonstrating various JDA events.
     */
    private static class AuditEventListener implements Listener {

        private final PluginContext context;
        private final AuditLogPlugin plugin;

        public AuditEventListener(PluginContext context, AuditLogPlugin plugin) {
            this.context = context;
            this.plugin = plugin;
        }

        // =============== Member Events ===============

        @EventHandler(priority = EventPriority.NORMAL)
        public void onMemberJoin(GuildMemberJoinEvent event) {
            sendAuditLog(event.getGuild().getIdLong(),
                    "Member Joined",
                    String.format("%s joined the server", event.getMember().getUser().getAsTag()),
                    Color.GREEN);
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void onMemberLeave(GuildMemberRemoveEvent event) {
            sendAuditLog(event.getGuild().getIdLong(),
                    "Member Left",
                    String.format("%s left the server", event.getUser().getAsTag()),
                    Color.ORANGE);
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void onNicknameChange(GuildMemberUpdateNicknameEvent event) {
            String oldNick = event.getOldNickname() != null ? event.getOldNickname() : "(none)";
            String newNick = event.getNewNickname() != null ? event.getNewNickname() : "(none)";
            sendAuditLog(event.getGuild().getIdLong(),
                    "Nickname Changed",
                    String.format("%s changed nickname from `%s` to `%s`",
                            event.getMember().getUser().getAsTag(), oldNick, newNick),
                    Color.BLUE);
        }

        // =============== Moderation Events ===============

        @EventHandler(priority = EventPriority.HIGH)
        public void onBan(GuildBanEvent event) {
            sendAuditLog(event.getGuild().getIdLong(),
                    "🔨 User Banned",
                    String.format("%s was banned from the server", event.getUser().getAsTag()),
                    Color.RED);
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onUnban(GuildUnbanEvent event) {
            sendAuditLog(event.getGuild().getIdLong(),
                    "User Unbanned",
                    String.format("%s was unbanned from the server", event.getUser().getAsTag()),
                    Color.GREEN);
        }

        // =============== Channel Events ===============

        @EventHandler(priority = EventPriority.NORMAL)
        public void onChannelCreate(ChannelCreateEvent event) {
            if (!event.isFromGuild()) return;
            sendAuditLog(event.getGuild().getIdLong(),
                    "Channel Created",
                    String.format("Channel `%s` was created", event.getChannel().getName()),
                    Color.GREEN);
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void onChannelDelete(ChannelDeleteEvent event) {
            if (!event.isFromGuild()) return;
            sendAuditLog(event.getGuild().getIdLong(),
                    "Channel Deleted",
                    String.format("Channel `%s` was deleted", event.getChannel().getName()),
                    Color.RED);
        }

        // =============== Role Events ===============

        @EventHandler(priority = EventPriority.NORMAL)
        public void onRoleCreate(RoleCreateEvent event) {
            sendAuditLog(event.getGuild().getIdLong(),
                    "Role Created",
                    String.format("Role `%s` was created", event.getRole().getName()),
                    Color.GREEN);
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void onRoleDelete(RoleDeleteEvent event) {
            sendAuditLog(event.getGuild().getIdLong(),
                    "Role Deleted",
                    String.format("Role `%s` was deleted", event.getRole().getName()),
                    Color.RED);
        }

        // =============== Message Events ===============

        @EventHandler(priority = EventPriority.NORMAL)
        public void onMessageEdit(MessageUpdateEvent event) {
            if (!event.isFromGuild() || event.getAuthor().isBot()) return;
            // Note: Old content is not available from JDA directly
            // You would need to cache messages to show old vs new content
            context.log("debug", String.format("Message edited by %s in #%s",
                    event.getAuthor().getAsTag(), event.getChannel().getName()));
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void onMessageDelete(MessageDeleteEvent event) {
            if (!event.isFromGuild()) return;
            // Note: Message content is not available after deletion
            // You would need to cache messages to log deleted content
            context.log("debug", String.format("Message %s deleted in #%s",
                    event.getMessageId(), event.getChannel().getName()));
        }

        // =============== Reaction Events (for reaction roles) ===============

        @EventHandler(priority = EventPriority.HIGH)
        public void onReactionAdd(MessageReactionAddEvent event) {
            if (!event.isFromGuild() || event.getUser() == null || event.getUser().isBot()) return;
            // This is where you'd implement reaction roles
            context.log("debug", String.format("Reaction %s added by %s",
                    event.getReaction().getEmoji().getAsReactionCode(),
                    event.getUser().getAsTag()));
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onReactionRemove(MessageReactionRemoveEvent event) {
            if (!event.isFromGuild() || event.getUser() == null || event.getUser().isBot()) return;
            // This is where you'd implement reaction role removal
            context.log("debug", String.format("Reaction %s removed by %s",
                    event.getReaction().getEmoji().getAsReactionCode(),
                    event.getUser().getAsTag()));
        }

        // =============== Guild Events ===============

        @EventHandler(priority = EventPriority.MONITOR)
        public void onGuildNameChange(GuildUpdateNameEvent event) {
            context.log("info", String.format("Guild renamed from '%s' to '%s'",
                    event.getOldName(), event.getNewName()));
        }

        // =============== Utility Methods ===============

        private void sendAuditLog(long guildId, String title, String description, Color color) {
            String channelId = plugin.getAuditChannelId();
            if (channelId == null) return;

            TextChannel channel = context.getJDA().getTextChannelById(channelId);
            if (channel == null) return;

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(title)
                    .setDescription(description)
                    .setColor(color)
                    .setTimestamp(Instant.now())
                    .setFooter("Audit Log", null);

            channel.sendMessageEmbeds(embed.build()).queue();
        }
    }
}

