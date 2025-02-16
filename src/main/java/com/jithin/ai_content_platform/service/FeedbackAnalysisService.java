package com.jithin.ai_content_platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.repository.ContentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FeedbackAnalysisService {
    @Autowired
    private ContentRepository contentRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private ContextAwareSentimentService sentimentService;

    /**
     * Analyzes feedback patterns from historical content
     * @param content The content to analyze feedback for
     * @return Map of analysis results
     */
    public Map<String, Object> analyzeFeedbackPatterns(Content content) {
        Map<String, Object> patterns = new HashMap<>();
        try {
            // Get similar content by category and type
            List<Content> similarContent = contentRepository.findByCategoryAndContentType(
                content.getCategory(), 
                content.getContentType()
            );
            
            // Analyze ratings distribution
            Map<Integer, Long> ratingDistribution = similarContent.stream()
                .filter(c -> c.getRating() != null)
                .collect(Collectors.groupingBy(
                    Content::getRating,
                    Collectors.counting()
                ));
            patterns.put("ratingDistribution", ratingDistribution);
            
            // Analyze common feedback themes
            Map<String, Integer> feedbackThemes = extractFeedbackThemes(similarContent);
            patterns.put("feedbackThemes", feedbackThemes);
            
            // Calculate success factors
            Map<String, Double> successFactors = calculateSuccessFactors(similarContent);
            patterns.put("successFactors", successFactors);
            
            // Add sentiment analysis of feedback
            Map<String, Double> sentimentAnalysis = analyzeFeedbackSentiment(similarContent);
            patterns.put("sentimentAnalysis", sentimentAnalysis);
            
            log.info("Successfully analyzed feedback patterns for content ID: {}", content.getId());
        } catch (Exception e) {
            log.error("Error analyzing feedback patterns for content ID: {}", content.getId(), e);
        }
        return patterns;
    }
    
    /**
     * Extract common themes from feedback comments
     */
    private Map<String, Integer> extractFeedbackThemes(List<Content> content) {
        return content.stream()
            .filter(c -> c.getComments() != null && !c.getComments().isEmpty())
            .map(c -> sentimentService.analyzeContextAwareSentiment(c.getComments()))
            .filter(Objects::nonNull)
            .flatMap(analysis -> ((Map<String, Object>) analysis.get("topic_sentiments")).keySet().stream())
            .collect(Collectors.groupingBy(
                theme -> theme,
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
            ));
    }
    
    /**
     * Calculate success factors based on high-rated content
     */
    private Map<String, Double> calculateSuccessFactors(List<Content> content) {
        Map<String, Double> factors = new HashMap<>();
        List<Content> highRatedContent = content.stream()
            .filter(c -> c.getRating() != null && c.getRating() >= 4)
            .collect(Collectors.toList());
            
        if (!highRatedContent.isEmpty()) {
            // Analyze emotional tone distribution
            factors.put("emotionalToneScore", calculateEmotionalToneScore(highRatedContent));
            // Analyze readability scores
            factors.put("readabilityScore", calculateReadabilityScore(highRatedContent));
            // Analyze engagement correlation
            factors.put("engagementScore", calculateEngagementCorrelation(highRatedContent));
        }
        
        return factors;
    }

    /**
     * Calculate emotional tone score from high-rated content
     */
    private double calculateEmotionalToneScore(List<Content> content) {
        return content.stream()
            .filter(c -> c.getEmotionalTone() != null)
            .mapToDouble(c -> {
                Map<String, Object> sentimentAnalysis = sentimentService.analyzeContextAwareSentiment(c.getEmotionalTone());
                return (double) sentimentAnalysis.getOrDefault("overall_sentiment", 0.0);
            })
            .average()
            .orElse(0.0);
    }

    /**
     * Calculate readability score from high-rated content
     */
    private double calculateReadabilityScore(List<Content> content) {
        return content.stream()
            .filter(c -> c.getReadabilityScore() != null)
            .mapToDouble(c -> Double.parseDouble(c.getReadabilityScore()))
            .average()
            .orElse(0.0);
    }

    /**
     * Calculate engagement correlation with ratings
     */
    private double calculateEngagementCorrelation(List<Content> content) {
        return content.stream()
            .filter(c -> c.getEngagement() != null)
            .mapToDouble(Content::getEngagement)
            .average()
            .orElse(0.0);
    }

    /**
     * Analyze sentiment of feedback comments
     */
    private Map<String, Double> analyzeFeedbackSentiment(List<Content> content) {
        Map<String, Double> sentimentAnalysis = new HashMap<>();
        
        List<String> comments = content.stream()
            .filter(c -> c.getComments() != null && !c.getComments().isEmpty())
            .map(Content::getComments)
            .collect(Collectors.toList());
            
        if (!comments.isEmpty()) {
            double averageSentiment = comments.stream()
                .mapToDouble(comment -> {
                    Map<String, Object> analysis = sentimentService.analyzeContextAwareSentiment(comment);
                    return (double) analysis.getOrDefault("overall_sentiment", 0.0);
                })
                .average()
                .orElse(0.0);
                
            sentimentAnalysis.put("averageSentiment", averageSentiment);
        }
        
        return sentimentAnalysis;
    }

    /**
     * Get feedback-based recommendations for content improvement
     */
    public List<String> getFeedbackBasedRecommendations(Content content) {
        List<String> recommendations = new ArrayList<>();
        Map<String, Object> patterns = analyzeFeedbackPatterns(content);
        
        try {
            Map<String, Double> successFactors = (Map<String, Double>) patterns.get("successFactors");
            if (successFactors != null) {
                // Emotional tone recommendations
                double emotionalToneScore = successFactors.getOrDefault("emotionalToneScore", 0.0);
                if (emotionalToneScore > 0.7) {
                    recommendations.add("Consider maintaining a positive emotional tone as it correlates with higher engagement");
                }
                
                // Readability recommendations
                double readabilityScore = successFactors.getOrDefault("readabilityScore", 0.0);
                if (readabilityScore > 0.8) {
                    recommendations.add("Maintain clear and readable content structure for better user engagement");
                }
            }
            
            // Add theme-based recommendations
            Map<String, Integer> themes = (Map<String, Integer>) patterns.get("feedbackThemes");
            if (themes != null && !themes.isEmpty()) {
                String topTheme = themes.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("");
                    
                if (!topTheme.isEmpty()) {
                    recommendations.add("Consider incorporating the theme '" + topTheme + "' as it resonates well with your audience");
                }
            }
        } catch (Exception e) {
            log.error("Error generating feedback-based recommendations", e);
        }
        
        return recommendations;
    }
}
