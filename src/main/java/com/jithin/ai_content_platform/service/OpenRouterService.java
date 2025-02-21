package com.jithin.ai_content_platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Service
@Slf4j
public class OpenRouterService {
    private final String apiKey;
    private final String httpReferer;
    private final String appTitle;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private static final String OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions";

    public OpenRouterService(
            @Value("${openai.api.key}") String apiKey,
            @Value("${openai.http.referer}") String httpReferer,
            @Value("${openai.app.title}") String appTitle) {
        this.apiKey = apiKey;
        this.httpReferer = httpReferer;
        this.appTitle = appTitle;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Cacheable(value = "openRouterResponses", key = "#root.method.name + '_' + #model + '_' + T(java.util.Objects).hash(#messages) + '_' + T(java.util.Objects).hash(#extraBody)", unless = "#result == null")
    public Map<String, Object> createChatCompletion(String model, List<Map<String, String>> messages, Map<String, Object> extraBody) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("HTTP-Referer", httpReferer);
            headers.set("X-Title", appTitle);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            if (extraBody != null) {
                requestBody.putAll(extraBody);
            }

             // Log the request for debugging
        log.debug("OpenRouter API Request - Headers: {}", headers);
        log.debug("OpenRouter API Request - Body: {}", objectMapper.writeValueAsString(requestBody));


            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                OPENROUTER_API_URL,
                HttpMethod.POST,
                request,
                Map.class
            );

            log.debug("OpenRouter API Response: {}", responseEntity.getBody());
            
            if (!responseEntity.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("OpenRouter API returned status code: " + responseEntity.getStatusCode());
            }

            return responseEntity.getBody();
        } catch (Exception e) {
            log.error("Error calling OpenRouter API: ", e);
            throw new RuntimeException("Failed to create chat completion", e);
        }
    }

    public String generateCacheKey(String model, List<Map<String, String>> messages, Map<String, Object> extraBody) {
        try {
            // Create a string that combines all input parameters
            StringBuilder keyBuilder = new StringBuilder();
            keyBuilder.append(model);
            
            // Add messages
            for (Map<String, String> message : messages) {
                keyBuilder.append("_").append(message.get("role")).append(":").append(message.get("content"));
            }
            
            // Add relevant extraBody parameters that affect the output
            if (extraBody != null) {
                if (extraBody.containsKey("temperature")) {
                    keyBuilder.append("_temp:").append(extraBody.get("temperature"));
                }
                if (extraBody.containsKey("max_tokens")) {
                    keyBuilder.append("_max:").append(extraBody.get("max_tokens"));
                }
            }
            
            // Generate MD5 hash of the key to keep it a reasonable length
            return DigestUtils.md5DigestAsHex(keyBuilder.toString().getBytes());
        } catch (Exception e) {
            log.error("Error generating cache key", e);
            // Fallback to a timestamp-based key if there's an error
            return "fallback_" + System.currentTimeMillis();
        }
    }

    public String extractContentFromResponse(Map<String, Object> response) {
        try {
            if (response == null) {
                log.error("Received null response from OpenRouter API");
                return "";
            }
    
            // Log the entire response for debugging
            log.debug("Full OpenRouter API response: {}", response);
    
            // Check for different possible response structures
            if (response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    
                    // Try different ways to extract content
                    if (choice.containsKey("message")) {
                        Map<String, Object> message = (Map<String, Object>) choice.get("message");
                        Object content = message.get("content");
                        return content != null ? content.toString() : "";
                    }
                    
                    // Alternative extraction if message structure is different
                    Object content = choice.get("content");
                    return content != null ? content.toString() : "";
                }
            }
    
            // Additional fallback checks
            if (response.containsKey("content")) {
                return response.get("content").toString();
            }
    
            log.error("Unable to extract content from OpenRouter API response");
            return "";
    
        } catch (Exception e) {
            log.error("Error extracting content from OpenRouter API response", e);
            log.error("Full response details: {}", response);
            return "";
        }
    }
}
