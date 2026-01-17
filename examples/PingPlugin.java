/*
 * Example Pudel Plugin - Ping Command
 *
 * This demonstrates TWO ways to create plugins:
 * 1. Using SimplePlugin (recommended for most cases)
 * 2. Using PudelPlugin interface directly
 */
package example.pudel.plugins;

import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import worldstandard.group.pudel.api.PudelPlugin;
import worldstandard.group.pudel.api.PluginContext;
import worldstandard.group.pudel.api.PluginInfo;
import worldstandard.group.pudel.api.command.CommandContext;
import worldstandard.group.pudel.api.command.TextCommandHandler;
import worldstandard.group.pudel.api.event.EventHandler;
import worldstandard.group.pudel.api.event.EventPriority;
import worldstandard.group.pudel.api.event.Listener;

// ============================================================
// OPTION 1: SimplePlugin (Recommended - Less Boilerplate)
// ============================================================

/**
 * Minimal ping plugin using SimplePlugin.
 * Only 15 lines of actual code!
 */
class SimplePingPlugin extends SimplePlugin {

    public SimplePingPlugin() {
        super("PingPlugin", "1.0", "Author", "A simple ping command");
    }

    @Override
    protected void setup() {
        // Register commands with lambdas
        command("ping", ctx -> ctx.reply("Pong! 🏓"));
        command("echo", ctx -> ctx.reply(ctx.getArgs()));

        // Multiple aliases for one handler
        command(ctx -> ctx.reply("Hello, " + ctx.getAuthor().getName() + "!"),
                "hello", "hi", "hey");
    }
}

// ============================================================
// OPTION 2: SimplePlugin with Events
// ============================================================

/**
 * Plugin with commands AND events using SimplePlugin.
 */
class WelcomePlugin extends SimplePlugin implements Listener {

    public WelcomePlugin() {
        super("WelcomePlugin", "1.0", "Author", "Welcome new members");
    }

    @Override
    protected void setup() {
        command("welcome", ctx ->
            ctx.reply("👋 Welcome message is active!"));

        // Register this class as a listener
        listener(this);
    }

    @EventHandler
    public void onMemberJoin(GuildMemberJoinEvent event) {
        event.getGuild().getDefaultChannel().asTextChannel()
            .sendMessage("Welcome " + event.getMember().getAsMention() + "! 🎉")
            .queue();
    }
}

// ============================================================
// OPTION 3: PudelPlugin Interface (Full Control)
// ============================================================

/**
 * Full control using PudelPlugin interface directly.
 * Use this when you need fine-grained lifecycle control.
 */
public class PingPlugin implements PudelPlugin {

    private final PluginInfo info = new PluginInfo(
            "AdvancedPing", "1.0", "Author", "Advanced ping with stats");

    private int pingCount = 0;

    @Override
    public PluginInfo getPluginInfo() {
        return info;
    }

    // Optional: Heavy initialization
    @Override
    public void initialize(PluginContext context) {
        context.log("info", "Loading ping statistics...");
        // Load from database, etc.
    }

    @Override
    public void onEnable(PluginContext context) {
        context.registerCommand("ping", ctx -> {
            pingCount++;
            ctx.reply("Pong! 🏓 (Used " + pingCount + " times)");
        });

        context.registerCommand("pingstats", ctx ->
            ctx.reply("📊 Ping count: " + pingCount));
    }

    @Override
    public void onDisable(PluginContext context) {
        context.unregisterCommand("ping");
        context.unregisterCommand("pingstats");
        context.log("info", "Final ping count: " + pingCount);
    }

    // Optional: Cleanup resources
    @Override
    public void shutdown(PluginContext context) {
        context.log("info", "Saving ping statistics...");
        // Save to database, etc.
    }
}
