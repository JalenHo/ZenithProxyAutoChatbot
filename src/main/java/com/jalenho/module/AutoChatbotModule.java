package com.jalenho.module;

import com.github.rfresh2.EventConsumer;
import com.zenith.event.chat.PublicChatEvent;
import com.zenith.event.chat.WhisperChatEvent;
import com.zenith.module.api.Module;
import com.zenith.util.ChatUtil;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.CACHE;
import static com.jalenho.AutoChatbotPlugin.LOG;
import static com.jalenho.AutoChatbotPlugin.DATA_DIRECTORY;
import static com.jalenho.AutoChatbotPlugin.PLUGIN_CONFIG;
import com.jalenho.ai.AIMemoryManager;
import com.jalenho.ai.OpenAIClient;
import com.jalenho.ai.ConversationMessage;

/**
 * Core module that listens for chat events, matches keywords, and sends responses.
 * Supports both keyword-based responses and AI-powered responses via OpenAI.
 */
public class AutoChatbotModule extends Module {
    private long lastResponseTime = 0L;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AutoChatbot-TypingDelay");
        t.setDaemon(true);
        return t;
    });

    // AI components
    private AIMemoryManager aiMemoryManager;
    private OpenAIClient openAIClient;
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingAiRequests = new ConcurrentHashMap<>();

    // Chat history for context (used when AI server chat is enabled)
    private final LinkedList<ChatContextEntry> recentChatHistory = new LinkedList<>();
    private final int MAX_CHAT_HISTORY = 50; // Keep last 50 messages in memory
    private final Object chatHistoryLock = new Object();

    @Override
    public void onEnable() {
        super.onEnable();
        initializeAI();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        shutdownAI();
    }

    /**
     * Initialize AI components if AI is enabled
     */
    private void initializeAI() {
        if (!PLUGIN_CONFIG.aiEnabled) {
            LOG.info("AI functionality is disabled");
            return;
        }

        if (PLUGIN_CONFIG.openaiApiKey == null || PLUGIN_CONFIG.openaiApiKey.isBlank()) {
            LOG.warn("AI enabled but OpenAI API key not configured. Use /autoChatbot ai apiKey <key>");
            return;
        }

        // Initialize memory manager
        var dataDir = DATA_DIRECTORY;
        var memoryPath = dataDir.resolve(PLUGIN_CONFIG.aiMemoryPath);
        aiMemoryManager = new AIMemoryManager(memoryPath);

        // Initialize OpenAI client
        openAIClient = new OpenAIClient(PLUGIN_CONFIG.openaiApiKey, PLUGIN_CONFIG.openaiModel);

        LOG.info("AI Chatbot initialized with model: {}", PLUGIN_CONFIG.openaiModel);
        if (!PLUGIN_CONFIG.aiServerChatEnabled) {
            LOG.info("AI Server Chat listening is disabled. Use /autoChatbot ai serverChat on to enable.");
        }
    }

    /**
     * Shutdown AI components
     */
    private void shutdownAI() {
        if (aiMemoryManager != null) {
            aiMemoryManager.saveAll();
        }
        if (openAIClient != null) {
            openAIClient.shutdown();
        }
        scheduler.shutdown();
    }

    /**
     * Reinitialize AI components when config changes
     */
    public void syncAIFromConfig() {
        shutdownAI();
        initializeAI();
    }

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(PublicChatEvent.class, this::onPublicChat),
            of(WhisperChatEvent.class, this::onWhisperChat)
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

        String message = event.message();
        String messageLower = message.toLowerCase();

        // Store message in chat history for AI context
        if (PLUGIN_CONFIG.aiEnabled && PLUGIN_CONFIG.aiServerChatEnabled && PLUGIN_CONFIG.aiChatContextLength > 0) {
            addToChatHistory(senderName, message);
        }

        // Check AI triggers in server chat
        if (PLUGIN_CONFIG.aiEnabled && PLUGIN_CONFIG.aiServerChatEnabled && shouldTriggerAI(messageLower)) {
            handleAIServerChatResponse(senderName, message);
            return;
        }

        // Check keyword-based responses (existing functionality)
        // check cooldown
        long now = System.currentTimeMillis();
        if (now - lastResponseTime < PLUGIN_CONFIG.cooldownMs) {
            return;
        }

        for (var entry : PLUGIN_CONFIG.keywords) {
            if (entry.keyword.isEmpty() || entry.responses.isEmpty()) continue;
            if (messageLower.contains(entry.keyword.toLowerCase())) {
                // pick a random response
                String response = entry.responses.get(
                    ThreadLocalRandom.current().nextInt(entry.responses.size())
                );
                String sanitized = ChatUtil.sanitizeChatMessage(response);
                LOG.info("Keyword '{}' triggered by '{}', responding: {}", entry.keyword, senderName, sanitized);

                // update cooldown immediately to prevent duplicate triggers
                lastResponseTime = System.currentTimeMillis();

                sendResponse(sanitized);
                return; // only respond to the first matching keyword
            }
        }
    }

    /**
     * Handle whisper/private messages (DM to bot)
     */
    private void onWhisperChat(WhisperChatEvent event) {
        if (!PLUGIN_CONFIG.aiEnabled) {
            return;
        }

        // Get sender name from PlayerListEntry
        String senderName = null;
        var senderEntry = event.sender();
        if (senderEntry != null) {
            // Try getName() first as it should have the player's display name
            senderName = senderEntry.getName();
            if (senderName == null || senderName.isBlank()) {
                // Fallback: try to get name from profile cache by UUID
                var profileId = senderEntry.getProfileId();
                if (profileId != null) {
                    var profile = CACHE.getProfileCache().getProfile();
                    if (profile != null && profile.getId().equals(profileId)) {
                        senderName = profile.getName();
                    }
                }
            }
        }
        String message = event.message();

        if (senderName == null || message == null || message.isBlank()) {
            return;
        }

        LOG.info("Whisper message from {}: {}", senderName, message);

        // Send to AI
        handleAIPrivateMessage(senderName, message);
    }

    /**
     * Check if message should trigger AI response based on keywords
     */
    private boolean shouldTriggerAI(String messageLower) {
        if (PLUGIN_CONFIG.aiTriggerKeywords.isEmpty()) {
            // Default: no trigger keywords configured, don't auto-respond
            return false;
        }

        for (String keyword : PLUGIN_CONFIG.aiTriggerKeywords) {
            if (keyword.isEmpty()) continue;
            if (messageLower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add message to chat history for AI context
     */
    private void addToChatHistory(String sender, String message) {
        synchronized (chatHistoryLock) {
            recentChatHistory.add(new ChatContextEntry(sender, message, System.currentTimeMillis()));
            while (recentChatHistory.size() > MAX_CHAT_HISTORY) {
                recentChatHistory.removeFirst();
            }
        }
    }

    /**
     * Get recent chat history for AI context
     */
    private List<ChatContextEntry> getRecentChatHistory(int count) {
        synchronized (chatHistoryLock) {
            int size = recentChatHistory.size();
            if (size <= count) {
                return List.copyOf(recentChatHistory);
            }
            return List.copyOf(recentChatHistory.subList(size - count, size));
        }
    }

    /**
     * Handle AI response for server chat trigger
     */
    private void handleAIServerChatResponse(String senderName, String triggerMessage) {
        if (openAIClient == null || aiMemoryManager == null) {
            LOG.error("AI not initialized properly");
            return;
        }

        // Build context from recent chat history
        List<ChatContextEntry> recentMessages = getRecentChatHistory(PLUGIN_CONFIG.aiChatContextLength);
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("Recent server chat context (last ").append(PLUGIN_CONFIG.aiChatContextLength).append(" messages):\n");
        for (ChatContextEntry entry : recentMessages) {
            contextBuilder.append(entry.sender).append(": ").append(entry.message).append("\n");
        }

        // Build conversation messages
        List<ConversationMessage> conversationHistory = new ArrayList<>();
        conversationHistory.add(new ConversationMessage("user", contextBuilder.toString() + "\nPlayer '" + senderName + "' triggered AI with: " + triggerMessage));

        String finalResponse = openAIClient.generateResponseSync(PLUGIN_CONFIG.aiSystemPrompt, conversationHistory);

        if (finalResponse.isEmpty()) {
            LOG.error("AI response was empty or failed");
            return;
        }

        // Save to player memory
        aiMemoryManager.addMessage(senderName, "user", triggerMessage);
        aiMemoryManager.addMessage(senderName, "assistant", finalResponse);

        LOG.info("AI triggered by '{}' in server chat, responding: {}", senderName, finalResponse);

        // Send response with typing delay
        sendResponse(ChatUtil.sanitizeChatMessage(finalResponse));
    }

    /**
     * Handle AI response for private messages (DM to bot)
     */
    private void handleAIPrivateMessage(String playerName, String message) {
        if (openAIClient == null || aiMemoryManager == null) {
            LOG.error("AI not initialized properly");
            return;
        }

        // Build conversation with memory
        List<ConversationMessage> conversationHistory = new ArrayList<>();

        // Add relevant player memory
        List<ConversationMessage> memoryMessages = aiMemoryManager.getConversationHistory(playerName, 20);
        conversationHistory.addAll(memoryMessages);

        // Add current message
        conversationHistory.add(new ConversationMessage("user", message));

        // Get AI response
        String response = openAIClient.generateResponseSync(PLUGIN_CONFIG.aiSystemPrompt, conversationHistory);

        if (response.isEmpty()) {
            LOG.error("AI response was empty for player {}", playerName);
            return;
        }

        // Save conversation to memory
        aiMemoryManager.addMessage(playerName, "user", message);
        aiMemoryManager.addMessage(playerName, "assistant", response);

        LOG.info("AI responded to {}: {}", playerName, response);

        // Send response - for whisper messages we reply via whisper
        sendWhisper(playerName, response);
    }

    /**
     * Send a chat response with optional typing delay
     */
    private void sendResponse(String message) {
        if (PLUGIN_CONFIG.typingDelay.enabled && PLUGIN_CONFIG.typingDelay.charsPerMinute > 0) {
            long delayMs = (long) ((double) message.length() / PLUGIN_CONFIG.typingDelay.charsPerMinute * 60000);
            delayMs = Math.max(100, Math.min(delayMs, 30000));
            scheduler.schedule(() -> {
                sendClientPacketAsync(new ServerboundChatPacket(message));
            }, delayMs, TimeUnit.MILLISECONDS);
        } else {
            sendClientPacketAsync(new ServerboundChatPacket(message));
        }
    }

    /**
     * Send a whisper message to a player
     */
    private void sendWhisper(String playerName, String message) {
        // Format: /msg <playerName> <message>
        String command = "/msg " + playerName + " " + message;
        sendClientPacketAsync(new ServerboundChatPacket(command));
    }

    /**
     * Clear conversation memory for a specific player
     */
    public void clearPlayerMemory(String playerName) {
        if (aiMemoryManager != null) {
            aiMemoryManager.clearConversation(playerName);
        }
    }

    /**
     * Data class for chat context entries
     */
    private static class ChatContextEntry {
        public final String sender;
        public final String message;
        public final long timestamp;

        public ChatContextEntry(String sender, String message, long timestamp) {
            this.sender = sender;
            this.message = message;
            this.timestamp = timestamp;
        }
    }
}
