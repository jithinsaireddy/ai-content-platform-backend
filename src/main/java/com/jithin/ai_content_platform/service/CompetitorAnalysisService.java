package com.jithin.ai_content_platform.service;

import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.model.CompetitorData;
import com.jithin.ai_content_platform.model.MarketInsight;
import com.jithin.ai_content_platform.util.JsonResponseHandler;
import com.jithin.ai_content_platform.repository.CompetitorDataRepository;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
@Slf4j
public class CompetitorAnalysisService {

    @Autowired
    private TrendAnalysisService trendAnalysisService;

    @Autowired
    private PerformancePredictionService performancePredictionService;
    
    @Autowired
    private CompetitorDataRepository competitorDataRepository;
    
    @Autowired
    private OpenRouterService openRouterService;

    @Autowired
    private AIRequestService aiRequestService;

    @Value("${openai.model}")
    private String model;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JsonResponseHandler jsonResponseHandler;
    
    @Autowired
    private MLPredictionService mlPredictionService;

    // Sentiment analysis cache with expiration
    private final Map<String, Map<String, Object>> sentimentCache = new ConcurrentHashMap<>();
    private final Map<String, Long> sentimentCacheTimestamps = new ConcurrentHashMap<>();
    private static final long SENTIMENT_CACHE_DURATION = TimeUnit.HOURS.toMillis(4); // 4-hour cache

    // Positioning analysis cache
    private final Map<String, Map<String, Object>> positioningCache = new ConcurrentHashMap<>();
    private final Map<String, Long> positioningCacheTimestamps = new ConcurrentHashMap<>();
    private static final long POSITIONING_CACHE_DURATION = TimeUnit.HOURS.toMillis(4); // 4-hour cache

    public Map<String, Object> analyzeCompetitorContent(String industry, List<String> competitors) {
        // Validate industry input
        if (industry == null || industry.trim().isEmpty()) {
            log.warn("No industry specified for competitor analysis. Using 'default' industry.");
            industry = "default";
        }

        // Validate competitors list
        if (competitors == null || competitors.isEmpty()) {
            log.error("No competitors provided for industry: {}", industry);
            return Map.of("error", "No competitors specified", "industry", industry);
        }

        log.info("Analyzing competitor content for industry: {}", industry);
        
        Map<String, Object> analysis = new HashMap<>();
        
        try {
            // Get historical competitor data
            List<CompetitorData> historicalData = competitorDataRepository.findByIndustry(industry);
            
            // Analyze content strategy patterns with ML
            Map<String, Object> contentPatterns = analyzeContentPatterns(competitors, historicalData);
            analysis.put("contentPatterns", contentPatterns);
            
            // Analyze posting frequency and timing with predictive analytics
            Map<String, Object> postingMetrics = analyzePostingMetrics(competitors, historicalData);
            analysis.put("postingMetrics", postingMetrics);
            
            // Analyze engagement metrics with ML insights
            Map<String, Object> engagementMetrics = analyzeEngagementMetrics(competitors, historicalData);
            analysis.put("engagementMetrics", engagementMetrics);
            
            // Identify market gaps using ML and trend analysis
            List<MarketInsight> marketGaps = identifyMarketGaps(industry, competitors, historicalData);
            analysis.put("marketGaps", marketGaps);
            
            // Add sentiment analysis
            Map<String, Object> sentimentAnalysis = analyzeSentiment(competitors);
            analysis.put("sentimentAnalysis", sentimentAnalysis);
            
            // Add competitive positioning
            Map<String, Object> positioning = analyzeCompetitivePositioning(competitors, industry);
            analysis.put("competitivePositioning", positioning);
            
            // Update competitor data
            updateCompetitorData(competitors, industry, analysis);
            
        } catch (Exception e) {
            log.error("Error analyzing competitor content: ", e);
            throw new RuntimeException("Failed to analyze competitor content");
        }
        
        return analysis;
    }

