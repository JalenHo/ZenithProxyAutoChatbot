package com.jalenho.ai;

import com.jalenho.AutoChatbotPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages persistent AI memory for each player.
 * Stores conversation history per player and saves to JSON files.
 */
public class AIMemoryManager {
    private final Path memoryDirectory;
    private final ConcurrentHashMap<String, PlayerMemory> playerMemories = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReadWriteLock> fileLocks = new ConcurrentHashMap<>();

    public AIMemoryManager(Path memoryDirectory) {
        this.memoryDirectory = memoryDirectory;
        try {
            Files.createDirectories(memoryDirectory);
            AutoChatbotPlugin.LOG.info("AI Memory directory initialized: {}", memoryDirectory);
        } catch (IOException e) {
            AutoChatbotPlugin.LOG.error("Failed to create memory directory: {}", e.getMessage());
        }
    }

    /**
     * Get or create memory for a player
     */
    public PlayerMemory getPlayerMemory(String playerName) {
        return playerMemories.computeIfAbsent(playerName.toLowerCase(), name -> {
            PlayerMemory memory = loadPlayerMemory(name);
            if (memory == null) {
                memory = new PlayerMemory(name);
            }
            return memory;
        });
    }

    /**
     * Add a conversation message to player's memory
     */
    public void addMessage(String playerName, String role, String content) {
        PlayerMemory memory = getPlayerMemory(playerName);
        memory.addMessage(role, content);
        savePlayerMemoryAsync(playerName, memory);
    }

    /**
     * Get conversation history for a player
     */
    public List<ConversationMessage> getConversationHistory(String playerName, int limit) {
        PlayerMemory memory = getPlayerMemory(playerName);
        List<ConversationMessage> messages = memory.getMessages();
        if (messages.size() <= limit) {
            return new ArrayList<>(messages);
        }
        // Return last 'limit' messages
        return new ArrayList<>(messages.subList(messages.size() - limit, messages.size()));
    }

    /**
     * Clear conversation history for a player
     */
    public void clearConversation(String playerName) {
        PlayerMemory memory = getPlayerMemory(playerName);
        memory.clearMessages();
        savePlayerMemoryAsync(playerName, memory);
        AutoChatbotPlugin.LOG.info("Cleared conversation memory for player: {}", playerName);
    }

    /**
     * Get memory file path for a player
     */
    private Path getMemoryFilePath(String playerName) {
        String safeName = playerName.replaceAll("[^a-zA-Z0-9_-]", "_");
        return memoryDirectory.resolve(safeName + ".json");
    }

