# Pudel Plugin Development Kit (PDK) - Commercial Plugin Guide

## License Overview

### Why You Can Create Proprietary Plugins

Pudel uses a **dual-license model with a Plugin Exception**:

1. **pudel-api (MIT License)** - The plugin SDK you code against
2. **pudel-core (AGPL v3 + Plugin Exception)** - The core bot

The key is the **Plugin Exception** in `PLUGIN_EXCEPTION` file:

> "As a special exception, plugins that use the Pudel Plugin API (pudel-api)
> to extend Pudel's functionality are NOT considered derivative works of
> Pudel, even when running in the same Java Virtual Machine process."

### What This Means for You

✅ Create proprietary/closed-source plugins  
✅ Sell plugins commercially  
✅ Create exclusive plugins for specific clients  
✅ Keep your source code completely private  
✅ Use any license you want for your plugin  
✅ No AGPL "viral" copyleft applies to your plugin  

### Legal Basis

This approach follows established precedents:
- **OpenJDK's Classpath Exception** - Allows proprietary Java applications
- **GCC Runtime Library Exception** - Allows proprietary compiled programs
- **WordPress Plugin Policy** - Allows proprietary WordPress plugins

## Creating a Commercial Plugin

### Step 1: Set Up Your Project

Create a new Maven/Gradle project and add the PDK dependency:

```xml
<dependency>
    <groupId>worldstandard.group</groupId>
    <artifactId>pudel-api</artifactId>
    <version>0.01-prototype</version>
    <scope>provided</scope>
</dependency>
```

### Step 2: Create Your Plugin Class

```java
package com.yourcompany.myplugin;

import worldstandard.group.pudel.api.PluginContext;
import worldstandard.group.pudel.api.PluginInfo;
import worldstandard.group.pudel.api.PudelPlugin;
import worldstandard.group.pudel.api.command.CommandContext;

@PluginInfo(
    name = "MyCommercialPlugin",
    version = "1.0.0",
    author = "Your Company",
    description = "A proprietary plugin for exclusive use"
)
public class MyCommercialPlugin implements PudelPlugin {

    private PluginContext context;

    @Override
    public void onEnable(PluginContext context) {
        this.context = context;
        
        // Register your commands
        context.registerCommand("mycommand", this::handleMyCommand);
        
        context.log("INFO", "MyCommercialPlugin enabled!");
    }

    @Override
    public void onDisable() {
        context.log("INFO", "MyCommercialPlugin disabled!");
    }

    private void handleMyCommand(CommandContext ctx) {
        ctx.reply("This is a proprietary command!");
    }
}
```

### Step 3: Package Your Plugin

Build your plugin as a JAR file:

```bash
mvn clean package
```

The JAR should contain:
- Your compiled classes
- `META-INF/MANIFEST.MF` (optional but recommended)
- Any resources your plugin needs

### Step 4: Distribute to Clients

You can distribute your plugin JAR to clients who will place it in their Pudel `plugins/` directory.

## Plugin Licensing Best Practices

### For Exclusive/Private Plugins

When creating exclusive plugins for specific clients:

1. **License Agreement**: Create a license agreement that specifies:
   - Single installation per license
   - No redistribution rights
   - Support terms
   - Update policy

2. **License Validation** (Optional): Implement license checking:

```java
@Override
public void onEnable(PluginContext context) {
    if (!validateLicense()) {
        context.log("ERROR", "Invalid license key!");
        throw new RuntimeException("License validation failed");
    }
    // Continue initialization
}

private boolean validateLicense() {
    // Implement your license validation logic
    // Could check against a license server, validate a key file, etc.
    return true;
}
```

3. **Code Obfuscation** (Optional): Use tools like ProGuard to protect your code:

```xml
<plugin>
    <groupId>com.github.wvengen</groupId>
    <artifactId>proguard-maven-plugin</artifactId>
    <version>2.6.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>proguard</goal></goals>
        </execution>
    </executions>
    <configuration>
        <proguardInclude>proguard.conf</proguardInclude>
    </configuration>
</plugin>
```

### For Marketplace Plugins

When publishing to the Pudel Plugin Marketplace:

1. **Source Code Link**: Marketplace requires a link to source code for transparency
2. **Clear Licensing**: State your plugin's license clearly
3. **Documentation**: Provide installation and usage instructions

## Event System for Commercial Plugins

Commercial plugins can use the full event system:

```java
@Override
public void onEnable(PluginContext context) {
    // Register event listeners
    context.registerListener(new MyEventListener());
    
    // Or use typed listeners
    context.registerEventListener(new PluginEventListener<MessageReceivedEvent>() {
        @Override
        public Class<MessageReceivedEvent> getEventType() {
            return MessageReceivedEvent.class;
        }
        
        @Override
        public void onEvent(MessageReceivedEvent event) {
            // Handle message events
        }
    });
}
```

## FAQ

### Q: Do I need to share my plugin's source code?
**A:** No. The Plugin Exception explicitly states that plugins are NOT derivative works of the AGPL-licensed core. Combined with the MIT-licensed PDK, you have no obligation to share source code.

### Q: But my plugin runs in the same JVM as the AGPL core - doesn't that make it a derivative work?
**A:** No. The Plugin Exception specifically addresses this concern:
> "...a plugin that meets the above criteria is NOT considered a 'derivative work' or 'work based on' pudel-core under the terms of the GNU Affero General Public License, **even though such plugin may run in the same Java Virtual Machine process as pudel-core**."

### Q: Can I charge for my plugins?
**A:** Yes. You can sell plugins at any price point, including one-time purchases, subscriptions, or exclusive licenses.

### Q: Can clients resell my plugin?
**A:** That depends on your license agreement with them. Standard practice is to prohibit redistribution.

### Q: What if I modify pudel-core?
**A:** Modifications to pudel-core (not pudel-api) are subject to AGPL v3 and must be open source. The Plugin Exception does NOT apply to core modifications.

### Q: What counts as "only using pudel-api interfaces"?
**A:** Your plugin should:
- Import only from `worldstandard.group.pudel.api.*`
- Not copy/include source code from `pudel-core`
- Be loaded through Pudel's plugin loading mechanism
- Interact with the core only through the public API

### Q: Is this Plugin Exception legally valid?
**A:** Yes. Copyright holders can grant additional permissions beyond what the base license provides. This is exactly what OpenJDK, GCC, and other major projects do. The FSF (creators of GPL) explicitly supports such exceptions.

### Q: Can I use pudel-core as a library in my commercial bot?
**A:** Using pudel-core as-is (without modification) is fine. Modifications require AGPL compliance.

## Legal Notice

This guide provides general information about licensing. For specific legal questions, consult a qualified attorney.

The MIT license for pudel-api is provided to encourage commercial plugin development while keeping the core bot open source for the community.

## Support

For PDK support and commercial licensing questions:
- Create an issue on the repository
- Contact the project maintainers

---

*Pudel Plugin API (PDK) - Copyright (c) 2026 Napapon Kamanee - MIT License*

