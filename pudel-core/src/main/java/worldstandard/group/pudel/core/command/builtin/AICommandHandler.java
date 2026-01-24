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
package worldstandard.group.pudel.core.command.builtin;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import worldstandard.group.pudel.api.command.CommandContext;
import worldstandard.group.pudel.api.command.TextCommandHandler;
import worldstandard.group.pudel.core.command.CommandContextImpl;
import worldstandard.group.pudel.core.entity.GuildSettings;
import worldstandard.group.pudel.core.service.GuildInitializationService;

import java.awt.Color;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Handler for the !ai command.
 * Controls Pudel's AI/chatbot functionality and personality settings per guild.
 * <p>
 * Usage:
 * - !ai - Show status and settings overview
 * - !ai on/off - Enable/disable AI
 * - !ai setup - Start interactive personality wizard
 * - !ai biography/personality/preferences/etc - Configure individual settings
 * - !ai agent/tables/memories - View agent data
 */
@Component
public class AICommandHandler extends ListenerAdapter implements TextCommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(AICommandHandler.class);

    private final GuildInitializationService guildInitializationService;
    private final worldstandard.group.pudel.model.agent.AgentDataExecutor agentDataExecutor;

    // Track active wizard sessions: guildId_userId -> WizardSession
    private final Map<String, WizardSession> activeWizards = new ConcurrentHashMap<>();

    private static final Set<String> VALID_LANGUAGES = Set.of(
            "en", "th", "ja", "ko", "zh", "de", "fr", "es", "pt", "ru", "it", "nl", "pl", "vi", "id", "auto"
    );

    public AICommandHandler(GuildInitializationService guildInitializationService,
                            worldstandard.group.pudel.model.agent.AgentDataExecutor agentDataExecutor) {
        this.guildInitializationService = guildInitializationService;
        this.agentDataExecutor = agentDataExecutor;
    }

    @Override
    public void handle(CommandContext context) {
        if (!context.isFromGuild()) {
            context.getChannel().sendMessage("❌ This command only works in guilds!").queue();
            return;
        }

        // Check for admin permission
        if (!context.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            context.getChannel().sendMessage("❌ You need ADMINISTRATOR permission to use this command!").queue();
            return;
        }

        String guildId = context.getGuild().getId();
        GuildSettings settings = guildInitializationService.getOrCreateGuildSettings(guildId);

        if (context.getArgs().length == 0) {
            // Show current status and all AI settings
            showAIStatus(context, settings);
            return;
        }

        String action = context.getArgs()[0].toLowerCase();

        switch (action) {
            case "on", "enable" -> enableAI(context, settings);
            case "off", "disable" -> disableAI(context, settings);
            case "setup", "wizard" -> startWizard(context, settings);
            case "biography", "bio" -> handleBiography(context, settings);
            case "personality" -> handlePersonality(context, settings);
            case "preferences", "prefs" -> handlePreferences(context, settings);
            case "dialoguestyle", "dialogue", "style" -> handleDialogueStyle(context, settings);
            case "nickname", "name" -> handleNickname(context, settings);
            case "language", "lang" -> handleLanguage(context, settings);
            case "responselength", "length" -> handleResponseLength(context, settings);
            case "formality" -> handleFormality(context, settings);
            case "emotes", "emoji" -> handleEmoteUsage(context, settings);
            case "quirks" -> handleQuirks(context, settings);
            case "interests", "topics" -> handleTopicsInterest(context, settings);
            case "avoid" -> handleTopicsAvoid(context, settings);
            case "systemprompt", "system" -> handleSystemPrompt(context, settings);
            case "agent", "secretary", "maid" -> showAgentInfo(context);
            case "tables" -> showAgentTables(context);
            case "memory", "memories" -> showAgentMemories(context);
            default -> showHelp(context);
        }
    }

    private void showAIStatus(CommandContext context, GuildSettings settings) {
        boolean aiEnabled = settings.getAiEnabled() != null ? settings.getAiEnabled() : true;
        String nickname = settings.getNickname() != null ? settings.getNickname() : "Pudel";
        String language = settings.getLanguage() != null ? settings.getLanguage() : "en";
        String responseLength = settings.getResponseLength() != null ? settings.getResponseLength() : "medium";
        String formality = settings.getFormality() != null ? settings.getFormality() : "balanced";
        String emoteUsage = settings.getEmoteUsage() != null ? settings.getEmoteUsage() : "moderate";

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🤖 " + nickname + "'s AI Configuration")
                .setColor(aiEnabled ? new Color(67, 181, 129) : new Color(240, 71, 71))
                .addField("AI Status", aiEnabled ? "✅ Enabled" : "❌ Disabled", true)
                .addField("Nickname", nickname, true)
                .addField("Language", language.toUpperCase(), true)
                .addField("Response Length", responseLength, true)
                .addField("Formality", formality, true)
                .addField("Emote Usage", emoteUsage, true)
                .addField("Biography", truncate(settings.getBiography(), 200, "Not set"), false)
                .addField("Personality", truncate(settings.getPersonality(), 200, "Not set"), false)
                .addField("Preferences", truncate(settings.getPreferences(), 200, "Not set"), false)
                .addField("Dialogue Style", truncate(settings.getDialogueStyle(), 200, "Not set"), false)
                .addField("Quirks", truncate(settings.getQuirks(), 100, "Not set"), true)
                .addField("Topics of Interest", truncate(settings.getTopicsInterest(), 100, "Not set"), true)
                .addField("Topics to Avoid", truncate(settings.getTopicsAvoid(), 100, "Not set"), true)
                .addField("System Prompt", truncate(settings.getSystemPromptPrefix(), 150, "Default"), false)
                .setFooter("Use !ai setup for wizard | !ai <setting> <value> to configure");

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private String truncate(String text, int maxLength, String defaultValue) {
        if (text == null || text.isEmpty()) {
            return defaultValue;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private void showHelp(CommandContext context) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🤖 AI Command Help")
                .setColor(new Color(114, 137, 218))
                .setDescription("Configure Pudel's AI personality and behavior.")
                .addField("Toggle AI", "`!ai on` / `!ai off` - Enable or disable AI brain", false)
                .addField("Setup Wizard", "`!ai setup` - Interactive personality configuration wizard", false)
                .addField("Individual Settings", """
                        `!ai biography <text>` - Set backstory
                        `!ai personality <text>` - Set character traits
                        `!ai preferences <text>` - Set likes/dislikes
                        `!ai dialoguestyle <text>` - Set speech patterns
                        `!ai nickname <name>` - Set custom name
                        `!ai language <code>` - Set response language
                        `!ai responselength <short|medium|detailed>`
                        `!ai formality <casual|balanced|formal>`
                        `!ai emotes <none|minimal|moderate|frequent>`
                        `!ai quirks <text>` - Set speech quirks
                        `!ai interests <topics>` - Topics to engage with
                        `!ai avoid <topics>` - Topics to steer away from
                        `!ai systemprompt <text>` - Custom system prefix
                        """, false)
                .addField("Agent (Maid/Secretary)", """
                        `!ai agent` - Info about agent capabilities
                        `!ai tables` - View your data tables
                        `!ai memories` - View stored memories
                        """, false)
                .setFooter("Use 'clear' as value to reset any setting");

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void enableAI(CommandContext context, GuildSettings settings) {
        settings.setAiEnabled(true);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🤖 AI Enabled")
                .setColor(new Color(67, 181, 129))
                .setDescription("""
                        Pudel's AI brain is now **active**!
                        
                        I will now:
                        • Respond to name/nickname mentions
                        • Track conversation context passively
                        • Build memory from conversations
                        • Act according to personality settings
                        
                        Use `!ai setup` to configure my personality!""");

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void disableAI(CommandContext context, GuildSettings settings) {
        settings.setAiEnabled(false);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🤖 AI Disabled")
                .setColor(new Color(240, 71, 71))
                .setDescription("""
                        Pudel's AI brain is now **disabled**.
                        
                        I will now:
                        • Only respond to direct `@Pudel` mentions
                        • Not record any conversation context
                        • Not build memory
                        
                        **Note:** You can still use `@Pudel [command]` to run commands without the prefix.""");

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    // ==================== WIZARD FUNCTIONALITY ====================

    private void startWizard(CommandContext context, GuildSettings settings) {
        String sessionKey = context.getGuild().getId() + "_" + context.getUser().getId();

        // Cancel any existing wizard
        activeWizards.remove(sessionKey);

        WizardSession session = new WizardSession(
                context.getGuild().getId(),
                context.getUser().getId(),
                context.getChannel().getId(),
                settings
        );
        activeWizards.put(sessionKey, session);

        // Send first step
        sendWizardStep(context, session);

        // Auto-cleanup after 10 minutes
        context.getChannel().sendMessage("").queueAfter(10, TimeUnit.MINUTES,
                msg -> activeWizards.remove(sessionKey),
                error -> {});
    }

    private void sendWizardStep(CommandContext context, WizardSession session) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(114, 137, 218))
                .setFooter("Type 'skip' to keep current | 'cancel' to exit wizard | Step " + (session.currentStep + 1) + "/8");

        switch (session.currentStep) {
            case 0 -> embed.setTitle("📝 Step 1: Nickname")
                    .setDescription("What should I be called in this server?\n\n" +
                            "**Current:** " + (session.settings.getNickname() != null ? session.settings.getNickname() : "Pudel") +
                            "\n\nType a new nickname or 'skip' to keep current:");
            case 1 -> embed.setTitle("📚 Step 2: Biography")
                    .setDescription("What's my backstory?\n\n" +
                            "**Current:** " + truncate(session.settings.getBiography(), 500, "Not set") +
                            "\n\nType my biography (e.g., 'I am the loyal maid of this guild...'):");
            case 2 -> embed.setTitle("✨ Step 3: Personality")
                    .setDescription("What are my character traits?\n\n" +
                            "**Current:** " + truncate(session.settings.getPersonality(), 500, "Not set") +
                            "\n\nType my personality (e.g., 'Cheerful, helpful, with a dry sense of humor'):");
            case 3 -> embed.setTitle("❤️ Step 4: Preferences")
                    .setDescription("What do I like and dislike?\n\n" +
                            "**Current:** " + truncate(session.settings.getPreferences(), 500, "Not set") +
                            "\n\nType my preferences (e.g., 'Likes gaming discussions, dislikes spam'):");
            case 4 -> embed.setTitle("💬 Step 5: Dialogue Style")
                    .setDescription("How should I speak?\n\n" +
                            "**Current:** " + truncate(session.settings.getDialogueStyle(), 500, "Not set") +
                            "\n\nType my dialogue style (e.g., 'Formal Victorian English' or 'Casual modern slang'):");
            case 5 -> embed.setTitle("🌐 Step 6: Language")
                    .setDescription("What language should I respond in?\n\n" +
                            "**Current:** " + (session.settings.getLanguage() != null ? session.settings.getLanguage().toUpperCase() : "EN") +
                            "\n\n**Options:** en, th, ja, ko, zh, de, fr, es, pt, ru, it, nl, auto\n\nType a language code:");
            case 6 -> embed.setTitle("📏 Step 7: Response Length")
                    .setDescription("How detailed should my responses be?\n\n" +
                            "**Current:** " + (session.settings.getResponseLength() != null ? session.settings.getResponseLength() : "medium") +
                            "\n\n**Options:**\n" +
                            "• `short` - Brief 1-2 sentences\n" +
                            "• `medium` - Balanced 2-4 sentences\n" +
                            "• `detailed` - Comprehensive responses\n\nChoose one:");
            case 7 -> embed.setTitle("🎭 Step 8: Formality")
                    .setDescription("How formal should I be?\n\n" +
                            "**Current:** " + (session.settings.getFormality() != null ? session.settings.getFormality() : "balanced") +
                            "\n\n**Options:**\n" +
                            "• `casual` - Friendly, relaxed, uses emojis\n" +
                            "• `balanced` - Mix of friendly and professional\n" +
                            "• `formal` - Professional, polite\n\nChoose one:");
            default -> {
                // Wizard complete
                completeWizard(context, session);
                return;
            }
        }

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void completeWizard(CommandContext context, WizardSession session) {
        String sessionKey = session.guildId + "_" + session.userId;
        activeWizards.remove(sessionKey);

        // Save all settings
        guildInitializationService.updateGuildSettings(session.guildId, session.settings);

        String nickname = session.settings.getNickname() != null ? session.settings.getNickname() : "Pudel";

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("✅ AI Setup Complete!")
                .setColor(new Color(67, 181, 129))
                .setDescription(nickname + " has been configured!\n\nUse `!ai` to view all settings or `!ai <setting> <value>` to adjust individual settings.")
                .addField("Nickname", nickname, true)
                .addField("Language", session.settings.getLanguage() != null ? session.settings.getLanguage().toUpperCase() : "EN", true)
                .addField("Response Length", session.settings.getResponseLength() != null ? session.settings.getResponseLength() : "medium", true);

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild()) return;

        String sessionKey = event.getGuild().getId() + "_" + event.getAuthor().getId();
        WizardSession session = activeWizards.get(sessionKey);

        if (session == null || !session.channelId.equals(event.getChannel().getId())) {
            return;
        }

        String input = event.getMessage().getContentRaw().trim();

        // Handle cancel
        if (input.equalsIgnoreCase("cancel")) {
            activeWizards.remove(sessionKey);
            event.getChannel().sendMessage("❌ Wizard cancelled. No changes were saved.").queue();
            return;
        }

        // Handle skip
        boolean skipped = input.equalsIgnoreCase("skip");

        if (!skipped) {
            // Process the input for current step
            switch (session.currentStep) {
                case 0 -> {
                    if (input.length() > 32) {
                        event.getChannel().sendMessage("❌ Nickname must be 32 characters or less. Try again:").queue();
                        return;
                    }
                    session.settings.setNickname(input);
                }
                case 1 -> session.settings.setBiography(input);
                case 2 -> session.settings.setPersonality(input);
                case 3 -> session.settings.setPreferences(input);
                case 4 -> session.settings.setDialogueStyle(input);
                case 5 -> {
                    String lang = input.toLowerCase();
                    if (!VALID_LANGUAGES.contains(lang)) {
                        event.getChannel().sendMessage("❌ Invalid language code. Use one of: " + String.join(", ", VALID_LANGUAGES)).queue();
                        return;
                    }
                    session.settings.setLanguage(lang);
                }
                case 6 -> {
                    String length = input.toLowerCase();
                    if (!Set.of("short", "medium", "detailed").contains(length)) {
                        event.getChannel().sendMessage("❌ Invalid option. Use: short, medium, or detailed").queue();
                        return;
                    }
                    session.settings.setResponseLength(length);
                }
                case 7 -> {
                    String formality = input.toLowerCase();
                    if (!Set.of("casual", "balanced", "formal").contains(formality)) {
                        event.getChannel().sendMessage("❌ Invalid option. Use: casual, balanced, or formal").queue();
                        return;
                    }
                    session.settings.setFormality(formality);
                }
            }
        }

        // Move to next step
        session.currentStep++;

        // Create a context for sending the next step
        CommandContext mockContext = new CommandContextImpl(event, "ai", new String[0], "");

        sendWizardStep(mockContext, session);
    }

    // ==================== INDIVIDUAL SETTING HANDLERS ====================

    private void handleBiography(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Biography:** " +
                    truncate(settings.getBiography(), 2000, "Not set") +
                    "\n\n*Usage:* `!ai biography <text>` or `!ai biography clear`").queue();
            return;
        }

        String text = String.join(" ", Arrays.copyOfRange(context.getArgs(), 1, context.getArgs().length));

        if (text.equalsIgnoreCase("clear")) {
            settings.setBiography(null);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
            context.getChannel().sendMessage("✅ Biography cleared!").queue();
            return;
        }

        settings.setBiography(text);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
        context.getChannel().sendMessage("✅ Biography set!").queue();
    }

    private void handlePersonality(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Personality:** " +
                    truncate(settings.getPersonality(), 2000, "Not set") +
                    "\n\n*Usage:* `!ai personality <text>` or `!ai personality clear`").queue();
            return;
        }

        String text = String.join(" ", Arrays.copyOfRange(context.getArgs(), 1, context.getArgs().length));

        if (text.equalsIgnoreCase("clear")) {
            settings.setPersonality(null);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
            context.getChannel().sendMessage("✅ Personality cleared!").queue();
            return;
        }

        settings.setPersonality(text);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
        context.getChannel().sendMessage("✅ Personality set!").queue();
    }

    private void handlePreferences(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Preferences:** " +
                    truncate(settings.getPreferences(), 2000, "Not set") +
                    "\n\n*Usage:* `!ai preferences <text>` or `!ai preferences clear`").queue();
            return;
        }

        String text = String.join(" ", Arrays.copyOfRange(context.getArgs(), 1, context.getArgs().length));

        if (text.equalsIgnoreCase("clear")) {
            settings.setPreferences(null);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
            context.getChannel().sendMessage("✅ Preferences cleared!").queue();
            return;
        }

        settings.setPreferences(text);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
        context.getChannel().sendMessage("✅ Preferences set!").queue();
    }

    private void handleDialogueStyle(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Dialogue Style:** " +
                    truncate(settings.getDialogueStyle(), 2000, "Not set") +
                    "\n\n*Usage:* `!ai dialoguestyle <text>` or `!ai dialoguestyle clear`").queue();
            return;
        }

        String text = String.join(" ", Arrays.copyOfRange(context.getArgs(), 1, context.getArgs().length));

        if (text.equalsIgnoreCase("clear")) {
            settings.setDialogueStyle(null);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
            context.getChannel().sendMessage("✅ Dialogue style cleared!").queue();
            return;
        }

        settings.setDialogueStyle(text);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
        context.getChannel().sendMessage("✅ Dialogue style set!").queue();
    }

    private void handleNickname(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Nickname:** " +
                    (settings.getNickname() != null ? settings.getNickname() : "Pudel (default)") +
                    "\n\n*Usage:* `!ai nickname <name>` or `!ai nickname reset`").queue();
            return;
        }

        String nickname = context.getArgs()[1];

        if (nickname.equalsIgnoreCase("reset") || nickname.equalsIgnoreCase("clear")) {
            settings.setNickname(null);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
            context.getChannel().sendMessage("✅ Nickname reset to default: **Pudel**").queue();
            return;
        }

        if (nickname.length() > 32) {
            context.getChannel().sendMessage("❌ Nickname must be 32 characters or less!").queue();
            return;
        }

        settings.setNickname(nickname);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
        context.getChannel().sendMessage("✅ Nickname set to: **" + nickname + "**").queue();
    }

    private void handleLanguage(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            String langName = getLanguageName(settings.getLanguage());
            context.getChannel().sendMessage("**Current Language:** " + langName +
                    "\n\n*Usage:* `!ai language <code>`\n*Options:* " + String.join(", ", VALID_LANGUAGES)).queue();
            return;
        }

        String language = context.getArgs()[1].toLowerCase();

        if (!VALID_LANGUAGES.contains(language)) {
            context.getChannel().sendMessage("❌ Unsupported language! Use: " + String.join(", ", VALID_LANGUAGES)).queue();
            return;
        }

        settings.setLanguage(language);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
        context.getChannel().sendMessage("✅ Response language set to: **" + getLanguageName(language) + "**").queue();
    }

    private String getLanguageName(String code) {
        if (code == null) return "English (default)";
        return switch (code) {
            case "en" -> "English";
            case "th" -> "Thai (ภาษาไทย)";
            case "ja" -> "Japanese (日本語)";
            case "ko" -> "Korean (한국어)";
            case "zh" -> "Chinese (中文)";
            case "de" -> "German (Deutsch)";
            case "fr" -> "French (Français)";
            case "es" -> "Spanish (Español)";
            case "pt" -> "Portuguese (Português)";
            case "ru" -> "Russian (Русский)";
            case "it" -> "Italian (Italiano)";
            case "nl" -> "Dutch (Nederlands)";
            case "pl" -> "Polish (Polski)";
            case "vi" -> "Vietnamese (Tiếng Việt)";
            case "id" -> "Indonesian (Bahasa Indonesia)";
            case "auto" -> "Auto-detect";
            default -> code.toUpperCase();
        };
    }

    private void handleResponseLength(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Response Length:** " +
                    (settings.getResponseLength() != null ? settings.getResponseLength() : "medium") +
                    "\n\n*Usage:* `!ai responselength <short|medium|detailed>`").queue();
            return;
        }

        String length = context.getArgs()[1].toLowerCase();
        if (!Set.of("short", "medium", "detailed").contains(length)) {
            context.getChannel().sendMessage("❌ Response length must be: short, medium, or detailed").queue();
            return;
        }

        settings.setResponseLength(length);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);

        String description = switch (length) {
            case "short" -> "Brief 1-2 sentence responses";
            case "medium" -> "Balanced 2-4 sentence responses";
            case "detailed" -> "Comprehensive responses with full explanations";
            default -> "";
        };

        context.getChannel().sendMessage("✅ Response length set to: **" + length + "** - " + description).queue();
    }

    private void handleFormality(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Formality:** " +
                    (settings.getFormality() != null ? settings.getFormality() : "balanced") +
                    "\n\n*Usage:* `!ai formality <casual|balanced|formal>`").queue();
            return;
        }

        String formality = context.getArgs()[1].toLowerCase();
        if (!Set.of("casual", "balanced", "formal").contains(formality)) {
            context.getChannel().sendMessage("❌ Formality must be: casual, balanced, or formal").queue();
            return;
        }

        settings.setFormality(formality);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);

        String description = switch (formality) {
            case "casual" -> "Friendly, relaxed tone with emojis and contractions";
            case "balanced" -> "Mix of friendly and professional";
            case "formal" -> "Professional, polite tone suitable for business";
            default -> "";
        };

        context.getChannel().sendMessage("✅ Formality set to: **" + formality + "** - " + description).queue();
    }

    private void handleEmoteUsage(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Emote Usage:** " +
                    (settings.getEmoteUsage() != null ? settings.getEmoteUsage() : "moderate") +
                    "\n\n*Usage:* `!ai emotes <none|minimal|moderate|frequent>`").queue();
            return;
        }

        String usage = context.getArgs()[1].toLowerCase();
        if (!Set.of("none", "minimal", "moderate", "frequent").contains(usage)) {
            context.getChannel().sendMessage("❌ Emote usage must be: none, minimal, moderate, or frequent").queue();
            return;
        }

        settings.setEmoteUsage(usage);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);

        String description = switch (usage) {
            case "none" -> "No emojis or emoticons in responses";
            case "minimal" -> "Occasional emojis for emphasis";
            case "moderate" -> "Regular use of emojis";
            case "frequent" -> "Heavy emoji usage for expressive responses ✨🎉";
            default -> "";
        };

        context.getChannel().sendMessage("✅ Emote usage set to: **" + usage + "** - " + description).queue();
    }

    private void handleQuirks(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Quirks:** " +
                    truncate(settings.getQuirks(), 500, "Not set") +
                    "\n\n*Usage:* `!ai quirks <text>` or `!ai quirks clear`\n" +
                    "*Example:* `!ai quirks Ends sentences with 'nya~', uses cat puns`").queue();
            return;
        }

        String text = String.join(" ", Arrays.copyOfRange(context.getArgs(), 1, context.getArgs().length));

        if (text.equalsIgnoreCase("clear")) {
            settings.setQuirks(null);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
            context.getChannel().sendMessage("✅ Quirks cleared!").queue();
            return;
        }

        settings.setQuirks(text);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
        context.getChannel().sendMessage("✅ Quirks set! I'll incorporate these speech patterns.").queue();
    }

    private void handleTopicsInterest(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Topics of Interest:** " +
                    truncate(settings.getTopicsInterest(), 500, "Not set") +
                    "\n\n*Usage:* `!ai interests <topics>` or `!ai interests clear`\n" +
                    "*Example:* `!ai interests gaming, anime, technology, music`").queue();
            return;
        }

        String text = String.join(" ", Arrays.copyOfRange(context.getArgs(), 1, context.getArgs().length));

        if (text.equalsIgnoreCase("clear")) {
            settings.setTopicsInterest(null);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
            context.getChannel().sendMessage("✅ Topics of interest cleared!").queue();
            return;
        }

        settings.setTopicsInterest(text);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
        context.getChannel().sendMessage("✅ Topics of interest set! I'll be more engaged on these topics.").queue();
    }

    private void handleTopicsAvoid(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Topics to Avoid:** " +
                    truncate(settings.getTopicsAvoid(), 500, "Not set") +
                    "\n\n*Usage:* `!ai avoid <topics>` or `!ai avoid clear`\n" +
                    "*Example:* `!ai avoid politics, religion, controversial topics`").queue();
            return;
        }

        String text = String.join(" ", Arrays.copyOfRange(context.getArgs(), 1, context.getArgs().length));

        if (text.equalsIgnoreCase("clear")) {
            settings.setTopicsAvoid(null);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
            context.getChannel().sendMessage("✅ Topics to avoid cleared!").queue();
            return;
        }

        settings.setTopicsAvoid(text);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
        context.getChannel().sendMessage("✅ Topics to avoid set! I'll steer away from these topics.").queue();
    }

    private void handleSystemPrompt(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current System Prompt Prefix:** " +
                    truncate(settings.getSystemPromptPrefix(), 500, "Using default system prompt") +
                    "\n\n*Usage:* `!ai systemprompt <text>` or `!ai systemprompt clear`\n" +
                    "*This is prepended to the LLM system prompt for advanced customization.*").queue();
            return;
        }

        String text = String.join(" ", Arrays.copyOfRange(context.getArgs(), 1, context.getArgs().length));

        if (text.equalsIgnoreCase("clear") || text.equalsIgnoreCase("reset")) {
            settings.setSystemPromptPrefix(null);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
            context.getChannel().sendMessage("✅ System prompt prefix cleared! Using default.").queue();
            return;
        }

        settings.setSystemPromptPrefix(text);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
        context.getChannel().sendMessage("✅ System prompt prefix set!").queue();
    }

    // ==================== AGENT METHODS ====================

    private void showAgentInfo(CommandContext context) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🤵 Pudel Agent System (Maid/Secretary AI)")
                .setColor(new Color(114, 137, 218))
                .setDescription("""
                        Pudel can act as your personal maid/secretary, managing data through natural conversation!
                        
                        **What I can do:**
                        • 📊 Create tables to organize information
                        • 📝 Store documents, notes, news
                        • 🔍 Search and retrieve data
                        • 🧠 Remember facts and recall them
                        
                        **How to use:**
                        Just talk to me naturally! For example:
                        • "Pudel, remember the meeting is on Friday"
                        • "Create a notes table for project updates"
                        • "Save this news article in my documents"
                        • "Find all notes about features"
                        
                        **Commands:**
                        • `!ai tables` - View your data tables
                        • `!ai memories` - View stored memories
                        """)
                .setFooter("All data is stored in your guild's private database");

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void showAgentTables(CommandContext context) {
        long guildId = context.getGuild().getIdLong();

        List<Map<String, Object>> tables = agentDataExecutor.listCustomTables(guildId, true);

        if (tables.isEmpty()) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📊 Agent Tables")
                    .setColor(new Color(114, 137, 218))
                    .setDescription("""
                            No tables created yet!
                            
                            Talk to me to create tables, for example:
                            "Pudel, create a notes table for team updates\"""");
            context.getChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 Agent Tables")
                .setColor(new Color(67, 181, 129));

        StringBuilder tableList = new StringBuilder();
        for (Map<String, Object> table : tables) {
            String name = (String) table.get("table_name");
            String desc = table.get("description") != null ? (String) table.get("description") : "No description";
            Object rowCount = table.get("row_count");

            tableList.append("• **").append(name.replace("agent_", "")).append("**");
            tableList.append(" (").append(rowCount).append(" entries)\n");
            tableList.append("  ").append(truncate(desc, 50, "")).append("\n\n");
        }

        embed.setDescription(tableList.toString());
        embed.setFooter("Total: " + tables.size() + " tables");

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void showAgentMemories(CommandContext context) {
        long guildId = context.getGuild().getIdLong();

        List<Map<String, Object>> memories = agentDataExecutor.getMemoriesByCategory(guildId, true, "agent_memory");

        if (memories.isEmpty()) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🧠 Agent Memories")
                    .setColor(new Color(114, 137, 218))
                    .setDescription("""
                            No memories stored yet!
                            
                            Ask me to remember things, for example:
                            "Pudel, remember that the deadline is next Friday\"""");
            context.getChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🧠 Agent Memories")
                .setColor(new Color(67, 181, 129));

        StringBuilder memoryList = new StringBuilder();
        int count = 0;
        for (Map<String, Object> memory : memories) {
            if (count >= 20) {
                memoryList.append("\n*... and ").append(memories.size() - 20).append(" more*");
                break;
            }

            String key = (String) memory.get("key");
            String value = (String) memory.get("value");

            memoryList.append("• **").append(key).append("**: ")
                    .append(truncate(value, 50, "")).append("\n");
            count++;
        }

        embed.setDescription(memoryList.toString());
        embed.setFooter("Total: " + memories.size() + " memories");

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    // ==================== WIZARD SESSION CLASS ====================

    private static class WizardSession {
        final String guildId;
        final String userId;
        final String channelId;
        final GuildSettings settings;
        int currentStep = 0;

        WizardSession(String guildId, String userId, String channelId, GuildSettings settings) {
            this.guildId = guildId;
            this.userId = userId;
            this.channelId = channelId;
            this.settings = settings;
        }
    }
}
