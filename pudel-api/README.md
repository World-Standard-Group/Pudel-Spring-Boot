# Pudel Plugin API (PDK) - Plugin Development Kit

**Version:** 1.0.0  
**License:** MIT (allows proprietary plugins)

The Pudel Plugin API provides everything you need to create plugins for the Pudel Discord Bot.

---

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>worldstandard.group</groupId>
    <artifactId>pudel-api</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

**Important:** Use `<scope>provided</scope>` - the API is provided by the Pudel runtime.

### 2. Create Your Plugin

```java
public class MyPlugin implements PudelPlugin {
    
    @Override
    public PluginInfo getPluginInfo() {
        return new PluginInfo("MyPlugin", "1.0.0", "Your Name", "My awesome plugin");
    }

    @Override
    public void onEnable(PluginContext ctx) {
        ctx.registerCommand("greet", command -> {
            command.reply("Hello, " + command.getUser().getName() + "!");
        });
        ctx.log("INFO", "MyPlugin enabled!");
    }
}
```

### 3. Create plugin.yml

Create `src/main/resources/plugin.yml`:

```yaml
name: MyPlugin
version: 1.0.0
main: com.example.MyPlugin
author: Your Name
description: My awesome plugin
```

### 4. Build & Deploy

```bash
mvn clean package
cp target/my-plugin.jar /path/to/pudel/plugins/
```

---

## Minimal POM Template

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>my-pudel-plugin</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- Pudel API - PROVIDED by runtime -->
        <dependency>
            <groupId>worldstandard.group</groupId>
            <artifactId>pudel-api</artifactId>
            <version>1.0.0</version>
            <scope>provided</scope>
        </dependency>

        <!-- JDA - PROVIDED by runtime -->
        <dependency>
            <groupId>net.dv8tion</groupId>
            <artifactId>JDA</artifactId>
            <version>5.3.1</version>
            <scope>provided</scope>
        </dependency>
        
        <!-- Your own dependencies - use compile scope -->
        <!-- Example:
        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
            <version>2.10.1</version>
        </dependency>
        -->
    </dependencies>
</project>
```

---

## Dependency Scope Guide

| Dependency Type | Scope | Reason |
|-----------------|-------|--------|
| `pudel-api` | `provided` | Included in Pudel runtime |
| `JDA` | `provided` | Included in Pudel runtime |
| `slf4j-api` | `provided` | Included in Pudel runtime |
| Your libraries | `compile` | Bundled in your plugin JAR |

**Rule:** If Pudel already has it, use `provided`. If it's your own dependency, use `compile`.

---

## Plugin Lifecycle

```
┌─────────────────────────────────────────────────────────┐
│  JAR loaded → initialize() → [plugin ready but disabled] │
│                                                          │
│  enable command → onEnable() → [plugin active]           │
│                                                          │
│  disable command → onDisable() → [plugin paused]         │
│                                                          │
│  unload/shutdown → shutdown() → [plugin destroyed]       │
└─────────────────────────────────────────────────────────┘
```

### Lifecycle Methods

| Method | When Called | What To Do |
|--------|-------------|------------|
| `initialize()` | JAR loaded | Create database connections, load configs |
| `onEnable()` | Plugin enabled | Register commands, start services |
| `onDisable()` | Plugin disabled | Unregister commands, pause services |
| `shutdown()` | Bot shutting down | Close connections, cleanup resources |

---

## PluginContext API

The `PluginContext` provides access to bot services:

```java
public void onEnable(PluginContext ctx) {
    // Get JDA instance
    JDA jda = ctx.getJDA();
    
    // Get bot user
    User botUser = ctx.getBotUser();
    
    // Get a guild
    Guild guild = ctx.getGuild(123456789L);
    
    // Register a command
    ctx.registerCommand("ping", cmd -> cmd.reply("Pong!"));
    
    // Register event listener
    ctx.registerListener(new MyListener());
    
    // Access voice manager (requires DAVE after March 2026)
    VoiceManager voice = ctx.getVoiceManager();
    
    // Logging
    ctx.log("INFO", "Plugin started!");
    ctx.log("ERROR", "Something went wrong", exception);
}
```

---

## Command Handling

### Simple Command

```java
ctx.registerCommand("hello", command -> {
    command.reply("Hello, " + command.getUser().getName() + "!");
});
```

### Command with Arguments

```java
ctx.registerCommand("say", command -> {
    String[] args = command.getArgs();
    if (args.length == 0) {
        command.reply("Usage: !say <message>");
        return;
    }
    command.reply(String.join(" ", args));
});
```

### Embed Response

```java
ctx.registerCommand("info", command -> {
    EmbedBuilder embed = new EmbedBuilder()
        .setTitle("Server Info")
        .setDescription("Information about this server")
        .addField("Members", String.valueOf(command.getGuild().getMemberCount()), true)
        .setColor(Color.BLUE);
    
    command.replyEmbed(embed.build());
});
```

---

## Event Handling

### Using Annotations

```java
public class MyListener implements Listener {
    
