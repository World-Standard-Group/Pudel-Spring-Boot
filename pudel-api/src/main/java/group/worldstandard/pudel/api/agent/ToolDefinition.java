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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package group.worldstandard.pudel.api.agent;

import java.util.*;
import java.util.function.BiFunction;

/**
 * Definition of an agent tool.
 * <p>
 * A tool definition contains all metadata about a tool and its executor.
 * Tools can be created either from @AgentTool annotated methods or
 * programmatically using the builder.
 * <p>
 * Example using builder:
 * <pre>
 * {@code
 * ToolDefinition tool = ToolDefinition.builder()
 *     .name("search_web")
 *     .description("Search the web for information")
 *     .parameter("query", String.class, "The search query", true)
 *     .parameter("limit", Integer.class, "Max results", false)
 *     .keyword("search")
 *     .keyword("find")
 *     .keyword("lookup")
 *     .executor((context, params) -> {
 *         String query = (String) params.get("query");
 *         int limit = (Integer) params.getOrDefault("limit", 5);
 *         return "Results for: " + query;
 *     })
 *     .build();
 * }
 * </pre>
 */
public final class ToolDefinition {
    /**
     * The unique name of the tool.
     * This identifier is used to reference the tool within the system and by the AI agent.
     * It should be descriptive and unique within the scope of the plugin or application.
     */
    private final String name;
    /**
     * A human-readable description of the tool's functionality.
     * This description is used by AI agents to understand the purpose and behavior of the tool,
     * enabling them to determine when it is appropriate to invoke this tool during execution.
     * The description should clearly explain what the tool does, its intended use cases,
     * and any relevant contextual information that may influence its application.
     */
    private final String description;
    /**
     * Unique identifier of the plugin that provides this tool.
     * This field stores the plugin ID as a string and is immutable once set.
     * It is used to associate the tool with its originating plugin for tracking and management purposes.
     */
    private final String pluginId;
    /**
     * The list of parameters defined for this tool.
     * Each parameter specifies its name, type, description, whether it is required,
     * and its default value if applicable.
     */
    private final List<ToolParameter> parameters;
    /**
     * A set of keywords associated with this tool definition.
     * These keywords are used by the AI agent to identify and match user intent
     * when determining which tool to invoke. The keywords help improve
     * the accuracy and relevance of tool selection during agent execution.
     * This field is immutable and initialized as an empty set if no keywords are provided.
     */
    private final Set<String> keywords;
    /**
     * Indicates whether this tool can only be used within a guild context.
     * When set to true, the tool will be restricted to execution within guild channels only.
     * If false, the tool may be executed in both guild and direct message contexts,
     * unless restricted by other conditions such as dmOnly.
     */
    private final boolean guildOnly;
    /**
     * Indicates whether this tool can only be used in direct message contexts.
     * When set to true, the tool will be restricted to direct message channels
     * and will not be available in guild channels.
     */
    private final boolean dmOnly;
    /**
     * The permission level required to execute this tool.
     * This determines which users are allowed to invoke the tool based on their permissions
     * within the context where the tool is being executed.
     */
    private final AgentTool.ToolPermission permission;
    /**
     * The priority level of this tool definition.
     * Tools with higher priority values are preferred by the AI agent when multiple tools match a given request.
     * This value is used to resolve conflicts when several tools could potentially handle the same user input.
     * Defaults to 0 if not explicitly set.
     */
    private final int priority;
    /**
     * The executor function responsible for performing the actual tool logic.
     * This function takes an {@link AgentToolContext} and a map of parameters,
     * then returns the result of the tool's execution as a string.
     * <p>
     * The context provides environmental and permission details relevant to the tool's operation,
     * while the parameters contain the input data required for the tool to perform its task.
     */
    private final BiFunction<AgentToolContext, Map<String, Object>, String> executor;

    /**
     * Constructs a ToolDefinition instance using the provided builder.
     * This constructor initializes all fields from the builder and creates immutable copies of collections.
     *
     * @param builder the builder containing all necessary configuration for the tool definition
     */
    private ToolDefinition(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.pluginId = builder.pluginId;
        this.parameters = List.copyOf(builder.parameters);
        this.keywords = Set.copyOf(builder.keywords);
        this.guildOnly = builder.guildOnly;
        this.dmOnly = builder.dmOnly;
        this.permission = builder.permission;
        this.priority = builder.priority;
        this.executor = builder.executor;
    }

    // Getters

    /**
     * Returns the name of this object.
     *
     * @return the name value
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the description of this tool definition.
     * The description provides a human-readable explanation of what the tool does
     * and is used by the AI agent to determine when to use this tool.
     *
     * @return the description string
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the unique identifier of the plugin that provides this tool.
     *
     * @return the plugin ID as a String
     */
    public String getPluginId() {
        return pluginId;
    }

