/*
 * Example Pudel Plugin - Music Player with DAVE Support
 *
 * This example demonstrates how to implement a music player plugin
 * that is compliant with Discord's DAVE (Audio/Voice Encryption) requirements.
 *
 * IMPORTANT: Starting March 1st, 2026, all Discord voice connections REQUIRE
 * End-to-End Encryption via the DAVE protocol. This plugin shows how to:
 * 1. Provide a DAVE implementation
 * 2. Connect to voice channels
 * 3. Play audio using the VoiceManager API
 */
package example.pudel.plugins;

import worldstandard.group.pudel.api.PudelPlugin;
import worldstandard.group.pudel.api.PluginContext;
import worldstandard.group.pudel.api.PluginInfo;
import worldstandard.group.pudel.api.audio.AudioProvider;
import worldstandard.group.pudel.api.audio.DAVEProvider;
import worldstandard.group.pudel.api.audio.VoiceConnectionStatus;
import worldstandard.group.pudel.api.audio.VoiceManager;
import worldstandard.group.pudel.api.command.CommandContext;
import worldstandard.group.pudel.api.command.TextCommandHandler;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Example music player plugin demonstrating DAVE-compliant voice functionality.
 *
 * <h2>Prerequisites</h2>
 * <p>This plugin requires a DAVE implementation. Choose one of:</p>
 * <ul>
 *   <li>JDAVE - Requires Java 25+ (https://github.com/MinnDevelopment/jdave)</li>
 *   <li>libdave-jvm - Requires Java 8+ (https://github.com/KyokoBot/libdave-jvm)</li>
 * </ul>
 */
public class MusicPlayerPlugin implements PudelPlugin {

    private PluginInfo info;
    private PluginContext context;
    private ExampleDAVEProvider daveProvider;

    public MusicPlayerPlugin() {
        this.info = new PluginInfo(
                "MusicPlayerPlugin",
                "1.0.0",
                "Example Author",
                "Music player with DAVE support for Discord voice"
        );
    }

    @Override
    public PluginInfo getPluginInfo() {
        return info;
    }

    @Override
    public void initialize(PluginContext context) {
        this.context = context;

        // Create and register DAVE provider
        this.daveProvider = new ExampleDAVEProvider();
        context.getVoiceManager().registerDAVEProvider(info.getName(), daveProvider);

        context.log("info", "MusicPlayerPlugin initialized");
    }

    @Override
    public void onEnable(PluginContext context) {
        // Register commands
        context.registerCommand("join", new JoinCommand());
        context.registerCommand("leave", new LeaveCommand());
        context.registerCommand("play", new PlayCommand());

        context.log("info", "MusicPlayerPlugin enabled with DAVE support");
    }

    @Override
    public void onDisable(PluginContext context) {
        // Unregister DAVE provider
        context.getVoiceManager().unregisterDAVEProvider(info.getName());

        context.unregisterCommand("join");
        context.unregisterCommand("leave");
        context.unregisterCommand("play");

        context.log("info", "MusicPlayerPlugin disabled");
    }

    @Override
    public void shutdown(PluginContext context) {
        if (daveProvider != null) {
            daveProvider.shutdown();
        }
    }

    // ============== Commands ==============

    /**
     * Command to join a voice channel.
     * Usage: !join
     */
    private class JoinCommand implements TextCommandHandler {
        @Override
        public void handle(CommandContext ctx) {
            var member = ctx.getMessage().getMember();
            if (member == null) {
                ctx.getChannel().sendMessage("This command can only be used in a server.").queue();
                return;
            }

            var voiceState = member.getVoiceState();
            if (voiceState == null || voiceState.getChannel() == null) {
                ctx.getChannel().sendMessage("You need to be in a voice channel!").queue();
                return;
            }

            long guildId = ctx.getGuild().getIdLong();
            long channelId = voiceState.getChannel().getIdLong();

            VoiceManager voiceManager = context.getVoiceManager();

            // Check DAVE availability
            if (!voiceManager.isDAVEAvailable(guildId)) {
                ctx.getChannel().sendMessage("⚠️ DAVE (Voice Encryption) is not available. " +
                         "Voice connections require DAVE after " + voiceManager.getDAVEDeadline()).queue();
                return;
            }

            // Connect to voice channel
            voiceManager.connect(guildId, channelId)
                .thenAccept(status -> {
                    switch (status) {
                        case CONNECTED -> ctx.getChannel().sendMessage("✅ Joined voice channel!").queue();
                        case DAVE_REQUIRED -> ctx.getChannel().sendMessage("❌ DAVE implementation required for voice.").queue();
                        case DAVE_ERROR -> ctx.getChannel().sendMessage("❌ DAVE initialization failed.").queue();
                        case NO_PERMISSION -> ctx.getChannel().sendMessage("❌ No permission to join voice channel.").queue();
                        default -> ctx.getChannel().sendMessage("❌ Failed to connect: " + status).queue();
                    }
                })
                .exceptionally(ex -> {
                    ctx.getChannel().sendMessage("❌ Error joining voice: " + ex.getMessage()).queue();
                    return null;
                });
        }
    }

    /**
     * Command to leave the voice channel.
     * Usage: !leave
     */
    private class LeaveCommand implements TextCommandHandler {
        @Override
        public void handle(CommandContext ctx) {
            long guildId = ctx.getGuild().getIdLong();
            VoiceManager voiceManager = context.getVoiceManager();

            if (!voiceManager.isConnected(guildId)) {
                ctx.getChannel().sendMessage("I'm not in a voice channel!").queue();
                return;
            }

            voiceManager.disconnect(guildId)
                .thenRun(() -> ctx.getChannel().sendMessage("👋 Disconnected from voice channel.").queue())
                .exceptionally(ex -> {
                    ctx.getChannel().sendMessage("❌ Error leaving voice: " + ex.getMessage()).queue();
                    return null;
                });
        }
    }

    /**
     * Command to play audio (placeholder - would integrate with audio source).
     * Usage: !play <url>
     */
    private class PlayCommand implements TextCommandHandler {
        @Override
        public void handle(CommandContext ctx) {
            long guildId = ctx.getGuild().getIdLong();
            VoiceManager voiceManager = context.getVoiceManager();

            if (!voiceManager.isConnected(guildId)) {
                ctx.getChannel().sendMessage("I need to be in a voice channel first! Use `!join`").queue();
                return;
            }

            String[] args = ctx.getArgs();
            if (args.length == 0) {
                ctx.getChannel().sendMessage("Usage: !play <url>").queue();
                return;
            }

            // Example: Set up audio provider
            // In a real implementation, you would load the audio from the URL
            // using a library like LavaPlayer
            ExampleAudioProvider provider = new ExampleAudioProvider();
            voiceManager.setAudioProvider(guildId, provider);

            ctx.getChannel().sendMessage("🎵 Now playing (example)...").queue();
        }
    }

    // ============== DAVE Provider Example ==============

    /**
     * Example DAVE provider implementation.
     *
     * <p>In a real plugin, you would integrate with an actual DAVE library:</p>
     * <ul>
     *   <li>JDAVE for Java 25+</li>
     *   <li>libdave-jvm for Java 8+</li>
     * </ul>
     */
    private static class ExampleDAVEProvider implements DAVEProvider {

        private boolean initialized = false;

        @Override
        public String getName() {
            return "ExampleDAVE";
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }

        @Override
        public boolean isAvailable() {
            // Check if the DAVE library is available
            // In reality, you would check if the native library loads
            return true;
        }

        @Override
        public int getRequiredJavaVersion() {
            return 25; // For JDAVE
            // return 8; // For libdave-jvm
        }

        @Override
        public void initialize() throws DAVEException {
            if (initialized) {
                return;
            }

            try {
                // In a real implementation:
                // - Load native DAVE library
                // - Initialize encryption contexts
                // - Set up key exchange handlers

                // Example for JDAVE:
                // DaveLibrary.load();
                // this.daveInstance = new DaveClient();

                initialized = true;
            } catch (Exception e) {
                throw new DAVEException("Failed to initialize DAVE", e);
            }
        }

        @Override
        public void shutdown() {
            if (initialized) {
                // Clean up DAVE resources
                initialized = false;
            }
        }

        @Override
        public Object getNativeImplementation() {
            // Return the native DAVE instance for JDA integration
            // In reality: return this.daveInstance;
            return null;
        }
    }

    // ============== Audio Provider Example ==============

    /**
     * Example audio provider for sending audio to Discord.
     */
    private static class ExampleAudioProvider implements AudioProvider {

        private final ConcurrentLinkedQueue<byte[]> audioQueue = new ConcurrentLinkedQueue<>();

        @Override
        public boolean canProvide() {
            return !audioQueue.isEmpty();
        }

        @Override
        public byte[] provide20MsAudio() {
            return audioQueue.poll();
        }

        @Override
        public boolean isOpus() {
            return true; // Our audio is Opus-encoded
        }

        /**
         * Queue audio data to be sent.
         * @param opusData Opus-encoded audio frame
         */
        public void queueAudio(byte[] opusData) {
            audioQueue.offer(opusData);
        }

        @Override
        public void close() {
            audioQueue.clear();
        }
    }
}

