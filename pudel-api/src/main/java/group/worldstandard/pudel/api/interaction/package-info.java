/**
 * Discord interaction handling API (slash commands, buttons, modals, etc.).
 *
 * <p>This package provides interfaces for registering and managing Discord interactions.
 * Interactions are the modern way to create Discord bot features, including slash commands,
 * context menus, buttons, select menus, modals, and autocomplete.</p>
 *
 * <h2>Key Components:</h2>
 * <ul>
 *   <li>{@link group.worldstandard.pudel.api.interaction.AutoCompleteHandler} - Handler for autocomplete</li>
 *   <li>{@link group.worldstandard.pudel.api.interaction.ButtonHandler} - Handler for button clicks</li>
 *   <li>{@link group.worldstandard.pudel.api.interaction.ContextMenuHandler} - Handler for context menus</li>
 *   <li>{@link group.worldstandard.pudel.api.interaction.InteractionManager} - Main manager for all interaction types</li>
 *   <li>{@link group.worldstandard.pudel.api.interaction.ModalHandler} - Handler for modal submissions</li>
 *   <li>{@link group.worldstandard.pudel.api.interaction.SelectMenuHandler} - Handler for select menu interactions</li>
 *   <li>{@link group.worldstandard.pudel.api.interaction.SlashCommandHandler} - Handler for slash commands</li>
 * </ul>
 *
 * <h2>Slash Command Example:</h2>
 * <pre>{@code
 * public class PingCommand implements SlashCommandHandler {
 *     @Override
 *     public String getName() { return "ping"; }
 *
 *     @Override
 *     public String getDescription() { return "Check bot latency"; }
 *
 *     @Override
 *     public void handle(SlashCommandInteractionEvent event) {
 *         long ping = event.getJDA().getGatewayPing();
 *         event.reply("Pong! " + ping + "ms").queue();
 *     }
 * }
 *
 * // Register in your plugin:
 * @OnEnable
 * public void onEnable(PluginContext context) {
 *     InteractionManager manager = context.getInteractionManager();
 *     manager.registerSlashCommand("my-plugin", new PingCommand());
 *     manager.syncCommands();
 * }
 * }</pre>
 *
 * <h2>Button Handler Example:</h2>
 * <pre>{@code
 * public class ConfirmButton implements ButtonHandler {
 *     @Override
 *     public String getIdPrefix() { return "confirm"; }
 *
 *     @Override
 *     public void handle(ButtonInteractionEvent event) {
 *         event.reply("Confirmed!").queue();
 *     }
 * }
 * }</pre>
 *
 * @since 2.3.0
 */
package group.worldstandard.pudel.api.interaction;