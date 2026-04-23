package com.jalenho.ai;

/**
 * Represents a single message in a conversation.
 */
public class ConversationMessage {
    /** Role: "user", "assistant", or "system" */
    public String role;
    /** The message content */
    public String content;
    /** Timestamp of the message in epoch milliseconds */
    public long timestamp;

    public ConversationMessage() {
    }

    public ConversationMessage(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public ConversationMessage(String role, String content, long timestamp) {
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }
}
