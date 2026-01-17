/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 Napapon Kamanee
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
package worldstandard.group.pudel.model.agent;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Tools that Pudel AI Agent can use to interact with guild/user data.
 * <p>
 * These tools allow Pudel to act as a true maid/secretary by:
 * - Managing custom data tables (documents, notes, reminders, etc.)
 * - Storing and retrieving information on behalf of users
 * - Organizing data intelligently based on conversation context
 * <p>
 * The tools operate within the guild's isolated schema for security.
 */
public class PudelAgentTools {

    private static final Logger logger = LoggerFactory.getLogger(PudelAgentTools.class);

    private final AgentDataExecutor dataExecutor;
    private final long targetId; // Guild or User ID
    private final boolean isGuild;
    private final long requestingUserId;

    public PudelAgentTools(AgentDataExecutor dataExecutor, long targetId, boolean isGuild, long requestingUserId) {
        this.dataExecutor = dataExecutor;
        this.targetId = targetId;
        this.isGuild = isGuild;
        this.requestingUserId = requestingUserId;
    }

    // ===========================================
    // Table Management Tools
    // ===========================================

    @Tool("Create a new data table to organize information. Use this when user wants to track something new like documents, notes, tasks, news, etc.")
    public String createTable(String tableName, String description) {
        try {
            // Sanitize table name
            String sanitizedName = sanitizeTableName(tableName);

            boolean success = dataExecutor.createCustomTable(targetId, isGuild, sanitizedName, description, requestingUserId);

            if (success) {
                logger.info("Agent created table '{}' for {} {}", sanitizedName, isGuild ? "guild" : "user", targetId);
                return "Successfully created table '" + sanitizedName + "' for storing " + description;
            } else {
                return "Table '" + sanitizedName + "' already exists. I can add data to it instead.";
            }
        } catch (Exception e) {
            logger.error("Error creating table: {}", e.getMessage());
            return "Sorry, I couldn't create that table: " + e.getMessage();
        }
    }

