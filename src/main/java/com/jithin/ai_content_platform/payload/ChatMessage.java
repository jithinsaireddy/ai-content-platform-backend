// src/main/java/com/jithin/ai_content_platform/payload/ChatMessage.java

package com.jithin.ai_content_platform.payload;

import lombok.Data;

@Data
public class ChatMessage {
    private String content;
    private String sender;
    private MessageType type;

    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE
    }
}