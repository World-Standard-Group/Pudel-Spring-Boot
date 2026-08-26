/*
 * Pudel Plugin API (PDK) - Plugin Development Kit for Pudel Discord Bot
 * Copyright (c) 2026 World Standard Group
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR,
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package group.worldstandard.pudel.api.agent;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Registry for MCP (Model Context Protocol) tools.
 * <p>
 * Manages the lifecycle of MCP tools provided by plugins and built-in sources.
 * Tools can be registered, unregistered, searched, and executed through this registry.
 * <p>
 * This replaces the legacy {@link AgentToolRegistry} with MCP-compatible tool management.
 */
public interface McpToolRegistry {

    /**
     * Register an MCP tool definition.
     *
     * @param pluginId   the plugin ID registering the tool
     * @param definition the tool definition
     * @return true if registered successfully, false if a tool with this name already exists
     */
    boolean registerTool(String pluginId, McpToolDefinition definition);

    /**
     * Unregister a tool by name.
     *
     * @param toolName the tool name
     * @return true if the tool was found and removed
     */
    boolean unregisterTool(String toolName);

    /**
     * Unregister all tools from a plugin.
     *
     * @param pluginId the plugin ID
     * @return the number of tools unregistered
     */
    int unregisterAll(String pluginId);

    /**
     * Get a tool definition by name.
     *
     * @param toolName the unique name of the tool to retrieve
     * @return an {@link Optional} containing the tool definition if registered, otherwise empty
     */
    Optional<McpToolDefinition> getTool(String toolName);

    /**
     * Get all registered tools.
     */
    Collection<McpToolDefinition> getAllTools();

    /**
     * Get all tools registered by a specific plugin.
     *
     * @param pluginId the identifier of the plugin whose tools should be returned
     * @return the collection of tool definitions owned by the plugin
     */
    Collection<McpToolDefinition> getToolsByPlugin(String pluginId);

    /**
     * Search tools by keyword (matches name, description, or keywords).
     *
     * @param keyword the search term to match against tool metadata
     * @return the collection of tool definitions matching the keyword
     */
    Collection<McpToolDefinition> searchTools(String keyword);

    /**
     * Check if a tool is registered.
     *
     * @param toolName the name of the tool to check
     * @return true if a tool with the given name is registered
     */
    boolean hasTool(String toolName);

    /**
     * Get the total number of registered tools.
     */
    int getToolCount();

    /**
     * Execute a tool by name.
     *
     * @param toolName  the tool name
     * @param context   the execution context
     * @param parameters the tool parameters
     * @return the tool's result
     */
    ToolResult executeTool(String toolName, AgentToolContext context, Map<String, Object> parameters);
}
