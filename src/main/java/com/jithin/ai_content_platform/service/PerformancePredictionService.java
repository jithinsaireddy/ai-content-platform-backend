package com.jithin.ai_content_platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.repository.ContentRepository;
import com.jithin.ai_content_platform.util.JsonResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PerformancePredictionService {

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OpenRouterService openRouterService;

    @Autowired
    private JsonResponseHandler jsonResponseHandler;

    @Autowired
    private FeedbackAnalysisService feedbackAnalysisService;

    @Value("${openai.model}")
    private String model;

    public Map<String, Object> predictContentPerformance(Content content) {
        Map<String, Object> prediction = new HashMap<>();
        
        // 1. Historical Performance Analysis
        double historicalScore = analyzeHistoricalPerformance(content);
        
        // 2. Content Quality Analysis
        double qualityScore = analyzeContentQuality(content);
        
        // 3. Timing Analysis
        double timingScore = analyzePublishingTiming(content);
        
        // 4. Audience Match Analysis
        double audienceScore = analyzeAudienceMatch(content);
        
        // 5. Feedback-based Analysis
        Map<String, Object> feedbackPatterns = feedbackAnalysisService.analyzeFeedbackPatterns(content);
        prediction.put("feedbackAnalysis", feedbackPatterns);
        
        // Adjust scores based on feedback patterns
        if (!feedbackPatterns.isEmpty()) {
            Map<String, Double> successFactors = (Map<String, Double>) feedbackPatterns.get("successFactors");
            if (successFactors != null && !successFactors.isEmpty()) {
                qualityScore = adjustScoreBasedOnFeedback(qualityScore, successFactors);
                audienceScore = adjustAudienceScoreBasedOnFeedback(audienceScore, successFactors);
            }
        }
        
        // Calculate overall prediction
        double overallScore = calculateOverallScore(
            historicalScore, qualityScore, timingScore, audienceScore
        );
        
        // Prepare detailed prediction
        prediction.put("overallScore", overallScore);
        prediction.put("estimatedEngagement", estimateEngagement(overallScore));
        prediction.put("predictedMetrics", predictMetrics(overallScore));
        prediction.put("recommendations", generateRecommendations(content));
        
        // Collect user feedback
        List<String> feedback = collectUserFeedback(content);
        prediction.put("userFeedback", feedback);

        return prediction;
    }

    private double analyzeHistoricalPerformance(Content content) {
        // Analyze similar content performance
        List<Content> similarContent = contentRepository.findByCategoryOrderByMetricsDesc(content.getCategory());
        return similarContent.stream()
            .mapToDouble(this::calculateEngagementScore)
            .average()
            .orElse(0.0);
    }

    private double analyzeContentQuality(Content content) {
        try {
            String prompt = String.format(
                "You are a JSON response generator for content quality analysis. Analyze the content and return ONLY a JSON object.\n\n" +
                "CRITICAL INSTRUCTIONS:\n" +
                "1. ONLY output a valid JSON object\n" +
                "2. DO NOT include any explanations\n" +
                "3. DO NOT use markdown formatting\n" +
                "4. DO NOT include any text before or after the JSON\n\n" +
                "Required JSON structure:\n" +
                "{\n" +
                "  \"qualityScore\": (number 0-1, overall content quality),\n" +
                "  \"clarity\": (number 0-1, content clarity),\n" +
                "  \"engagement\": (number 0-1, engagement potential),\n" +
                "  \"value\": (number 0-1, content value),\n" +
                "  \"recommendations\": [array of improvement suggestions]\n" +
                "}\n\n" +
                "Content to analyze:\n%s",
                content.getContentBody()
            );

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", "You are a JSON-only response system. Never include explanations or natural language. Only output valid JSON objects."
            ));
            messages.add(Map.of(
                "role", "user",
                "content", prompt
            ));

            Map<String, Object> response = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.2,  // Lower temperature for more consistent output
                    "max_tokens", 500   // Allow more tokens for recommendations
                )
            );

            String analysis = openRouterService.extractContentFromResponse(response);
            return jsonResponseHandler.extractScore(analysis, "qualityScore", 0.5);

        } catch (Exception e) {
            log.error("Error in content quality analysis", e);
            return 0.5; // Default score
        }
    }

    private double analyzePublishingTiming(Content content) {
        LocalDateTime publishTime = content.getScheduledPublishTime();
        if (publishTime == null) {
            return 0.5; // Default score for unscheduled content
        }
        
        // Analyze optimal timing based on historical data
        int hour = publishTime.getHour();
        int dayOfWeek = publishTime.getDayOfWeek().getValue();
        
        List<Content> historicalContent = contentRepository.findByPublishHourAndDayOfWeek(hour, dayOfWeek);
        return historicalContent.stream()
            .mapToDouble(this::calculateEngagementScore)
            .average()
            .orElse(0.5);
    }

    private double analyzeAudienceMatch(Content content) {
        // Analyze audience engagement patterns
        return 0.7; // Placeholder for audience match analysis
    }

    private double calculateOverallScore(double... scores) {
        return Arrays.stream(scores).average().orElse(0.0);
    }

    private Map<String, Double> estimateEngagement(double overallScore) {
        Map<String, Double> engagement = new HashMap<>();
        engagement.put("likes", overallScore * 100);      // Estimated likes
        engagement.put("shares", overallScore * 20);      // Estimated shares
        engagement.put("comments", overallScore * 10);    // Estimated comments
        engagement.put("clicks", overallScore * 50);      // Estimated clicks
        return engagement;
    }

    private Map<String, Object> predictMetrics(double overallScore) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("reach", overallScore * 1000);        // Estimated reach
        metrics.put("impressions", overallScore * 2000);  // Estimated impressions
        metrics.put("conversion", overallScore * 0.02);   // Estimated conversion rate
        metrics.put("roi", calculatePredictedROI(overallScore));
        return metrics;
    }

    private double calculatePredictedROI(double overallScore) {
        // Simple ROI calculation based on predicted performance
        double estimatedReach = overallScore * 1000;
        double conversionRate = overallScore * 0.02;
        double averageOrderValue = 50.0; // Example value
        
        double predictedRevenue = estimatedReach * conversionRate * averageOrderValue;
        double marketingCost = 100.0; // Example cost
        
        return (predictedRevenue - marketingCost) / marketingCost;
    }

    private List<String> generateRecommendations(Content content) {
        List<String> recommendations = new ArrayList<>();
        
        // Add timing recommendations
        if (content.getScheduledPublishTime() != null) {
            recommendations.add(String.format(
                "Optimal publishing time: %s",
                getOptimalPublishingTime(content)
            ));
        }
        
        // Add content improvement recommendations
        recommendations.addAll(getContentImprovementSuggestions(content));
        
        return recommendations;
    }

    private String getOptimalPublishingTime(Content content) {
        // Analyze historical performance by time
        // Return the best performing time slot
        return "9:00 AM EST"; // Placeholder
    }

    private List<String> getContentImprovementSuggestions(Content content) {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("Add more visual content for better engagement");
        suggestions.add("Include a clear call-to-action");
        suggestions.add("Optimize headline for better click-through rate");
        return suggestions;
    }

    private double calculateEngagementScore(Content content) {
        try {
            if (content.getMetrics() == null) {
                log.warn("No metrics available for content ID: {}", content.getId());
                return getDefaultEngagementScore();
            }

            Map<String, Object> metrics = objectMapper.readValue(
                content.getMetrics(), 
                new TypeReference<Map<String,Object>>() {}
            );
            
            // Extract metrics with type safety
            int likes = getMetricValue(metrics, "likes", 0);
            int shares = getMetricValue(metrics, "shares", 0);
            int comments = getMetricValue(metrics, "comments", 0);
            int views = getMetricValue(metrics, "views", 1); // Default to 1 to avoid division by zero
            
            // Calculate weighted engagement metrics
            double likeWeight = 1.0;
            double shareWeight = 2.0;
            double commentWeight = 3.0;
            
            // Calculate engagement rate (weighted sum / views)
            double engagementRate = ((likes * likeWeight) + 
                                   (shares * shareWeight) + 
                                   (comments * commentWeight)) / (double) views;
            
            // Normalize engagement rate to 0-1 scale
            double normalizedScore = Math.min(engagementRate, 1.0);
            
            // Apply time decay factor
            double timeDecayFactor = calculateTimeDecayFactor(content.getCreatedAt());
            
            return normalizedScore * timeDecayFactor;
            
        } catch (Exception e) {
            log.error("Error calculating engagement score for content ID: {}", content.getId(), e);
            return getDefaultEngagementScore();
        }
    }
    
    private int getMetricValue(Map<String, Object> metrics, String key, int defaultValue) {
        Object value = metrics.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Long) {
            return ((Long) value).intValue();
        } else if (value instanceof Double) {
            return ((Double) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                log.warn("Could not parse metric value for key: {}", key);
            }
        }
        return defaultValue;
    }
    
    private double calculateTimeDecayFactor(LocalDateTime createdAt) {
        if (createdAt == null) {
            return 1.0;
        }
        
        long daysOld = ChronoUnit.DAYS.between(createdAt, LocalDateTime.now());
        // Exponential decay with half-life of 7 days
        return Math.exp(-0.099 * daysOld); // ln(2)/7 ≈ 0.099
    }
    
    private double getDefaultEngagementScore() {
        return 0.5; // Return a moderate default score
    }

    private double extractQualityScore(String analysis) {
        // Parse OpenAI analysis to extract numeric score
        // This is a simplified version
        return 0.8; // Placeholder
    }

    private List<String> collectUserFeedback(Content content) {
        // Get feedback from similar content
        List<Content> similarContent = contentRepository.findByCategoryAndContentType(
            content.getCategory(),
            content.getContentType()
        );
        
        return similarContent.stream()
            .filter(c -> c.getComments() != null && !c.getComments().isEmpty())
            .map(Content::getComments)
            .collect(Collectors.toList());
    }

    /**
     * Adjusts the quality score based on feedback patterns
     * @param originalScore The original quality score
     * @param successFactors Map of success factors from feedback analysis
     * @return Adjusted quality score
     */
    private double adjustScoreBasedOnFeedback(double originalScore, Map<String, Double> successFactors) {
        double adjustedScore = originalScore;
        
        // Consider emotional tone correlation
        if (successFactors.containsKey("emotionalToneScore")) {
            double emotionalToneImpact = successFactors.get("emotionalToneScore") * 0.2; // 20% weight
            adjustedScore = adjustedScore * (1 + emotionalToneImpact);
        }
        
        // Consider readability impact
        if (successFactors.containsKey("readabilityScore")) {
            double readabilityImpact = successFactors.get("readabilityScore") * 0.15; // 15% weight
            adjustedScore = adjustedScore * (1 + readabilityImpact);
        }
        
        // Consider engagement correlation
        if (successFactors.containsKey("engagementScore")) {
            double engagementImpact = successFactors.get("engagementScore") * 0.25; // 25% weight
            adjustedScore = adjustedScore * (1 + engagementImpact);
        }
        
        // Ensure score stays within bounds
        return Math.min(1.0, Math.max(0.0, adjustedScore));
    }

    /**
     * Adjusts the audience score based on feedback patterns
     * @param originalScore The original audience score
     * @param successFactors Map of success factors from feedback analysis
     * @return Adjusted audience score
     */
    private double adjustAudienceScoreBasedOnFeedback(double originalScore, Map<String, Double> successFactors) {
        double adjustedScore = originalScore;
        
        // Consider engagement correlation with audience match
        if (successFactors.containsKey("engagementScore")) {
            double engagementImpact = successFactors.get("engagementScore") * 0.3; // 30% weight
            adjustedScore = adjustedScore * (1 + engagementImpact);
        }
        
        // Consider sentiment impact on audience reception
        if (successFactors.containsKey("sentimentScore")) {
            double sentimentImpact = successFactors.get("sentimentScore") * 0.2; // 20% weight
            adjustedScore = adjustedScore * (1 + sentimentImpact);
        }
        
        // Consider topic relevance to audience
        if (successFactors.containsKey("topicRelevanceScore")) {
            double relevanceImpact = successFactors.get("topicRelevanceScore") * 0.25; // 25% weight
            adjustedScore = adjustedScore * (1 + relevanceImpact);
        }
        
        // Ensure score stays within bounds
        return Math.min(1.0, Math.max(0.0, adjustedScore));
    }
}
