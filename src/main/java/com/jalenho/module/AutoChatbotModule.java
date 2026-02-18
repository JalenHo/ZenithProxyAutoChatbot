package com.jalenho.module;

import com.github.rfresh2.EventConsumer;
import com.zenith.event.chat.PublicChatEvent;
import com.zenith.module.api.Module;
import com.zenith.util.ChatUtil;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.CACHE;
import static com.jalenho.AutoChatbotPlugin.PLUGIN_CONFIG;

/**
 * Core module that listens for PublicChatEvent,
 * matches keywords, and sends a random response.
 */
public class AutoChatbotModule extends Module {
    private long lastResponseTime = 0L;

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(PublicChatEvent.class, this::onPublicChat)
        );
    }

    private void onPublicChat(PublicChatEvent event) {
        // ignore our own messages
        var botProfile = CACHE.getProfileCache().getProfile();
        if (botProfile != null && event.sender().getProfileId().equals(botProfile.getId())) {
            return;
        }

        // ignore configured accounts
        String senderName = event.sender().getName();
        if (senderName != null) {
            for (String ignored : PLUGIN_CONFIG.ignoredAccounts) {
                if (senderName.equalsIgnoreCase(ignored)) {
                    return;
                }
            }
        }

        // check cooldown
        long now = System.currentTimeMillis();
        if (now - lastResponseTime < PLUGIN_CONFIG.cooldownMs) {
            return;
        }

        String messageLower = event.message().toLowerCase();

        for (var entry : PLUGIN_CONFIG.keywords) {
            if (entry.keyword.isEmpty() || entry.responses.isEmpty()) continue;
            if (messageLower.contains(entry.keyword.toLowerCase())) {
                // pick a random response
                String response = entry.responses.get(
                    ThreadLocalRandom.current().nextInt(entry.responses.size())
                );
                String sanitized = ChatUtil.sanitizeChatMessage(response);
                info("Keyword '{}' triggered by '{}', responding: {}", entry.keyword, senderName, sanitized);
                sendClientPacketAsync(new ServerboundChatPacket(sanitized));
                lastResponseTime = now;
                return; // only respond to the first matching keyword
            }
        }
    }
}
