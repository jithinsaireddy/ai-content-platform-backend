package com.jithin.ai_content_platform.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Entity
@Data
@NoArgsConstructor
@Table(name = "trend_data")
public class TrendData {
    private static final Logger logger = LoggerFactory.getLogger(TrendData.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "analysis_timestamp")
    private LocalDateTime analysisTimestamp;
    
    @Column(name = "trending_topics", columnDefinition = "TEXT")
    private String trendingTopics;
    
    @Column(name = "sentiment_distribution", columnDefinition = "TEXT")
    private String sentimentDistribution;
    
    @Column(name = "trend_score")
    private Double trendScore;
    
    @Column(name = "topic", columnDefinition = "TEXT")
    private String topic;
    
    @Column(name = "confidence_score")
    private Double confidenceScore;
    
    @Column(name = "trend_pattern")
    @Enumerated(EnumType.STRING)
    private TrendPattern trendPattern;
    
    @Column(name = "seasonality_data", columnDefinition = "TEXT")
    private String seasonalityData;
    
    @Column(name = "momentum")
    private Double momentum;
    
    @Column(name = "volatility")
    private Double volatility;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "region")
    private Region region;
    
    @Column(name = "category", columnDefinition = "TEXT")
    private String category;
    
    @Column(name = "historical_values", columnDefinition = "TEXT")
    private String historicalValues;
    
    @Column(name = "historical_dates", columnDefinition = "TEXT")
    private String historicalDates;

    @Column(name = "growth_metrics", columnDefinition = "TEXT")
    private String growthMetrics;

    @Column(name = "metrics", columnDefinition = "TEXT")
    private String metrics;

    @Column(name = "sentiment_score")
    private Double sentimentScore;

    @Column(name = "engagement_score")
    private Double engagementScore;

    @Transient
    private Map<String, Map<String, Object>> trendingTopicsMap;
    
    @Transient
    private Map<String, Object> seasonalityMap;
    
    @Transient
    private List<Double> historicalValuesList;
    
    @Transient
    private List<String> historicalDatesList;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadataString;
    
    @Column(name = "start_time")
    private LocalDateTime startTime;
    
    @Column(name = "industry")
    private String industry;
    
    @Column(name = "engagement_metrics", columnDefinition = "TEXT")
    private String engagementMetricsString;
    
    @Column(name = "timestamps", columnDefinition = "TEXT")
    private String timestampsString;
    
    @Transient
    private Map<String, Double> engagementMetrics;
    
    @Transient
    private List<LocalDateTime> timestamps;

    public Map<String, Map<String, Object>> getTrendingTopicsMap() {
        if (trendingTopicsMap == null && trendingTopics != null) {
            try {
                trendingTopicsMap = objectMapper.readValue(trendingTopics, 
                    new TypeReference<Map<String, Map<String, Object>>>() {});
            } catch (JsonProcessingException e) {
                logger.error("Error deserializing trending topics", e);
                return new HashMap<>();
            }
        }
        return trendingTopicsMap != null ? trendingTopicsMap : new HashMap<>();
    }

    public void setTrendingTopicsMap(Map<String, Map<String, Object>> trendingTopicsMap) {
        this.trendingTopicsMap = trendingTopicsMap;
        try {
            this.trendingTopics = objectMapper.writeValueAsString(trendingTopicsMap);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing trending topics", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getSeasonalityMap() {
        if (seasonalityMap == null && seasonalityData != null) {
            try {
                seasonalityMap = objectMapper.readValue(seasonalityData, Map.class);
            } catch (JsonProcessingException e) {
                logger.error("Error deserializing seasonality data", e);
                return new HashMap<>();
            }
        }
        return seasonalityMap != null ? seasonalityMap : new HashMap<>();
    }

    public void setSeasonalityMap(Map<String, Object> seasonalityMap) {
        this.seasonalityMap = seasonalityMap;
        try {
            this.seasonalityData = objectMapper.writeValueAsString(seasonalityMap);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing seasonality data", e);
        }
    }

    public List<Double> getHistoricalValuesList() {
        if (historicalValuesList == null && historicalValues != null) {
            try {
                historicalValuesList = objectMapper.readValue(historicalValues, 
                    new TypeReference<List<Double>>() {});
            } catch (JsonProcessingException e) {
                logger.error("Error deserializing historical values", e);
                return List.of();
            }
        }
        return historicalValuesList != null ? historicalValuesList : List.of();
    }

    public void setHistoricalValuesList(List<Double> historicalValuesList) {
        this.historicalValuesList = historicalValuesList;
        try {
            this.historicalValues = objectMapper.writeValueAsString(historicalValuesList);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing historical values", e);
        }
    }

    public List<String> getHistoricalDatesList() {
        if (historicalDatesList == null && historicalDates != null) {
            try {
                historicalDatesList = objectMapper.readValue(historicalDates, 
                    new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                logger.error("Error deserializing historical dates", e);
                return List.of();
            }
        }
        return historicalDatesList != null ? historicalDatesList : List.of();
    }

    public void setHistoricalDatesList(List<String> historicalDatesList) {
        this.historicalDatesList = historicalDatesList;
        try {
            this.historicalDates = objectMapper.writeValueAsString(historicalDatesList);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing historical dates", e);
        }
    }

    public void setSentimentAnalysis(Map<String, Double> sentimentAnalysis) {
        try {
            this.sentimentDistribution = objectMapper.writeValueAsString(sentimentAnalysis);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing sentiment analysis", e);
        }
    }

    public Map<String, Double> getEngagementMetrics() {
        if (engagementMetrics == null && engagementMetricsString != null) {
            try {
                engagementMetrics = objectMapper.readValue(engagementMetricsString,
                    new TypeReference<Map<String, Double>>() {});
            } catch (JsonProcessingException e) {
                logger.error("Error deserializing engagement metrics", e);
                return new HashMap<>();
            }
        }
        return engagementMetrics != null ? engagementMetrics : new HashMap<>();
    }
    
    public void setEngagementMetrics(Map<String, Double> engagementMetrics) {
        this.engagementMetrics = engagementMetrics;
        try {
            this.engagementMetricsString = objectMapper.writeValueAsString(engagementMetrics);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing engagement metrics", e);
        }
    }
    
    public List<LocalDateTime> getTimestamps() {
        if (timestamps == null && timestampsString != null) {
            try {
                List<String> dateStrings = objectMapper.readValue(timestampsString,
                    new TypeReference<List<String>>() {});
                timestamps = dateStrings.stream()
                    .map(LocalDateTime::parse)
                    .toList();
            } catch (JsonProcessingException e) {
                logger.error("Error deserializing timestamps", e);
                return List.of();
            }
        }
        return timestamps != null ? timestamps : List.of();
    }
    
    public void setTimestamps(List<LocalDateTime> timestamps) {
        this.timestamps = timestamps;
        try {
            List<String> dateStrings = timestamps.stream()
                .map(LocalDateTime::toString)
                .toList();
            this.timestampsString = objectMapper.writeValueAsString(dateStrings);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing timestamps", e);
        }
    }
    
    public Duration getTimeSinceStart() {
        if (startTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(startTime, LocalDateTime.now());
    }
    
    public double getScore() {
        return trendScore != null ? trendScore : 0.0;
    }
    
    public List<Region> getRegions() {
        return region != null ? List.of(region) : List.of();
    }
    
    public Map<String, Double> getSentimentAnalysis() {
        if (sentimentDistribution != null) {
            try {
                return objectMapper.readValue(sentimentDistribution, 
                    new TypeReference<Map<String, Double>>() {});
            } catch (JsonProcessingException e) {
                logger.error("Error deserializing sentiment analysis", e);
                return new HashMap<>();
            }
        }
        return new HashMap<>();
    }

    public void setTrendScoreValue(double score) {
        this.trendScore = score;
    }

    public Double getSentimentScore() {
        return sentimentScore;
    }

    public void setSentimentScore(Double sentimentScore) {
        this.sentimentScore = sentimentScore;
    }

    public Double getEngagementScore() {
        return engagementScore;
    }

    public Map<String, Object> getMetadata() {
        if (metadataString != null) {
            try {
                return objectMapper.readValue(metadataString, Map.class);
            } catch (JsonProcessingException e) {
                logger.error("Error deserializing metadata", e);
                return new HashMap<>();
            }
        }
        return new HashMap<>();
    }

    public void setMetadata(Map<String, Object> metadata) {
        try {
            this.metadataString = objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing metadata", e);
        }
    }

    public void setGrowthMetrics(Map<String, Double> growthMetrics) {
        try {
            this.growthMetrics = objectMapper.writeValueAsString(growthMetrics);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing growth metrics", e);
        }
    }

    public Map<String, Double> getGrowthMetrics() {
        if (growthMetrics != null) {
            try {
                return objectMapper.readValue(growthMetrics, new TypeReference<Map<String, Double>>() {});
            } catch (JsonProcessingException e) {
                logger.error("Error deserializing growth metrics", e);
                return new HashMap<>();
            }
        }
        return new HashMap<>();
    }

    public void setMetrics(String metrics) {
        this.metrics = metrics;
    }

    public String getMetrics() {
        return metrics;
    }

    public enum Region {
        NORTH_AMERICA,
        EUROPE,
        ASIA,
        SOUTH_AMERICA,
        AFRICA,
        OCEANIA,
        GLOBAL
    }

    @PrePersist
    @PreUpdate
    private void prePersist() {
        if (analysisTimestamp == null) {
            analysisTimestamp = LocalDateTime.now();
        }
    }
}
