// src/main/java/com/jithin/ai_content_platform/service/ContentStrategyService.java

package com.jithin.ai_content_platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jithin.ai_content_platform.model.TrendData;
import com.jithin.ai_content_platform.model.User;
import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.dto.StrategyRequest;
import com.jithin.ai_content_platform.repository.ContentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import java.io.IOException;
import java.io.File;

import jakarta.annotation.PostConstruct;
import com.jithin.ai_content_platform.exception.UnauthorizedException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.jithin.ai_content_platform.exception.UserNotFoundException;
import java.io.IOException;
import java.io.File;

@Service
@Slf4j
public class ContentStrategyService {

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private TrendAnalysisService trendAnalysisService;

    @Autowired
    private ContentService contentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AIRequestService aiRequestService;

    @Value("${openai.model}")
    private String model;

    @Value("${ml.model.engagement.weights}")
    private String engagementWeightsPath;

    @Value("${ml.model.content.performance}")
    private String contentPerformancePath;

    private Map<String, Double> engagementWeights;
    private Map<String, Object> performanceModel;

    private final Map<String, CachedInsight> targetAudienceCache = new ConcurrentHashMap<>();
    private final Map<String, CachedInsight> contentTypesCache = new ConcurrentHashMap<>();
    private final Map<String, CachedInsight> trendingTopicsCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = TimeUnit.MINUTES.toMillis(30); // Cache for 30 minutes

    private static class CachedInsight {
        private final Object data;
        private final long timestamp;

        public CachedInsight(Object data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION;
        }
    }