    /**
     * Returns the list of parameters defined for this tool.
     * Each parameter specifies its name, type, description, whether it is required,
     * and its default value if applicable.
     *
     * @return a list of ToolParameter objects representing the tool's parameters
     */
    public List<ToolParameter> getParameters() {
        return parameters;
    }

    /**
     * Returns the set of keywords associated with this tool definition.
     * Keywords are used by the AI agent to identify and match user intent
     * when determining which tool to invoke. These keywords help improve
     * the accuracy and relevance of tool selection during agent execution.
     *
     * @return a set of keyword strings associated with this tool
     */
    public Set<String> getKeywords() {
        return keywords;
    }

    /**
     * Indicates whether this tool can only be used within a guild context.
     * When true, the tool is restricted to execution in guild channels only.
     * @return true if the tool requires a guild context, false otherwise
     */
    public boolean isGuildOnly() {
        return guildOnly;
    }

    /**
     * Indicates whether this tool can only be used within a direct message context.
     * When true, the tool is restricted to execution in private channels only.
     * @return true if the tool requires a direct message context, false otherwise
     */
    public boolean isDmOnly() {
        return dmOnly;
    }

    /**
     * Returns the permission level required to use this tool.
     * The permission level determines which users are allowed to execute the tool
     * based on their role or privileges within the guild or system.
     *
     * @return the required permission level as defined in {@link AgentTool.ToolPermission}
     */
    public AgentTool.ToolPermission getPermission() {
        return permission;
    }

    /**
     * Returns the priority level assigned to this tool definition.
     * The priority determines the order in which tools are considered
     * when multiple tools match a given request. Higher values indicate
     * higher priority and thus a greater likelihood of being selected.
     *
     * @return the priority level as an integer
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Execute this tool with the given context and parameters.
     *
     * @param context the execution context
     * @param parameters the tool parameters
     * @return the tool result as a string
     */
    public String execute(AgentToolContext context, Map<String, Object> parameters) {
        if (executor == null) {
            throw new IllegalStateException("Tool '" + name + "' has no executor");
        }
        return executor.apply(context, parameters);
    }

    /**
     * Determines if this tool is available for use within the specified execution context.
     * Availability is determined by the tool's configuration for guild-only or DM-only usage.
     * If the tool is configured as guild-only, it will only be available in guild contexts.
     * If configured as DM-only, it will only be available in direct message contexts.
     *
     * @param context the execution context in which tool availability is being checked
     * @return true if the tool can be used in the given context, false otherwise
     */
    public boolean isAvailableIn(AgentToolContext context) {
        if (guildOnly && !context.isGuild()) {
            return false;
        }
        if (dmOnly && context.isGuild()) {
            return false;
        }
        return true;
    }

