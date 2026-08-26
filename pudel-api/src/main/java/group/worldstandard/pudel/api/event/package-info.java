/**
 * Event handling and listener registration API.
 *
 * <p>This package provides interfaces and classes for registering event listeners
 * to handle Discord events. The preferred approach is to create a listener class
 * that implements {@link group.worldstandard.pudel.api.event.Listener} and uses
 * {@link group.worldstandard.pudel.api.event.EventHandler} annotated methods.</p>
 *
 * <h2>Key Components:</h2>
 * <ul>
 *   <li>{@link group.worldstandard.pudel.api.event.EventHandler} - Annotation for event handler methods</li>
 *   <li>{@link group.worldstandard.pudel.api.event.EventManager} - Manager for listener registration</li>
 *   <li>{@link group.worldstandard.pudel.api.event.EventPriority} - Enum for handler priority</li>
 *   <li>{@link group.worldstandard.pudel.api.event.Listener} - Marker interface for listener classes</li>
 *   <li>{@link group.worldstandard.pudel.api.event.PluginEventListener} - Typed event listener interface</li>
 * </ul>
 *
 * <h2>Annotation-based Event Handling (Preferred):</h2>
 * <pre>{@code
 * public class MyListener implements Listener {
 *
 *     @EventHandler(priority = EventPriority.NORMAL)
 *     public void onMessageReceived(MessageReceivedEvent event) {
 *         // Handle message
 *     }
 *
 *     @EventHandler
 *     public void onReactionAdd(MessageReactionAddEvent event) {
 *         // Handle reaction
 *     }
 * }
 *
 * // Register in your plugin:
 * @OnEnable
 * public void onEnable(PluginContext context) {
 *     context.registerListener(new MyListener());
 * }
 * }</pre>
 *
 * <h2>Programmatic Registration:</h2>
 * <pre>{@code
 * @OnEnable
 * public void onEnable(PluginContext context) {
 *     context.registerEventListener((MessageReactionAddEvent event) -> {
 *         // Handle reaction
 *     });
 * }
 * }</pre>
 *
 * @since 2.3.0
 */
package group.worldstandard.pudel.api.event;