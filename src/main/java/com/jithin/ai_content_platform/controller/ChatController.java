// src/main/java/com/jithin/ai_content_platform/controller/ChatController.java

package com.jithin.ai_content_platform.controller;

import com.jithin.ai_content_platform.model.ChatHistory;
import com.jithin.ai_content_platform.payload.ChatMessage;
import com.jithin.ai_content_platform.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage) {
        return chatService.processMessage(chatMessage);
    }

    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    public ChatMessage addUser(@Payload ChatMessage chatMessage) {
        chatMessage.setType(ChatMessage.MessageType.JOIN);
        return chatMessage;
    }

    @GetMapping("/history/{userId}")
    public List<ChatHistory> getChatHistory(
        @PathVariable String userId,
        @RequestParam(defaultValue = "50") int limit
    ) {
        return chatService.getChatHistory(userId, limit);
    }
}