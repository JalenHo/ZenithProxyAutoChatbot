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
import com.jalenho.ai.CodexConfigLoader;

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
     * Initialize AI components if AI is enabled.
     * Tries to load from Codex config files first (config.toml + auth.json).
     */
    private void initializeAI() {
        // Try loading from Codex config files first
        CodexConfigLoader codexLoader = new CodexConfigLoader();
        if (codexLoader.load()) {
            codexLoader.applyToConfig(PLUGIN_CONFIG);
            LOG.info("AI configuration loaded from Codex config files");
        }

        if (!PLUGIN_CONFIG.aiEnabled) {
            LOG.info("AI functionality is disabled");
            return;
        }

        if (PLUGIN_CONFIG.openaiApiKey == null || PLUGIN_CONFIG.openaiApiKey.isBlank()) {
            LOG.warn("AI enabled but OpenAI API key not configured. Use /autoChatbot ai apiKey <key> or place auth.json in plugins/auto-chatbot/codex/");
            return;
        }

        // Initialize memory manager
        var dataDir = DATA_DIRECTORY;
        var memoryPath = dataDir.resolve(PLUGIN_CONFIG.aiMemoryPath);
        aiMemoryManager = new AIMemoryManager(memoryPath);

        // Initialize OpenAI client with config from Codex files if available
        openAIClient = new OpenAIClient(
            PLUGIN_CONFIG.openaiApiKey,
            PLUGIN_CONFIG.openaiModel,
            PLUGIN_CONFIG.openaiBaseUrl,
            PLUGIN_CONFIG.openaiReasoningEffort
        );

        LOG.info("AI Chatbot initialized with model: {}, base_url: {}, reasoning_effort: {}",
                PLUGIN_CONFIG.openaiModel, PLUGIN_CONFIG.openaiBaseUrl,
                PLUGIN_CONFIG.openaiReasoningEffort.isEmpty() ? "default" : PLUGIN_CONFIG.openaiReasoningEffort);
        if (!PLUGIN_CONFIG.aiServerChatEnabled) {
            LOG.info("AI Server Chat listening is disabled. Use /autoChatbot ai serverChat on to enable.");
        }
    }

    /**
     * Shutdown AI components (memory + client only).
     * The scheduler thread pool is NOT shut down here — it stays alive
     * for typing delay messages sent while AI is re-initialized.
     */
    private void shutdownAI() {
        if (aiMemoryManager != null) {
            aiMemoryManager.saveAll();
            aiMemoryManager = null;
        }
        if (openAIClient != null) {
            openAIClient.shutdown();
            openAIClient = null;
        }
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
        // Module is enabled if either keyword auto-chat OR AI is enabled.
        // Each feature checks its own toggle internally.
        return PLUGIN_CONFIG.enabled || PLUGIN_CONFIG.aiEnabled;
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

        // Strip sender name from message if it's included (some servers include "username message" in the raw event)
        if (senderName != null && messageLower.startsWith(senderName.toLowerCase())) {
            String stripped = message.substring(senderName.length());
            // Remove leading separators like ": ", " > ", " » ", space, etc.
            stripped = stripped.replaceAll("^\\s*[>:»]\\s*", "").trim();
            if (!stripped.isEmpty()) {
                message = stripped;
                messageLower = message.toLowerCase();
            }
        }

        // ---- AI Server Chat ----
        if (PLUGIN_CONFIG.aiEnabled && PLUGIN_CONFIG.aiServerChatEnabled && PLUGIN_CONFIG.aiChatContextLength > 0) {
            addToChatHistory(senderName, message);
        }

        if (PLUGIN_CONFIG.aiEnabled && PLUGIN_CONFIG.aiServerChatEnabled && shouldTriggerAI(messageLower)) {
            handleAIServerChatResponse(senderName, message);
            return;
        }

        // ---- Keyword Auto-Chat (only when autoChatbot is on) ----
        if (!PLUGIN_CONFIG.enabled) {
            return;
        }

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
     * Handle whisper/private messages (DM to bot).
     * AI only — works independently of keyword auto-chat.
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

        // ignore configured accounts
        if (senderName != null) {
            for (String ignored : PLUGIN_CONFIG.ignoredAccounts) {
                if (senderName.equalsIgnoreCase(ignored)) {
                    return;
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

        String finalResponse = openAIClient.generateResponseSync(PLUGIN_CONFIG.aiSystemPrompt + CHAT_LENGTH_HINT, conversationHistory);

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
        String response = openAIClient.generateResponseSync(PLUGIN_CONFIG.aiSystemPrompt + CHAT_LENGTH_HINT, conversationHistory);

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

    private static final int MC_CHAT_MAX_LENGTH = 256;
    private static final int MAX_SPLIT_MESSAGES = 3; // Don't spam too many messages
    private static final String CHAT_LENGTH_HINT =
        " IMPORTANT: You are chatting in Minecraft. Keep ALL responses under 200 characters total. Be brief, casual, one or two short sentences max. Never use bullet points or lists.";

    /**
     * Send a chat response with optional typing delay.
     * Splits long messages to fit Minecraft's 256 char limit.
     */
    private void sendResponse(String message) {
        if (scheduler.isShutdown()) {
            LOG.warn("Scheduler shutdown, sending message immediately without typing delay");
            sendClientPacketAsync(new ServerboundChatPacket(message));
            return;
        }

        if (!PLUGIN_CONFIG.typingDelay.enabled || PLUGIN_CONFIG.typingDelay.charsPerMinute <= 0) {
            LOG.debug("Typing delay disabled (enabled={}, cpm={}), sending immediately",
                    PLUGIN_CONFIG.typingDelay.enabled, PLUGIN_CONFIG.typingDelay.charsPerMinute);
        }

        List<String> parts = splitMessage(message, MC_CHAT_MAX_LENGTH);
        long cumulativeDelay = 0;

        for (String part : parts) {
            if (PLUGIN_CONFIG.typingDelay.enabled && PLUGIN_CONFIG.typingDelay.charsPerMinute > 0) {
                long delayMs = (long) ((double) part.length() / PLUGIN_CONFIG.typingDelay.charsPerMinute * 60000);
                delayMs = Math.max(100, Math.min(delayMs, 30000));
                cumulativeDelay += delayMs;
                final long delay = cumulativeDelay;
                scheduler.schedule(() -> {
                    sendClientPacketAsync(new ServerboundChatPacket(part));
                }, delay, TimeUnit.MILLISECONDS);
            } else {
                final long delay = cumulativeDelay;
                scheduler.schedule(() -> {
                    sendClientPacketAsync(new ServerboundChatPacket(part));
                }, delay, TimeUnit.MILLISECONDS);
                cumulativeDelay += 500; // small delay between split messages
            }
        }
    }

    /**
     * Send a whisper message to a player.
     * Splits long messages to fit Minecraft's 256 char limit.
     */
    private void sendWhisper(String playerName, String message) {
        String prefix = "/msg " + playerName + " ";
        int maxContentLen = MC_CHAT_MAX_LENGTH - prefix.length();
        List<String> parts = splitMessage(message, maxContentLen);
        long cumulativeDelay = 0;

        for (String part : parts) {
            String command = prefix + part;
            final long delay = cumulativeDelay;
            scheduler.schedule(() -> {
                sendClientPacketAsync(new ServerboundChatPacket(command));
            }, delay, TimeUnit.MILLISECONDS);
            cumulativeDelay += 500;
        }
    }

    /**
     * Split a message into chunks that fit within maxLength.
     * Tries to split on spaces/newlines. Caps at MAX_SPLIT_MESSAGES.
     */
    private List<String> splitMessage(String message, int maxLength) {
        // Replace newlines with spaces for cleaner chat output
        message = message.replace("\n", " ").replace("\r", "").trim();

        if (message.length() <= maxLength) {
            return List.of(message);
        }

        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < message.length() && parts.size() < MAX_SPLIT_MESSAGES) {
            int end = Math.min(start + maxLength, message.length());
            if (end < message.length()) {
                // Try to split on a space
                int lastSpace = message.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }
            parts.add(message.substring(start, end).trim());
            start = end + 1; // skip the space
        }

        // If there's still leftover text, append "..." to the last part
        if (start < message.length() && !parts.isEmpty()) {
            String last = parts.get(parts.size() - 1);
            if (last.length() + 3 <= maxLength) {
                parts.set(parts.size() - 1, last + "...");
            }
        }

        return parts;
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


