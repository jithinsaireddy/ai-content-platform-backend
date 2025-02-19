package com.jithin.ai_content_platform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "content")
@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class Content {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String contentBody;

    @Column(columnDefinition = "TEXT")
    private String feedbackAnalysis;

    @Column(nullable = false)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metrics = "{}";

    @Column(name = "engagement")
    private Double engagement;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String trendData;

    @Column(name = "created_at")
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(columnDefinition = "TEXT")
    private String analyzedSentiment;

    @Column(columnDefinition = "TEXT")
    private String stanfordSentiment;

    @Column(columnDefinition = "TEXT")
    private String improvedContent;

    @Column(columnDefinition = "TEXT")
    private String improvementSuggestions;

    @Column(columnDefinition = "TEXT")
    private String seoMetadata;

    @Column(columnDefinition = "TEXT")
    private String readabilityScore;

    private String language;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String interestOverTime = "{}";

    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comments;

    private Integer likes;
    private Integer shares;

    @Column(columnDefinition = "TEXT")
    private String topic;

    @Column(columnDefinition = "TEXT")
    private String contentType;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    @Column(columnDefinition = "TEXT")
    private String emotionalTone;

    @Column(columnDefinition = "TEXT")
    private String keywords;

    @Column(columnDefinition = "TEXT")
    private String region;

    @Column(columnDefinition = "boolean default false")
    private boolean optimizeForSeo;

    @Column(name = "test_id")
    private String testId;

    @Column(name = "scheduled_publish_time")
    private LocalDateTime scheduledPublishTime;

    @Column(columnDefinition = "TEXT")
    private String writingStyle;

    @Column(columnDefinition = "TEXT")
    private String seoSuggestions; // Field to store SEO suggestions

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String abTestResults; // Field to store A/B test results

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String engagementPredictions;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String qualityScores;

    @Column(name = "virality_score")
    private Double viralityScore;

    @Column(name = "audience_reach")
    private Integer audienceReach;

    @Column(name = "content_quality_score")
    private Double contentQualityScore;

    private static final Logger logger = LoggerFactory.getLogger(Content.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public User getAuthor() {
        return user;
    }

    public void setAuthor(User user) {
        this.user = user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getContent() {
        return contentBody;
    }

    public String getContentBody() {
        return contentBody;
    }

    public void setContentBody(String contentBody) {
        this.contentBody = contentBody;
    }

    public String getAnalyzedSentiment() {
        return analyzedSentiment;
    }

    public void setAnalyzedSentiment(String analyzedSentiment) {
        this.analyzedSentiment = analyzedSentiment;
    }

    public String getStanfordSentiment() {
        return stanfordSentiment;
    }

    public void setStanfordSentiment(String stanfordSentiment) {
        this.stanfordSentiment = stanfordSentiment;
    }

    public double getEngagementScore() {
        double score = 0.0;
        score += this.likes != null ? this.likes : 0; // Each like adds 1 point
        score += this.shares != null ? this.shares * 2 : 0; // Each share adds 2 points
        score += this.comments != null ? this.comments.length() : 0; // Assuming each comment adds 1 point
        return score;
    }

    public void setEngagementScore(double engagementScore) {
        this.engagement = engagementScore;
    }

    /**
     * Calculates and returns the interest metrics over time for this content.
     * The interest is calculated based on:
     * 1. View trends
     * 2. Engagement metrics (likes, shares, comments)
     * 3. Time-based decay factor
     * 
     * @return Map containing timestamps and corresponding interest scores
     */
    public Map<String, Double> getInterestOverTime() {
        Map<String, Double> interestOverTime = new HashMap<>();
        
        try {
            // Get current metrics
            Map<String, Object> currentMetrics = getMetricsMap();
            
            // Calculate base interest score
            double baseScore = calculateBaseInterestScore(currentMetrics);
            
            // Calculate time decay factor (reduces interest as content ages)
            double timeDecayFactor = calculateTimeDecayFactor();
            
            // Calculate final interest score
            double interestScore = baseScore * timeDecayFactor;
            
            // Add the score with current timestamp
            interestOverTime.put(LocalDateTime.now().toString(), interestScore);
            
            return interestOverTime;
        } catch (Exception e) {
            // If metrics can't be parsed, return empty map
            return interestOverTime;
        }
    }
    
    /**
     * Calculates the base interest score from metrics
     */
    private double calculateBaseInterestScore(Map<String, Object> metrics) {
        double viewWeight = 1.0;
        double likeWeight = 2.0;
        double shareWeight = 3.0;
        double commentWeight = 2.5;
        
        return (Integer.parseInt(metrics.getOrDefault("views", 0).toString()) * viewWeight +
                Integer.parseInt(metrics.getOrDefault("likes", 0).toString()) * likeWeight +
                Integer.parseInt(metrics.getOrDefault("shares", 0).toString()) * shareWeight +
                Integer.parseInt(metrics.getOrDefault("comments", 0).toString()) * commentWeight);
    }
    
    /**
     * Calculates time decay factor based on content age
     * @return A score between 0 and 1, where 1 is completely fresh content
     */
    public double calculateTimeDecayFactor() {
        long contentAgeHours = ChronoUnit.HOURS.between(this.getCreatedAt(), LocalDateTime.now());
        double decayRate = 0.01; // 1% decay per hour
        return Math.exp(-decayRate * contentAgeHours);
    }

    @JsonIgnore
    public Map<String, Object> getTrendDataMap() {
        try {
            if (trendData == null || trendData.isEmpty()) {
                return new HashMap<>();
            }
            return objectMapper.readValue(trendData, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            logger.error("Error parsing trend data for content {}: {}", id, e.getMessage());
            return new HashMap<>();
        }
    }

    @JsonIgnore
    public Map<String, Object> getMetricsMap() {
        try {
            if (metrics == null || metrics.isEmpty()) {
                return new HashMap<>();
            }
            return objectMapper.readValue(metrics, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            logger.error("Error parsing metrics for content {}: {}", id, e.getMessage());
            return new HashMap<>();
        }
    }

    @JsonIgnore
    public Map<String, Object> getAnalyzedSentimentMap() {
        try {
            if (analyzedSentiment == null || analyzedSentiment.isEmpty()) {
                return new HashMap<>();
            }
            return objectMapper.readValue(analyzedSentiment, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            logger.error("Error parsing analyzed sentiment for content {}: {}", id, e.getMessage());
            return new HashMap<>();
        }
    }

    public void setTrendDataMap(Map<String, Object> trendDataMap) {
        try {
            this.trendData = objectMapper.writeValueAsString(trendDataMap);
        } catch (JsonProcessingException e) {
            logger.error("Error setting trend data map for content {}: {}", id, e.getMessage());
        }
    }

    public void setMetricsMap(Map<String, Object> metricsMap) {
        try {
            this.metrics = objectMapper.writeValueAsString(metricsMap);
        } catch (JsonProcessingException e) {
            logger.error("Error setting metrics map for content {}: {}", id, e.getMessage());
        }
    }

    public void setAnalyzedSentimentMap(Map<String, Object> sentimentMap) {
        try {
            this.analyzedSentiment = objectMapper.writeValueAsString(sentimentMap);
        } catch (JsonProcessingException e) {
            logger.error("Error setting analyzed sentiment map for content {}: {}", id, e.getMessage());
        }
    }

    public String getSeoSuggestions() {
        return seoSuggestions;
    }

    public void setSeoSuggestions(String seoSuggestions) {
        this.seoSuggestions = seoSuggestions;
    }

    public String getAbTestResults() {
        return abTestResults;
    }

    public void setAbTestResults(String abTestResults) {
        this.abTestResults = abTestResults;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}