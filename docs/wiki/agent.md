# Agent System - Maid/Secretary AI

Pudel's Agent System allows her to act as a true maid/secretary by managing data autonomously through natural conversation.

## Overview

The Agent System uses LangChain4j AI Services with tool annotations to enable Pudel to:
- **Create tables** to organize different types of information
- **Store documents, notes, news** and any data users want to keep
- **Search and retrieve** stored information
- **Remember facts** and recall them later

All operations are scoped to the guild/user's isolated database schema for security.

::: tip Unified Tool Standard
Built-in agent tools use the **same `AgentToolProvider` + `@AgentTool` pattern** as plugin tools.
Everything flows through a single `AgentToolRegistry` pipeline.
:::

---

## How It Works

```
User Message
     ↓
[Intent Detection]
     ↓
Data management intent? ─── No ───→ [Normal Chat Response]
     │
     Yes
     ↓
[Agent Service]
     ↓
[PluginToolAdapter] ←→ [AgentToolRegistry]
     ↓                    ↓
[LLM + Tools]    BuiltinAgentTools + Plugin Tools
     ↓                    ↓
Natural Response   [AgentDataExecutor → PostgreSQL]
```

---

## Available Tools

The Agent has access to two categories of built-in tools:

### Agent Tools (Data Management)

Registered via `AgentToolRegistry` as `pudel-core`:

| Tool | Description | Example |
|------|-------------|---------|
| `create_table` | Create a new data table | "Create a table for meeting notes" |
| `list_tables` | List all custom tables | "What tables do I have?" |
| `store_data` | Store information in a table | "Save this in my notes" |
| `search_data` | Search by keyword | "Find notes about marketing" |
| `get_all_data` | Get all entries from table | "Show all my notes" |
| `get_data_by_id` | Get specific entry | "Show note #5" |
| `update_data` | Update an entry | "Update note #5 with..." |
| `delete_data` | Delete an entry | "Delete note #5" |
| `delete_table` | Delete entire table | "Delete my notes table" |
| `remember` | Store a key-value fact | "Remember John's birthday is April 5" |
| `recall` | Retrieve a remembered fact | "When is John's birthday?" |
| `list_memories` | List all memories | "What do you remember?" |
| `archive_message` | Archive a Discord message | "Archive this in announcements" |
| `get_current_datetime` | Get current timestamp | "What time is it?" |

### MCP Tools (Brain Context Access)

Registered via `McpToolRegistry` as `builtin`. These tools give the LLM access to Pudel's brain context:

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_passive_context` | Get recent observed messages in a channel | `channel_id`, `is_guild`, `target_id`, `limit` |
| `get_dialogue_history` | Get conversation history with a user | `user_id`, `is_guild`, `target_id`, `limit` |
| `get_message_by_id` | Fetch a specific message from passive context | `message_id`, `channel_id`, `is_guild`, `target_id` |
| `get_forwarded_messages` | Get forwarded message data from a message | `message_id`, `channel_id`, `is_guild`, `target_id` |
| `get_brain_status` | Get Ollama availability and context queue stats | _(no parameters)_ |

::: info Unified Tool Dispatch
Both MCP tools and Agent tools are exposed to the LLM through the same tool-calling mechanism in `PudelAgentService.processWithTools()`. The LLM doesn't need to know which registry a tool belongs to — it just calls tools by name and the system routes them correctly.
:::

---

## Usage Examples

### Creating and Using Tables

**User:** "Pudel, create a table called project_ideas for storing our team's ideas"

**Pudel:** "✅ Created table 'project_ideas'. You can now store your team's ideas here!"

**User:** "Save this idea: AI-powered code review bot"

**Pudel:** "Saved! ID #1: 'AI-powered code review bot' in project_ideas"

**User:** "Add another: Discord music bot with Spotify integration"

**Pudel:** "Saved! ID #2: 'Discord music bot with Spotify integration'"

**User:** "Show all project ideas"

**Pudel:** 
```
📋 Project Ideas (2 entries):
#1: AI-powered code review bot
#2: Discord music bot with Spotify integration
```

### Quick Memory

**User:** "Remember that the team meeting is every Tuesday at 3 PM"

**Pudel:** "Got it! I'll remember: team_meeting = Every Tuesday at 3 PM"

**User:** "When is the team meeting?"

**Pudel:** "The team meeting is every Tuesday at 3 PM"

### Searching

**User:** "Find all notes containing 'budget'"

**Pudel:**
```
🔍 Found 2 results for 'budget':
#3: Q1 Budget Review - Approved $50k for marketing
#7: Budget meeting scheduled for March 15
```

### Archiving Messages

**User:** *replies to a message* "Archive this in announcements"

**Pudel:** "Archived message from @Admin in announcements (ID #1)"

---

## Intent Detection

The agent activates when your message contains keywords like:

| Category | Keywords |
|----------|----------|
| **Storage** | remember, store, save, add, keep, record |
| **Creation** | create table, create list, make a table |
| **Retrieval** | find, search, show, get, recall, what was |
| **Modification** | update, edit, change, modify |
| **Deletion** | delete, remove, forget, clear |
| **Organization** | archive, organize, categorize |

---

## Database Structure

Agent tables are automatically prefixed with `agent_`:

```sql
-- Your table: project_ideas
-- Actual table: guild_123.agent_project_ideas

CREATE TABLE agent_project_ideas (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

Metadata tracking:
```sql
CREATE TABLE agent_table_metadata (
    table_name VARCHAR(100) PRIMARY KEY,
    description TEXT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## Security

1. **Schema Isolation**: All tables are in your guild's private schema
2. **Table Prefix**: Agent tables use `agent_` prefix
3. **Input Sanitization**: Table names are validated to prevent injection
4. **User Attribution**: All records track the creating user
5. **Permission Control**: `delete_table` requires Manage Server permission

---

## Subscription Limits

Agent operations count against storage capacity:

| Resource | Counted Against |
|----------|-----------------|
| Table rows | Dialogue limit |
| Memory entries | Memory limit |
| Custom tables | No specific limit |

See [Subscriptions](./subscription) for tier limits.

---

## Plugin Integration

Plugins can extend the agent with custom tools — using the **exact same API** as the built-in tools:

```java
public class MyTools implements AgentToolProvider {
    
    @AgentTool(
        name = "check_weather",
        description = "Get current weather for a city",
        keywords = {"weather", "temperature", "forecast"}
    )
    public String checkWeather(AgentToolContext ctx, String city) {
        // Implementation
        return "Weather in " + city + ": 22°C, Sunny";
    }
}

// Register in plugin
context.getAgentToolRegistry().registerProvider("my-plugin", new MyTools());
```

See [Plugin Development](./plugin-development) for details.

---

## Best Practices

1. **Be specific**: "Save this as a meeting note" works better than "save this"
2. **Name tables clearly**: Use descriptive names like `project_ideas`, `meeting_notes`
3. **Use search**: "Find notes about X" is efficient for large tables
4. **Organize early**: Create separate tables for different data types

---

## Troubleshooting

**Agent doesn't respond to data requests:**
- Ensure AI is enabled (via `/settings` > AI view)
- Check if Ollama is running
- Verify intent keywords are used

**"Table not found" errors:**
- Ask "What tables do I have?" to list tables
- Table names are case-insensitive
- Tables are guild-specific

**Search returns nothing:**
- Check spelling
- Try broader keywords
- Ask "Show all entries in <table>" to see everything

---

## Be Patient

Pudel may not recognize your intent as agent work from time to time. Do not expect AI generate completely blindfold.

---

*Pudel is ready to be your data assistant!* 📝
