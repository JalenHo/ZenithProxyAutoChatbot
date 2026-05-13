package com.jalenho.command;

import com.jalenho.ai.AIMemoryManager;
import com.jalenho.ai.CodexConfigLoader;
import com.jalenho.AutoChatbotConfig;
import com.jalenho.module.AutoChatbotModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import java.util.List;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static com.jalenho.AutoChatbotPlugin.PLUGIN_CONFIG;

public class AutoChatbotCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("autoChatbot")
            .category(CommandCategory.MODULE)
            .description("""
                Auto Chatbot - responds to keywords in chat and AI commands
                """)
            .usageLines(
                "status",
                "on/off",
                "cooldown <ms>",
                "ignore add <name>",
                "ignore remove <name>",
                "ignore list",
                "keyword add <keyword> | <response>",
                "keyword addResponse <keyword> | <response>",
                "keyword remove <keyword>",
                "keyword list",
                "typing on/off",
                "typing speed <cpm>",
                "ai on/off",
                "ai serverChat on/off",
                "ai apiKey <key>",
                "ai model <model>",
                "ai prompt <system-prompt>",
                "ai contextLength <count>",
                "ai triggerKeyword add <keyword>",
                "ai triggerKeyword remove <keyword>",
                "ai triggerKeyword list",
                "ai memory clear <player>",
                "ai reasoningEffort <level>",
                "ai loadConfig"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autoChatbot")
            // status
            .then(literal("status").executes(c -> {
                c.getSource().getEmbed().title("Auto Chatbot Status");
                defaultEmbed(c.getSource().getEmbed());
            }))
            // on/off
            .then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.enabled = getToggle(c, "toggle");
                syncModule();
                c.getSource().getEmbed()
                    .title("Auto Chatbot " + toggleStrCaps(PLUGIN_CONFIG.enabled));
            }))
            // cooldown <ms>
            .then(literal("cooldown").then(argument("ms", integer(0)).executes(c -> {
                PLUGIN_CONFIG.cooldownMs = getInteger(c, "ms");
                c.getSource().getEmbed()
                    .title("Cooldown Set")
                    .addField("Cooldown", PLUGIN_CONFIG.cooldownMs + " ms");
            })))
            // ignore add/remove/list
            .then(literal("ignore")
                .then(literal("add").then(argument("name", greedyString()).executes(c -> {
                    String name = getString(c, "name").trim();
                    if (!PLUGIN_CONFIG.ignoredAccounts.contains(name)) {
                        PLUGIN_CONFIG.ignoredAccounts.add(name);
                    }
                    c.getSource().getEmbed()
                        .title("Ignored Account Added")
                        .addField("Account", name)
                        .addField("Total Ignored", String.valueOf(PLUGIN_CONFIG.ignoredAccounts.size()));
                })))
                .then(literal("remove").then(argument("name", greedyString()).executes(c -> {
                    String name = getString(c, "name").trim();
                    boolean removed = PLUGIN_CONFIG.ignoredAccounts.removeIf(n -> n.equalsIgnoreCase(name));
                    c.getSource().getEmbed()
                        .title(removed ? "Ignored Account Removed" : "Account Not Found")
                        .addField("Account", name);
                })))
                .then(literal("list").executes(c -> {
                    c.getSource().getEmbed()
                        .title("Ignored Accounts")
                        .addField("Accounts", PLUGIN_CONFIG.ignoredAccounts.isEmpty()
                            ? "None"
                            : String.join(", ", PLUGIN_CONFIG.ignoredAccounts));
                }))
            )
            // keyword add/addResponse/remove/list
            .then(literal("keyword")
                .then(literal("add").then(argument("input", greedyString()).executes(c -> {
                    String input = getString(c, "input");
                    String[] parts = input.split("\\|", 2);
                    if (parts.length < 2) {
                        c.getSource().getEmbed()
                            .title("Invalid Format")
                            .addField("Usage", "keyword add <keyword> | <response>");
                        return;
                    }
                    String keyword = parts[0].trim();
                    String response = parts[1].trim();
                    if (keyword.isEmpty() || response.isEmpty()) {
                        c.getSource().getEmbed()
                            .title("Invalid Format")
                            .addField("Usage", "keyword add <keyword> | <response>");
                        return;
                    }
                    // check if keyword already exists
                    for (var entry : PLUGIN_CONFIG.keywords) {
                        if (entry.keyword.equalsIgnoreCase(keyword)) {
                            c.getSource().getEmbed()
                                .title("Keyword Already Exists")
                                .addField("Keyword", keyword)
                                .addField("Hint", "Use 'keyword addResponse' to add more responses");
                        return;
                        }
                    }
                    PLUGIN_CONFIG.keywords.add(new AutoChatbotConfig.KeywordEntry(keyword, List.of(response)));
                    c.getSource().getEmbed()
                        .title("Keyword Added")
                        .addField("Keyword", keyword)
                        .addField("Response", response);
                })))
                .then(literal("addResponse").then(argument("input", greedyString()).executes(c -> {
                    String input = getString(c, "input");
                    String[] parts = input.split("\\|", 2);
                    if (parts.length < 2) {
                        c.getSource().getEmbed()
                            .title("Invalid Format")
                            .addField("Usage", "keyword addResponse <keyword> | <response>");
                        return;
                    }
                    String keyword = parts[0].trim();
                    String response = parts[1].trim();
                    if (keyword.isEmpty() || response.isEmpty()) {
                        c.getSource().getEmbed()
                            .title("Invalid Format")
                            .addField("Usage", "keyword addResponse <keyword> | <response>");
                        return;
                    }
                    for (var entry : PLUGIN_CONFIG.keywords) {
                        if (entry.keyword.equalsIgnoreCase(keyword)) {
                            entry.responses.add(response);
                            c.getSource().getEmbed()
                                .title("Response Added")
                                .addField("Keyword", keyword)
                                .addField("New Response", response)
                                .addField("Total Responses", String.valueOf(entry.responses.size()));
                        return;
                        }
                    }
                    c.getSource().getEmbed()
                        .title("Keyword Not Found")
                        .addField("Keyword", keyword);
                })))
                .then(literal("remove").then(argument("keyword", greedyString()).executes(c -> {
                    String keyword = getString(c, "keyword").trim();
                    boolean removed = PLUGIN_CONFIG.keywords.removeIf(k -> k.keyword.equalsIgnoreCase(keyword));
                    c.getSource().getEmbed()
                        .title(removed ? "Keyword Removed" : "Keyword Not Found")
                        .addField("Keyword", keyword);
                })))
                .then(literal("list").executes(c -> {
                    String keywordList = PLUGIN_CONFIG.keywords.isEmpty()
                        ? "None"
                        : PLUGIN_CONFIG.keywords.stream()
                            .map(k -> "\"" + k.keyword + "\" → " + k.responses)
                            .collect(Collectors.joining("\n"));
                    c.getSource().getEmbed()
                        .title("Keywords")
                        .addField("Keywords", keywordList);
                }))
            )
            // typing on/off, typing speed <cpm>
            .then(literal("typing")
                .then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.typingDelay.enabled = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                        .title("Typing Delay " + toggleStrCaps(PLUGIN_CONFIG.typingDelay.enabled));
                }))
                .then(literal("speed").then(argument("cpm", integer(1)).executes(c -> {
                    PLUGIN_CONFIG.typingDelay.charsPerMinute = getInteger(c, "cpm");
                    c.getSource().getEmbed()
                        .title("Typing Speed Set")
                        .addField("Speed", PLUGIN_CONFIG.typingDelay.charsPerMinute + " CPM")
                        .addField("~ WPM", String.valueOf(PLUGIN_CONFIG.typingDelay.charsPerMinute / 5));
                })))
            )
            // ==================== AI Commands ====================
            .then(literal("ai")
                // ai on/off
                .then(literal("on").executes(c -> {
                    PLUGIN_CONFIG.aiEnabled = true;
                    MODULE.get(AutoChatbotModule.class).syncAIFromConfig();
                    c.getSource().getEmbed()
                        .title("AI Chatbot ON");
                }))
                .then(literal("off").executes(c -> {
                    PLUGIN_CONFIG.aiEnabled = false;
                    MODULE.get(AutoChatbotModule.class).syncAIFromConfig();
                    c.getSource().getEmbed()
                        .title("AI Chatbot OFF");
                }))
                // ai serverChat on/off
                .then(literal("serverChat")
                    .then(literal("on").executes(c -> {
                        PLUGIN_CONFIG.aiServerChatEnabled = true;
                        c.getSource().getEmbed()
                            .title("AI Server Chat ON")
                            .addField("Note", "AI will respond to server chat when trigger keywords are matched");
                    }))
                    .then(literal("off").executes(c -> {
                        PLUGIN_CONFIG.aiServerChatEnabled = false;
                        c.getSource().getEmbed()
                            .title("AI Server Chat OFF");
                    }))
                )
                // ai apiKey <key>
                .then(literal("apiKey").then(argument("key", greedyString()).executes(c -> {
                    String key = getString(c, "key").trim();
                    PLUGIN_CONFIG.openaiApiKey = key;
                    MODULE.get(AutoChatbotModule.class).syncAIFromConfig();
                    c.getSource().getEmbed()
                        .title("OpenAI API Key Set")
                        .addField("Model", PLUGIN_CONFIG.openaiModel);
                })))
                // ai model <model>
                .then(literal("model").then(argument("model", greedyString()).executes(c -> {
                    String model = getString(c, "model").trim();
                    PLUGIN_CONFIG.openaiModel = model;
                    MODULE.get(AutoChatbotModule.class).syncAIFromConfig();
                    c.getSource().getEmbed()
                        .title("OpenAI Model Set")
                        .addField("Model", model);
                })))
                // ai prompt <prompt>
                .then(literal("prompt").then(argument("prompt", greedyString()).executes(c -> {
                    String prompt = getString(c, "prompt").trim();
                    PLUGIN_CONFIG.aiSystemPrompt = prompt;
                    c.getSource().getEmbed()
                        .title("AI System Prompt Set")
                        .addField("Prompt", prompt.length() > 100 ? prompt.substring(0, 100) + "..." : prompt)
                        .addField("Length", prompt.length() + " characters");
                })))
                // ai contextLength <count>
                .then(literal("contextLength").then(argument("count", integer(0, 100)).executes(c -> {
                    int count = getInteger(c, "count");
                    PLUGIN_CONFIG.aiChatContextLength = count;
                    c.getSource().getEmbed()
                        .title("AI Context Length Set")
                        .addField("Context Messages", count)
                        .addField("Note", count == 0 ? "Server chat context disabled" : "Will include last " + count + " messages as context");
                })))
                // ai triggerKeyword add/remove/list
                .then(literal("triggerKeyword")
                    .then(literal("add").then(argument("keyword", greedyString()).executes(c -> {
                        String keyword = getString(c, "keyword").trim();
                        if (!PLUGIN_CONFIG.aiTriggerKeywords.contains(keyword)) {
                            PLUGIN_CONFIG.aiTriggerKeywords.add(keyword);
                        }
                        c.getSource().getEmbed()
                            .title("AI Trigger Keyword Added")
                            .addField("Keyword", keyword)
                            .addField("Total Keywords", String.valueOf(PLUGIN_CONFIG.aiTriggerKeywords.size()));
                    })))
                    .then(literal("remove").then(argument("keyword", greedyString()).executes(c -> {
                        String keyword = getString(c, "keyword").trim();
                        boolean removed = PLUGIN_CONFIG.aiTriggerKeywords.removeIf(k -> k.equalsIgnoreCase(keyword));
                        c.getSource().getEmbed()
                            .title(removed ? "AI Trigger Keyword Removed" : "Keyword Not Found")
                            .addField("Keyword", keyword);
                    })))
                    .then(literal("list").executes(c -> {
                        c.getSource().getEmbed()
                            .title("AI Trigger Keywords")
                            .addField("Keywords", PLUGIN_CONFIG.aiTriggerKeywords.isEmpty()
                                ? "None (AI will not auto-respond in server chat)"
                                : String.join(", ", PLUGIN_CONFIG.aiTriggerKeywords));
                    }))
                )
                // ai memory clear <player>
                .then(literal("memory")
                    .then(literal("clear").then(argument("player", string()).executes(c -> {
                        String playerName = getString(c, "player").trim();
                        var module = MODULE.get(AutoChatbotModule.class);
                        module.clearPlayerMemory(playerName);
                        c.getSource().getEmbed()
                            .title("Player Memory Cleared")
                            .addField("Player", playerName);
                    })))
                )
                // ai reasoningEffort <level>
                .then(literal("reasoningEffort").then(argument("level", greedyString()).executes(c -> {
                    String level = getString(c, "level").trim().toLowerCase();
                    if (!level.matches("^(low|medium|high|xhigh)$")) {
                        c.getSource().getEmbed()
                            .title("Invalid Reasoning Effort")
                            .addField("Valid Options", "low, medium, high, xhigh")
                            .addField("Got", level);
                        return;
                    }
                    PLUGIN_CONFIG.openaiReasoningEffort = level;
                    MODULE.get(AutoChatbotModule.class).syncAIFromConfig();
                    c.getSource().getEmbed()
                        .title("AI Reasoning Effort Set")
                        .addField("Level", level);
                })))
                // ai loadConfig - load from codex config.toml + auth.json
                .then(literal("loadConfig").executes(c -> {
                    CodexConfigLoader loader = new CodexConfigLoader();
                    if (loader.load()) {
                        loader.applyToConfig(PLUGIN_CONFIG);
                        MODULE.get(AutoChatbotModule.class).syncAIFromConfig();
                        c.getSource().getEmbed()
                            .title("Codex Config Loaded")
                            .addField("Model", loader.getModel() != null ? loader.getModel() : "(unchanged)")
                            .addField("Base URL", loader.getBaseUrl() != null ? loader.getBaseUrl() : "(unchanged)")
                            .addField("Reasoning Effort", loader.getReasoningEffort() != null ? loader.getReasoningEffort() : "(unchanged)")
                            .addField("API Key", loader.getApiKey() != null ? "Set (****" + loader.getApiKey().substring(Math.max(0, loader.getApiKey().length() - 4)) + ")" : "(unchanged)");
                    } else {
                        c.getSource().getEmbed()
                            .title("Config Load Failed")
                            .addField("Missing", CodexConfigLoader.CODEX_DIR.resolve("config.toml").toString())
                            .addField("Hint", "Place config.toml and auth.json in: " + CodexConfigLoader.CODEX_DIR);
                    }
                }))
            );
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed
            .primaryColor()
            .addField("Enabled", toggleStr(PLUGIN_CONFIG.enabled))
            .addField("Cooldown", PLUGIN_CONFIG.cooldownMs + " ms")
            .addField("Typing Delay", toggleStr(PLUGIN_CONFIG.typingDelay.enabled)
                + " (" + PLUGIN_CONFIG.typingDelay.charsPerMinute + " CPM / ~"
                + (PLUGIN_CONFIG.typingDelay.charsPerMinute / 5) + " WPM)")
            .addField("Ignored Accounts", PLUGIN_CONFIG.ignoredAccounts.isEmpty()
                ? "None"
                : String.join(", ", PLUGIN_CONFIG.ignoredAccounts))
            .addField("Keywords", PLUGIN_CONFIG.keywords.isEmpty()
                ? "None"
                : PLUGIN_CONFIG.keywords.stream()
                    .map(k -> "\"" + k.keyword + "\" → " + k.responses)
                    .collect(Collectors.joining("\n")))
            // AI Section
            .addField("", "─────────── AI Configuration ───────────")
            .addField("AI Enabled", toggleStr(PLUGIN_CONFIG.aiEnabled))
            .addField("AI Server Chat", toggleStr(PLUGIN_CONFIG.aiServerChatEnabled))
            .addField("OpenAI Model", PLUGIN_CONFIG.openaiModel)
            .addField("Context Length", PLUGIN_CONFIG.aiChatContextLength + " messages")
            .addField("Trigger Keywords", PLUGIN_CONFIG.aiTriggerKeywords.isEmpty()
                ? "None (use /autoChatbot ai triggerKeyword add <keyword>)"
                : String.join(", ", PLUGIN_CONFIG.aiTriggerKeywords))
            .addField("API Key Set", PLUGIN_CONFIG.openaiApiKey.isEmpty() ? "No" : "Yes (****" + maskedKey() + ")")
            .addField("System Prompt", PLUGIN_CONFIG.aiSystemPrompt.length() > 50
                ? PLUGIN_CONFIG.aiSystemPrompt.substring(0, 50) + "..."
                : PLUGIN_CONFIG.aiSystemPrompt);
    }

    private String maskedKey() {
        String key = PLUGIN_CONFIG.openaiApiKey;
        if (key == null || key.length() < 8) return "****";
        return key.substring(key.length() - 4);
    }

    private void syncModule() {
        MODULE.get(AutoChatbotModule.class).syncEnabledFromConfig();
    }
}