    @PostConstruct
    public void init() {
        try {
            // Parse engagement weights from comma-separated values
            String[] weights = engagementWeightsPath.split(",");
            this.engagementWeights = new HashMap<>();
            this.engagementWeights.put("content", Double.parseDouble(weights[0]));
            this.engagementWeights.put("timing", Double.parseDouble(weights[1]));
            this.engagementWeights.put("audience", Double.parseDouble(weights[2]));
            
            // Parse performance model value
            this.performanceModel = new HashMap<>();
            this.performanceModel.put("threshold", Double.parseDouble(contentPerformancePath));
            
            log.info("Successfully initialized ML model weights: {}", this.engagementWeights);
            log.info("Successfully initialized performance model: {}", this.performanceModel);

            // Start cache cleanup scheduler
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(this::cleanupCache, CACHE_DURATION, CACHE_DURATION, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Error parsing ML model weights, using default values", e);
            initializeDefaultWeights();
        }
    }

    private void cleanupCache() {
        long now = System.currentTimeMillis();
        targetAudienceCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        contentTypesCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        trendingTopicsCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public Map<String, Object> getStrategyAdvice(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }

        String username = user.getUsername();
        log.info("Generating content strategy advice for user: {}", username);
        Map<String, Object> advice = new HashMap<>();

        // 1. Analyze best posting times using ML model
        Map<String, List<LocalTime>> bestPostingTimes = analyzeBestPostingTimesML(user);
        advice.put("bestPostingTimes", bestPostingTimes);

        // 2. Analyze target audience using NLP and clustering
        Map<String, Object> targetAudience = analyzeTargetAudienceML(user);
        advice.put("targetAudience", targetAudience);

        // 3. Get content type recommendations using collaborative filtering
        Map<String, Object> contentTypes = recommendContentTypesML(user);
        advice.put("contentTypes", contentTypes);

        // 4. Get trending topics with AI-powered relevance
        List<String> trendingTopics = getTrendingTopicsWithAIRelevance(user);
        advice.put("trendingTopics", trendingTopics);

        // 5. Get engagement metrics using predictive analytics
        Map<String, Object> engagementMetrics = analyzeEngagementMetricsML(user);
        advice.put("engagementMetrics", engagementMetrics);

        return advice;
    }

    private Map<String, List<LocalTime>> analyzeBestPostingTimesML(User user) {
        Map<String, List<LocalTime>> optimizedTimes = new HashMap<>();
        
        // Get user's historical content performance
        List<Content> userContent = contentRepository.findByUser(user);
        
        // Group content by posting time and analyze engagement
        Map<DayOfWeek, Map<LocalTime, List<Double>>> timePerformance = new HashMap<>();
        
        for (Content content : userContent) {
            LocalDateTime postTime = content.getCreatedAt();
            DayOfWeek day = postTime.getDayOfWeek();
            LocalTime time = postTime.toLocalTime();
            
            double engagement = calculateContentEngagementML(content);
            
            timePerformance.computeIfAbsent(day, k -> new HashMap<>())
                .computeIfAbsent(time, k -> new ArrayList<>())
                .add(engagement);
        }
        
        // Use machine learning to identify optimal posting times
        for (DayOfWeek day : DayOfWeek.values()) {
            Map<LocalTime, List<Double>> dayPerformance = timePerformance.getOrDefault(day, new HashMap<>());
            
            // Apply time series analysis and pattern recognition
            List<LocalTime> optimalTimes = findOptimalPostingTimes(dayPerformance);
            optimizedTimes.put(day.toString(), optimalTimes);
        }
        
        return optimizedTimes;
    }

    public Map<String, Object> analyzeTargetAudienceML(User user) {
        String cacheKey = user.getId().toString();
        CachedInsight cached = targetAudienceCache.get(cacheKey);
        
        if (cached != null && !cached.isExpired()) {
            log.debug("Using cached target audience analysis for user: {}", user.getUsername());
            return (Map<String, Object>) cached.data;
        }

        Map<String, Object> audienceInsights = new HashMap<>();
        
        try {
            // Get user's content and engagement data
            List<Content> userContent = contentRepository.findByUser(user);
            
            // Perform audience clustering and segmentation
            Map<String, Object> clusters = performAudienceClustering(userContent);
            audienceInsights.put("segments", clusters);
            
            // Analyze audience behavior patterns
            Map<String, Object> behaviorPatterns = analyzeAudienceBehavior(userContent);
            audienceInsights.put("behavior", behaviorPatterns);
            
            // Make single AI request for comprehensive analysis
            Map<String, Object> aiInsights = aiRequestService.makeRequest(
                "audience_analysis",
                String.format(
                    "Analyze the following audience data and provide recommendations:\n" +
                    "Segments: %s\n" +
                    "Behavior Patterns: %s\n" +
                    "Provide analysis in JSON format with fields: targetDemographics, contentPreferences, engagementPatterns, recommendations",
                    objectMapper.writeValueAsString(clusters),
                    objectMapper.writeValueAsString(behaviorPatterns)
                ),
                Map.of("userId", user.getId())
            );
            
            audienceInsights.putAll(aiInsights);

            // Cache the results
            targetAudienceCache.put(cacheKey, new CachedInsight(audienceInsights));
            
        } catch (Exception e) {
            log.error("Error in audience analysis", e);
        }
        
        return audienceInsights;
    }

    public Map<String, Object> recommendContentTypesML(User user) {
        String cacheKey = user.getId().toString();
        CachedInsight cached = contentTypesCache.get(cacheKey);
        
        if (cached != null && !cached.isExpired()) {
            log.debug("Using cached content type recommendations for user: {}", user.getUsername());
            return (Map<String, Object>) cached.data;
        }

        Map<String, Object> recommendations = new HashMap<>();
        
        try {
            // Get user's content performance data
            List<Content> userContent = contentRepository.findByUser(user);
            
            // Analyze content type performance using ML
            Map<String, Double> typePerformance = analyzeContentTypePerformance(userContent);
            recommendations.put("typePerformance", typePerformance);
            
            // Get similar users' successful content types
            List<String> similarUsersTypes = findSimilarUsersContentTypes(user);
            recommendations.put("recommendedTypes", similarUsersTypes);
            
            // Make single AI request for recommendations
            Map<String, Object> aiInsights = aiRequestService.makeRequest(
                "content_recommendations",
                String.format(
                    "Analyze content performance and provide recommendations:\n" +
                    "Current Performance: %s\n" +
                    "Similar Users' Types: %s\n" +
                    "Provide recommendations in JSON format with fields: recommendedFormats, contentStrategy, optimizationTips",
                    objectMapper.writeValueAsString(typePerformance),
                    objectMapper.writeValueAsString(similarUsersTypes)
                ),
                Map.of("userId", user.getId())
            );
            
            recommendations.putAll(aiInsights);

            // Cache the results
            contentTypesCache.put(cacheKey, new CachedInsight(recommendations));
            
        } catch (Exception e) {
            log.error("Error in content type recommendations", e);
        }
        
        return recommendations;
    }

    public List<String> getTrendingTopicsWithAIRelevance(User user) {
        String cacheKey = user.getId().toString();
        CachedInsight cached = trendingTopicsCache.get(cacheKey);
        
        if (cached != null && !cached.isExpired()) {
            log.debug("Using cached trending topics for user: {}", user.getUsername());
            return (List<String>) cached.data;
        }

        List<TrendData> trendingTopics = new ArrayList<>();
        
        try {
            // Get trending topics from TrendAnalysisService
            trendingTopics = trendAnalysisService.getAITrendingTopics();
            
            // Sort by base relevance first
            List<TrendData> sortedTopics = trendingTopics.stream()
                .sorted((t1, t2) -> Double.compare(calculateTopicRelevance(t2, user), calculateTopicRelevance(t1, user)))
                .collect(Collectors.toList());
            
            // Make single AI request for relevance analysis
            Map<String, Object> insights = aiRequestService.makeRequest(
                "trend_analysis",
                String.format(
                    "Analyze these trending topics for relevance to the user's audience:\n" +
                    "Topics: %s\n" +
                    "User Content History: %s\n" +
                    "Provide analysis in JSON format with fields: relevanceScores, recommendedTopics, topicInsights",
                    objectMapper.writeValueAsString(sortedTopics),
                    objectMapper.writeValueAsString(contentRepository.findByUser(user))
                ),
                Map.of("userId", user.getId())
            );
            
            @SuppressWarnings("unchecked")
            List<String> recommendedTopics = (List<String>) insights.get("recommendedTopics");
            List<String> result = recommendedTopics != null ? recommendedTopics : 
                sortedTopics.stream().map(TrendData::getTopic).collect(Collectors.toList());

            // Cache the results
            trendingTopicsCache.put(cacheKey, new CachedInsight(result));
            
            return result;
            
        } catch (Exception e) {
            log.error("Error analyzing topic relevance", e);
            return trendingTopics.stream()
                .map(TrendData::getTopic)
                .collect(Collectors.toList());
        }
    }

    private double calculateContentEngagementML(Content content) {
        if (content.getMetrics() == null) {
            return 0.0;
        }
        
        try {
            Map<String, Integer> metrics = objectMapper.readValue(content.getMetrics(), 
                new TypeReference<Map<String, Integer>>() {});
            
            // Apply ML model weights for different engagement types
            double weightedScore = 0.0;
            for (Map.Entry<String, Integer> metric : metrics.entrySet()) {
                String metricType = metric.getKey();
                int value = metric.getValue();
                double weight = engagementWeights.getOrDefault(metricType, 1.0);
                
                weightedScore += value * weight;
            }
            
            // Apply time decay factor
            long daysSincePosted = java.time.temporal.ChronoUnit.DAYS.between(content.getCreatedAt(), LocalDateTime.now());
            double timeDecay = Math.exp(-0.05 * daysSincePosted); // Exponential decay
            
            return weightedScore * timeDecay;
            
        } catch (JsonProcessingException e) {
            log.error("Error calculating ML-based engagement score", e);
            return 0.0;
        }
    }

    private String getAIRecommendations(String prompt) {
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "user",
                "content", prompt
            ));
            
