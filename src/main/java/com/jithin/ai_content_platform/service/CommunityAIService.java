package com.jithin.ai_content_platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jithin.ai_content_platform.model.CommunityModel;
import com.jithin.ai_content_platform.model.CommunityModelTrainingData;
import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.payload.ContentRequest;
import com.jithin.ai_content_platform.repository.CommunityModelTrainingDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CommunityAIService {

    @Autowired
    private OpenRouterService openRouterService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CommunityModelTrainingDataRepository trainingDataRepository;

    @Value("${community.model.min-quality-score:0.7}")
    private double minQualityScore;

    @Value("${community.model.max-examples:5}")
    private int maxExamples;

    @Value("${openai.model}")
    private String model;

    public String generateContentWithCommunityModel(ContentRequest request, CommunityModel communityModel) {
        try {
            // Get high-quality training examples
            List<CommunityModelTrainingData> examples = trainingDataRepository
                .findHighQualityTrainingData(communityModel, minQualityScore)
                .stream()
                .limit(maxExamples)
                .collect(Collectors.toList());

            // Create the few-shot prompt
            String prompt = createFewShotPrompt(request, examples, communityModel);

            // Generate content using the community model
            Map<String, Object> response = openRouterService.createChatCompletion(
                model,
                createMessages(prompt),
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", 2000
                )
            );

            String generatedContent = openRouterService.extractContentFromResponse(response);

            // Apply style guide and post-processing
            return applyStyleGuide(generatedContent, communityModel.getStyleGuide());

        } catch (Exception e) {
            log.error("Error generating content with community model: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate content with community model", e);
        }
    }

    private String createFewShotPrompt(
        ContentRequest request,
        List<CommunityModelTrainingData> examples,
        CommunityModel model
    ) {
        StringBuilder prompt = new StringBuilder();
        
        // Add system context and style guide
        prompt.append("You are a specialized content generator trained on community-provided examples. ");
        prompt.append("Follow these style guidelines:\n");
        prompt.append(model.getStyleGuide()).append("\n\n");
        
        // Add examples
        prompt.append("Here are some examples of high-quality content in this style:\n\n");
        for (CommunityModelTrainingData example : examples) {
            prompt.append("Example:\n").append(example.getContent()).append("\n\n");
        }
        
        // Add the actual request
        prompt.append("Now, generate new content with the following parameters:\n");
        prompt.append("Title: ").append(request.getTitle()).append("\n");
        prompt.append("Topic: ").append(request.getTopic()).append("\n");
        prompt.append("Category: ").append(request.getCategory()).append("\n");
        if (request.getKeywords() != null && !request.getKeywords().isEmpty()) {
            prompt.append("Keywords: ").append(String.join(", ", request.getKeywords())).append("\n");
        }
        prompt.append("Target Audience: ").append(request.getTargetAudience()).append("\n\n");
        
        return prompt.toString();
    }

    private List<Map<String, String>> createMessages(String prompt) {
        return Arrays.asList(
            Map.of(
                "role", "system",
                "content", "You are a specialized content generator that creates content following specific community guidelines and examples."
            ),
            Map.of(
                "role", "user",
                "content", prompt
            )
        );
    }

    private String applyStyleGuide(String content, String styleGuideJson) {
        try {
            Map<String, Object> styleGuide = objectMapper.readValue(styleGuideJson, Map.class);
            
            // Create a prompt for style guide application
            String prompt = String.format(
                "Apply the following style guide to the content while preserving its meaning:\n" +
                "Style Guide: %s\n\n" +
                "Content:\n%s",
                objectMapper.writeValueAsString(styleGuide),
                content
            );
            
            Map<String, Object> response = openRouterService.createChatCompletion(
                model,
                Arrays.asList(
                    Map.of(
                        "role", "system",
                        "content", "You are a content editor that applies style guidelines while preserving the original meaning."
                    ),
                    Map.of(
                        "role", "user",
                        "content", prompt
                    )
                ),
                Map.of(
                    "temperature", 0.3,
                    "max_tokens", 2000
                )
            );
            
            return openRouterService.extractContentFromResponse(response);
            
        } catch (Exception e) {
            log.error("Error applying style guide: {}", e.getMessage(), e);
            return content; // Return original content if style guide application fails
        }
    }
}
