package com.jalenho;

import com.zenith.plugin.api.Plugin;
import com.zenith.plugin.api.PluginAPI;
import com.zenith.plugin.api.ZenithProxyPlugin;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import com.jalenho.command.AutoChatbotCommand;
import com.jalenho.module.AutoChatbotModule;

import java.nio.file.Path;
import java.nio.file.Paths;

@Plugin(
    id = BuildConstants.PLUGIN_ID,
    version = BuildConstants.VERSION,
    description = "Auto Chatbot - responds to keyword triggers in server chat",
    url = "https://github.com/JalenHo/ZenithProxyAutoChatbot",
    authors = {"JalenHo"},
    mcVersions = {"*"}
)
public class AutoChatbotPlugin implements ZenithProxyPlugin {
    public static AutoChatbotConfig PLUGIN_CONFIG;
    public static ComponentLogger LOG;
    public static Path DATA_DIRECTORY;

    @Override
    public void onLoad(PluginAPI pluginAPI) {
        LOG = pluginAPI.getLogger();
        LOG.info("AutoChatbot Plugin loading...");
        PLUGIN_CONFIG = pluginAPI.registerConfig(BuildConstants.PLUGIN_ID, AutoChatbotConfig.class);
        // Store data directory: plugins/<pluginId>/
        DATA_DIRECTORY = Paths.get("plugins", BuildConstants.PLUGIN_ID);
        pluginAPI.registerModule(new AutoChatbotModule());
        pluginAPI.registerCommand(new AutoChatbotCommand());
        LOG.info("AutoChatbot Plugin loaded! {} keyword(s) configured.", PLUGIN_CONFIG.keywords.size());
    }
}