    @EventHandler
    public void onMessage(MessageReceivedEvent event) {
        if (event.getMessage().getContentRaw().contains("hello")) {
            event.getChannel().sendMessage("Hi there!").queue();
        }
    }
    
    @EventHandler
    public void onReaction(MessageReactionAddEvent event) {
        // Handle reaction
    }
}

// Register in onEnable
ctx.registerListener(new MyListener());
```

### Using Lambda

```java
ctx.registerEventListener(new PluginEventListener<MessageReceivedEvent>() {
    @Override
    public Class<MessageReceivedEvent> getEventType() {
        return MessageReceivedEvent.class;
    }
    
    @Override
    public void onEvent(MessageReceivedEvent event) {
        // Handle event
    }
});
```

---

## Voice & Audio (DAVE Required)

> ⚠️ **Important:** Starting March 1st, 2026, Discord requires DAVE (Discord Audio/Voice Encryption) for all voice connections.

### Check DAVE Availability

```java
VoiceManager voice = ctx.getVoiceManager();

if (voice.isDAVEAvailable()) {
    // Safe to use voice
} else {
    command.reply("Voice encryption (DAVE) not available!");
}
```

### Provide DAVE Implementation

If your plugin provides DAVE support:

```java
public void initialize(PluginContext ctx) {
    VoiceManager voice = ctx.getVoiceManager();
    voice.registerDAVEProvider(new MyDAVEProvider());
}
```

See [DAVE_PROTOCOL.md](/docs/DAVE_PROTOCOL.md) for implementation details.

---

## Hot-Reload Support

Pudel supports hot-reload for plugins:

1. **Disabled plugins**: Updates are applied immediately when JAR changes
2. **Enabled plugins**: Updates are queued until plugin is disabled or bot restarts
3. **Hash detection**: Uses SHA-256 to detect file changes

To support hot-reload gracefully:

```java
@Override
public void shutdown(PluginContext ctx) {
    // Clean up ALL resources
    // This is called before hot-reload
    executor.shutdown();
    database.close();
    cache.clear();
}
```

---

## Best Practices

### DO ✅

- Use `provided` scope for pudel-api and JDA
- Clean up resources in `shutdown()`
- Handle errors gracefully
- Use async operations for I/O
- Cache frequently used data
- Log important events

### DON'T ❌

- Include pudel-api or JDA in your fat JAR
- Block the main thread
- Store static references to JDA/Guild objects
- Ignore exceptions
- Hardcode configuration values

---

## Plugin Discovery

Pudel finds your plugin main class using (in order):

1. **MANIFEST.MF**: `Plugin-Main: com.example.MyPlugin`
2. **plugin.yml**: `main: com.example.MyPlugin`
3. **Class scanning**: Classes ending in `Plugin`, `Bundle`, `Main`

Recommended: Use the manifest approach with maven-jar-plugin.

---

## Debugging

### Enable Debug Logging

In `application.yml`:

```yaml
logging:
  level:
    worldstandard.group: DEBUG
```

### Check Plugin Status

```bash
# Via REST API
curl http://localhost:8080/api/plugins
```

### Common Issues

| Issue | Solution |
|-------|----------|
| Plugin not found | Check `Plugin-Main` in MANIFEST or plugin.yml |
| ClassNotFoundException | Missing dependency - add to pom.xml |
| NoClassDefFoundError | Dependency scope wrong - use `compile` not `provided` |
| Plugin won't enable | Check logs for initialization errors |

---

## Example Plugins

See the `/examples/` directory for complete plugin examples:

- `PingPlugin.java` - Minimal plugin example
- `MusicPlayerPlugin.java` - Audio playback with LavaPlayer
- `ReactionRolesPlugin.java` - Reaction role management
- `AuditLogPlugin.java` - Server audit logging

---

## License

The Pudel Plugin API is licensed under **MIT License**, allowing you to:

- ✅ Create proprietary/commercial plugins
- ✅ Distribute without source code
- ✅ Modify and fork
- ✅ Use in any project

**Note:** The core Pudel bot is AGPL-3.0 licensed, but plugins using only the PDK are not considered derivative works.

---

## Support

- **Documentation**: [TBA]({TBA})
- **Discord**: Join our support server
- **GitHub**: Report issues and contribute

---

## Version History

| Version | Changes |
|---------|---------|
| 1.0.0 | Initial stable release |

