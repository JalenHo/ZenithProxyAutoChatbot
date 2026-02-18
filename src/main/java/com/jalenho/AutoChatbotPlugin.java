package com.jalenho;

import com.zenith.plugin.api.Plugin;
import com.zenith.plugin.api.PluginAPI;
import com.zenith.plugin.api.ZenithProxyPlugin;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import com.jalenho.command.AutoChatbotCommand;
import com.jalenho.module.AutoChatbotModule;

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

    @Override
    public void onLoad(PluginAPI pluginAPI) {
        LOG = pluginAPI.getLogger();
        LOG.info("AutoChatbot Plugin loading...");
        PLUGIN_CONFIG = pluginAPI.registerConfig(BuildConstants.PLUGIN_ID, AutoChatbotConfig.class);
        pluginAPI.registerModule(new AutoChatbotModule());
        pluginAPI.registerCommand(new AutoChatbotCommand());
        LOG.info("AutoChatbot Plugin loaded! {} keyword(s) configured.", PLUGIN_CONFIG.keywords.size());
    }
}
