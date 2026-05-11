package com.jalenho.ai;

import com.jalenho.AutoChatbotPlugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Client for interacting with OpenAI's Chat Completions API.
 * Sends messages and returns AI-generated responses.
 */
public class OpenAIClient {
    private static final int MAX_REQUEST_TIMEOUT_SECONDS = 30;
    private static final int MAX_CONCURRENT_REQUESTS = 5;

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final String reasoningEffort;
    private final HttpClient httpClient;
    private final ExecutorService executorService;
    private final Semaphore requestSemaphore;

    public OpenAIClient(String apiKey, String model) {
        this(apiKey, model, "https://api.openai.com", "");
    }

    public OpenAIClient(String apiKey, String model, String baseUrl, String reasoningEffort) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl : "https://api.openai.com";
        this.reasoningEffort = reasoningEffort != null ? reasoningEffort : "";
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.requestSemaphore = new Semaphore(MAX_CONCURRENT_REQUESTS);
    }

    /**
     * Send a request to OpenAI and get a response asynchronously.
     *
     * @param systemPrompt The system prompt for the AI
     * @param messages     List of conversation messages to send
     * @return CompletableFuture containing the AI response, or failed future on error
     */
    public CompletableFuture<String> generateResponseAsync(String systemPrompt, List<ConversationMessage> messages) {
        return CompletableFuture.supplyAsync(() -> {
            // Acquire permit for rate limiting
            try {
                requestSemaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "AI request interrupted";
            }

            try {
                return generateResponseSync(systemPrompt, messages);
            } finally {
                requestSemaphore.release();
            }
        }, executorService);
    }

    /**
     * Synchronous version of generateResponseAsync
     */
    public String generateResponseSync(String systemPrompt, List<ConversationMessage> messages) {
        if (apiKey == null || apiKey.isBlank()) {
            AutoChatbotPlugin.LOG.error("OpenAI API key is not set. Configure it with /autoChatbot ai apiKey <key>");
            return "";
        }

        // Build the request body manually
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\"model\":\"").append(escapeJson(model)).append("\"");
        jsonBuilder.append(",\"messages\":[");

        // Add system prompt
        jsonBuilder.append("{\"role\":\"system\",\"content\":\"").append(escapeJson(systemPrompt)).append("\"}");

        // Add conversation history
        for (ConversationMessage msg : messages) {
            jsonBuilder.append(",{\"role\":\"").append(escapeJson(msg.role)).append("\"");
            jsonBuilder.append(",\"content\":\"").append(escapeJson(msg.content)).append("\"}");
        }

        jsonBuilder.append("]");
        jsonBuilder.append(",\"max_tokens\":80");
        jsonBuilder.append(",\"temperature\":0.8");

        // Add reasoning_effort for supported models (low/medium/high/xhigh)
        if (!reasoningEffort.isBlank()) {
            jsonBuilder.append(",\"reasoning_effort\":\"").append(escapeJson(reasoningEffort)).append("\"");
        }

        jsonBuilder.append("}");
        String requestBody = jsonBuilder.toString();

        String apiUrl = baseUrl + "/v1/chat/completions";
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .timeout(Duration.ofSeconds(MAX_REQUEST_TIMEOUT_SECONDS))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            AutoChatbotPlugin.LOG.debug("API response (HTTP {}): {}", response.statusCode(),
                    response.body().length() > 500 ? response.body().substring(0, 500) + "..." : response.body());

            if (response.statusCode() == 200) {
                String content = extractContentFromResponse(response.body());
                if (content != null && !content.isEmpty()) {
                    return content;
                }
                AutoChatbotPlugin.LOG.error("OpenAI returned empty response content. Raw response: {}",
                        response.body().length() > 300 ? response.body().substring(0, 300) + "..." : response.body());
                return "";
            } else if (response.statusCode() == 401) {
                AutoChatbotPlugin.LOG.error("OpenAI API authentication failed. Check your API key.");
                return "";
            } else if (response.statusCode() == 429) {
                AutoChatbotPlugin.LOG.error("OpenAI rate limit hit. Try again in a moment.");
                return "";
            } else {
                AutoChatbotPlugin.LOG.error("OpenAI API error (HTTP {}): {}", response.statusCode(), response.body());
                return "";
            }
        } catch (IOException e) {
            AutoChatbotPlugin.LOG.error("Network error communicating with OpenAI: {}", e.getMessage());
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            AutoChatbotPlugin.LOG.error("OpenAI request was interrupted");
            return "";
        }
    }

    /**
     * Extract content from OpenAI response.
     * Supports both Chat Completions API and Responses API formats.
     *
     * Chat Completions: {"choices":[{"message":{"content":"text"}}]}
     * Responses API:    {"output":[{"content":[{"text":"text"}]}]}
     *                   or {"output_text":"text"}
     */
    private String extractContentFromResponse(String json) {
        // 1. Try Responses API: "output_text":"..." (simplest format)
        String outputText = extractJsonStringValue(json, "\"output_text\"");
        if (outputText != null) return outputText;

        // 2. Try Responses API: "output":[{"content":[{"text":"..."}]}]
        String responseText = extractNestedResponseValue(json);
        if (responseText != null) return responseText;

        // 3. Try Chat Completions: "content":"..." inside choices[0].message
        return extractChatCompletionContent(json);
    }

    /**
     * Extract a string value after a JSON key like "output_text":"value"
     */
    private String extractJsonStringValue(String json, String key) {
        int idx = json.indexOf(key + ":");
        if (idx == -1) idx = json.indexOf(key + " :");
        if (idx == -1) return null;

        int colon = json.indexOf(':', idx + key.length());
        if (colon == -1) return null;

        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length() || json.charAt(start) != '"') return null;
        start++;

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

    /**
     * Extract from Responses API nested format: "text":"..." inside output -> content -> text
     */
    private String extractNestedResponseValue(String json) {
        // Find "text":"..." pattern inside "content":[{  ...  }]
        int contentArray = json.indexOf("\"content\":[");
        if (contentArray == -1) return null;

        // Look for "text":"..." after this point
        int textIndex = json.indexOf("\"text\":\"", contentArray);
        if (textIndex == -1) textIndex = json.indexOf("\"text\": \"", contentArray);
        if (textIndex == -1) return null;

        int start = textIndex + 7; // skip "text":"
        if (json.charAt(start - 1) == ' ') start++; // handle "text": "
        start++; // skip opening quote

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
        return unescapeJson(sb.toString());
    }

    /**
     * Extract from Chat Completions format: "content":"..." inside message.
     * Finds the LAST "content":" match since reasoning_content appears before it.
     */
    private String extractChatCompletionContent(String json) {
        // Find ALL occurrences of "content":" and take the last one
        // (reasoning_content comes first, actual content comes last)
        int lastContentIndex = -1;
        int lastSkipLen = -1;
        int searchFrom = 0;
        while (true) {
            int idx = json.indexOf("\"content\":\"", searchFrom);
            int skipLen = 11; // "content":" + opening quote = 11 chars to value start
            if (idx == -1) {
                idx = json.indexOf("\"content\": \"", searchFrom);
                skipLen = 12; // "content": " + opening quote = 12 chars
            }
            if (idx == -1) break;

            // Skip if this is "reasoning_content"
            if (idx >= 11) {
                String before = json.substring(idx - 10, idx);
                if (before.equals("reasoning_")) {
                    searchFrom = idx + 1;
                    continue;
                }
            }

            lastContentIndex = idx;
            lastSkipLen = skipLen;
            searchFrom = idx + 1;
        }

        if (lastContentIndex == -1) return null;

        int start = lastContentIndex + lastSkipLen;
        int end = start;

        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '\\') {
                end++; // skip escape character
            } else if (c == '"') {
                break;
            }
            end++;
        }

        String content = json.substring(start, end);
        return unescapeJson(content);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescapeJson(String s) {
        if (s == null) return "";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                switch (next) {
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    default -> {
                        result.append(c);
                        result.append(next);
                    }
                }
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Check if the API key is valid by making a simple request
     */
    public boolean validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        try {
            String response = generateResponseSync(
                "You are a test assistant. Reply with only 'OK'.",
                List.of(new ConversationMessage("user", "Reply with only 'OK'."))
            );
            return response.contains("OK");
        } catch (Exception e) {
            AutoChatbotPlugin.LOG.error("API key validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Shutdown the executor service
     */
    public void shutdown() {
        executorService.shutdown();
    }
}
