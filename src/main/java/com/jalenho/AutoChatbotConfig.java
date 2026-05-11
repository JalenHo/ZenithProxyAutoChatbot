package com.jalenho;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the Auto Chatbot plugin.
 *
 * Saved/loaded automatically as JSON by ZenithProxy.
 * All fields should be public and mutable.
 */
public class AutoChatbotConfig {
    public boolean enabled = true;
    public int cooldownMs = 3000;
    public List<String> ignoredAccounts = new ArrayList<>();

    public final TypingDelayConfig typingDelay = new TypingDelayConfig();

    public static class TypingDelayConfig {
        /** Whether to simulate human typing delay before sending */
        public boolean enabled = true;
        /** Characters per minute — average human is ~200 CPM (roughly 40 WPM) */
        public int charsPerMinute = 200;
    }

    public final List<KeywordEntry> keywords = new ArrayList<>();

    public static class KeywordEntry {
        public String keyword = "";
        public List<String> responses = new ArrayList<>();

        // no-arg constructor for JSON deserialization
        public KeywordEntry() {
        }

        public KeywordEntry(String keyword, List<String> responses) {
            this.keyword = keyword;
            this.responses = new ArrayList<>(responses);
        }
    }

    // ==================== AI Configuration ====================

    /** Enable AI-powered responses via OpenAI API */
    public boolean aiEnabled = false;

    /** Toggle whether bot reads and responds to server chat via AI */
    public boolean aiServerChatEnabled = false;

    /** OpenAI API key for AI responses */
    public String openaiApiKey = "";

    /** OpenAI model to use (e.g., gpt-4o-mini, gpt-4o, gpt-5.4) */
    public String openaiModel = "gpt-4o-mini";

    /** Base URL for OpenAI API (default: official OpenAI endpoint) */
    public String openaiBaseUrl = "https://api.openai.com";

    /** Reasoning effort for supported models: low, medium, high, xhigh */
    public String openaiReasoningEffort = "";

    /** System prompt for the AI agent's personality and behavior */
    public String aiSystemPrompt = "You are a helpful assistant responding in Minecraft chat. Keep responses brief and conversational. You have memory of previous conversations.";

    /** Maximum number of recent chat messages to include as context (0 = disabled) */
    public int aiChatContextLength = 10;

    /** Keywords that trigger AI response in server chat (default: bot account name) */
    public List<String> aiTriggerKeywords = new ArrayList<>();

    /** Path to AI memory directory (relative to ZenithProxy data folder) */
    public String aiMemoryPath = "plugins/auto-chatbot/memory/";

    // ==================== AI Channel Commands ====================

    /**
     * DM Commands - When a player /msg to the bot, it will respond via AI.
     * This is enabled by default when aiEnabled = true.
     */

    /**
     * Server Chat Commands - Toggle to enable AI reading server chat.
     * Use: /autoChatbot ai serverChat on/off
     * When enabled, AI reads server chat and responds when trigger keywords match.
     */
}