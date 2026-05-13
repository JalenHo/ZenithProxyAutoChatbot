package com.jalenho.ai;

import com.jalenho.AutoChatbotPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads AI configuration from Codex CLI config files:
 *   - config.toml  (model, provider, reasoning effort, etc.)
 *   - auth.json    (API key)
 *
 * Expected location: plugins/auto-chatbot/codex/config.toml
 *                    plugins/auto-chatbot/codex/auth.json
 */
public class CodexConfigLoader {

    public static final Path CODEX_DIR = AutoChatbotPlugin.DATA_DIRECTORY.resolve("codex");

    private String apiKey;
    private String model;
    private String baseUrl;
    private String wireApi;
    private String reasoningEffort;
    private boolean loaded = false;

    /**
     * Try to load config.toml and auth.json from the codex directory.
     * Returns true if at least config.toml was found and parsed.
     */
    public boolean load() {
        loaded = false;

        Path configPath = CODEX_DIR.resolve("config.toml");
        Path authPath = CODEX_DIR.resolve("auth.json");

        if (!Files.exists(configPath)) {
            AutoChatbotPlugin.LOG.info("No Codex config.toml found at {}", configPath);
            return false;
        }

        try {
            String content = Files.readString(configPath);
            Map<String, String> flat = parseFlatToml(content);

            model = flat.get("model");
            reasoningEffort = flat.get("model_reasoning_effort");

            // Parse [model_providers.OpenAI] section
            Map<String, String> providerSection = parseTomlSection(content, "model_providers.OpenAI");
            baseUrl = providerSection.get("base_url");
            wireApi = providerSection.get("wire_api");

            loaded = true;
            AutoChatbotPlugin.LOG.info("Loaded Codex config: model={}, reasoning_effort={}, base_url={}",
                    model, reasoningEffort, baseUrl);
        } catch (IOException e) {
            AutoChatbotPlugin.LOG.error("Failed to read config.toml: {}", e.getMessage());
            return false;
        }

        // Load auth.json for the API key
        if (Files.exists(authPath)) {
            try {
                String authContent = Files.readString(authPath);
                apiKey = extractJsonString(authContent, "OPENAI_API_KEY");
                if (apiKey != null && !apiKey.isBlank()) {
                    AutoChatbotPlugin.LOG.info("Loaded API key from auth.json (sk-...****)", maskedKey());
                }
            } catch (IOException e) {
                AutoChatbotPlugin.LOG.error("Failed to read auth.json: {}", e.getMessage());
            }
        } else {
            AutoChatbotPlugin.LOG.info("No auth.json found at {}", authPath);
        }

        return loaded;
    }

    /**
     * Apply loaded settings to the plugin config.
     */
    public void applyToConfig(com.jalenho.AutoChatbotConfig config) {
        if (!loaded) return;

        if (apiKey != null && !apiKey.isBlank()) {
            config.openaiApiKey = apiKey;
        }
        if (model != null && !model.isBlank()) {
            config.openaiModel = model;
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            config.openaiBaseUrl = baseUrl;
        }
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            config.openaiReasoningEffort = reasoningEffort;
        }

        AutoChatbotPlugin.LOG.info("Applied Codex config to plugin: model={}, reasoning_effort={}",
                config.openaiModel, config.openaiReasoningEffort);
    }

    // ==================== TOML Parsing ====================

    /**
     * Parse flat key = "value" pairs from the top-level TOML (ignoring [sections]).
     */
    private Map<String, String> parseFlatToml(String content) {
        Map<String, String> map = new HashMap<>();
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("[")) continue;
            int eq = trimmed.indexOf('=');
            if (eq < 0) continue;
            String key = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            value = stripQuotes(value);
            map.put(key, value);
        }
        return map;
    }

    /**
     * Parse a named TOML section like [model_providers.OpenAI].
     * Supports dotted keys like base_url = "..."
     */
    private Map<String, String> parseTomlSection(String content, String sectionName) {
        Map<String, String> map = new HashMap<>();
        String sectionHeader = "[" + sectionName + "]";
        boolean inSection = false;

        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.equals(sectionHeader)) {
                inSection = true;
                continue;
            }
            if (trimmed.startsWith("[") && inSection) {
                break; // next section
            }
            if (!inSection || trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            int eq = trimmed.indexOf('=');
            if (eq < 0) continue;
            String key = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            value = stripQuotes(value);
            map.put(key, value);
        }
        return map;
    }

    private String stripQuotes(String s) {
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /**
     * Extract a string value from simple JSON: "KEY": "value"
     */
    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;

        // find the colon after the key
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return null;

        // skip whitespace and opening quote
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length() || json.charAt(start) != '"') return null;
        start++; // skip opening "

        // find closing quote (handle escaped quotes)
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                sb.append(json.charAt(++i));
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String maskedKey() {
        if (apiKey == null || apiKey.length() < 8) return "****";
        return apiKey.substring(apiKey.length() - 4);
    }

    // ==================== Getters ====================

    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public String getBaseUrl() { return baseUrl; }
    public String getReasoningEffort() { return reasoningEffort; }
    public boolean isLoaded() { return loaded; }
}
