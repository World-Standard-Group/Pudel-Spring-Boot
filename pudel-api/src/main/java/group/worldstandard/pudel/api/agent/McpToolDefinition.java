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

import java.util.*;

/**
 * MCP-compatible tool definition with JSON Schema for parameters.
 * <p>
 * Extends the legacy ToolDefinition concept with proper MCP support:
 * - JSON Schema inputSchema for structured parameter definitions
 * - Standard MCP tool metadata
 * - Compatible with the Model Context Protocol specification
 */
public class McpToolDefinition {

    private final String name;
    private final String description;
    private final String inputSchema;
    private final String pluginId;
    private final List<String> keywords;
    private final boolean guildOnly;
    private final boolean dmOnly;
    private final AgentTool.ToolPermission permission;
    private final int priority;
    private final McpToolExecutor executor;

    private McpToolDefinition(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.inputSchema = builder.inputSchema;
        this.pluginId = builder.pluginId;
        this.keywords = builder.keywords != null ? List.copyOf(builder.keywords) : List.of();
        this.guildOnly = builder.guildOnly;
        this.dmOnly = builder.dmOnly;
        this.permission = builder.permission;
        this.priority = builder.priority;
        this.executor = builder.executor;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getInputSchema() { return inputSchema; }
    public String getPluginId() { return pluginId; }
    public List<String> getKeywords() { return keywords; }
    public boolean isGuildOnly() { return guildOnly; }
    public boolean isDmOnly() { return dmOnly; }
    public AgentTool.ToolPermission getPermission() { return permission; }
    public int getPriority() { return priority; }
    public McpToolExecutor getExecutor() { return executor; }

    /**
     * Execute this tool with the given context and parameters.
     *
     * @param context    the agent tool context the tool runs in (guild vs DM, etc.)
     * @param parameters the parameter map passed to the tool (may be empty)
     * @return the tool's textual output, or an error message if no executor is set
     */
    public String execute(AgentToolContext context, Map<String, Object> parameters) {
        if (executor != null) {
            return executor.execute(context, parameters);
        }
        return "Error: No executor for tool " + name;
    }

    /**
     * Check if this tool is available in the given context.
     *
     * @param context the agent tool context to evaluate availability against
     * @return true if the tool may run in the context (guild-only / DM-only honored)
     */
    public boolean isAvailableIn(AgentToolContext context) {
        if (guildOnly && !context.isGuild()) return false;
        if (dmOnly && context.isGuild()) return false;
        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private String inputSchema = "{\"type\":\"object\",\"properties\":{}}";
        private String pluginId = "builtin";
        private List<String> keywords = new ArrayList<>();
        private boolean guildOnly = false;
        private boolean dmOnly = false;
        private AgentTool.ToolPermission permission = AgentTool.ToolPermission.EVERYONE;
        private int priority = 0;
        private McpToolExecutor executor;

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder inputSchema(String inputSchema) { this.inputSchema = inputSchema; return this; }
        public Builder pluginId(String pluginId) { this.pluginId = pluginId; return this; }
        public Builder keywords(List<String> keywords) { this.keywords = keywords; return this; }
        public Builder guildOnly(boolean guildOnly) { this.guildOnly = guildOnly; return this; }
        public Builder dmOnly(boolean dmOnly) { this.dmOnly = dmOnly; return this; }
        public Builder permission(AgentTool.ToolPermission permission) { this.permission = permission; return this; }
        public Builder priority(int priority) { this.priority = priority; return this; }
        public Builder executor(McpToolExecutor executor) { this.executor = executor; return this; }

        public McpToolDefinition build() {
            Objects.requireNonNull(name, "Tool name is required");
            Objects.requireNonNull(description, "Tool description is required");
            return new McpToolDefinition(this);
        }
    }
}

