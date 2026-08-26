/**
 * Annotations for declaring plugin metadata, commands, and event handlers.
 *
 * <p>This package contains all annotations used to declaratively define plugin
 * behavior. Using annotations is the preferred approach for plugin development
 * as it simplifies registration and lifecycle management.</p>
 *
 * <h2>Plugin Declaration:</h2>
 * <ul>
 *   <li>{@link group.worldstandard.pudel.api.annotation.Plugin} - Marks a class as a plugin entrypoint</li>
 * </ul>
 *
 * <h2>Command Annotations:</h2>
 * <ul>
 *   <li>{@link group.worldstandard.pudel.api.annotation.Choice} - Defines predefined choices for command options</li>
 *   <li>{@link group.worldstandard.pudel.api.annotation.CommandOption} - Defines command options/parameters</li>
 *   <li>{@link group.worldstandard.pudel.api.annotation.ContextMenu} - Declares a context menu handler</li>
 *   <li>{@link group.worldstandard.pudel.api.annotation.SlashCommand} - Declares a slash command handler</li>
 *   <li>{@link group.worldstandard.pudel.api.annotation.Subcommand} - Defines subcommands for slash commands</li>
 *   <li>{@link group.worldstandard.pudel.api.annotation.TextCommand} - Declares a text (prefix) command handler</li>
 * </ul>
 *
 * <h2>Interaction Handler Annotations:</h2>
 * <ul>
 *   <li>{@link group.worldstandard.pudel.api.annotation.ButtonHandler} - Handles button interactions</li>
 *   <li>{@link group.worldstandard.pudel.api.annotation.ModalHandler} - Handles modal submissions</li>
 *   <li>{@link group.worldstandard.pudel.api.annotation.SelectMenuHandler} - Handles select menu interactions</li>
 * </ul>
 *
 * <h2>Lifecycle Annotations:</h2>
 * <ul>
 *   <li>{@link group.worldstandard.pudel.api.annotation.OnDisable} - Called when plugin is disabled</li>
 *   <li>{@link group.worldstandard.pudel.api.annotation.OnEnable} - Called when plugin is enabled</li>
 *   <li>{@link group.worldstandard.pudel.api.annotation.OnShutdown} - Called when bot is shutting down</li>
 * </ul>
 *
 * <h2>Example Usage:</h2>
 * <pre>{@code
 * @Plugin(name = "MyPlugin", version = "1.0.0", author = "Author")
 * public class MyPlugin {
 *
 *     @OnEnable
 *     public void onEnable(PluginContext context) {
 *         // Plugin initialization
 *     }
 *
 *     @SlashCommand(name = "ping", description = "Check latency")
 *     public void ping(SlashCommandInteractionEvent event) {
 *         event.reply("Pong!").queue();
 *     }
 * }
 * }</pre>
 *
 * @since 2.3.0
 */
package group.worldstandard.pudel.api.annotation;