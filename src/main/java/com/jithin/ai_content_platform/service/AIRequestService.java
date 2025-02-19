package com.jithin.ai_content_platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import com.jithin.ai_content_platform.util.JsonResponseHandler;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import java.util.HashMap;

@Service
@Slf4j
public class AIRequestService {

    @Autowired
    private OpenRouterService openRouterService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JsonResponseHandler jsonResponseHandler;

    @Value("${openai.model}")
    private String model;

    private final Map<String, Map<String, Object>> requestCache = new ConcurrentHashMap<>();
    private final Map<String, Long> requestTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = TimeUnit.MINUTES.toMillis(30);

    public Map<String, Object> makeRequest(String requestType, String prompt, Map<String, Object> metadata) {
        // Create a unique cache key based on request type and prompt
        String cacheKey = requestType + ":" + prompt;
        
        // Check cache with timestamp validation
        long currentTime = System.currentTimeMillis();
        Map<String, Object> cachedResponse = requestCache.get(cacheKey);
        Long cacheTimestamp = requestTimestamps.get(cacheKey);
        
        // Return cached result if available and not expired
        if (cachedResponse != null && 
            cacheTimestamp != null && 
            (currentTime - cacheTimestamp) < CACHE_DURATION) {
            log.info("Returning cached response for request type: {}", requestType);
            return cachedResponse;
        }

        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, String>> messages = List.of(
                Map.of("role", "system", 
                       "content", getSystemPromptForRequestType(requestType)),
                Map.of("role", "user", 
                       "content", prompt)
            );

            Map<String, Object> openAiResponse = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", 2000
                )
            );

            String content = openRouterService.extractContentFromResponse(openAiResponse);
            response = jsonResponseHandler.parseAndValidateJson(content, new HashMap<>());

            // Add metadata
            response.put("request_type", requestType);
            response.put("timestamp", currentTime);
            response.putAll(metadata);

            // Cache the response
            requestCache.put(cacheKey, response);
            requestTimestamps.put(cacheKey, currentTime);

            // Cleanup old cache entries
            cleanupCache();

        } catch (Exception e) {
            log.error("Error making AI request for type {}: {}", requestType, e.getMessage());
            response.put("error", "Failed to process request");
            response.put("error_details", e.getMessage());
        }

        return response;
    }

    private String getSystemPromptForRequestType(String requestType) {
        return switch (requestType) {
            case "content_generation" -> 
                "You are a professional content creator. Generate high-quality, engaging content.";
            case "sentiment_analysis" -> 
                "You are a sentiment analysis expert. Analyze text sentiment comprehensively.";
            case "competitive_analysis" -> 
                "You are a competitive analysis expert. Analyze market positioning and competition.";
            case "trend_analysis" -> 
                "You are a trend analysis expert. Identify and analyze market trends.";
            case "content_optimization" -> 
                "You are a content optimization expert. Improve content quality and engagement.";
            default -> 
                "You are an AI assistant. Provide accurate and helpful responses.";
        };
    }

    private void cleanupCache() {
        long currentTime = System.currentTimeMillis();
        requestCache.entrySet().removeIf(entry -> {
            Long timestamp = requestTimestamps.get(entry.getKey());
            return timestamp == null || (currentTime - timestamp) > CACHE_DURATION;
        });
        requestTimestamps.entrySet().removeIf(
            entry -> (currentTime - entry.getValue()) > CACHE_DURATION
        );
    }
}
