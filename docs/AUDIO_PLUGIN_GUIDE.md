# Audio Plugin Development Guide

This guide explains how to develop audio plugins for Pudel that comply with Discord's DAVE (Audio/Voice Encryption) requirements.

## Overview

Starting **March 1st, 2026**, Discord requires all voice connections to use End-to-End Encryption (E2EE) via the DAVE protocol. This guide covers:

1. Understanding Pudel's audio architecture
2. Creating audio plugins that use DAVE
3. Providing custom DAVE implementations
4. Best practices for audio plugins

## Prerequisites

- Java 25+ (for JDAVE) or Java 8+ (for libdave-jvm)
- Understanding of Pudel's plugin system
- Familiarity with JDA's audio API

## Pudel's Audio Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Discord                                 │
└──────────────────────────┬──────────────────────────────────────┘
                           │ DAVE Protocol (E2EE)
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                    JDA 6.x                                      │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ AudioModuleConfig + DaveSessionFactory                  │    │
│  └─────────────────────────────────────────────────────────┘    │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Pudel Core                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ VoiceManager                                            │    │
│  │  - DAVE provider registration                           │    │
│  │  - Voice connection management                          │    │
│  │  - Audio provider/receiver routing                      │    │
│  └─────────────────────────────────────────────────────────┘    │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Your Plugin                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ AudioProvider (send audio)                              │    │
│  │ AudioReceiver (receive audio)                           │    │
│  │ DAVEProvider (optional - provide encryption)            │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

## Quick Start: Using Existing DAVE

If you just want to use voice without providing your own DAVE:

```java
public class SimpleAudioPlugin implements PudelPlugin {
    
    private PluginContext context;
    
    @Override
    public void initialize(PluginContext context) {
        this.context = context;
    }
    
    @Override
    public void onEnable(PluginContext context) {
        context.registerCommand("joinvc", ctx -> {
            VoiceManager voiceManager = context.getVoiceManager();
            long guildId = ctx.getGuild().getIdLong();
            
            // Check DAVE is available first!
            if (!voiceManager.isDAVEAvailable(guildId)) {
                ctx.getChannel().sendMessage(
                    "⚠️ Voice encryption (DAVE) is not available. " +
                    "Please ensure a DAVE provider plugin is installed."
                ).queue();
                return;
            }
            
            // Get voice channel from user
            var member = ctx.getMember();
            var voiceState = member.getVoiceState();
            if (voiceState == null || voiceState.getChannel() == null) {
                ctx.getChannel().sendMessage("❌ Join a voice channel first!").queue();
                return;
            }
            
            long channelId = voiceState.getChannel().getIdLong();
            
            // Connect (DAVE is handled automatically)
            voiceManager.connect(guildId, channelId)
                .thenAccept(status -> {
                    if (status == VoiceConnectionStatus.CONNECTED) {
                        ctx.getChannel().sendMessage("✅ Connected!").queue();
                    } else {
                        ctx.getChannel().sendMessage("❌ Failed: " + status).queue();
                    }
                });
        });
    }
}
```

## Providing Audio Data

To send audio to Discord, implement `AudioProvider`:

```java
public class MyAudioProvider implements AudioProvider {
    
    private final BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>();
    
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
        return true; // Return true if your audio is Opus-encoded
    }
    
    @Override
    public void close() {
        audioQueue.clear();
    }
    
    // Call this to queue audio data
    public void queueAudio(byte[] opusFrame) {
        audioQueue.offer(opusFrame);
    }
}
```

Then set it on the VoiceManager:

```java
MyAudioProvider provider = new MyAudioProvider();
voiceManager.setAudioProvider(guildId, provider);
```

## Receiving Audio

To receive audio from users, implement `AudioReceiver`:

```java
public class MyAudioReceiver implements AudioReceiver {
    
    @Override
    public void receiveUserAudio(long userId, byte[] audioData) {
        // Process audio from this user
        // audioData is Opus-encoded
    }
    
    @Override
    public void onUserSpeaking(long userId, boolean speaking) {
        // Called when a user starts/stops speaking
    }
    
    @Override
    public void close() {
        // Clean up resources
    }
}
```

Set it on the VoiceManager:

```java
voiceManager.setAudioReceiver(guildId, new MyAudioReceiver());
```

## Providing Your Own DAVE Implementation

If you need to provide DAVE (e.g., for a self-contained audio plugin):

```java
public class MyDAVEProvider implements DAVEProvider {
    
    private JDaveSessionFactory sessionFactory;
    private boolean initialized = false;
    
    @Override
    public String getName() {
        return "MyPluginDAVE";
    }
    
    @Override
    public String getVersion() {
        return "1.0.0";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            Class.forName("club.minnced.jdave.JDaveSessionFactory");
            return Runtime.version().feature() >= 25;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    @Override
    public int getRequiredJavaVersion() {
        return 25;
    }
    
    @Override
    public void initialize() throws DAVEException {
        if (initialized) return;
        
        try {
            sessionFactory = new JDaveSessionFactory();
            initialized = true;
        } catch (Exception e) {
            throw new DAVEException("Failed to initialize DAVE", e);
        }
    }
    
    @Override
    public void shutdown() {
        sessionFactory = null;
        initialized = false;
    }
    
    @Override
    public Object getNativeImplementation() {
        return sessionFactory;
    }
}
```

