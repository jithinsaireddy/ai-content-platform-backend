package com.jithin.ai_content_platform.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class TrendInsight {
    private String topic;
    private String insight;
    private double score;
    private List<TrendData> historicalData;
    private double averageSentiment;
    private double engagementScore;
    private Map<String, Double> topicRelations;
    private List<String> relatedKeywords;
    private TrendDirection trendDirection;
    private LocalDateTime lastUpdated;
    private Map<String, Object> metrics;

    public TrendInsight(String topic, String insight, double score) {
        this.topic = topic;
        this.insight = insight;
        this.score = score;
        this.lastUpdated = LocalDateTime.now();
        this.metrics = new HashMap<>();
    }

    // Additional constructor for detailed insights
    public TrendInsight(String topic, String insight, double score, 
                       List<TrendData> historicalData, double averageSentiment,
                       double engagementScore, Map<String, Double> topicRelations) {
        this(topic, insight, score);
        this.historicalData = historicalData;
        this.averageSentiment = averageSentiment;
        this.engagementScore = engagementScore;
        this.topicRelations = topicRelations;
    }

    // Getters and Setters
    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getInsight() {
        return insight;
    }

    public void setInsight(String insight) {
        this.insight = insight;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public List<TrendData> getHistoricalData() {
        return historicalData;
    }

    public void setHistoricalData(List<TrendData> historicalData) {
        this.historicalData = historicalData;
    }

    public double getAverageSentiment() {
        return averageSentiment;
    }

    public void setAverageSentiment(double averageSentiment) {
        this.averageSentiment = averageSentiment;
    }

    public double getEngagementScore() {
        return engagementScore;
    }

    public void setEngagementScore(double engagementScore) {
        this.engagementScore = engagementScore;
    }

    public Map<String, Double> getTopicRelations() {
        return topicRelations;
    }

    public void setTopicRelations(Map<String, Double> topicRelations) {
        this.topicRelations = topicRelations;
    }

    public List<String> getRelatedKeywords() {
        return relatedKeywords;
    }

    public void setRelatedKeywords(List<String> relatedKeywords) {
        this.relatedKeywords = relatedKeywords;
    }

    public TrendDirection getTrendDirection() {
        return trendDirection;
    }

    public void setTrendDirection(TrendDirection trendDirection) {
        this.trendDirection = trendDirection;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public Map<String, Object> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Object> metrics) {
        this.metrics = metrics;
    }
}
