package com.jithin.ai_content_platform.service;

import com.jithin.ai_content_platform.model.ChatHistory;
import com.jithin.ai_content_platform.payload.ChatMessage;
import com.jithin.ai_content_platform.repository.ChatHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class ChatService {
    
    @Autowired
    private ChatHistoryRepository chatHistoryRepository;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Value("${openai.api.key}")
    private String openaiApiKey;
    
    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String openaiApiUrl;

    @Value("${openai.model:gpt-3.5-turbo}")
    private String openaiModel;

    public ChatMessage processMessage(ChatMessage message) {
        try {
            // Store user message
            ChatHistory userChat = new ChatHistory();
            userChat.setUserId(message.getSender());
            userChat.setMessage(message.getContent());
            userChat.setTimestamp(LocalDateTime.now());
            userChat.setAiResponse(false);
            chatHistoryRepository.save(userChat);

            // Get AI response
            String aiResponse = getAiResponse(message.getContent());
            
            // Store AI response
            ChatHistory aiChat = new ChatHistory();
            aiChat.setUserId(message.getSender());
            aiChat.setMessage(aiResponse);
            aiChat.setResponse(aiResponse);
            aiChat.setTimestamp(LocalDateTime.now());
            aiChat.setAiResponse(true);
            chatHistoryRepository.save(aiChat);

            // Create response message
            ChatMessage response = new ChatMessage();
            response.setSender("AI Assistant");
            response.setContent(aiResponse);
            response.setType(ChatMessage.MessageType.CHAT);
            
            return response;
        } catch (Exception e) {
            // Log error and return error message
            ChatMessage errorResponse = new ChatMessage();
            errorResponse.setSender("AI Assistant");
            errorResponse.setContent("I apologize, but I encountered an error processing your message. Please try again.");
            errorResponse.setType(ChatMessage.MessageType.ERROR);
            return errorResponse;
        }
    }

    private String getAiResponse(String userMessage) {
        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            return "API key not configured. Please set the OpenAI API key in the application properties.";
        }

        // Prepare OpenAI API request
        Map<String, Object> request = new HashMap<>();
        request.put("model", openaiModel);
        request.put("messages", List.of(
            Map.of(
                "role", "system", 
                "content", "You are an AI assistant specialized in content strategy and marketing. " +
                          "Help users with their content-related questions and provide specific, " +
                          "actionable advice."
            ),
            Map.of("role", "user", "content", userMessage)
        ));
        request.put("temperature", 0.7);
        request.put("max_tokens", 500);
        request.put("presence_penalty", 0.6);
        request.put("frequency_penalty", 0.5);

        // Call OpenAI API
        try {
            var headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(openaiApiKey);
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            
            var entity = new org.springframework.http.HttpEntity<>(request, headers);
            var response = restTemplate.postForObject(openaiApiUrl, entity, Map.class);
            
            if (response != null && response.containsKey("choices")) {
                var choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    var message = (Map<String, String>) choices.get(0).get("message");
                    return message.get("content");
                }
            }
            return "I apologize, but I received an unexpected response format. Please try again.";
        } catch (Exception e) {
            return "I apologize, but I'm having trouble connecting to the AI service. Please try again later.";
        }
    }

    public List<ChatHistory> getChatHistory(String userId, int limit) {
        return chatHistoryRepository.findByUserIdOrderByTimestampDesc(
            userId, 
            PageRequest.of(0, limit)
        );
    }
}
