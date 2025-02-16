package com.jithin.ai_content_platform.payload;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jithin.ai_content_platform.model.Content;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentRequest {
    private String contentType;    // e.g., "text", "image", "video"
    private String topic;
    private String emotionalTone;  // Optional
    private String category;       // Category of the content
    private String writingStyleSample;
    private String imageSize;
    private String keywords; // Comma-separated keywords
    private boolean optimizeForSEO; // Flag to optimize for SEO
    private String title;
    private String contentBody;
    private String region;  // Region for content localization
    private String targetAudience; // Target audience for the content
    private Map<String, String> metadata; // Additional metadata for the content
    private Long communityModelId; // ID of the community model to use for generation
    private String description; // Description of the content

    // In ContentRequest.java
    public String getWritingStyleSample() {
        return writingStyleSample;
    }

    public void setWritingStyleSample(String writingStyleSample) {
        this.writingStyleSample = writingStyleSample;
    }

    public boolean isOptimizeForSEO() {
        return optimizeForSEO;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // Constructor to create ContentRequest from Content entity
    public ContentRequest(Content content) {
        this.contentType = content.getContentType();
        this.topic = content.getTopic() != null ? content.getTopic() : "";
        this.category = content.getCategory();
        this.title = content.getTitle();
        
        // Only set optional fields if they exist
        if (content.getEmotionalTone() != null) {
            this.emotionalTone = content.getEmotionalTone();
        } else {
            this.emotionalTone = determineEmotionalTone(content.getAnalyzedSentiment());
        }
        if (content.getWritingStyle() != null) {
            this.writingStyleSample = content.getWritingStyle();
        }
        
        // Extract keywords from content body
        this.keywords = extractKeywords(content.getContentBody());
    }

    private String extractKeywords(String contentBody) {
        // Simple keyword extraction - take first few significant words
        if (contentBody == null) return "";
        return Arrays.stream(contentBody.split("\\s+"))
            .filter(word -> word.length() > 4)  // Filter out short words
            .limit(5)  // Take top 5 words
            .collect(Collectors.joining(", "));
    }

    private String determineEmotionalTone(String analyzedSentiment) {
        if (analyzedSentiment == null) return "neutral";
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Double> sentiment = mapper.readValue(analyzedSentiment, new TypeReference<Map<String, Double>>() {});
            double overallSentiment = sentiment.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            
            if (overallSentiment > 0.6) return "positive";
            if (overallSentiment < -0.3) return "negative";
            return "neutral";
        } catch (Exception e) {
            return "neutral";
        }
    }

    public Integer getContentLength() {
        return contentBody != null ? contentBody.length() : null;
    }
}