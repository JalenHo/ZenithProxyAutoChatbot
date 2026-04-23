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
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final int MAX_REQUEST_TIMEOUT_SECONDS = 30;
    private static final int MAX_CONCURRENT_REQUESTS = 5;

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ExecutorService executorService;
    private final Semaphore requestSemaphore;

    public OpenAIClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
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
        jsonBuilder.append(",\"max_tokens\":500");
        jsonBuilder.append(",\"temperature\":0.8}");
        String requestBody = jsonBuilder.toString();

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(OPENAI_API_URL))
            .timeout(Duration.ofSeconds(MAX_REQUEST_TIMEOUT_SECONDS))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String content = extractContentFromResponse(response.body());
                if (content != null && !content.isEmpty()) {
                    return content;
                }
                AutoChatbotPlugin.LOG.error("OpenAI returned empty response content");
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
     * Extract content from OpenAI response
     */
    private String extractContentFromResponse(String json) {
        // Find: "content":"text"}
        // or: "content":"text"}],"usage"
        int contentIndex = json.indexOf("\"content\":\"");
        if (contentIndex == -1) {
            contentIndex = json.indexOf("\"content\": \"");
        }
        if (contentIndex == -1) {
            return null;
        }

        int start = contentIndex + 10; // skip past "content":" or "content": "
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
