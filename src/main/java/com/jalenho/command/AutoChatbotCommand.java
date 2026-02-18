package com.jalenho.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.jalenho.module.AutoChatbotModule;

import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
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
                "on/off",
                "cooldown <ms>"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autoChatbot")
            .then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.enabled = getToggle(c, "toggle");
                MODULE.get(AutoChatbotModule.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    .title("Auto Chatbot " + toggleStrCaps(PLUGIN_CONFIG.enabled));
            }))
            .then(literal("cooldown").then(argument("ms", integer(0)).executes(c -> {
                PLUGIN_CONFIG.cooldownMs = getInteger(c, "ms");
                c.getSource().getEmbed()
                    .title("Cooldown Set")
                    .addField("Cooldown", PLUGIN_CONFIG.cooldownMs + " ms");
            })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed
            .primaryColor()
            .addField("Enabled", toggleStr(PLUGIN_CONFIG.enabled))
            .addField("Cooldown", PLUGIN_CONFIG.cooldownMs + " ms")
            .addField("Ignored Accounts", PLUGIN_CONFIG.ignoredAccounts.isEmpty()
                ? "None"
                : String.join(", ", PLUGIN_CONFIG.ignoredAccounts))
            .addField("Keywords", PLUGIN_CONFIG.keywords.stream()
                .map(k -> "\"" + k.keyword + "\" → " + k.responses)
                .collect(Collectors.joining("\n")));
    }
}
