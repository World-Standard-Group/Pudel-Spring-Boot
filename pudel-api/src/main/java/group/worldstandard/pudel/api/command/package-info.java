/**
 * Text (prefix-based) command handling API.
 *
 * <p>This package provides interfaces and classes for creating and managing text-based
 * commands. Text commands are triggered by a configurable prefix (e.g., "!") followed
 * by the command name.</p>
 *
 * <p>The preferred approach is to use the {@link group.worldstandard.pudel.api.annotation.TextCommand}
 * annotation on methods in your plugin class. For dynamic command registration,
 * implement {@link group.worldstandard.pudel.api.command.TextCommandHandler}.</p>
 *
 * <h2>Key Components:</h2>
 * <ul>
 *   <li>{@link group.worldstandard.pudel.api.command.CommandContext} - Context provided to command handlers</li>
 *   <li>{@link group.worldstandard.pudel.api.command.CommandInfo} - Annotation for command metadata on handler classes</li>
 *   <li>{@link group.worldstandard.pudel.api.command.CommandRegister} - Interface for programmatic registration</li>
 *   <li>{@link group.worldstandard.pudel.api.command.TextCommandHandler} - Functional interface for command handling</li>
 * </ul>
 *
 * <h2>Annotation-based Command:</h2>
 * <pre>{@code
 * @Plugin(name = "MyPlugin", version = "1.0.0", author = "Author")
 * public class MyPlugin {
 *
 *     @TextCommand(name = "greet", description = "Greet a user",
 *             usage = "greet <name>", aliases = {"hi", "hello"})
 *     public void greet(CommandContext context) {
 *         String name = context.hasArgs() ? context.getArgsString() : "World";
 *         context.reply("Hello, " + name + "!");
 *     }
 * }
 * }</pre>
 *
 * <h2>Programmatic Registration:</h2>
 * <pre>{@code
 * @OnEnable
 * public void onEnable(PluginContext context) {
 *     context.registerCommand("ping", ctx -> ctx.reply("Pong!"));
 * }
 * }</pre>
 *
 * @since 2.3.0
 */
package group.worldstandard.pudel.api.command;