    /**
     * Creates and returns a new instance of the {@link Builder} class for constructing
     * {@link ToolDefinition} instances. The builder provides a fluent API for setting
     * various properties of the tool definition such as name, description, plugin ID,
     * parameters, keywords, context restrictions, permissions, priority, and execution logic.
     *
     * @return a new Builder instance initialized with default values
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing ToolDefinition instances.
     * Provides a fluent API for configuring tool properties including name, description,
     * plugin identification, parameters, keywords, context restrictions, permissions,
     * priority level, and execution logic.
     * <p>
     * The builder validates that required fields are present when building the final
     * ToolDefinition instance. Required fields include name, description, and executor.
     * <p>
     * Collection properties such as parameters and keywords are accumulated through
     * multiple method calls, allowing flexible configuration of the tool definition.
     * <p>
     * Context restriction methods guildOnly and dmOnly allow specifying where the tool
     * can be executed. Permission settings control who can use the tool, while priority
     * affects tool selection order when multiple tools are applicable.
     */
    public static class Builder {
        /**
         * The name of the tool.
         * This field holds the identifier used to reference the tool within the system.
         * It should be unique within the scope of the plugin or application where it is defined.
         */
        private String name;
        /**
         * The description of the tool.
         * <p>
         * This field holds a textual explanation of what the tool does. The description
         * is used by the AI agent to understand the purpose and functionality of the tool,
         * aiding in determining when it is appropriate to invoke it. It should be concise
         * yet descriptive enough to convey the tool's utility.
         */
        private String description;
        /**
         * Identifier for the plugin that provides this tool.
         * Used to associate the tool with its originating plugin for management and categorization purposes.
         * Defaults to "core" for built-in tools.
         */
        private String pluginId = "core";
        /**
         * List of parameters that define the inputs accepted by the tool.
         * Each parameter specifies its name, type, description, whether it is required,
         * and optionally a default value. These parameters are used to generate
         * the tool's signature for AI agents and validate incoming requests.
         * The list is mutable during the builder phase and becomes immutable upon
         * building the final {@link ToolDefinition}.
         */
        private final List<ToolParameter> parameters = new ArrayList<>();
        /**
         * A set of keywords associated with the tool.
         * These keywords are used by the AI agent to help determine when the tool should be invoked.
         * The set is mutable during the builder phase and will be finalized upon building the tool definition.
         */
        private final Set<String> keywords = new HashSet<>();
        /**
         * Indicates whether the tool is restricted to guild contexts only.
         * When set to true, the tool will only be available for execution within guild channels.
         * If false, the tool can be executed in both guild and direct message contexts.
         */
        private boolean guildOnly = false;
        /**
         * Indicates whether this tool is restricted to direct message contexts only.
         * When set to true, the tool will only be available for use in private channels
         * between the user and the bot. If false, the tool can be used in both
         * direct messages and guild channels, unless further restricted by other settings.
         */
        private boolean dmOnly = false;
        /**
         * The permission level required to use this tool.
         * Determines which users can execute the tool based on their permissions.
         * Defaults to {@link AgentTool.ToolPermission#EVERYONE}, allowing all users to use the tool.
         */
        private AgentTool.ToolPermission permission = AgentTool.ToolPermission.EVERYONE;
        /**
         * The priority level of the tool which determines its precedence when multiple tools are available.
         * Tools with higher priority values are selected over those with lower values when multiple tools
         * match a given context or intent. This allows for fine-grained control over tool selection behavior.
         */
        private int priority = 0;
        /**
         * The function responsible for executing the tool's logic.
         * It takes an {@link AgentToolContext} providing execution context and a map of input parameters,
         * then returns a string result of the tool's operation.
         */
        private BiFunction<AgentToolContext, Map<String, Object>, String> executor;

        /**
         * Sets the name for the builder instance.
         *
         * @param name the name to be set
         * @return the current builder instance
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the description of the tool.
         * <p>
         * The description should clearly explain what the tool does and is used by the AI agent
         * to determine when to invoke the tool. It should be concise and informative.
         *
         * @param description the description of the tool
         * @return this builder instance
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the plugin identifier for the tool being built.
         * <p>
         * The plugin ID is used to associate the tool with a specific plugin,
         * allowing the system to organize and manage tools by their originating plugin.
         *
         * @param pluginId the unique identifier of the plugin that provides this tool
         * @return this builder instance for method chaining
         */
        public Builder pluginId(String pluginId) {
            this.pluginId = pluginId;
            return this;
        }

        /**
         * Adds a parameter to the tool being built.
         *
         * @param name the name of the parameter
         * @param type the data type of the parameter
         * @param description a textual description of the parameter's purpose
         * @param required whether the parameter is mandatory
         * @return the current builder instance
         */
        public Builder parameter(String name, Class<?> type, String description, boolean required) {
            this.parameters.add(new ToolParameter(name, type, description, required, null));
            return this;
        }

        /**
         * Adds a parameter to the tool being built with a default value.
         *
         * @param name the name of the parameter
         * @param type the data type of the parameter
         * @param description a textual description of the parameter's purpose
         * @param required whether the parameter is mandatory
         * @param defaultValue the default value of the parameter if not provided
         * @return the current builder instance
         */
        public Builder parameter(String name, Class<?> type, String description, boolean required, Object defaultValue) {
            this.parameters.add(new ToolParameter(name, type, description, required, defaultValue));
            return this;
        }

        /**
         * Adds a parameter to the tool being built.
         *
         * @param parameter the ToolParameter object containing the parameter details
         * @return the current builder instance
         */
        public Builder parameter(ToolParameter parameter) {
            this.parameters.add(parameter);
            return this;
        }

        /**
         * Adds a keyword to the tool being built.
         * <p>
         * Keywords are used to help identify and categorize the tool, and may be used
         * by the system to match user queries with appropriate tools. Keywords are
         * case-insensitive and will be stored in lowercase.
         *
         * @param keyword the keyword to add
         * @return the current builder instance for method chaining
         */
        public Builder keyword(String keyword) {
            this.keywords.add(keyword.toLowerCase());
            return this;
        }

        /**
         * Adds multiple keywords to the tool being built.
         * <p>
         * Keywords are used to help identify and categorize the tool. They are typically used
         * for searching or filtering tools based on their functionality or purpose.
         * All keywords are converted to lowercase to ensure consistent casing.
         *
         * @param keywords the keywords to be added
         * @return this builder instance for method chaining
         */
        public Builder keywords(String... keywords) {
            for (String kw : keywords) {
                this.keywords.add(kw.toLowerCase());
            }
            return this;
        }

