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

    public final List<KeywordEntry> keywords = new ArrayList<>(List.of(
        new KeywordEntry("type shi", List.of("shi")),
        new KeywordEntry("6 or 7", List.of("67"))
    ));

    public static class KeywordEntry {
        public String keyword = "";
        public List<String> responses = new ArrayList<>();

        // no-arg constructor for JSON deserialization
        public KeywordEntry() {}

        public KeywordEntry(String keyword, List<String> responses) {
            this.keyword = keyword;
            this.responses = new ArrayList<>(responses);
        }
    }
}
