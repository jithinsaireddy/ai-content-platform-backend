package com.jithin.ai_content_platform.service;

import com.jithin.ai_content_platform.model.TrendData;
import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.model.TrendPattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DynamicTrendWeightService {
    
    @Autowired
    private CompetitorAnalysisService competitorAnalysisService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private final Map<String, Double> trendWeights = new HashMap<>();
    
    public Map<String, Double> calculateContentWeights(Content content) {
        Map<String, Double> weights = new HashMap<>();
        try {
            // Extract and validate basic metrics
            double engagement = content.getEngagement() != null ? content.getEngagement() : 0.0;
            Map<String, Object> originalMetrics = content.getMetricsMap();
            if (originalMetrics == null) {
                log.warn("No metrics available for content ID: {}", content.getId());
                return getFallbackWeights();
            }
            
            Map<String, Integer> metrics = new HashMap<>();
            for (Map.Entry<String, Object> entry : originalMetrics.entrySet()) {
                if (entry.getValue() instanceof Integer) {
                    metrics.put(entry.getKey(), (Integer) entry.getValue());
                } else if (entry.getValue() instanceof Double) {
                    metrics.put(entry.getKey(), ((Double) entry.getValue()).intValue());
                }
            }
            
            // Validate minimum required metrics
            if (!hasRequiredMetrics(metrics)) {
                log.warn("Missing required metrics for content ID: {}", content.getId());
                return getFallbackWeights();
            }
            
            // Calculate weights based on content metrics
            double viralityScore = calculateViralityFromMetrics(metrics, engagement);
            double relevanceScore = calculateRelevanceFromMetrics(metrics);
            double engagementScore = engagement;
            double timeDecayFactor = calculateTimeDecayFromDate(content.getCreatedAt());
            double competitorScore = calculateCompetitorScoreFromMetrics(metrics);
            double seasonalityScore = 0.7; // Default seasonality
            double marketPotentialScore = 0.75; // Default market potential
            double momentumScore = calculateMomentumScore(content);
            
            // Store individual weights
            weights.put("virality", viralityScore);
            weights.put("relevance", relevanceScore);
            weights.put("engagement", engagementScore);
            weights.put("timeDecay", timeDecayFactor);
            weights.put("competitor", competitorScore);
            weights.put("seasonality", seasonalityScore);
            weights.put("marketPotential", marketPotentialScore);
            weights.put("momentum", momentumScore);
            
            // Calculate combined weight
            double dynamicWeight = calculateCombinedWeight(
                viralityScore,
                relevanceScore,
                engagementScore,
                timeDecayFactor,
                competitorScore,
                seasonalityScore,
                marketPotentialScore,
                momentumScore
            );
            weights.put("dynamicWeight", dynamicWeight);
            
            // Store trend weight for future reference
            trendWeights.put(content.getTitle(), dynamicWeight);
            
            return weights;
        } catch (Exception e) {
            log.error("Error calculating content weights for content ID: {}", content.getId(), e);
            return createDefaultWeights();
        }
    }
    
    private double calculateViralityFromMetrics(Map<String, Integer> metrics, double engagement) {
        try {
            double shares = getMetricValue(metrics, "shares", 0.0);
            double likes = getMetricValue(metrics, "likes", 0.0);
            double comments = getMetricValue(metrics, "comments", 0.0);
            double retweets = getMetricValue(metrics, "retweets", 0.0);
            double saves = getMetricValue(metrics, "saves", 0.0);
            
            // Normalize metrics with adjusted thresholds for better social performance
            double normalizedShares = normalize(shares, 0, 2000);  // Increased threshold
            double normalizedLikes = normalize(likes, 0, 10000);   // Increased threshold
            double normalizedComments = normalize(comments, 0, 1000); // Increased threshold
            double normalizedRetweets = normalize(retweets, 0, 1500);
            double normalizedSaves = normalize(saves, 0, 500);
            
            // Enhanced weighted combination with new metrics
            double viralityScore = (
                normalizedShares * 0.25 +     // Reduced weight
                normalizedLikes * 0.2 +       // Reduced weight
                normalizedComments * 0.25 +   // Increased weight for engagement
                normalizedRetweets * 0.2 +    // New metric
                normalizedSaves * 0.1         // New metric
            ) * (engagement > 0 ? engagement * 1.2 : 1.0); // Increased engagement multiplier
            
            return Math.min(1.0, viralityScore); // Ensure we don't exceed 1.0
        } catch (Exception e) {
            log.warn("Error calculating virality score from metrics", e);
            return 0.5;
        }
    }
    
    private double calculateRelevanceFromMetrics(Map<String, Integer> metrics) {
        try {
            double relevanceScore = getMetricValue(metrics, "relevanceScore", 0.5);
            double topicMatch = getMetricValue(metrics, "topicMatch", 0.5);
            
            return (relevanceScore * 0.6 + topicMatch * 0.4);
        } catch (Exception e) {
            log.warn("Error calculating relevance score from metrics", e);
            return 0.5;
        }
    }
    
    private double calculateCompetitorScoreFromMetrics(Map<String, Integer> metrics) {
        try {
            double competitorScore = getMetricValue(metrics, "competitorScore", 0.5);
            double marketShare = getMetricValue(metrics, "marketShare", 0.5);
            
            return (competitorScore * 0.7 + marketShare * 0.3);
        } catch (Exception e) {
            log.warn("Error calculating competitor score from metrics", e);
            return 0.5;
        }
    }
    
    private Map<String, Double> createDefaultWeights() {
        Map<String, Double> weights = new HashMap<>();
        weights.put("virality", 0.0);
        weights.put("relevance", 0.7);  // Higher default relevance for new content
        weights.put("engagement", 0.0);
        weights.put("timeDecay", 1.0);
        weights.put("competitor", 0.5);
        weights.put("seasonality", 0.7);  // Higher default seasonality for new content
        weights.put("marketPotential", 0.75);  // Higher market potential for new content
        weights.put("momentum", 0.0);
        weights.put("dynamicWeight", 0.7);  // Higher initial dynamic weight
        return weights;
    }
    
    private double getMetricValue(Map<String, Integer> metrics, String key, double defaultValue) {
        if (metrics == null || !metrics.containsKey(key)) {
            return defaultValue;
        }
        
        Object value = metrics.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }
    
    private double normalize(double value, double min, double max) {
        if (max == min) return 0.5;
        return Math.min(1.0, Math.max(0.0, (value - min) / (max - min)));
    }
    
    private double calculateTimeDecayFromDate(LocalDateTime createdAt) {
        if (createdAt == null) {
            return 0.5;
        }
        
        long hoursDiff = ChronoUnit.HOURS.between(createdAt, LocalDateTime.now());
        return Math.exp(-0.01 * hoursDiff); // Exponential decay
    }
    
    private double calculateCombinedWeight(
            double viralityScore,
            double relevanceScore,
            double engagementScore,
            double timeDecayFactor,
            double competitorScore,
            double seasonalityScore,
            double marketPotentialScore,
            double momentumScore) {
        
        return (viralityScore * 0.20 +
                relevanceScore * 0.15 +
                engagementScore * 0.15 +
                competitorScore * 0.15 +
                seasonalityScore * 0.10 +
                marketPotentialScore * 0.15 +
                momentumScore * 0.10) * timeDecayFactor;
    }
    
    private double calculateMomentumScore(Content content) {
        try {
            Map<String, Object> originalMetrics = content.getMetricsMap();
            Map<String, Integer> metrics = new HashMap<>();
            for (Map.Entry<String, Object> entry : originalMetrics.entrySet()) {
                if (entry.getValue() instanceof Integer) {
                    metrics.put(entry.getKey(), (Integer) entry.getValue());
                }
            }
            double recentEngagement = content.getEngagement() != null ? content.getEngagement() : 0.0;
            
            // Get historical engagement data if available
            Double historicalEngagement = content.getEngagementScore();
            if (historicalEngagement == null) {
                return recentEngagement; // Return current engagement if no historical data
            }
            
            // Calculate momentum as rate of change in engagement
            double previousEngagement = historicalEngagement;
            double momentum = previousEngagement != 0 ? (recentEngagement - previousEngagement) / previousEngagement : 0.0;
            
            // Normalize momentum score between 0 and 1
            momentum = Math.max(0.0, Math.min(1.0, (momentum + 1.0) / 2.0));
            
            return momentum;
        } catch (Exception e) {
            log.error("Error calculating momentum score: {}", e.getMessage());
            return 0.5; // Return default score in case of error
        }
    }
    
    /**
     * Returns default fallback weights when metrics are not available
     * @return Map containing default weights for content evaluation
     */
    private Map<String, Double> getFallbackWeights() {
        Map<String, Double> fallbackWeights = new HashMap<>();
        fallbackWeights.put("virality", 0.0);
        fallbackWeights.put("relevance", 0.5);
        fallbackWeights.put("engagement", 0.0);
        fallbackWeights.put("timeDecay", 1.0);
        fallbackWeights.put("competitor", 0.5);
        fallbackWeights.put("seasonality", 0.5);
        fallbackWeights.put("marketPotential", 0.5);
        fallbackWeights.put("momentum", 0.0);
        return fallbackWeights;
    }
    
    /**
     * Validates if all required metrics are present in the metrics map
     * @param metrics Map of metric names to their values
     * @return true if all required metrics are present, false otherwise
     */
    private boolean hasRequiredMetrics(Map<String, Integer> metrics) {
        if (metrics == null) {
            return false;
        }
        // Check for essential metrics even if they're zero
        return metrics.containsKey("views") && 
               metrics.containsKey("likes") && 
               metrics.containsKey("shares") && 
               metrics.containsKey("comments") && 
               metrics.containsKey("engagement");
    }
    
    public double calculateDynamicWeight(TrendData trend) {
        try {
            if (trend == null) {
                return 0.5;
            }
            
            double viralityScore = trend.getTrendScore() != null ? trend.getTrendScore() : 0.5;
            double confidenceScore = trend.getConfidenceScore() != null ? trend.getConfidenceScore() : 0.5;
            double timeDecayFactor = calculateTimeDecayFromDate(trend.getAnalysisTimestamp());
            
            // Apply pattern-based adjustment
            double patternMultiplier = 1.0;
            if (trend.getTrendPattern() == TrendPattern.STEADY_RISE) {
                patternMultiplier = 1.2;
            } else if (trend.getTrendPattern() == TrendPattern.STEADY_DECLINE) {
                patternMultiplier = 0.8;
            }
            
            double weight = viralityScore * confidenceScore * patternMultiplier * timeDecayFactor;
            trendWeights.put(trend.getTopic(), weight);
            
            return weight;
        } catch (Exception e) {
            log.error("Error calculating dynamic weight for trend: {}", trend.getTopic(), e);
            return 0.5;
        }
    }

}
