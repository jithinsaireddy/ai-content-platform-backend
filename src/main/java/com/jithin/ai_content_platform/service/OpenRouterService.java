package com.jithin.ai_content_platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
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

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                OPENROUTER_API_URL,
                HttpMethod.POST,
                request,
                Map.class
            );

            if (!responseEntity.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("OpenRouter API returned status code: " + responseEntity.getStatusCode());
            }

            return responseEntity.getBody();
        } catch (Exception e) {
            log.error("Error calling OpenRouter API: ", e);
            throw new RuntimeException("Failed to create chat completion", e);
        }
    }

    public String extractContentFromResponse(Map<String, Object> response) {
        if (response != null && response.containsKey("choices")) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (!choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                return (String) message.get("content");
            }
        }
        throw new RuntimeException("Invalid response format from OpenRouter API");
    }
}

