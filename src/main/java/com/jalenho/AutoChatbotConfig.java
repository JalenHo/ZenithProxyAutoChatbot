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
}
