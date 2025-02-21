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
            
            Map<String, Number> metrics = new HashMap<>();
            for (Map.Entry<String, Object> entry : originalMetrics.entrySet()) {
                if (entry.getValue() instanceof Number) {
                    metrics.put(entry.getKey(), (Number) entry.getValue());
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
    
    private double calculateViralityFromMetrics(Map<String, Number> metrics, double engagement) {
        double shares = metrics.get("shares").doubleValue();
        double views = metrics.get("views").doubleValue();
        
        // Calculate virality score based on share rate and engagement
        double shareRate = views > 0 ? shares / views : 0;
        return normalize((shareRate * 0.7 + engagement * 0.3), 0, 1);
    }
    
    private double calculateRelevanceFromMetrics(Map<String, Number> metrics) {
        double comments = metrics.get("comments").doubleValue();
        double likes = metrics.get("likes").doubleValue();
        double views = metrics.get("views").doubleValue();
        
        // Calculate relevance based on engagement metrics
        double commentRate = views > 0 ? comments / views : 0;
        double likeRate = views > 0 ? likes / views : 0;
        
        return normalize((commentRate * 0.4 + likeRate * 0.6), 0, 1);
    }
    
    private double calculateCompetitorScoreFromMetrics(Map<String, Number> metrics) {
        try {
            // Get the raw metrics we already have
            double engagement = metrics.get("engagement").doubleValue();
            
            // Calculate a composite score based on the metrics we have
            double views = metrics.get("views").doubleValue();
            double totalInteractions = metrics.get("likes").doubleValue() + 
                                      metrics.get("shares").doubleValue() * 2 + // Shares count double
                                      metrics.get("comments").doubleValue() * 3; // Comments count triple
            
            // Calculate interaction rate (weighted interactions per view)
            double interactionRate = views > 0 ? totalInteractions / views : 0;
            
            // Combine engagement score with interaction rate
            double score = (engagement * 0.6) + (interactionRate * 0.4);
            
            // Normalize to 0-1 range
            return normalize(score, 0, 1);
        } catch (Exception e) {
            log.warn("Error calculating competitor score: {}", e.getMessage());
            return 0.5; // Default competitor score
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
    
    private double getMetricValue(Map<String, Number> metrics, String key, double defaultValue) {
        if (metrics == null || !metrics.containsKey(key)) {
            return defaultValue;
        }
        
        return metrics.get(key).doubleValue();
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
            Map<String, Number> metrics = new HashMap<>();
            for (Map.Entry<String, Object> entry : originalMetrics.entrySet()) {
                if (entry.getValue() instanceof Number) {
                    metrics.put(entry.getKey(), (Number) entry.getValue());
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
    private boolean hasRequiredMetrics(Map<String, Number> metrics) {
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
