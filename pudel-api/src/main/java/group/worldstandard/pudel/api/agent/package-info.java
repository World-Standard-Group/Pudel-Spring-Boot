/**
 * AI Agent Tools API for plugin developers.
 *
 * <p>This package provides the interfaces and classes needed to create custom tools
 * that the AI agent can use when processing user requests. Plugins can register
 * tools that extend the agent's capabilities with domain-specific functionality.</p>
 *
 * <h2>Key Components:</h2>
 * <ul>
 *   <li>{@link group.worldstandard.pudel.api.agent.AgentTool} - Annotation for marking methods as agent tools</li>
 *   <li>{@link group.worldstandard.pudel.api.agent.AgentToolContext} - Context provided during tool execution</li>
 *   <li>{@link group.worldstandard.pudel.api.agent.AgentToolProvider} - Interface for tool provider classes</li>
 *   <li>{@link group.worldstandard.pudel.api.agent.AgentToolRegistry} - Registry for managing agent tools</li>
 *   <li>{@link group.worldstandard.pudel.api.agent.ToolDefinition} - Programmatic tool definition builder</li>
 *   <li>{@link group.worldstandard.pudel.api.agent.ToolResult} - Result of tool execution</li>
 * </ul>
 *
 * <h2>Creating a Tool:</h2>
 * <pre>{@code
 * public class WeatherTools implements AgentToolProvider {
 *
 *     @AgentTool(
 *         name = "get_weather",
 *         description = "Get the current weather for a location",
 *         keywords = {"weather", "temperature"}
 *     )
 *     public String getWeather(AgentToolContext context, String location) {
 *         return "Weather in " + location + ": Sunny, 25°C";
 *     }
 * }
 *
 * // Register in your plugin:
 * @OnEnable
 * public void onEnable(PluginContext context) {
 *     context.getAgentToolRegistry().registerProvider("my-plugin", new WeatherTools());
 * }
 * }</pre>
 *
 * @since 2.3.0
 */
package group.worldstandard.pudel.api.agent;