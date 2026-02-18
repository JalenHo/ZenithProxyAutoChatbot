package com.jalenho.command;

import com.jalenho.AutoChatbotConfig;
import com.jalenho.module.AutoChatbotModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import java.util.ArrayList;
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
                Auto Chatbot - responds to keywords in chat
                """)
            .usageLines(
                "status",
                "on/off",
                "cooldown <ms>",
                "ignore add <name>",
                "ignore remove <name>",
                "ignore list",
                "keyword add <keyword> <response>",
                "keyword addResponse <keyword> <response>",
                "keyword remove <keyword>",
                "keyword list",
                "typing on/off",
                "typing speed <cpm>"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autoChatbot")
            // status
            .then(literal("status").executes(c -> {
                defaultEmbed(c.getSource().getEmbed());
            }))
            // on/off
            .then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.enabled = getToggle(c, "toggle");
                MODULE.get(AutoChatbotModule.class).syncEnabledFromConfig();
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
                .then(literal("add").then(argument("keyword", string()).then(argument("response", greedyString()).executes(c -> {
                    String keyword = getString(c, "keyword");
                    String response = getString(c, "response").trim();
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
                }))))
                .then(literal("addResponse").then(argument("keyword", string()).then(argument("response", greedyString()).executes(c -> {
                    String keyword = getString(c, "keyword");
                    String response = getString(c, "response").trim();
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
                }))))
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
                        .addField("≈ WPM", String.valueOf(PLUGIN_CONFIG.typingDelay.charsPerMinute / 5));
                })))
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
                    .collect(Collectors.joining("\n")));
    }
}
