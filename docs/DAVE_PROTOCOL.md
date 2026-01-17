# DAVE Protocol - Discord Audio/Voice Encryption

## Overview

Starting **March 1st, 2026**, Discord requires all voice connections to use End-to-End Encryption (E2EE) via the **DAVE (Discord Audio/Voice Encryption)** protocol.

Pudel fully supports DAVE through a plugin-based architecture, allowing hosters to choose their preferred DAVE implementation.

## Why DAVE?

Discord announced in their [changelog](https://discord.com/developers/docs/change-log#deprecating-non-e2ee-voice-calls) that non-encrypted voice connections will be deprecated. This means:

- All bots using voice functionality must implement DAVE
- Voice connections without DAVE will fail after the deadline
- Plugin developers must ensure their audio plugins provide or use a DAVE implementation

## Supported Implementations

### JDAVE (Recommended for Java 25+)

[JDAVE](https://github.com/MinnDevelopment/jdave) is the official JDA-compatible implementation by Minn.

**Requirements:**
- Java 25 or higher
- Native libdave library (bundled with JDAVE)

**Usage:**
```java
JDABuilder.createLight(TOKEN)
    .setAudioModuleConfig(new AudioModuleConfig()
        .withDaveSessionFactory(new JDaveSessionFactory()))
    .build();
```

### libdave-jvm (For Java 8+)

[libdave-jvm](https://github.com/KyokoBot/libdave-jvm) provides broader Java version compatibility.

**Requirements:**
- Java 8 or higher
- Native libdave library

## Pudel's DAVE Architecture

### How It Works

1. **Plugin Registration**: Audio plugins register their DAVE provider with `VoiceManager.registerDAVEProvider()`
2. **Priority Selection**: If multiple providers are registered, Pudel selects the one with the highest Java version requirement
3. **Automatic Configuration**: JDA is configured to use the active DAVE provider for all voice connections

### Default Plugin Bundle

The `default-pudel` plugin bundle includes:

- `JDAVEProviderPlugin` - JDAVE wrapper for Java 25+
- `MusicPlugin` - Full music player that uses DAVE

### Plugin Developer Guide

If you're developing an audio plugin:

```java
public class MyAudioPlugin implements PudelPlugin {
    
    @Override
    public void onEnable(PluginContext context) {
        VoiceManager voiceManager = context.getVoiceManager();
        
        // Check DAVE availability before using voice
        if (!voiceManager.isDAVEAvailable(guildId)) {
            context.log("error", "DAVE not available - voice will fail");
            return;
        }
        
        // Connect to voice channel
        voiceManager.connect(guildId, channelId)
            .thenAccept(status -> {
                if (status == VoiceConnectionStatus.CONNECTED) {
                    // Voice connected with DAVE encryption
                }
            });
    }
}
```

### Providing Your Own DAVE Implementation

```java
public class CustomDAVEProvider implements DAVEProvider {
    
    @Override
    public String getName() {
        return "CustomDAVE";
    }
    
    @Override
    public String getVersion() {
        return "1.0.0";
    }
    
    @Override
    public boolean isAvailable() {
        // Check if your implementation can be used
        return true;
    }
    
    @Override
    public int getRequiredJavaVersion() {
        return 25; // or 8 for libdave-jvm based
    }
    
    @Override
    public void initialize() throws DAVEException {
        // Initialize your DAVE library
    }
    
    @Override
    public void shutdown() {
        // Clean up resources
    }
    
    @Override
    public Object getNativeImplementation() {
        // Return the DaveSessionFactory for JDA
        return yourSessionFactory;
    }
}

// Register it
context.getVoiceManager().registerDAVEProvider("MyPlugin", new CustomDAVEProvider());
```

## Configuration

### application.yml

```yaml
pudel:
  audio:
    enabled: true  # Set to false to disable all audio functionality
```

### Deadline Warnings

Pudel automatically logs warnings as the deadline approaches:

- **60 days before**: Warning logged at startup
- **30 days before**: Prominent warning banner
- **After deadline**: Info message that DAVE is now required

## Troubleshooting

### "DAVE implementation required"

This error means no DAVE provider is registered. Solutions:

1. Ensure `default-pudel` plugin is loaded
2. Check that Java 25+ is being used for JDAVE
3. Verify the JDAVE library is in the classpath

### "Failed to load native libdave library"

The native library couldn't be loaded. Solutions:

1. Ensure the native library is in `java.library.path`
2. Check platform compatibility (Windows/Linux/macOS)
3. Verify architecture match (x64/arm64)

### "JDAVE requires Java 25"

You're running an older Java version. Solutions:

1. Upgrade to Java 25+
2. Use `libdave-jvm` which supports Java 8+

## Timeline

| Date | Event |
|------|-------|
| 2024 | Discord announces DAVE requirement |
| Late 2024 | libdave C-interface released |
| 2025 | JDA 6 adds DAVE support |
| **March 1, 2026** | **DAVE required for all voice connections** |

## References

- [Discord DAVE Protocol Documentation](https://discord.com/developers/docs/topics/voice-connections#dave-protocol)
- [Discord Changelog - Deprecating Non-E2EE Voice](https://discord.com/developers/docs/change-log#deprecating-non-e2ee-voice-calls)
- [JDA DAVE Pull Request #2988](https://github.com/discord-jda/JDA/pull/2988)
- [JDAVE GitHub Repository](https://github.com/MinnDevelopment/jdave)
- [libdave-jvm GitHub Repository](https://github.com/KyokoBot/libdave-jvm)