        /**
         * Sets the keywords associated with the tool being built.
         * <p>
         * This method accepts a collection of strings representing keywords,
         * which are used to identify and categorize the tool. Each keyword
         * is converted to lowercase before being added to ensure consistency.
         *
         * @param keywords a collection of keywords to associate with the tool
         * @return this builder instance for method chaining
         */
        public Builder keywords(Collection<String> keywords) {
            keywords.forEach(kw -> this.keywords.add(kw.toLowerCase()));
            return this;
        }

        /**
         * Sets whether the tool can only be used within a guild context.
         * <p>
         * When set to {@code true}, the tool will only be available when invoked within a guild.
         * If set to {@code false}, the tool may also be used in other contexts such as direct messages,
         * depending on other restrictions like {@link #dmOnly(boolean)}.
         *
         * @param guildOnly {@code true} to restrict usage to guilds only, {@code false} otherwise
         * @return this builder instance for method chaining
         */
        public Builder guildOnly(boolean guildOnly) {
            this.guildOnly = guildOnly;
            return this;
        }

        /**
         * Sets whether the tool can only be used in direct messages.
         *
         * @param dmOnly true if the tool is restricted to direct messages, false otherwise
         * @return this builder instance for method chaining
         */
        public Builder dmOnly(boolean dmOnly) {
            this.dmOnly = dmOnly;
            return this;
        }

        /**
         * Sets the permission level required to use the tool.
         * @param permission the required permission level for the tool
         * @return this builder instance for method chaining
         */
        public Builder permission(AgentTool.ToolPermission permission) {
            this.permission = permission;
            return this;
        }

        /**
         * Sets the priority level for the tool being built.
         * <p>
         * The priority determines the order in which tools are considered when multiple tools match a given query.
         * Tools with higher priority values are evaluated before those with lower values.
         *
         * @param priority the priority level to assign to the tool
         * @return this builder instance for method chaining
         */
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * Sets the executor function for this tool.
         * The executor is responsible for performing the actual work when the tool is invoked,
         * using the provided context and parameters.
         *
         * @param executor a BiFunction that takes an AgentToolContext and a Map of parameters
         *                 and returns a String result
         * @return this Builder instance for chaining
         */
        public Builder executor(BiFunction<AgentToolContext, Map<String, Object>, String> executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Builds and returns a new ToolDefinition instance using the current configuration.
         * Validates that all required fields (name, description, and executor) are present
         * before creating the tool definition.
         *
         * @return a new ToolDefinition instance configured with the current builder settings
         * @throws NullPointerException if any of the required fields (name, description, executor) are null
         */
        public ToolDefinition build() {
            Objects.requireNonNull(name, "Tool name is required");
            Objects.requireNonNull(description, "Tool description is required");
            Objects.requireNonNull(executor, "Tool executor is required");
            return new ToolDefinition(this);
        }
    }

    /**
     * Represents a parameter for a tool, defining its characteristics such as name, type, description,
     * whether it is required, and its default value.
     * <p>
     * Provides a method to retrieve the type name suitable for LLM descriptions.
     *
     * @param name         the parameter name as exposed to the LLM / caller
     * @param type         the Java class representing the parameter's data type
     * @param description  human-readable description of what the parameter is for
     * @param required     whether the parameter must be supplied
     * @param defaultValue the default value used when the parameter is omitted (may be null)
     */
    public record ToolParameter(
            String name,
            Class<?> type,
            String description,
            boolean required,
            Object defaultValue
    ) {
        /**
         * Returns the name of the parameter type as a string suitable for LLM descriptions.
         * <p>
         * Maps Java primitive and wrapper types to their corresponding JSON schema type names.
         * For example, {@code Integer} and {@code int} both map to "integer", while
         * {@code Double} and {@code double} map to "number".
         *
         * @return the type name as a string; one of "string", "integer", "number", or "boolean"
         */
        public String getTypeName() {
            if (type == String.class) return "string";
            if (type == Integer.class || type == int.class) return "integer";
            if (type == Long.class || type == long.class) return "integer";
            if (type == Double.class || type == double.class) return "number";
            if (type == Float.class || type == float.class) return "number";
            if (type == Boolean.class || type == boolean.class) return "boolean";
            return "string";
        }
    }

    /**
     * Returns a string representation of this ToolDefinition object.
     * The string includes the name, description, plugin ID, and the number of parameters.
     *
     * @return a string representation of the ToolDefinition
     */
    @Override
    public String toString() {
        return "ToolDefinition{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", pluginId='" + pluginId + '\'' +
                ", parameters=" + parameters.size() +
                '}';
    }
}