            Map<String, Object> response = aiRequestService.makeRequest(
                "content_recommendations",
                prompt,
                Map.of("userId", "default")
            );
            
            return (String) response.get("content");
                
        } catch (Exception e) {
            log.error("Error getting AI recommendations", e);
            return "";
        }
    }

    private Map<String, Object> performAudienceClustering(List<Content> content) {
        // Implement K-means clustering for audience segmentation
        Map<String, Object> clusters = new HashMap<>();
        
        // Extract features for clustering
        List<Map<String, Object>> features = content.stream()
            .map(this::extractAudienceFeatures)
            .collect(Collectors.toList());
        
        // Perform clustering and return results
        // This is a placeholder - implement actual clustering logic
        return performKMeansClustering(features);
    }

    private Map<String, Object> analyzeAudienceBehavior(List<Content> content) {
        Map<String, Object> behavior = new HashMap<>();
        
        // Analyze engagement patterns
        Map<String, Double> engagementPatterns = analyzeEngagementPatterns(content);
        behavior.put("engagement", engagementPatterns);
        
        // Analyze content preferences
        Map<String, Object> preferences = analyzeContentPreferences(content);
        behavior.put("preferences", preferences);
        
        // Analyze interaction times
        Map<String, List<LocalTime>> interactionTimes = analyzeInteractionTimes(content);
        behavior.put("interactionTimes", interactionTimes);
        
        return behavior;
    }

    private double calculateTopicRelevance(TrendData trend, User user) {
        double relevanceScore = 0.0;

        // Base score from trend metrics
        try {
            relevanceScore += Double.parseDouble(trend.getTrendScore().toString().toString()) * 0.4; // 40% weight to trend score
        } catch (NumberFormatException e) {
            // Handle the case where trend.getTrendScore() is not a valid number
            relevanceScore += 0; // Default to 0 if parsing fails
        }

        // Engagement potential from sentiment analysis
        try {
            String sentimentDistribution = trend.getSentimentDistribution();
            Map<String, Object> sentimentMap = objectMapper.readValue(sentimentDistribution, new TypeReference<Map<String, Object>>() {});
            double positiveScore = (double) sentimentMap.getOrDefault("positive", 0.0);
            double engagementScore = (double) sentimentMap.getOrDefault("engagement", 0.0);
            double sentimentStrength = (double) sentimentMap.getOrDefault("sentiment_strength", 0.0);
            
            // Combine sentiment metrics for relevance score
            relevanceScore += (positiveScore * 0.15 + engagementScore * 0.1 + sentimentStrength * 0.05);
        } catch (Exception e) {
            log.error("Error processing sentiment analysis", e);
        }

        // Growth potential
        if (trend.getGrowthMetrics() != null) {
            Map<String, Double> growthMap = trend.getGrowthMetrics();
            if (growthMap != null) {
                double growthRate = growthMap.getOrDefault("growth_rate", 0.0);
                double momentum = growthMap.getOrDefault("momentum", 0.0);
                relevanceScore += (growthRate + momentum) * 0.3; // 30% weight to growth metrics
            }
        }

        // Get growth metrics from trending topics
        if (trend.getTrendingTopics() != null) {
            try {
                Map<String, Double> growthMap = objectMapper.readValue(trend.getTrendingTopics(), 
                    new TypeReference<Map<String, Double>>() {});
                
                if (growthMap != null) {
                    double momentum = growthMap.getOrDefault("momentum", 0.0);
                    double volatility = growthMap.getOrDefault("volatility", 0.0);
                    
                    // Use growth metrics to adjust relevance score
                    relevanceScore += (momentum * 0.1 - volatility * 0.05);
                }
            } catch (JsonProcessingException e) {
                log.error("Error parsing growth metrics", e);
            }
        }

        // Get user's recent content performance in this category
        List<Content> userContent = contentRepository.findByUserAndCategory(user, trend.getCategory());
        if (!userContent.isEmpty()) {
            // Calculate average engagement for user's content in this category
            double avgEngagement = userContent.stream()
                .mapToDouble(content -> calculateContentEngagementML(content))
                .average()
                .orElse(0.0);
            
            // Boost score if user has good performance in this category
            relevanceScore *= (1.0 + avgEngagement * 0.2); // Up to 20% boost based on past performance
        }

        return relevanceScore;
    }

    private Map<String, Object> analyzeEngagementMetricsML(User user) {
        Map<String, Object> metrics = new HashMap<>();
        
        // Historical engagement rates
        Map<String, Double> engagementRates = new HashMap<>();
        engagementRates.put("likes", 0.05);
        engagementRates.put("comments", 0.02);
        engagementRates.put("shares", 0.01);
        
        metrics.put("engagementRates", engagementRates);
        
        // Platform performance
        Map<String, Double> platformPerformance = new HashMap<>();
        platformPerformance.put("LinkedIn", 0.8);
        platformPerformance.put("Twitter", 0.7);
        platformPerformance.put("Facebook", 0.6);
        
        metrics.put("platformPerformance", platformPerformance);
        
        return metrics;
    }

    private void initializeDefaultWeights() {
        // Initialize default weights for engagement metrics
        engagementWeights = new HashMap<>();
        engagementWeights.put("likes", 1.0);
        engagementWeights.put("comments", 1.0);
        engagementWeights.put("shares", 1.0);
    }

    private List<LocalTime> findOptimalPostingTimes(Map<LocalTime, List<Double>> dayPerformance) {
        // Implement time series analysis and pattern recognition to find optimal posting times
        // This is a placeholder - implement actual logic
        return new ArrayList<>();
    }

    private Map<String, Object> performKMeansClustering(List<Map<String, Object>> features) {
        // Implement K-means clustering algorithm
        // This is a placeholder - implement actual logic
        return new HashMap<>();
    }

    private Map<String, Object> extractAudienceFeatures(Content content) {
        // Extract features for audience clustering
        // This is a placeholder - implement actual logic
        return new HashMap<>();
    }

    private Map<String, Double> analyzeEngagementPatterns(List<Content> content) {
        // Analyze engagement patterns
        // This is a placeholder - implement actual logic
        return new HashMap<>();
    }

    private Map<String, Object> analyzeContentPreferences(List<Content> content) {
        // Analyze content preferences
        // This is a placeholder - implement actual logic
        return new HashMap<>();
    }

    private Map<String, List<LocalTime>> analyzeInteractionTimes(List<Content> content) {
        // Analyze interaction times
        // This is a placeholder - implement actual logic
        return new HashMap<>();
    }

    private String generateAudienceAnalysisPrompt(Map<String, Object> clusters, Map<String, Object> behaviorPatterns) {
        // Generate prompt for AI-powered audience analysis
        // This is a placeholder - implement actual logic
        return "";
    }

    private String generateContentFormatPrompt(Map<String, Double> typePerformance, List<String> similarUsersTypes) {
        // Generate prompt for AI-powered content format suggestions
        // This is a placeholder - implement actual logic
        return "";
    }

    private List<String> findSimilarUsersContentTypes(User user) {
        // Find similar users' successful content types
        // This is a placeholder - implement actual logic
        return new ArrayList<>();
    }

    /**
     * Get comprehensive audience insights using ML-based clustering and analysis
     * @param user The user requesting audience insights
     * @return Map containing various audience insights metrics
     */
    public Map<String, Object> getAudienceInsights(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }

        log.info("Generating audience insights for user: {}", user.getUsername());
        Map<String, Object> insights = new HashMap<>();
        
        // Get user's content history
        List<Content> userContent = contentRepository.findByUser(user);
        
        // 1. Get ML-based audience analysis
        Map<String, Object> audienceAnalysis = analyzeTargetAudienceML(user);
        insights.put("audienceAnalysis", audienceAnalysis);
        
        // 2. Get engagement metrics
        Map<String, Object> engagementMetrics = analyzeEngagementMetricsML(user);
        insights.put("engagementMetrics", engagementMetrics);
        
        // 3. Get trending topics for the audience
        List<String> trendingTopics = getTrendingTopicsWithAIRelevance(user);
        insights.put("trendingTopics", trendingTopics);
        
        // 4. Get content preferences
        Map<String, Object> contentPreferences = analyzeContentPreferences(userContent);
        insights.put("contentPreferences", contentPreferences);
        
        // 5. Get interaction patterns
        Map<String, List<LocalTime>> interactionPatterns = analyzeInteractionTimes(userContent);
        insights.put("interactionPatterns", interactionPatterns);
        
        return insights;
    }

    /**
     * Get content type recommendations with AI suggestions
     * @param user The user requesting recommendations
     * @param categories Optional list of categories to filter recommendations
     * @return Map containing content type recommendations and AI suggestions
     */
    public Map<String, Object> getContentTypeRecommendations(User user, List<String> categories) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }

        log.info("Generating content type recommendations for user: {}", user.getUsername());
        return recommendContentTypesML(user, categories);
    }

    /**
     * Get trending topics with AI-powered relevance scoring
     * @param user The user requesting trending topics
     * @param category Optional category to filter topics
     * @param limit Maximum number of topics to return
     * @return Map containing trending topics and their relevance scores
     */
    public Map<String, Object> getTrendingTopicsWithRelevance(User user, String category, int limit) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }

        log.info("Fetching trending topics for user: {}", user.getUsername());
        Map<String, Object> result = new HashMap<>();
        
        // Get trending topics with AI relevance scoring
        List<Map<String, Object>> topics = getTrendingTopicsWithAIRelevance(user)
            .stream()
            .map(topic -> {
                Map<String, Object> topicMap = new HashMap<>();
                topicMap.put("topic", topic);
                List<TrendData> trends = trendAnalysisService.getTrendsByCategory(topic);
                // Take the first trend if available, otherwise use a default score
                double relevanceScore = !trends.isEmpty() ? calculateTopicRelevance(trends.get(0), user) : 0.0;
                topicMap.put("relevanceScore", relevanceScore);
                return topicMap;
            })
            .filter(topic -> category == null || topic.get("topic").toString().toLowerCase().contains(category.toLowerCase()))
            .limit(limit)
            .collect(Collectors.toList());
            
        result.put("topics", topics);
        result.put("category", category);
        result.put("total", topics.size());
        
        return result;
    }

    /**
     * Get engagement metrics with ML-based predictions
     * @param user The user requesting engagement metrics
     * @param timeframe Optional timeframe to analyze (e.g., "last_week", "last_month")
     * @return Map containing engagement metrics and predictions
     */
    public Map<String, Object> getEngagementMetrics(User user, String timeframe) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }

        log.info("Analyzing engagement metrics for user: {}", user.getUsername());
        return analyzeEngagementMetricsML(user, timeframe);
    }

    /**
     * Generate personalized content strategy based on specific parameters
     * @param user The user requesting the strategy
     * @param request Strategy generation parameters
     * @return Map containing the personalized strategy
     */
    public Map<String, Object> generatePersonalizedStrategy(User user, StrategyRequest request) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Strategy request must not be null");
        }

        log.info("Generating personalized strategy for user: {}", user.getUsername());
        
        Map<String, Object> strategy = new HashMap<>();
        
        // Get base strategy advice
        Map<String, Object> baseStrategy = getStrategyAdvice(user);
        strategy.putAll(baseStrategy);
        
        // Apply customizations from request
        if (request.getTargetAudience() != null) {
            Map<String, Object> targetAudienceMap = new HashMap<>();
            targetAudienceMap.put("audience", request.getTargetAudience());
            strategy.put("targetAudience", analyzeTargetAudienceML(user, targetAudienceMap));
        }
        if (request.getContentTypes() != null) {
            strategy.put("contentTypes", recommendContentTypesML(user, request.getContentTypes()));
        }
        if (request.getTimeframe() != null) {
            strategy.put("engagementMetrics", analyzeEngagementMetricsML(user, request.getTimeframe()));
        }
        
        return strategy;
    }

    private Map<String, Object> analyzeEngagementMetricsML(User user, String timeframe) {
        Map<String, Object> metrics = analyzeEngagementMetricsML(user);
        
        if (timeframe != null) {
            // Filter metrics based on timeframe
            metrics = filterMetricsByTimeframe(metrics, timeframe);
        }
        
        return metrics;
    }

    private Map<String, Object> filterMetricsByTimeframe(Map<String, Object> metrics, String timeframe) {
        // Implementation for filtering metrics by timeframe
        // This is a placeholder - implement actual logic
        return metrics;
    }

    private Map<String, Object> recommendContentTypesML(User user, List<String> categories) {
        Map<String, Object> recommendations = recommendContentTypesML(user);
        
        if (categories != null && !categories.isEmpty()) {
            // Filter recommendations by categories
            recommendations = filterRecommendationsByCategories(recommendations, categories);
        }
        
        return recommendations;
    }

    private Map<String, Object> filterRecommendationsByCategories(Map<String, Object> recommendations, List<String> categories) {
        // Implementation for filtering recommendations by categories
        // This is a placeholder - implement actual logic
        return recommendations;
    }

    private Map<String, Object> analyzeTargetAudienceML(User user, Map<String, Object> targetAudience) {
        Map<String, Object> analysis = analyzeTargetAudienceML(user);
        
        // Customize analysis based on target audience parameters
        if (targetAudience != null) {
            analysis.put("customTargeting", targetAudience);
        }
        
        return analysis;
    }

    private Map<String, Double> analyzeContentTypePerformance(List<Content> content) {
        // Analyze content type performance using ML
        // This is a placeholder - implement actual logic
        return new HashMap<>();
    }

    /**
     * Calculate optimal posting times based on historical engagement data and ML analysis
     * @param user The user requesting optimal posting times
     * @param timezone The timezone to calculate posting times for (optional)
     * @return Map containing optimal posting times and related metrics
     */
    /**
     * Calculate optimal posting times based on historical engagement data and ML analysis
     * @param user The user requesting optimal posting times
     * @param timezone The timezone to calculate posting times for (optional)
     * @return Map containing optimal posting times and related metrics
     * @throws UnauthorizedException if user is not authorized
     * @throws UserNotFoundException if user is not found
     */
    public Map<String, Object> getOptimalPostingTimes(User user, String timezone) {
        if (user == null) {
            throw new UnauthorizedException("User must be authenticated to get optimal posting times");
        }
        log.info("Calculating optimal posting times for user: {} with timezone: {}", user.getUsername(), timezone);
        
        Map<String, Object> result = new HashMap<>();
        try {
            // Get user's historical content and engagement data
            List<Content> userContent = contentRepository.findByUser(user);
            if (userContent == null) {
                throw new UserNotFoundException("User data not found: " + user.getUsername());
            }
            
            // Apply timezone adjustment if specified
            ZoneId userZone = timezone != null ? ZoneId.of(timezone) : ZoneId.systemDefault();
            
            // Group engagement by day and time
            Map<DayOfWeek, List<LocalTime>> highEngagementTimes = userContent.stream()
                .filter(content -> content.getEngagement() != null && content.getEngagement() > 0.7)
                .map(content -> adjustToUserTimezone(content, userZone))
                .collect(Collectors.groupingBy(
                    content -> content.getCreatedAt().getDayOfWeek(),
                    Collectors.mapping(
                        content -> content.getCreatedAt().toLocalTime(),
                        Collectors.toList()
                    )
                ));
            
            // Calculate optimal times for each day using ML weights
            Map<String, List<String>> optimalTimes = new HashMap<>();
            for (DayOfWeek day : DayOfWeek.values()) {
                List<LocalTime> times = highEngagementTimes.getOrDefault(day, new ArrayList<>());
                List<String> formattedTimes = calculateOptimalTimesForDay(times, userContent.stream()
                    .filter(c -> adjustToUserTimezone(c, userZone).getCreatedAt().getDayOfWeek() == day)
                    .collect(Collectors.toList()));
                optimalTimes.put(day.toString(), formattedTimes);
            }
            
            // Structure response according to API contract
            Map<String, Object> timingData = new HashMap<>();
            timingData.put("times", optimalTimes);
            timingData.put("confidence", calculateConfidenceScore(userContent));
            timingData.put("recommendedDays", getRecommendedDays(highEngagementTimes));
            result.put("timing", timingData);
            
            // Add ML insights
            Map<String, Object> insightData = new HashMap<>();
            if (trendAnalysisService != null) {
                Map<String, Object> trendInsights = trendAnalysisService.analyzeTrends(userContent);
                insightData.put("trends", trendInsights);
            }
            insightData.put("dataPoints", userContent.size());
            insightData.put("analysisTimestamp", LocalDateTime.now());
            result.put("insights", insightData);
            
        } catch (DateTimeException e) {
            log.error("Invalid timezone specified: {}", timezone, e);
            throw new IllegalArgumentException("Invalid timezone: " + timezone, e);
        } catch (Exception e) {
            log.error("Error calculating optimal posting times for user {}", user.getUsername(), e);
            throw new RuntimeException("Failed to calculate optimal posting times", e);
        }
        
        return result;
    }
    
    private Content adjustToUserTimezone(Content content, ZoneId userZone) {
        if (content.getCreatedAt() == null) return content;
        
        LocalDateTime adjustedTime = content.getCreatedAt()
            .atZone(ZoneId.systemDefault())
            .withZoneSameInstant(userZone)
            .toLocalDateTime();
        
        content.setCreatedAt(adjustedTime);
        return content;
    }
    
    private List<String> calculateOptimalTimesForDay(List<LocalTime> times, List<Content> dayContent) {
        if (times.isEmpty()) {
            // Default recommendations if no historical data
            return Arrays.asList("09:00", "12:00", "17:00");
        }
        
        // Calculate optimal times based on historical engagement clusters and ML weights
        Map<Integer, Double> weightedEngagement = new HashMap<>();
        
        for (Content content : dayContent) {
            int hour = content.getCreatedAt().getHour();
            double engagement = content.getEngagement() != null ? content.getEngagement() : 0.0;
            
            // Apply ML model weights
            try {
                Map<String, Integer> metrics = objectMapper.readValue(content.getMetrics(), 
                    new TypeReference<Map<String, Integer>>() {});
                
                for (Map.Entry<String, Integer> metric : metrics.entrySet()) {
                    double weight = engagementWeights.getOrDefault(metric.getKey(), 1.0);
                    engagement += metric.getValue() * weight;
                }
            } catch (JsonProcessingException e) {
                log.warn("Could not parse metrics for content {}", content.getId());
            }
            
            weightedEngagement.merge(hour, engagement, Double::sum);
        }
        
        return weightedEngagement.entrySet().stream()
            .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
            .limit(3)
            .map(entry -> String.format("%02d:00", entry.getKey()))
            .collect(Collectors.toList());
    }
    
    private double calculateConfidenceScore(List<Content> userContent) {
        if (userContent.isEmpty()) {
            return 0.5; // Base confidence for new users
        }
        
        // Calculate confidence based on amount and consistency of historical data
        long highEngagementPosts = userContent.stream()
            .filter(content -> content.getEngagement() != null && content.getEngagement() > 0.7)
            .count();
        
        // Factor in the amount of data and its recency
        double dataAmount = Math.min(1.0, userContent.size() / 100.0); // Scale with amount of data
        double recencyScore = calculateRecencyScore(userContent);
        
        return Math.min(0.95, Math.max(0.5, 
            (0.4 * (double) highEngagementPosts / userContent.size()) +
            (0.3 * dataAmount) +
            (0.3 * recencyScore)));
    }
    
    private double calculateRecencyScore(List<Content> userContent) {
        if (userContent.isEmpty()) return 0.5;
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime mostRecent = userContent.stream()
            .map(Content::getCreatedAt)
            .max(LocalDateTime::compareTo)
            .orElse(now);
        
        long daysSinceLastPost = ChronoUnit.DAYS.between(mostRecent, now);
        return Math.exp(-daysSinceLastPost / 30.0); // Exponential decay over 30 days
    }
    
    private List<String> getRecommendedDays(Map<DayOfWeek, List<LocalTime>> highEngagementTimes) {
        return highEngagementTimes.entrySet().stream()
            .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
            .limit(3)
            .map(entry -> entry.getKey().toString())
            .collect(Collectors.toList());
    }

    public Map<String, Object> generateContentStrategy(String industry, List<String> competitors, String contentType) {
        if (industry == null || industry.trim().isEmpty()) {
            log.warn("No industry specified for strategy generation");
            return Map.of("error", "No industry specified");
        }

        // Create prompt for strategy generation
        String prompt = String.format(
            "Generate a content strategy for the %s industry, considering competitors: %s. " +
            "Focus on %s content. Include the following in JSON format: " +
            "contentPillars (array), targetAudience (object), keyTopics (array), " +
            "contentCalendar (object), distributionChannels (array), metrics (object)",
            industry, String.join(", ", competitors), contentType
        );

        // Make request through centralized service
        return aiRequestService.makeRequest(
            "content_strategy",
            prompt,
            Map.of(
                "industry", industry,
                "competitors", competitors,
                "contentType", contentType
            )
        );
    }
}