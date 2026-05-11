package com.jalenho.module;

import com.github.rfresh2.EventConsumer;
import com.zenith.event.chat.PublicChatEvent;
import com.zenith.module.api.Module;
import com.zenith.util.ChatUtil;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;

import java.util.List;
import java.util.concurrent.*;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.CACHE;
import static com.jalenho.AutoChatbotPlugin.PLUGIN_CONFIG;

/**
 * Core module that listens for PublicChatEvent,
 * matches keywords, and sends a random response.
 * Optionally simulates human typing delay before sending.
 */
public class AutoChatbotModule extends Module {
    private long lastResponseTime = 0L;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AutoChatbot-TypingDelay");
        t.setDaemon(true);
        return t;
    });

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

        String message = event.message();

        // Strip sender name from message if it's included (some servers include "username message" in the raw event)
        if (senderName != null && message.toLowerCase().startsWith(senderName.toLowerCase())) {
            String stripped = message.substring(senderName.length());
            stripped = stripped.replaceAll("^\\s*[>:»]\\s*", "").trim();
            if (!stripped.isEmpty()) {
                message = stripped;
            }
        }

        String messageLower = message.toLowerCase();

        for (var entry : PLUGIN_CONFIG.keywords) {
            if (entry.keyword.isEmpty() || entry.responses.isEmpty()) continue;
            if (messageLower.contains(entry.keyword.toLowerCase())) {
                // pick a random response
                String response = entry.responses.get(
                    ThreadLocalRandom.current().nextInt(entry.responses.size())
                );
                String sanitized = ChatUtil.sanitizeChatMessage(response);
                info("Keyword '{}' triggered by '{}', responding: {}", entry.keyword, senderName, sanitized);

                // update cooldown immediately to prevent duplicate triggers
                lastResponseTime = System.currentTimeMillis();

                if (PLUGIN_CONFIG.typingDelay.enabled && PLUGIN_CONFIG.typingDelay.charsPerMinute > 0) {
                    // calculate delay: (chars / CPM) * 60000ms
                    long delayMs = (long) ((double) sanitized.length() / PLUGIN_CONFIG.typingDelay.charsPerMinute * 60000);
                    // clamp to at least 100ms and at most 30s
                    delayMs = Math.max(100, Math.min(delayMs, 30000));
                    info("Typing delay: {} ms for {} chars", delayMs, sanitized.length());
                    scheduler.schedule(() -> {
                        sendClientPacketAsync(new ServerboundChatPacket(sanitized));
                    }, delayMs, TimeUnit.MILLISECONDS);
                } else {
                    sendClientPacketAsync(new ServerboundChatPacket(sanitized));
                }
                return; // only respond to the first matching keyword
            }
        }
    }
}
