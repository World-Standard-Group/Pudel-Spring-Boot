/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 World Standard Group
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed with an additional permission known as the
 * "Pudel Plugin Exception".
 *
 * See the LICENSE and PLUGIN_EXCEPTION files in the project root for details.
 */
package group.worldstandard.pudel.core.interaction.builtin;

import group.worldstandard.pudel.api.PudelProperties;
import group.worldstandard.pudel.api.agent.AgentTool;
import group.worldstandard.pudel.api.agent.AgentToolContext;
import group.worldstandard.pudel.api.agent.AgentToolProvider;
import group.worldstandard.pudel.core.brain.agent.AgentDataExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Built-in Pudel agent tools for data management.
 * <p>
 * These are the core AI tools that allow Pudel to act as a secretary/maid:
 * <ul>
 *   <li>Table management — create, list, delete custom data tables</li>
 *   <li>Data operations — store, search, retrieve, update, delete entries</li>
 *   <li>Memory tools — quick remember/recall for key-value pairs</li>
 *   <li>Utility tools — date/time, archiving</li>
 * </ul>
 * <p>
 * Registered through the same {@code AgentToolRegistry} as plugin tools,
 * ensuring a single standard for all tools in the system.
 */
@Component
public class BuiltinAgentTools implements AgentToolProvider {
    private static final Logger logger = LoggerFactory.getLogger(BuiltinAgentTools.class);
    public static final String PLUGIN_ID = "pudel-core-tools";

    private final AgentDataExecutor dataExecutor;
    private final PudelProperties pudelProperties;

    public BuiltinAgentTools(AgentDataExecutor dataExecutor,
                             PudelProperties pudelProperties
    ) {
        this.dataExecutor = dataExecutor;
        this.pudelProperties = pudelProperties;
    }

    @Override
    public String getProviderName() {
        return pudelProperties.getUserAgent();
    }

    @Override
    public void onRegister() {
        logger.info("Built-in agent tools registered");
    }

    @Override
    public void onUnregister() {
        logger.info("Built-in agent tools unregistered");
    }

    // ===========================================
    // Table Management Tools
    // ===========================================

    @AgentTool(
        name = "create_table",
        description = "Create a new data table to organize information. Use this when a user wants to track something new like documents, notes, tasks, news, etc.",
        keywords = {"create", "table", "organize", "new", "track", "category"},
        priority = 10
    )
    public String createTable(AgentToolContext context, String tableName, String description) {
        try {
            String sanitizedName = sanitizeTableName(tableName);
            boolean success = dataExecutor.createCustomTable(
                    context.getTargetId(), context.isGuild(),
                    sanitizedName, description, context.getRequestingUserId());

            if (success) {
                logger.info("Agent created table '{}' for {} {}",
                        sanitizedName, context.isGuild() ? "guild" : "user", context.getTargetId());
                return "Successfully created table '" + sanitizedName + "' for storing " + description;
            } else {
                return "Table '" + sanitizedName + "' already exists. I can add data to it instead.";
            }
        } catch (Exception e) {
            logger.error("Error creating table: {}", e.getMessage());
            return "Sorry, I couldn't create that table: " + e.getMessage();
        }
    }

