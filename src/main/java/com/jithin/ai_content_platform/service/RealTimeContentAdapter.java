package com.jithin.ai_content_platform.service;

import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.model.TrendData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Queue;
import java.util.List;
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
    
    private final Queue<TrendData> recentTrends = new ConcurrentLinkedQueue<>();
    private final Map<String, Double> trendWeights = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    public void init() {
        // Schedule periodic trend weight updates
        scheduler.scheduleAtFixedRate(this::updateTrendWeights, 0, 15, TimeUnit.MINUTES);
    }
    
    public Content adaptContent(Content content, List<TrendData> newTrends) {
        try {
            // Update recent trends
            updateRecentTrends(newTrends);
            
            // Get significant trends
            List<TrendData> significantTrends = getSignificantTrends();
            
            // Adapt content based on trends
            Content adaptedContent = adaptContentToTrends(content, significantTrends);
            
            // Validate with A/B testing
            Map<String, Object> testResults = abTestingService.createABTest(
                content, 
                List.of(adaptedContent.getContent())
            );
            
            // Only use adapted content if it's predicted to perform better
            if (isPredictedBetter(testResults)) {
                return adaptedContent;
            }
            
            return content;
        } catch (Exception e) {
            log.error("Error adapting content in real-time", e);
            return content;
        }
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
        // Use competitor analysis to determine relevance
        try {
            Map<String, Object> competitorInsights = competitorAnalysisService
                .analyzeCompetitorContent(trend.getIndustry(), List.of(trend.getTopic()));
            return (double) competitorInsights.getOrDefault("relevanceScore", 0.5);
        } catch (Exception e) {
            log.error("Error calculating relevance", e);
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