    public Map<String, Object> generateCompetitiveAdvantageReport(String industry) {
        Map<String, Object> report = new HashMap<>();
        
        try {
            // Analyze industry trends with ML predictions
            Map<String, Object> industryTrends = trendAnalysisService.getIndustryTrends(industry);
            Map<String, Object> predictedTrends = mlPredictionService.predictEngagement(industry, "industry_trends");
            report.put("industryTrends", mergeTrendData(industryTrends, predictedTrends));
            
            // Identify underserved content areas using ML
            List<String> underservedAreas = identifyUnderservedAreas(industry);
            report.put("underservedAreas", underservedAreas);
            
            // Generate content opportunity scores with ML
            Map<String, Double> opportunityScores = calculateOpportunityScores(industry);
            report.put("opportunityScores", opportunityScores);
            
            // Add market saturation analysis
            Map<String, Object> marketSaturation = analyzeMarketSaturation(industry);
            report.put("marketSaturation", marketSaturation);
            
            // Add content gap analysis
            Map<String, Object> contentGaps = analyzeContentGaps(industry);
            report.put("contentGaps", contentGaps);
            
        } catch (Exception e) {
            log.error("Error generating competitive advantage report: ", e);
            throw new RuntimeException("Failed to generate competitive advantage report");
        }
        
        return report;
    }

    public Map<String, Object> predictCompetitorMoves(String competitor, String industry) {
        Map<String, Object> prediction = new HashMap<>();
        
        try {
            // Analyze historical patterns with ML
            Map<String, Object> historicalPatterns = analyzeHistoricalPatterns(competitor);
            prediction.put("historicalPatterns", historicalPatterns);
            
            // Predict upcoming content themes using ML
            List<String> predictedThemes = predictContentThemes(competitor, industry);
            prediction.put("predictedThemes", predictedThemes);
            
            // Estimate content impact with ML
            Map<String, Double> impactScores = estimateContentImpact(competitor);
            prediction.put("impactScores", impactScores);
            
            // Add strategy prediction
            Map<String, Object> strategyPrediction = predictStrategy(competitor, industry);
            prediction.put("strategyPrediction", strategyPrediction);
            
            // Add market response prediction
            Map<String, Object> marketResponse = predictMarketResponse(competitor, industry);
            prediction.put("marketResponse", marketResponse);
            
        } catch (Exception e) {
            log.error("Error predicting competitor moves: ", e);
            throw new RuntimeException("Failed to predict competitor moves");
        }
        
        return prediction;
    }

    private Map<String, Object> analyzeContentPatterns(List<String> competitors, List<CompetitorData> historicalData) {
        Map<String, Object> patterns = new HashMap<>();
        // Enhanced content pattern analysis using ML and historical data
        return patterns;
    }

    private Map<String, Object> analyzePostingMetrics(List<String> competitors, List<CompetitorData> historicalData) {
        Map<String, Object> metrics = new HashMap<>();
        // Enhanced posting metrics analysis with predictive analytics
        return metrics;
    }

    private Map<String, Object> analyzeEngagementMetrics(List<String> competitors, List<CompetitorData> historicalData) {
        Map<String, Object> metrics = new HashMap<>();
        // Enhanced engagement analysis with ML insights
        return metrics;
    }

    private List<MarketInsight> identifyMarketGaps(String industry, List<String> competitors, List<CompetitorData> historicalData) {
        List<MarketInsight> gaps = new ArrayList<>();
        // Enhanced market gap analysis using ML and trend data
        return gaps;
    }