    /**
     * Convert PlayerMemory to JSON string
     */
    private String toJson(PlayerMemory memory) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"playerName\":\"").append(escapeJson(memory.playerName)).append("\"");
        sb.append(",\"messages\":[");
        for (int i = 0; i < memory.messages.size(); i++) {
            if (i > 0) sb.append(",");
            ConversationMessage msg = memory.messages.get(i);
            sb.append("{\"role\":\"").append(escapeJson(msg.role)).append("\"");
            sb.append(",\"content\":\"").append(escapeJson(msg.content)).append("\"");
            sb.append(",\"timestamp\":").append(msg.timestamp).append("}");
        }
        sb.append("]");
        sb.append(",\"firstInteraction\":").append(memory.firstInteraction);
        sb.append(",\"lastInteraction\":").append(memory.lastInteraction);
        sb.append(",\"interactionCount\":").append(memory.interactionCount);
        sb.append("}");
        return sb.toString();
    }

    /**
     * Parse JSON to PlayerMemory
     */
    private PlayerMemory fromJson(String json) {
        PlayerMemory memory = new PlayerMemory();
        memory.messages = new ArrayList<>();

        try {
            // Simple JSON parsing without external library
            memory.playerName = extractString(json, "playerName");
            memory.firstInteraction = extractLong(json, "firstInteraction");
            memory.lastInteraction = extractLong(json, "lastInteraction");
            memory.interactionCount = extractInt(json, "interactionCount");

            // Parse messages array
            int messagesStart = json.indexOf("\"messages\":[");
            if (messagesStart != -1) {
                int arrayStart = json.indexOf("[", messagesStart);
                int arrayEnd = findMatchingBracket(json, arrayStart);
                if (arrayStart != -1 && arrayEnd != -1) {
                    String messagesArray = json.substring(arrayStart + 1, arrayEnd);
                    memory.messages = parseMessages(messagesArray);
                }
            }
        } catch (Exception e) {
            AutoChatbotPlugin.LOG.error("Failed to parse memory JSON: {}", e.getMessage());
        }

        return memory;
    }

    private List<ConversationMessage> parseMessages(String array) {
        List<ConversationMessage> messages = new ArrayList<>();
        int depth = 0;
        int start = -1;

        for (int i = 0; i < array.length(); i++) {
            char c = array.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start != -1) {
                    String obj = array.substring(start, i + 1);
                    ConversationMessage msg = new ConversationMessage();
                    msg.role = extractString(obj, "role");
                    msg.content = extractString(obj, "content");
                    msg.timestamp = extractLong(obj, "timestamp");
                    messages.add(msg);
                    start = -1;
                }
            }
        }
        return messages;
    }

    private String extractString(String json, String field) {
        int fieldPos = json.indexOf("\"" + field + "\"");
        if (fieldPos == -1) return "";
        int colon = json.indexOf(":", fieldPos);
        int valueStart = json.indexOf("\"", colon);
        int valueEnd = json.indexOf("\"", valueStart + 1);
        if (valueStart != -1 && valueEnd != -1) {
            return json.substring(valueStart + 1, valueEnd);
        }
        return "";
    }

    private long extractLong(String json, String field) {
        int fieldPos = json.indexOf("\"" + field + "\"");
        if (fieldPos == -1) return 0;
        int colon = json.indexOf(":", fieldPos);
        int valueStart = colon + 1;
        while (valueStart < json.length() && (json.charAt(valueStart) == ' ' || json.charAt(valueStart) == '\n')) {
            valueStart++;
        }
        int valueEnd = valueStart;
        while (valueEnd < json.length() && (Character.isDigit(json.charAt(valueEnd)) || json.charAt(valueEnd) == '-')) {
            valueEnd++;
        }
        String numStr = json.substring(valueStart, valueEnd);
        return numStr.isEmpty() ? 0 : Long.parseLong(numStr.trim());
    }

    private int extractInt(String json, String field) {
        return (int) extractLong(json, field);
    }

    private int findMatchingBracket(String s, int start) {
        int depth = 1;
        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                i++; // skip escaped character
            } else if (c == '[' || c == '{') {
                depth++;
            } else if (c == ']' || c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Load player memory from file
     */
    private PlayerMemory loadPlayerMemory(String playerName) {
        Path filePath = getMemoryFilePath(playerName);
        ReadWriteLock lock = fileLocks.computeIfAbsent(playerName, k -> new ReentrantReadWriteLock());

        lock.readLock().lock();
        try {
            if (Files.exists(filePath)) {
                String json = Files.readString(filePath);
                return fromJson(json);
            }
        } catch (IOException e) {
            AutoChatbotPlugin.LOG.error("Failed to load memory for player {}: {}", playerName, e.getMessage());
        } finally {
            lock.readLock().unlock();
        }
        return null;
    }

    /**
     * Save player memory asynchronously to avoid blocking
     */
    private void savePlayerMemoryAsync(String playerName, PlayerMemory memory) {
        Path filePath = getMemoryFilePath(playerName);
        ReadWriteLock lock = fileLocks.computeIfAbsent(playerName, k -> new ReentrantReadWriteLock());

        // Create a copy for async saving
        PlayerMemory memoryCopy = memory.copy();
        String json = toJson(memoryCopy);

        Thread.ofVirtual().start(() -> {
            lock.writeLock().lock();
            try {
                Files.writeString(filePath, json);
            } catch (IOException e) {
                AutoChatbotPlugin.LOG.error("Failed to save memory for player {}: {}", playerName, e.getMessage());
            } finally {
                lock.writeLock().unlock();
            }
        });
    }

    /**
     * Save all player memories to disk (called on shutdown)
     */
    public void saveAll() {
        for (var entry : playerMemories.entrySet()) {
            savePlayerMemoryAsync(entry.getKey(), entry.getValue());
        }
        AutoChatbotPlugin.LOG.info("Saved all player AI memories to disk");
    }
}
