package com.jithin.ai_content_platform.service;

import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.model.TrendData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class RealTimeContentAdapter {
    
    @Autowired
    private TrendAnalysisService trendAnalysisService;
    
    @Autowired
    private ABTestingService abTestingService;
    
    @Autowired
    private CompetitorAnalysisService competitorAnalysisService;
    
    @Autowired
    private MLPredictionService mlPredictionService;
    
    private final Queue<TrendData> recentTrends = new ConcurrentLinkedQueue<>();
    private final Map<String, Double> trendWeights = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> abTestCache = new ConcurrentHashMap<>();
    private static final long AB_TEST_CACHE_DURATION = TimeUnit.MINUTES.toMillis(30); // Cache for 30 minutes
    
    private Map<String, Double> relevanceScores = new ConcurrentHashMap<>();
    private static final long RELEVANCE_CACHE_DURATION = TimeUnit.MINUTES.toMillis(30);
    private Map<String, Long> relevanceTimestamps = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // No need for continuous updates
    }
    
    public Content adaptContent(Content content, List<TrendData> newTrends) {
        try {
            // Update trends only if new trends are provided
            if (newTrends != null && !newTrends.isEmpty()) {
                updateRecentTrends(newTrends);
                updateTrendWeights();
            }
            
            // Check cache for A/B test results
            String cacheKey = content.getId() != null ? content.getId().toString() : "";
            Map<String, Object> testResults = abTestCache.get(cacheKey);
            if (testResults == null) {
                // Create A/B test variations only if not in cache
                testResults = abTestingService.createABTest(
                    content,
                    List.of(content.getContentBody()) // Use original content as variation
                );
                // Cache the results
                abTestCache.put(cacheKey, testResults);
                
                // Clean up old cache entries
                cleanupCache();
            }
            
            // Get ML predictions only if not already in metrics
            Map<String, Object> metrics = content.getMetricsMap();
            if (metrics == null) {
                metrics = new HashMap<>();
            }
            
            if (!metrics.containsKey("predictions")) {
                Map<String, Object> predictions = mlPredictionService.predictContentPerformance(content);
                metrics.put("predictions", predictions);
                
                // Update specific fields
                if (predictions.containsKey("engagement")) {
                    content.setEngagement((Double) predictions.get("engagement"));
                }
                if (predictions.containsKey("virality")) {
                    metrics.put("virality_score", predictions.get("virality"));
                }
            }
            
            // Add test results to metrics
            metrics.put("ab_test_results", testResults);
            content.setMetricsMap(metrics);
            
            log.info("Content adapted with predictions for ID: {}", content.getId());
            return content;
            
        } catch (Exception e) {
            log.error("Error adapting content in real-time for ID: {}", content.getId(), e);
            return content; // Return original content if adaptation fails
        }
    }
    
    private void cleanupCache() {
        long now = System.currentTimeMillis();
        abTestCache.entrySet().removeIf(entry -> 
            now - entry.getValue().getOrDefault("timestamp", now).hashCode() > AB_TEST_CACHE_DURATION);
    }
    
    private void updateRecentTrends(List<TrendData> newTrends) {
        newTrends.forEach(trend -> {
            recentTrends.offer(trend);
            // Keep only last 100 trends
            while (recentTrends.size() > 100) {
                recentTrends.poll();
            }
        });
    }
    
    private void updateTrendWeights() {
        recentTrends.forEach(trend -> {
            double weight = calculateTrendWeight(trend);
            trendWeights.merge(trend.getTopic(), weight,
                (old, new_) -> old * 0.7 + new_ * 0.3); // Exponential moving average
        });
    }
    
    private double calculateTrendWeight(TrendData trend) {
        return new TrendWeightBuilder()
            .withViralityScore(calculateVirality(trend))
            .withRelevanceScore(calculateRelevance(trend))
            .withEngagementScore(calculateEngagement(trend))
            .withTimeDecayFactor(calculateTimeDecay(trend))
            .build();
    }
    
    private double calculateVirality(TrendData trend) {
        return trend.getScore() * (trend.getRegions().size() / 10.0);
    }
    
    private double calculateRelevance(TrendData trend) {
        String cacheKey = trend.getIndustry() + ":" + trend.getTopic();
        long currentTime = System.currentTimeMillis();
        
        // Check cache
        Double cachedScore = relevanceScores.get(cacheKey);
        Long timestamp = relevanceTimestamps.get(cacheKey);
        
        if (cachedScore != null && timestamp != null && 
            (currentTime - timestamp) < RELEVANCE_CACHE_DURATION) {
            return cachedScore;
        }
        
        try {
            // Batch process trends for the same industry
            List<TrendData> trendsToProcess = recentTrends.stream()
                .filter(t -> t.getIndustry().equals(trend.getIndustry()))
                .filter(t -> !relevanceScores.containsKey(t.getIndustry() + ":" + t.getTopic()) ||
                           currentTime - relevanceTimestamps.getOrDefault(
                               t.getIndustry() + ":" + t.getTopic(), 0L) > RELEVANCE_CACHE_DURATION)
                .toList();
            
            if (!trendsToProcess.isEmpty()) {
                Map<String, Object> batchInsights = competitorAnalysisService.analyzeCompetitorContent(
                    trend.getIndustry(),
                    trendsToProcess.stream().map(TrendData::getTopic).toList()
                );
                
                // Cache all results
                if (batchInsights.containsKey("relevanceScores")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Double> scores = (Map<String, Double>) batchInsights.get("relevanceScores");
                    scores.forEach((topic, score) -> {
                        String key = trend.getIndustry() + ":" + topic;
                        relevanceScores.put(key, score);
                        relevanceTimestamps.put(key, currentTime);
                    });
                }
            }
            
            return relevanceScores.getOrDefault(cacheKey, 0.5);
        } catch (Exception e) {
            log.error("Error calculating relevance for trend: {}", trend.getTopic(), e);
            return 0.5;
        }
    }
    
    private double calculateEngagement(TrendData trend) {
        return trend.getEngagementMetrics().values().stream()
            .mapToDouble(Double::valueOf)
            .average()
            .orElse(0.5);
    }
    
    private double calculateTimeDecay(TrendData trend) {
        long hoursSinceStart = trend.getTimeSinceStart().toHours();
        return Math.exp(-0.1 * hoursSinceStart); // Exponential decay
    }
    
    private List<TrendData> getSignificantTrends() {
        return recentTrends.stream()
            .filter(trend -> trendWeights.getOrDefault(trend.getTopic(), 0.0) > 0.7)
            .toList();
    }
    
    private Content adaptContentToTrends(Content content, List<TrendData> trends) {
        Content adaptedContent = content.toBuilder().build();
        
        // Adapt content based on trends
        StringBuilder adaptedText = new StringBuilder(content.getContent());
        
        trends.forEach(trend -> {
            // Add trend-related content if relevant
            if (isRelevantToContent(trend, content)) {
                String trendContent = generateTrendContent(trend);
                adaptedText.append("\n\n").append(trendContent);
            }
        });
        
        adaptedContent.setContentBody(adaptedText.toString());
        return adaptedContent;
    }
    
    private boolean isRelevantToContent(TrendData trend, Content content) {
        return trendAnalysisService.isRelevantToKeyword(trend, content.getKeywords());
    }
    
    private String generateTrendContent(TrendData trend) {
        // Generate content that incorporates the trend
        return String.format(
            "Recent trends show increasing interest in %s, with engagement rates of %.2f",
            trend.getTopic(),
            calculateEngagement(trend)
        );
    }
    
    private boolean isPredictedBetter(Map<String, Object> testResults) {
        @SuppressWarnings("unchecked")
        Map<String, Double> predictions = (Map<String, Double>) testResults.get("predictions");
        return predictions.getOrDefault("variant", 0.0) > predictions.getOrDefault("original", 0.0);
    }
    
    private static class TrendWeightBuilder {
        private double viralityScore;
        private double relevanceScore;
        private double engagementScore;
        private double timeDecayFactor;
        
        public TrendWeightBuilder withViralityScore(double score) {
            this.viralityScore = score;
            return this;
        }
        
        public TrendWeightBuilder withRelevanceScore(double score) {
            this.relevanceScore = score;
            return this;
        }
        
        public TrendWeightBuilder withEngagementScore(double score) {
            this.engagementScore = score;
            return this;
        }
        
        public TrendWeightBuilder withTimeDecayFactor(double factor) {
            this.timeDecayFactor = factor;
            return this;
        }
        
        public double build() {
            return (0.3 * viralityScore + 
                    0.3 * relevanceScore + 
                    0.2 * engagementScore + 
                    0.2 * timeDecayFactor);
        }
    }
}