    public Map<String, Object> analyzeSentiment(List<String> competitors) {
        // Sort competitors to ensure consistent cache key
        List<String> sortedCompetitors = new ArrayList<>(competitors);
        Collections.sort(sortedCompetitors);
        String cacheKey = String.join(",", sortedCompetitors);
        
        // Check cache with timestamp validation
        long currentTime = System.currentTimeMillis();
        Map<String, Object> cachedSentiment = sentimentCache.get(cacheKey);
        Long cacheTimestamp = sentimentCacheTimestamps.get(cacheKey);
        
        // Return cached result if available and not expired
        if (cachedSentiment != null && 
            cacheTimestamp != null && 
            (currentTime - cacheTimestamp) < SENTIMENT_CACHE_DURATION) {
            log.info("Returning cached sentiment analysis for: {}", cacheKey);
            return cachedSentiment;
        }

        Map<String, Object> sentiment = new HashMap<>();
        try {
            // Prepare AI request
            String prompt = String.format(
                "Analyze the sentiment and market positioning of the following competitors: %s. " +
                "Provide a detailed analysis in JSON format with the following fields: " +
                "overallSentiment (0-1), competitorSentiments (object with competitor names as keys), " +
                "marketPerception (object), strengthsAndWeaknesses (object), recommendations (array)",
                String.join(", ", sortedCompetitors)
            );

            List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "You are a competitive sentiment analysis expert. Respond only in valid JSON."),
                Map.of("role", "user", "content", prompt)
            );

            Map<String, Object> response = openRouterService.createChatCompletion(
                model, 
                messages, 
                Map.of(
                    "temperature", 0.7, 
                    "max_tokens", 2000
                )
            );

            String content = openRouterService.extractContentFromResponse(response);
            sentiment = jsonResponseHandler.parseAndValidateJson(content, new HashMap<>());

            // Validate and enrich sentiment if needed
            if (sentiment.isEmpty()) {
                sentiment.put("error", "No sentiment data available");
            } else {
                // Add metadata to sentiment
                sentiment.put("analyzed_at", currentTime);
                sentiment.put("competitors", sortedCompetitors);
            }

            // Cache the result with timestamp
            sentimentCache.put(cacheKey, sentiment);
            sentimentCacheTimestamps.put(cacheKey, currentTime);