Register it during plugin initialization:

```java
@Override
public void initialize(PluginContext context) {
    MyDAVEProvider daveProvider = new MyDAVEProvider();
    context.getVoiceManager().registerDAVEProvider("MyPlugin", daveProvider);
}

@Override
public void shutdown(PluginContext context) {
    context.getVoiceManager().unregisterDAVEProvider("MyPlugin");
}
```

## Integrating with LavaPlayer

For music bots, use LavaPlayer:

```java
// In your plugin
AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
AudioSourceManagers.registerRemoteSources(playerManager);

// Create a player for a guild
AudioPlayer player = playerManager.createPlayer();

// Bridge LavaPlayer to Pudel
public class LavaPlayerBridge implements AudioProvider {
    private final AudioPlayer player;
    private final MutableAudioFrame frame;
    private final ByteBuffer buffer;
    
    public LavaPlayerBridge(AudioPlayer player) {
        this.player = player;
        this.buffer = ByteBuffer.allocate(1024);
        this.frame = new MutableAudioFrame();
        this.frame.setBuffer(buffer);
    }
    
    @Override
    public boolean canProvide() {
        return player.provide(frame);
    }
    
    @Override
    public byte[] provide20MsAudio() {
        buffer.flip();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        buffer.clear();
        return data;
    }
    
    @Override
    public boolean isOpus() {
        return true;
    }
}

// Use it
voiceManager.setAudioProvider(guildId, new LavaPlayerBridge(player));
```

## Dependencies

Add these to your plugin's `pom.xml`:

```xml
<!-- JDAVE (Java 25+) -->
<dependency>
    <groupId>club.minnced</groupId>
    <artifactId>jdave</artifactId>
    <version>0.1.2</version>
</dependency>

<!-- LavaPlayer for audio playback -->
<dependency>
    <groupId>dev.arbjerg</groupId>
    <artifactId>lavaplayer</artifactId>
    <version>2.2.6</version>
</dependency>

<!-- YouTube source -->
<dependency>
    <groupId>dev.lavalink.youtube</groupId>
    <artifactId>common</artifactId>
    <version>1.16.0</version>
</dependency>
```

## Best Practices

### 1. Always Check DAVE Availability

```java
if (!voiceManager.isDAVEAvailable(guildId)) {
    // Handle gracefully - don't just fail
    return;
}
```

### 2. Handle Connection States

```java
voiceManager.connect(guildId, channelId)
    .thenAccept(status -> {
        switch (status) {
            case CONNECTED -> handleConnected();
            case DAVE_REQUIRED -> handleNoDAVE();
            case DAVE_ERROR -> handleDAVEError();
            case NO_PERMISSION -> handleNoPermission();
            case ERROR -> handleGenericError();
        }
    });
```

### 3. Clean Up Resources

```java
@Override
public void onDisable(PluginContext context) {
    // Disconnect from all voice channels
    for (Long guildId : activeGuilds) {
        context.getVoiceManager().disconnect(guildId);
    }
    
    // Unregister DAVE provider if you provided one
    context.getVoiceManager().unregisterDAVEProvider("MyPlugin");
}
```

### 4. Log DAVE Status

```java
@Override
public void onEnable(PluginContext context) {
    VoiceManager voiceManager = context.getVoiceManager();
    DAVEProvider provider = voiceManager.getActiveDAVEProvider();
    
    if (provider != null) {
        context.log("info", "Using DAVE provider: " + provider.getName() + 
                   " v" + provider.getVersion());
    } else {
        context.log("warn", "No DAVE provider available - " +
                   "voice features will not work after " + 
                   voiceManager.getDAVEDeadline());
    }
}
```

## Troubleshooting

### VoiceConnectionStatus.DAVE_REQUIRED

The DAVE deadline has passed and no DAVE provider is registered.

**Solution:** Ensure `default-pudel` plugin bundle is loaded, or provide your own DAVE implementation.

### VoiceConnectionStatus.DAVE_ERROR

DAVE initialization failed.

**Solution:** Check logs for specific error. Common causes:
- Native library not found
- Java version mismatch
- Memory issues

### Audio Not Playing

1. Check `canProvide()` is returning `true`
2. Verify audio is Opus-encoded if `isOpus()` returns `true`
3. Ensure `setAudioProvider()` was called after connecting

## Example Project Structure

```
my-music-plugin/
├── pom.xml
└── src/main/java/com/example/
    ├── MyMusicPlugin.java          # Main plugin class
    ├── audio/
    │   ├── GuildPlayer.java        # Per-guild audio management
    │   ├── LavaPlayerBridge.java   # AudioProvider implementation
    │   └── TrackScheduler.java     # Queue management
    ├── command/
    │   ├── PlayCommand.java
    │   ├── SkipCommand.java
    │   └── ...
    └── dave/
        └── MyDAVEProvider.java     # Optional DAVE provider
```

## See Also

- [DAVE Protocol Documentation](DAVE_PROTOCOL.md)
- [Plugin Development Guide](COMMERCIAL_PLUGIN_GUIDE.md)
- [Pudel API Reference](API_SPECIFICATION.md)