    @AgentTool(
        name = "list_tables",
        description = "List all custom data tables created for organizing data in this guild/chat",
        keywords = {"list", "tables", "show", "categories", "what"},
        priority = 8
    )
    public String listTables(AgentToolContext context) {
        try {
            List<Map<String, Object>> tables = dataExecutor.listCustomTables(
                    context.getTargetId(), context.isGuild());

            if (tables.isEmpty()) {
                return "I haven't created any custom tables yet. Would you like me to create one?";
            }

            StringBuilder sb = new StringBuilder("Here are the tables I'm managing:\n");
            for (Map<String, Object> table : tables) {
                sb.append("• **").append(table.get("table_name")).append("**");
                if (table.get("description") != null) {
                    sb.append(" - ").append(table.get("description"));
                }
                sb.append(" (").append(table.get("row_count")).append(" entries)\n");
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("Error listing tables: {}", e.getMessage());
            return "Sorry, I couldn't list the tables: " + e.getMessage();
        }
    }

    @AgentTool(
        name = "delete_table",
        description = "Delete a table and all its data. Use only when user explicitly wants to remove everything.",
        keywords = {"delete", "remove", "drop", "table", "destroy"},
        permission = AgentTool.ToolPermission.GUILD_MANAGER,
        priority = 5
    )
    public String deleteTable(AgentToolContext context, String tableName) {
        try {
            String sanitizedName = sanitizeTableName(tableName);
            boolean success = dataExecutor.deleteTable(
                    context.getTargetId(), context.isGuild(), sanitizedName);

            if (success) {
                return "Deleted table " + sanitizedName + " and all its data";
            } else {
                return "Table " + sanitizedName + " doesn't exist";
            }
        } catch (Exception e) {
            logger.error("Error deleting table: {}", e.getMessage());
            return "Sorry, I couldn't delete that table: " + e.getMessage();
        }
    }

    // ===========================================
    // Data Storage Tools
    // ===========================================

    @AgentTool(
        name = "store_data",
        description = "Store a piece of information in a table. Use this to save documents, notes, news, or any data the user wants to keep.",
        keywords = {"store", "save", "add", "insert", "record", "write", "note"},
        priority = 10
    )
    public String storeData(AgentToolContext context, String tableName, String title, String content) {
        try {
            String sanitizedName = sanitizeTableName(tableName);
            dataExecutor.ensureTableExists(context.getTargetId(), context.isGuild(),
                    sanitizedName, context.getRequestingUserId());

            long id = dataExecutor.insertData(context.getTargetId(), context.isGuild(),
                    sanitizedName, title, content, context.getRequestingUserId());

            logger.info("Agent stored data '{}' in table '{}' for {} {}",
                    title, sanitizedName, context.isGuild() ? "guild" : "user", context.getTargetId());
            return "Got it! I've saved \"" + title + "\" in my " + sanitizedName + " records (ID: " + id + ")";
        } catch (Exception e) {
            logger.error("Error storing data: {}", e.getMessage());
            return "Sorry, I couldn't save that: " + e.getMessage();
        }
    }

    @AgentTool(
        name = "archive_message",
        description = "Archive a message or reply in a specific table. Good for archiving important messages.",
        keywords = {"archive", "message", "important", "keep", "save"},
        priority = 7
    )
    public String archiveMessage(AgentToolContext context, String tableName, String messageContent,
                                 String authorName) {
        try {
            String sanitizedName = sanitizeTableName(tableName);
            dataExecutor.ensureTableExists(context.getTargetId(), context.isGuild(),
                    sanitizedName, context.getRequestingUserId());

            String title = "Message from " + authorName;
            long id = dataExecutor.insertData(context.getTargetId(), context.isGuild(),
                    sanitizedName, title, messageContent, context.getRequestingUserId());

            return "Archived message from " + authorName + " in " + sanitizedName + " (ID: " + id + ")";
        } catch (Exception e) {
            logger.error("Error archiving message: {}", e.getMessage());
            return "Sorry, I couldn't archive that message: " + e.getMessage();
        }
    }

    // ===========================================
    // Data Retrieval Tools
    // ===========================================

    @AgentTool(
        name = "search_data",
        description = "Search for data in a table by keyword. Use this to find specific information the user is looking for.",
        keywords = {"search", "find", "look", "query", "where", "match"},
        priority = 9
    )
    public String searchData(AgentToolContext context, String tableName, String searchQuery) {
        try {
            String sanitizedName = sanitizeTableName(tableName);
            List<Map<String, Object>> results = dataExecutor.searchData(
                    context.getTargetId(), context.isGuild(), sanitizedName, searchQuery, 10);

            if (results.isEmpty()) {
                return "I couldn't find anything matching '" + searchQuery + "' in " + sanitizedName;
            }

            StringBuilder sb = new StringBuilder("Here's what I found in " + sanitizedName + ":\n\n");
            for (Map<String, Object> row : results) {
                sb.append("**").append(row.get("title")).append("** (ID: ").append(row.get("id")).append(")\n");
                String content = (String) row.get("content");
                if (content != null && content.length() > 200) {
                    content = content.substring(0, 200) + "...";
                }
                sb.append(content).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("Error searching data: {}", e.getMessage());
            return "Sorry, I couldn't search: " + e.getMessage();
        }
    }

    @AgentTool(
        name = "get_all_data",
        description = "Get all data from a table. Use this to show everything stored in a category.",
        keywords = {"all", "list", "show", "everything", "entries"},
        priority = 7
    )
    public String getAllData(AgentToolContext context, String tableName, int limit) {
        try {
            String sanitizedName = sanitizeTableName(tableName);
            List<Map<String, Object>> results = dataExecutor.getAllData(
                    context.getTargetId(), context.isGuild(), sanitizedName, Math.min(limit, 20));

            if (results.isEmpty()) {
                return "The " + sanitizedName + " table is empty.";
            }

            StringBuilder sb = new StringBuilder("Here are the entries in " + sanitizedName + ":\n\n");
            for (Map<String, Object> row : results) {
                sb.append("**").append(row.get("title")).append("** (ID: ").append(row.get("id")).append(")\n");
                String content = (String) row.get("content");
                if (content != null && content.length() > 100) {
                    content = content.substring(0, 100) + "...";
                }
                sb.append(content).append("\n");
                sb.append("_Created: ").append(row.get("created_at")).append("_\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("Error getting all data: {}", e.getMessage());
            return "Sorry, I couldn't retrieve that: " + e.getMessage();
        }
    }

    @AgentTool(
        name = "get_data_by_id",
        description = "Get a specific entry by its ID from a table.",
        keywords = {"get", "id", "specific", "entry", "detail"},
        priority = 6
    )
    public String getDataById(AgentToolContext context, String tableName, long id) {
        try {
            String sanitizedName = sanitizeTableName(tableName);
            Map<String, Object> result = dataExecutor.getDataById(
                    context.getTargetId(), context.isGuild(), sanitizedName, id);

            if (result == null) {
                return "I couldn't find entry with ID " + id + " in " + sanitizedName;
            }

            return "**" + result.get("title") + "**\n\n" +
                    result.get("content") + "\n\n" +
                    "_Created by: <@" + result.get("created_by") + ">_\n" +
                    "_Date: " + result.get("created_at") + "_";
        } catch (Exception e) {
            logger.error("Error getting data by ID: {}", e.getMessage());
            return "Sorry, I couldn't retrieve that: " + e.getMessage();
        }
    }

    // ===========================================
    // Data Update/Delete Tools
    // ===========================================

    @AgentTool(
        name = "update_data",
        description = "Update an existing entry in a table. Use this when user wants to modify stored data.",
        keywords = {"update", "edit", "modify", "change", "fix"},
        priority = 7
    )
    public String updateData(AgentToolContext context, String tableName, long id,
                             String newTitle, String newContent) {
        try {
            String sanitizedName = sanitizeTableName(tableName);
            boolean success = dataExecutor.updateData(
                    context.getTargetId(), context.isGuild(), sanitizedName, id, newTitle, newContent);

            if (success) {
                return "Updated entry " + id + " in " + sanitizedName;
            } else {
                return "Couldn't find entry " + id + " to update";
            }
        } catch (Exception e) {
            logger.error("Error updating data: {}", e.getMessage());
            return "Sorry, I couldn't update that: " + e.getMessage();
        }
    }

    @AgentTool(
        name = "delete_data",
        description = "Delete an entry from a table by ID. Use this when user wants to remove data.",
        keywords = {"delete", "remove", "erase", "entry"},
        priority = 6
    )
    public String deleteData(AgentToolContext context, String tableName, long id) {
        try {
            String sanitizedName = sanitizeTableName(tableName);
            boolean success = dataExecutor.deleteData(
                    context.getTargetId(), context.isGuild(), sanitizedName, id);

            if (success) {
                return "Deleted entry " + id + " from " + sanitizedName;
            } else {
                return "Couldn't find entry " + id + " to delete";
            }
        } catch (Exception e) {
            logger.error("Error deleting data: {}", e.getMessage());
            return "Sorry, I couldn't delete that: " + e.getMessage();
        }
    }

    // ===========================================
    // Memory Tools
    // ===========================================

    @AgentTool(
        name = "remember",
        description = "Remember something important. Use this for quick notes or facts the user wants me to remember.",
        keywords = {"remember", "keep", "note", "memorize", "save"},
        priority = 9
    )
    public String remember(AgentToolContext context, String key, String value) {
        try {
            dataExecutor.storeMemory(context.getTargetId(), context.isGuild(),
                    key, value, "agent_memory", context.getRequestingUserId());
            return "I'll remember that: " + key + " = " + value;
        } catch (Exception e) {
            logger.error("Error remembering: {}", e.getMessage());
            return "Sorry, I couldn't remember that: " + e.getMessage();
        }
    }

    @AgentTool(
        name = "recall",
        description = "Recall something I was asked to remember. Use this when user asks about something I should know.",
        keywords = {"recall", "what", "remember", "do you know", "what did"},
        priority = 9
    )
    public String recall(AgentToolContext context, String key) {
        try {
            String value = dataExecutor.getMemory(
                    context.getTargetId(), context.isGuild(), key);
            if (value != null) {
                return key + ": " + value;
            } else {
                return "I don't have any memory of '" + key + "'";
            }
        } catch (Exception e) {
            logger.error("Error recalling: {}", e.getMessage());
            return "Sorry, I couldn't recall that: " + e.getMessage();
        }
    }

    @AgentTool(
        name = "list_memories",
        description = "List all things I've been asked to remember",
        keywords = {"list", "memories", "all", "remember", "what do you know"},
        priority = 7
    )
    public String listMemories(AgentToolContext context) {
        try {
            List<Map<String, Object>> memories = dataExecutor.getMemoriesByCategory(
                    context.getTargetId(), context.isGuild(), "agent_memory");

            if (memories.isEmpty()) {
                return "I haven't been asked to remember anything yet.";
            }

            StringBuilder sb = new StringBuilder("Here's what I remember:\n");
            for (Map<String, Object> memory : memories) {
                sb.append("• **").append(memory.get("key")).append("**: ")
                        .append(memory.get("value")).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("Error listing memories: {}", e.getMessage());
            return "Sorry, I couldn't list my memories: " + e.getMessage();
        }
    }

    // ===========================================
    // Utility Tools
    // ===========================================

    @AgentTool(
        name = "get_current_datetime",
        description = "Get current date and time",
        keywords = {"time", "date", "now", "today", "what time", "what day"},
        priority = 5
    )
    public String getCurrentDateTime(AgentToolContext context) {
        return Instant.now().toString();
    }

    // ===========================================
    // Helper Methods
    // ===========================================

    /**
     * Sanitize table name to prevent SQL injection and ensure valid identifier.
     */
    private String sanitizeTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Table name cannot be empty");
        }

        String sanitized = tableName.toLowerCase()
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-z0-9_]", "")
                .replaceAll("^_+|_+$", "");

        if (!sanitized.isEmpty() && Character.isDigit(sanitized.charAt(0))) {
            sanitized = "t_" + sanitized;
        }

        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }

        if (!sanitized.startsWith("agent_")) {
            sanitized = "agent_" + sanitized;
        }

        if (sanitized.equals("agent_")) {
            throw new IllegalArgumentException("Invalid table name '"+ sanitized + "'");
        }

        return sanitized;
    }
}