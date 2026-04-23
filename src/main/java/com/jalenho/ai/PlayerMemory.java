package com.jalenho.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents persistent memory for a single player.
 * Stores player name, conversation history, and metadata.
 */
public class PlayerMemory {
    /** Player name (lowercase) */
    public String playerName;
    /** Conversation history */
    public List<ConversationMessage> messages = new ArrayList<>();
    /** First time this player interacted with the bot */
    public long firstInteraction;
    /** Last interaction timestamp */
    public long lastInteraction;
    /** Number of total interactions */
    public int interactionCount;

    public PlayerMemory() {
    }

    public PlayerMemory(String playerName) {
        this.playerName = playerName;
        this.firstInteraction = System.currentTimeMillis();
        this.lastInteraction = this.firstInteraction;
        this.interactionCount = 0;
    }

    public void addMessage(String role, String content) {
        messages.add(new ConversationMessage(role, content));
        lastInteraction = System.currentTimeMillis();
        interactionCount++;
    }

    public List<ConversationMessage> getMessages() {
        return messages;
    }

    public void clearMessages() {
        messages.clear();
    }

    public PlayerMemory copy() {
        PlayerMemory copy = new PlayerMemory(this.playerName);
        copy.messages = new ArrayList<>(this.messages);
        copy.firstInteraction = this.firstInteraction;
        copy.lastInteraction = this.lastInteraction;
        copy.interactionCount = this.interactionCount;
        return copy;
    }
}