    @Tool("List all custom tables I've created for organizing data in this guild/chat")
    public String listTables() {
        try {
            List<Map<String, Object>> tables = dataExecutor.listCustomTables(targetId, isGuild);

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

    // ===========================================
    // Data Storage Tools
    // ===========================================

    @Tool("Store a piece of information in a table. Use this to save documents, notes, news, or any data the user wants to keep.")
    public String storeData(String tableName, String title, String content) {
        try {
            String sanitizedName = sanitizeTableName(tableName);

            // Ensure table exists
            dataExecutor.ensureTableExists(targetId, isGuild, sanitizedName, requestingUserId);

            long id = dataExecutor.insertData(targetId, isGuild, sanitizedName, title, content, requestingUserId);

            logger.info("Agent stored data '{}' in table '{}' for {} {}", title, sanitizedName, isGuild ? "guild" : "user", targetId);
            return "Got it! I've saved \"" + title + "\" in my " + sanitizedName + " records (ID: " + id + ")";
        } catch (Exception e) {
            logger.error("Error storing data: {}", e.getMessage());
            return "Sorry, I couldn't save that: " + e.getMessage();
        }
    }

    @Tool("Store a message or reply in a specific table. Good for archiving important messages.")
    public String archiveMessage(String tableName, String messageContent, String authorName, String context) {
        try {
            String sanitizedName = sanitizeTableName(tableName);

            // Ensure table exists
            dataExecutor.ensureTableExists(targetId, isGuild, sanitizedName, requestingUserId);

            String title = "Message from " + authorName;
            String fullContent = messageContent;
            if (context != null && !context.isBlank()) {
                fullContent = "Context: " + context + "\n\n" + messageContent;
            }

            long id = dataExecutor.insertData(targetId, isGuild, sanitizedName, title, fullContent, requestingUserId);

            return "Archived message from " + authorName + " in " + sanitizedName + " (ID: " + id + ")";
        } catch (Exception e) {
            logger.error("Error archiving message: {}", e.getMessage());
            return "Sorry, I couldn't archive that message: " + e.getMessage();
        }
    }

    // ===========================================
    // Data Retrieval Tools
    // ===========================================

    @Tool("Search for data in a table by keyword. Use this to find specific information the user is looking for.")
    public String searchData(String tableName, String searchQuery) {
        try {
            String sanitizedName = sanitizeTableName(tableName);

            List<Map<String, Object>> results = dataExecutor.searchData(targetId, isGuild, sanitizedName, searchQuery, 10);

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

    @Tool("Get all data from a table. Use this to show everything stored in a category.")
    public String getAllData(String tableName, int limit) {
        try {
            String sanitizedName = sanitizeTableName(tableName);

            List<Map<String, Object>> results = dataExecutor.getAllData(targetId, isGuild, sanitizedName, Math.min(limit, 20));

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

    @Tool("Get a specific entry by its ID from a table.")
    public String getDataById(String tableName, long id) {
        try {
            String sanitizedName = sanitizeTableName(tableName);

            Map<String, Object> result = dataExecutor.getDataById(targetId, isGuild, sanitizedName, id);

            if (result == null) {
                return "I couldn't find entry with ID " + id + " in " + sanitizedName;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("**").append(result.get("title")).append("**\n\n");
            sb.append(result.get("content")).append("\n\n");
            sb.append("_Created by: <@").append(result.get("created_by")).append(">_\n");
            sb.append("_Date: ").append(result.get("created_at")).append("_");

            return sb.toString();
        } catch (Exception e) {
            logger.error("Error getting data by ID: {}", e.getMessage());
            return "Sorry, I couldn't retrieve that: " + e.getMessage();
        }
    }

    // ===========================================
    // Data Update/Delete Tools
    // ===========================================

    @Tool("Update an existing entry in a table. Use this when user wants to modify stored data.")
    public String updateData(String tableName, long id, String newTitle, String newContent) {
        try {
            String sanitizedName = sanitizeTableName(tableName);

            boolean success = dataExecutor.updateData(targetId, isGuild, sanitizedName, id, newTitle, newContent);

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

    @Tool("Delete an entry from a table by ID. Use this when user wants to remove data.")
    public String deleteData(String tableName, long id) {
        try {
            String sanitizedName = sanitizeTableName(tableName);

            boolean success = dataExecutor.deleteData(targetId, isGuild, sanitizedName, id);

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

    @Tool("Delete a table and all its data. Use only when user explicitly wants to remove everything.")
    public String deleteTable(String tableName) {
        try {
            String sanitizedName = sanitizeTableName(tableName);

            boolean success = dataExecutor.deleteTable(targetId, isGuild, sanitizedName);

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
    // Utility Tools
    // ===========================================

    @Tool("Remember something important. Use this for quick notes or facts the user wants me to remember.")
    public String remember(String key, String value) {
        try {
            dataExecutor.storeMemory(targetId, isGuild, key, value, "agent_memory", requestingUserId);
            return "I'll remember that: " + key + " = " + value;
        } catch (Exception e) {
            logger.error("Error remembering: {}", e.getMessage());
            return "Sorry, I couldn't remember that: " + e.getMessage();
        }
    }

    @Tool("Recall something I was asked to remember. Use this when user asks about something I should know.")
    public String recall(String key) {
        try {
            String value = dataExecutor.getMemory(targetId, isGuild, key);
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

    @Tool("Get current date and time")
    public String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Tool("List all things I've been asked to remember")
    public String listMemories() {
        try {
            List<Map<String, Object>> memories = dataExecutor.getMemoriesByCategory(targetId, isGuild, "agent_memory");

            if (memories.isEmpty()) {
                return "I haven't been asked to remember anything yet.";
            }

            StringBuilder sb = new StringBuilder("Here's what I remember:\n");
            for (Map<String, Object> memory : memories) {
                sb.append("• **").append(memory.get("key")).append("**: ").append(memory.get("value")).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("Error listing memories: {}", e.getMessage());
            return "Sorry, I couldn't list my memories: " + e.getMessage();
        }
    }

    // ===========================================
    // Helper Methods
    // ===========================================

    /**
     * Sanitize table name to prevent SQL injection and ensure valid identifier
     */
    private String sanitizeTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Table name cannot be empty");
        }

        // Convert to lowercase, replace spaces with underscores, remove special chars
        String sanitized = tableName.toLowerCase()
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-z0-9_]", "")
                .replaceAll("^_+|_+$", ""); // Remove leading/trailing underscores

        // Ensure it starts with a letter
        if (!sanitized.isEmpty() && Character.isDigit(sanitized.charAt(0))) {
            sanitized = "t_" + sanitized;
        }

        // Limit length
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }

        // Prefix with 'agent_' to identify agent-created tables
        if (!sanitized.startsWith("agent_")) {
            sanitized = "agent_" + sanitized;
        }

        if (sanitized.isEmpty() || sanitized.equals("agent_")) {
            throw new IllegalArgumentException("Invalid table name");
        }

        return sanitized;
    }
}