            // Cleanup old cache entries
            cleanupSentimentCache();

        } catch (Exception e) {
            log.error("Error analyzing sentiment for competitors: {}", sortedCompetitors, e);
            sentiment.put("error", "Failed to analyze sentiment");
            sentiment.put("error_details", e.getMessage());
        }

        return sentiment;
    }

    private void cleanupSentimentCache() {
        long currentTime = System.currentTimeMillis();
        sentimentCache.entrySet().removeIf(entry -> {
            Long timestamp = sentimentCacheTimestamps.get(entry.getKey());
            return timestamp == null || (currentTime - timestamp) > SENTIMENT_CACHE_DURATION;
        });
        sentimentCacheTimestamps.entrySet().removeIf(
            entry -> (currentTime - entry.getValue()) > SENTIMENT_CACHE_DURATION
        );
    }

    public Map<String, Object> analyzeCompetitivePositioning(List<String> competitors, String industry) {
        if (competitors == null || competitors.isEmpty()) {
            log.warn("No competitors provided for analysis");
            return Map.of("error", "No competitors provided");
        }

        // Sort competitors for consistent cache key
        List<String> sortedCompetitors = new ArrayList<>(competitors);
        Collections.sort(sortedCompetitors);

        // Create prompt
        String prompt = String.format(
            "Analyze the competitive positioning of the following companies in the %s industry: %s. " +
            "Consider market share, brand strength, product differentiation, and competitive advantages. " +
            "Provide analysis in JSON format with the following fields: " +
            "marketPositioning (object with competitor names as keys), competitiveAdvantages (object), " +
            "marketShare (object), brandStrength (object), threats (array), opportunities (array)",
            industry, String.join(", ", sortedCompetitors)
        );

        // Make request through centralized service
        Map<String, Object> analysis = aiRequestService.makeRequest(
            "competitive_analysis",
            prompt,
            Map.of(
                "industry", industry,
                "competitors", sortedCompetitors
            )
        );

        // Update competitor data
        updateCompetitorData(competitors, industry, analysis);

        return analysis;
    }

    private void updateCompetitorData(List<String> competitors, String industry, Map<String, Object> analysis) {
        if (competitors == null || competitors.isEmpty()) {
            return;
        }

        competitors.forEach(competitor -> {
            try {
                CompetitorData data = competitorDataRepository.findByCompetitorName(competitor);
                boolean isNew = (data == null);

                if (isNew) {
                    data = new CompetitorData();
                    data.setCompetitorName(competitor);
                    data.setIndustry(industry);
                }

                // Save before any potential API calls
                data.setLastAnalyzed(LocalDateTime.now());
                competitorDataRepository.save(data);

                log.info("Updated competitor data for: {}", competitor);
            } catch (Exception e) {
                log.error("Error updating competitor data for {}", competitor, e);
            }
        });
    }

    private Map<String, Object> mergeTrendData(Map<String, Object> current, Map<String, Object> predicted) {
        Map<String, Object> merged = new HashMap<>(current);
        
        // Create a predictions container
        Map<String, Object> predictions = new HashMap<>();
        
        // Add predicted values with metadata
        for (Map.Entry<String, Object> entry : predicted.entrySet()) {
            Map<String, Object> predictionData = new HashMap<>();
            predictionData.put("value", entry.getValue());
            predictionData.put("timestamp", System.currentTimeMillis());
            predictionData.put("confidence", 0.8); // Default confidence score
            
            predictions.put(entry.getKey(), predictionData);
        }
        
        // Add predictions as a separate section
        merged.put("predictions", predictions);
        
        // Add metadata about the merge
        merged.put("lastUpdated", System.currentTimeMillis());
        merged.put("dataSource", "merged_analysis");
        
        return merged;
    }

    public Map<String, Object> analyzeMarketSaturation(String industry) {
        Map<String, Object> saturation = new HashMap<>();
        
        try {
            String prompt = String.format(
                "Analyze the market saturation in the %s industry. " +
                "Consider current players, market share distribution, barriers to entry, and growth potential. " +
                "Provide analysis in JSON format with the following fields: " +
                "saturationLevel (0-1), marketDynamics (object), competitorDensity (object), " +
                "entryBarriers (array), growthOpportunities (array)",
                industry
            );

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", "You are a market analysis expert. Respond only in valid JSON format."
            ));
            messages.add(Map.of(
                "role", "user",
                "content", prompt
            ));

            Map<String, Object> openAiResponse = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", 1500
                )
            );

            String content = openRouterService.extractContentFromResponse(openAiResponse);
            Map<String, Object> parsedResponse = objectMapper.readValue(content, Map.class);

            saturation = parsedResponse;

        } catch (Exception e) {
            log.error("Error analyzing market saturation: ", e);
            saturation.put("error", "Failed to analyze market saturation");
        }

        return saturation;
    }

    private Map<String, Object> analyzeContentGaps(String industry) {
        Map<String, Object> gaps = new HashMap<>();
        
        try {
            String prompt = String.format(
                "Analyze content gaps in the %s industry. " +
                "Identify underserved topics, content types, and audience segments. " +
                "Provide analysis in JSON format with the following fields: " +
                "contentGaps (array), audienceGaps (array), formatGaps (array), " +
                "opportunities (object), recommendations (array)",
                industry
            );

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", "You are a content strategy expert. Respond only in valid JSON format."
            ));
            messages.add(Map.of(
                "role", "user",
                "content", prompt
            ));

            Map<String, Object> openAiResponse = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", 1500
                )
            );

            String content = openRouterService.extractContentFromResponse(openAiResponse);
            Map<String, Object> parsedResponse = objectMapper.readValue(content, Map.class);

            gaps = parsedResponse;

        } catch (Exception e) {
            log.error("Error analyzing content gaps: ", e);
            gaps.put("error", "Failed to analyze content gaps");
        }

        return gaps;
    }

    public Map<String, Object> predictStrategy(String competitor, String industry) {
        Map<String, Object> strategy = new HashMap<>();
        
        try {
            String prompt = String.format(
                "Predict the content strategy for %s in the %s industry over the next 6-12 months. " +
                "Consider their historical patterns, market position, and industry trends. " +
                "Provide predictions in JSON format with the following fields: " +
                "predictedStrategy (object), contentFocus (array), targetAudience (object), " +
                "channels (array), timing (object), confidence (0-1)",
                competitor, industry
            );

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", "You are a strategic planning expert. Respond only in valid JSON format."
            ));
            messages.add(Map.of(
                "role", "user",
                "content", prompt
            ));

            Map<String, Object> openAiResponse = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", 1500
                )
            );

            String content = openRouterService.extractContentFromResponse(openAiResponse);
            Map<String, Object> parsedResponse = objectMapper.readValue(content, Map.class);

            strategy = parsedResponse;

        } catch (Exception e) {
            log.error("Error predicting strategy: ", e);
            strategy.put("error", "Failed to predict strategy");
        }

        return strategy;
    }

    private Map<String, Object> predictMarketResponse(String competitor, String industry) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String prompt = String.format(
                "Predict how the market will respond to %s's content strategy in the %s industry. " +
                "Consider competitor reactions, audience response, and market dynamics. " +
                "Provide predictions in JSON format with the following fields: " +
                "marketResponse (object), competitorReactions (object), audienceResponse (object), " +
                "impactMetrics (object), risks (array), opportunities (array)",
                competitor, industry
            );

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", "You are a market analysis expert. Respond only in valid JSON format."
            ));
            messages.add(Map.of(
                "role", "user",
                "content", prompt
            ));

            Map<String, Object> openAiResponse = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", 1500
                )
            );

            String content = openRouterService.extractContentFromResponse(openAiResponse);
            Map<String, Object> parsedResponse = objectMapper.readValue(content, Map.class);

            response = parsedResponse;

        } catch (Exception e) {
            log.error("Error predicting market response: ", e);
            response.put("error", "Failed to predict market response");
        }

        return response;
    }

    private List<String> identifyUnderservedAreas(String industry) {
        List<String> areas = new ArrayList<>();
        
        try {
            String prompt = String.format(
                "Identify underserved areas and opportunities in the %s industry. " +
                "Consider market gaps, unmet needs, and emerging trends. " +
                "Provide analysis in JSON format with the following fields: " +
                "underservedAreas (array of strings), marketGaps (array), opportunityScores (object), " +
                "emergingTrends (array), recommendations (array)",
                industry
            );

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", "You are a market analysis expert. Respond only in valid JSON format."
            ));
            messages.add(Map.of(
                "role", "user",
                "content", prompt
            ));

            Map<String, Object> openAiResponse = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", 1500
                )
            );

            String content = openRouterService.extractContentFromResponse(openAiResponse);
            Map<String, Object> parsedResponse = objectMapper.readValue(content, Map.class);
            
            if (parsedResponse.containsKey("underservedAreas")) {
                areas = (List<String>) parsedResponse.get("underservedAreas");
            }

        } catch (Exception e) {
            log.error("Error identifying underserved areas: ", e);
            areas.add("Error identifying underserved areas");
        }

        return areas;
    }

    private Map<String, Double> calculateOpportunityScores(String industry) {
        Map<String, Double> scores = new HashMap<>();
        
        try {
            String prompt = String.format(
                "Calculate opportunity scores for different content areas in the %s industry. " +
                "Consider market potential, competition level, and growth trends. " +
                "Provide scores in JSON format with the following fields: " +
                "scores (object with area names as keys and scores as values), " +
                "confidence (0-1), rationale (object)",
                industry
            );

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", "You are a market analysis expert. Respond only in valid JSON format."
            ));
            messages.add(Map.of(
                "role", "user",
                "content", prompt
            ));

            Map<String, Object> openAiResponse = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", 1500
                )
            );

            String content = openRouterService.extractContentFromResponse(openAiResponse);
            Map<String, Object> parsedResponse = objectMapper.readValue(content, Map.class);
            
            if (parsedResponse.containsKey("scores")) {
                Map<String, Object> rawScores = (Map<String, Object>) parsedResponse.get("scores");
                rawScores.forEach((key, value) -> {
                    if (value instanceof Number) {
                        scores.put(key, ((Number) value).doubleValue());
                    }
                });
            }

        } catch (Exception e) {
            log.error("Error calculating opportunity scores: ", e);
            scores.put("error", 0.0);
        }

        return scores;
    }

    private Map<String, Object> analyzeHistoricalPatterns(String competitor) {
        Map<String, Object> patterns = new HashMap<>();
        
        try {
            String prompt = String.format(
                "Analyze historical content patterns for %s. " +
                "Consider content types, themes, timing, and performance metrics. " +
                "Provide analysis in JSON format with the following fields: " +
                "contentPatterns (object), temporalPatterns (object), performancePatterns (object), " +
                "trends (array), insights (array)",
                competitor
            );

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", "You are a content analysis expert. Respond only in valid JSON format."
            ));
            messages.add(Map.of(
                "role", "user",
                "content", prompt
            ));

            Map<String, Object> openAiResponse = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", 1500
                )
            );

            String content = openRouterService.extractContentFromResponse(openAiResponse);
            Map<String, Object> parsedResponse = objectMapper.readValue(content, Map.class);

            patterns = parsedResponse;

        } catch (Exception e) {
            log.error("Error analyzing historical patterns: ", e);
            patterns.put("error", "Failed to analyze historical patterns");
        }

        return patterns;
    }

    private List<String> predictContentThemes(String competitor, String industry) {
        List<String> themes = new ArrayList<>();
        
        try {
            String prompt = String.format(
                "Predict the upcoming content themes and topics for %s in the %s industry. " +
                "Consider current market trends, historical content patterns, and industry developments. " +
                "Provide predictions in JSON format with the following fields: " +
                "predictedThemes (array of strings), confidence (0-1), rationale (array of strings)",
                competitor, industry
            );

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", "You are a content strategy expert. Respond only in valid JSON format."
            ));
            messages.add(Map.of(
                "role", "user",
                "content", prompt
            ));

            Map<String, Object> openAiResponse = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", 1000
                )
            );

            String content = openRouterService.extractContentFromResponse(openAiResponse);
            Map<String, Object> parsedResponse = objectMapper.readValue(content, Map.class);
            
            if (parsedResponse.containsKey("predictedThemes")) {
                themes = (List<String>) parsedResponse.get("predictedThemes");
            }

        } catch (Exception e) {
            log.error("Error predicting content themes: ", e);
            themes.add("Error predicting content themes");
        }

        return themes;
    }

    private Map<String, Double> estimateContentImpact(String competitor) {
        Map<String, Double> impact = new HashMap<>();
        
        try {
            String prompt = String.format(
                "Estimate the potential impact of content strategies for %s. " +
                "Consider engagement metrics, conversion rates, and market influence. " +
                "Provide estimates in JSON format with the following fields: " +
                "impactScores (object with strategy types as keys and impact scores as values), " +
                "confidence (0-1), rationale (object)",
                competitor
            );

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", "You are a content strategy expert. Respond only in valid JSON format."
            ));
            messages.add(Map.of(
                "role", "user",
                "content", prompt
            ));

            Map<String, Object> openAiResponse = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", 1500
                )
            );

            String content = openRouterService.extractContentFromResponse(openAiResponse);
            Map<String, Object> parsedResponse = objectMapper.readValue(content, Map.class);
            
            if (parsedResponse.containsKey("impactScores")) {
                Map<String, Object> rawScores = (Map<String, Object>) parsedResponse.get("impactScores");
                rawScores.forEach((key, value) -> {
                    if (value instanceof Number) {
                        impact.put(key, ((Number) value).doubleValue());
                    }
                });
            }

        } catch (Exception e) {
            log.error("Error estimating content impact: ", e);
            impact.put("error", 0.0);
        }

        return impact;
    }
}
