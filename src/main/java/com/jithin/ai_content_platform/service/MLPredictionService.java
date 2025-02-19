package com.jithin.ai_content_platform.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.util.JsonResponseHandler;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MLPredictionService {
    
    @Autowired
    private OpenRouterService openRouterService;
    
    @Value("${openai.model}")
    private String model;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private JsonResponseHandler jsonResponseHandler;

    @Autowired
private EnhancedWord2VecService word2VecService;

    @Value("${ml.model.engagement.weights}")
    private String engagementWeights;

    @Value("${ml.model.content.performance}")
    private String contentPerformance;

    private Map<String, Double> modelWeights;
    private boolean modelsInitialized = false;

    @PostConstruct
    public void init() {
        try {
            // Initialize model weights
            modelWeights = new HashMap<>();
            String[] weights = engagementWeights.split(",");
            modelWeights.put("content", Double.parseDouble(weights[0]));
            modelWeights.put("audience", Double.parseDouble(weights[1]));
            modelWeights.put("timing", Double.parseDouble(weights[2]));
            
            modelsInitialized = true;
            log.info("ML models initialized successfully");
        } catch (Exception e) {
            log.error("Error initializing ML models: {}", e.getMessage());
            // Set default weights if initialization fails
            modelWeights = new HashMap<>();
            modelWeights.put("content", 0.4);
            modelWeights.put("audience", 0.3);
            modelWeights.put("timing", 0.3);
        }
    }
    
    public Map<String, Object> analyzeSensitivity(String content, String region) {
        log.info("Analyzing cultural sensitivity for region: {}", region);
        
        Map<String, Object> analysis = new HashMap<>();
        
        try {
            // Analyze content for cultural sensitivity using OpenRouter
            String prompt = String.format(
                "You are a JSON response generator for cultural sensitivity analysis. Analyze the content and return ONLY a JSON object.\n\n" +
                "CRITICAL INSTRUCTIONS:\n" +
                "1. ONLY output a valid JSON object\n" +
                "2. DO NOT include any explanations\n" +
                "3. DO NOT use markdown formatting\n" +
                "4. DO NOT include any text before or after the JSON\n\n" +
                "Required JSON structure:\n" +
                "{\n" +
                "  \"sensitivityScore\": (number 0-1, overall cultural sensitivity),\n" +
                "  \"warnings\": [array of specific cultural sensitivity concerns],\n" +
                "  \"suggestions\": [array of actionable improvements],\n" +
                "  \"confidence\": (number 0-1, confidence in analysis)\n" +
                "}\n\n" +
                "Region: %s\n" +
                "Content: %s",
                region, content
            );
            
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", "You are a JSON-only response system. Never include explanations or natural language. Only output valid JSON objects."
            ));
            messages.add(Map.of(
                "role", "user",
                "content", prompt
            ));
            
            Map<String, Object> response = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.2,  // Lower temperature for more consistent output
                    "max_tokens", 1000   // Increased limit to avoid truncation
                )
            );
            
            String responseContent = openRouterService.extractContentFromResponse(response);
            
            // First try to clean and parse using JsonResponseHandler
            Map<String, Object> defaultResponse = new HashMap<>();
            defaultResponse.put("sensitivityScore", 0.5);
            defaultResponse.put("warnings", Arrays.asList("Unable to perform sensitivity analysis"));
            defaultResponse.put("suggestions", Arrays.asList("Please try again or contact support if the issue persists"));
            defaultResponse.put("confidence", 0.0);

            // Try JsonResponseHandler first for complete JSON
            analysis = jsonResponseHandler.parseAndValidateJson(responseContent, defaultResponse);

            // If that fails or returns default values, try regex extraction
            if (analysis.equals(defaultResponse)) {
                Map<String, Object> extractedMetrics = extractSensitivityMetrics(responseContent);
                if (!extractedMetrics.isEmpty()) {
                    analysis = extractedMetrics;
                }
            }
            
        } catch (Exception e) {
            log.error("Error analyzing cultural sensitivity: {}", e.getMessage());
            // Return a safe fallback response instead of throwing
            analysis.put("sensitivityScore", 0.5);
            analysis.put("warnings", Arrays.asList("Unable to perform sensitivity analysis"));
            analysis.put("suggestions", Arrays.asList("Please try again or contact support if the issue persists"));
            analysis.put("confidence", 0.0);
        }
        
        return analysis;
    }
    
    /**
     * Predicts engagement metrics for content including score, confidence, and contributing factors
     * @param content The content to analyze
     * @return Map containing engagement prediction details
     */
    public Map<String, Object> predictEngagementMetrics(Content content) {
        if (!modelsInitialized) {
            log.warn("ML models not initialized, using fallback prediction");
            return getFallbackPrediction();
        }
        return predictEngagementDetailed(content.getContentBody(), content.getRegion());
    }

    public Map<String, Object> predictEngagementMetrics(String content, Map<String, Object> metadata) {
        if (!modelsInitialized) {
            log.warn("ML models not initialized, using fallback prediction");
            return getFallbackPrediction();
        }
        String region = metadata.containsKey("region") ? (String) metadata.get("region") : "global";
        return predictEngagementDetailed(content, region);
    }

    private Map<String, Object> getFallbackPrediction() {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("score", 0.5);
        fallback.put("confidence", 0.5);
        fallback.put("factors", Arrays.asList("Using fallback prediction due to model initialization issue"));
        return fallback;
    }

    /**
     * Extracts sensitivity metrics from the response using regex patterns
     * @param response The response content to parse
     * @return Map containing extracted metrics or empty map if extraction fails
     */
    private Map<String, Object> extractSensitivityMetrics(String response) {
        Map<String, Object> metrics = new HashMap<>();
        try {
            // Patterns for numeric values with proper decimal handling
            Pattern scorePattern = Pattern.compile("\"sensitivityScore\"\\s*:\\s*(-?[0-9]+(\\.[0-9]+)?)");
            Pattern confidencePattern = Pattern.compile("\"confidence\"\\s*:\\s*(-?[0-9]+(\\.[0-9]+)?)");
            
            // Improved array patterns that handle nested objects, arrays, and various value types
            String arrayContentPattern = "(?:\"(?:[^\"\\\\]|\\\\.)*\"|[0-9.]+|true|false|null|\\{[^{}]*\\})";
            Pattern warningsPattern = Pattern.compile("\"warnings\"\\s*:\\s*\\[\\s*(" + arrayContentPattern + "\\s*(?:,\\s*" + arrayContentPattern + ")*)?\\s*\\]");
            Pattern suggestionsPattern = Pattern.compile("\"suggestions\"\\s*:\\s*\\[\\s*(" + arrayContentPattern + "\\s*(?:,\\s*" + arrayContentPattern + ")*)?\\s*\\]");

            Matcher scoreMatcher = scorePattern.matcher(response);
            Matcher confidenceMatcher = confidencePattern.matcher(response);
            Matcher warningsMatcher = warningsPattern.matcher(response);
            Matcher suggestionsMatcher = suggestionsPattern.matcher(response);

            if (scoreMatcher.find()) {
                metrics.put("sensitivityScore", Double.parseDouble(scoreMatcher.group(1)));
            }
            if (confidenceMatcher.find()) {
                metrics.put("confidence", Double.parseDouble(confidenceMatcher.group(1)));
            }
            if (warningsMatcher.find()) {
                String warningsStr = warningsMatcher.group(1);
                List<String> warnings = Arrays.stream(warningsStr.split(","))
                    .map(s -> s.trim().replaceAll("^\"|\"$", ""))
                    .collect(Collectors.toList());
                metrics.put("warnings", warnings);
            }
            if (suggestionsMatcher.find()) {
                String suggestionsStr = suggestionsMatcher.group(1);
                List<String> suggestions = Arrays.stream(suggestionsStr.split(","))
                    .map(s -> s.trim().replaceAll("^\"|\"$", ""))
                    .collect(Collectors.toList());
                metrics.put("suggestions", suggestions);
            }
        } catch (Exception e) {
            log.debug("Error extracting sensitivity metrics: {}", e.getMessage());
            return new HashMap<>();
        }
        return metrics;
    }

    private Map<String, Object> extractEngagementMetrics(String response) {
        Map<String, Object> metrics = new HashMap<>();
        try {
            // Patterns for numeric values with proper decimal handling
            Pattern scorePattern = Pattern.compile("\"score\"\\s*:\\s*(-?[0-9]+(\\.[0-9]+)?)");
            Pattern confidencePattern = Pattern.compile("\"confidence\"\\s*:\\s*(-?[0-9]+(\\.[0-9]+)?)");
            
            // Improved array pattern that handles nested objects, arrays, and various value types
            String arrayContentPattern = "(?:\"(?:[^\"\\\\]|\\\\.)*\"|[0-9.]+|true|false|null|\\{[^{}]*\\})";
            Pattern factorsPattern = Pattern.compile("\"factors\"\\s*:\\s*\\[\\s*(" + arrayContentPattern + "\\s*(?:,\\s*" + arrayContentPattern + ")*)?\\s*\\]");

            Matcher scoreMatcher = scorePattern.matcher(response);
            Matcher confidenceMatcher = confidencePattern.matcher(response);
            Matcher factorsMatcher = factorsPattern.matcher(response);

            if (scoreMatcher.find()) {
                metrics.put("score", Double.parseDouble(scoreMatcher.group(1)));
            }
            if (confidenceMatcher.find()) {
                metrics.put("confidence", Double.parseDouble(confidenceMatcher.group(1)));
            }
            if (factorsMatcher.find()) {
                String factorsStr = factorsMatcher.group(1);
                if (factorsStr != null && !factorsStr.trim().isEmpty()) {
                    List<String> factors = Arrays.stream(factorsStr.split("\\s*,\\s*"))
                        .map(String::trim)
                        .map(s -> s.replaceAll("^\"|\"|\\\\\"|\"", "")) // Handle escaped quotes
                        .filter(s -> !s.isEmpty())
                        .filter(s -> !s.equals("null")) // Filter out null values
                        .collect(Collectors.toList());
                    if (!factors.isEmpty()) {
                        metrics.put("factors", factors);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting engagement metrics: {}", e.getMessage());
            return new HashMap<>();
        }
        return metrics;
    }

    private Map<String, Object> predictEngagementDetailed(String content, String region) {
        log.info("Predicting engagement metrics for region: {}", region);
        
        Map<String, Object> predictions = new HashMap<>();
        
        try {
            String prompt = String.format(
                "You are a JSON response generator. Analyze the content and return ONLY a JSON object with engagement metrics.\n\n" +
                "CRITICAL INSTRUCTIONS:\n" +
                "1. ONLY output a valid JSON object\n" +
                "2. DO NOT include any explanations\n" +
                "3. DO NOT use markdown formatting\n" +
                "4. DO NOT include any text before or after the JSON\n\n" +
                "Required JSON structure:\n" +
                "{\n" +
                "  \"score\": (number 0-1, predicted engagement score),\n" +
                "  \"confidence\": (number 0-1, confidence in prediction),\n" +
                "  \"factors\": [strings explaining key factors]\n" +
                "}\n\n" +
                "Region: %s\n" +
                "Content: %s",
                region, content
            );
            
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", "You are a JSON-only response system. Never include explanations or natural language. Only output valid JSON objects."
            ));
            messages.add(Map.of(
                "role", "user",
                "content", prompt
            ));
            
            Map<String, Object> response = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.2, // Lower temperature for more consistent output
                    "max_tokens", 1000   // Increased limit to avoid truncation
                )
            );
            
            String responseContent = openRouterService.extractContentFromResponse(response);
            
            // First try to clean and parse using JsonResponseHandler
            Map<String, Object> defaultPredictions = new HashMap<>();
            defaultPredictions.put("score", 0.5);
            defaultPredictions.put("confidence", 0.5);
            defaultPredictions.put("factors", Arrays.asList("Error occurred during prediction"));

            // Try JsonResponseHandler first for complete JSON
            predictions = jsonResponseHandler.parseAndValidateJson(responseContent, defaultPredictions);

            // If that fails or returns default values, try regex extraction
            if (predictions.equals(defaultPredictions)) {
                Map<String, Object> extractedMetrics = extractEngagementMetrics(responseContent);
                if (!extractedMetrics.isEmpty()) {
                    predictions = extractedMetrics;
                }
            }
            
        } catch (Exception e) {
            log.error("Error predicting engagement: {}", e.getMessage());
            predictions.put("score", 0.5);
            predictions.put("confidence", 0.5);
            predictions.put("factors", Arrays.asList("Error occurred during prediction"));
        }
        
        return predictions;
    }
    
    public Map<String, Object> predictEngagement(String target, String analysisType) {
        log.info("Predicting engagement for {} with analysis type: {}", target, analysisType);
        
        if (!modelsInitialized) {
            log.warn("Models not initialized. Using fallback prediction.");
            return getFallbackPrediction();
        }
        
        Map<String, Object> predictions = new HashMap<>();
        
        try {
            // Apply model weights for better prediction accuracy
            double weightedScore = modelWeights.getOrDefault("engagement", 0.5);
            String prompt = String.format(
                "Predict engagement metrics and trends for %s based on %s. " +
                "Consider historical performance patterns and current market dynamics. " +
                "Provide predictions in JSON format with the following fields: " +
                "engagementScore (0-1), predictedTrends (array), growthPotential (0-1), " +
                "recommendedActions (array), confidence (0-1)",
                target, analysisType
            );
            
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", "You are an engagement prediction analyzer. Respond only in valid JSON format."
            ));
            messages.add(Map.of(
                "role", "user",
                "content", prompt
            ));
            
            Map<String, Object> response = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", 1500
                )
            );
            
            String responseContent = openRouterService.extractContentFromResponse(response);
            
            // Use JsonResponseHandler to parse and validate the response
            Map<String, Object> defaultPredictions = new HashMap<>();
            defaultPredictions.put("score", 0.5);
            defaultPredictions.put("confidence", 0.0);
            defaultPredictions.put("factors", Arrays.asList("Unable to generate engagement prediction"));
            
            predictions = jsonResponseHandler.parseAndValidateJson(responseContent, defaultPredictions);
            
            // Apply weighted score if available
            if (predictions.containsKey("engagementScore")) {
                double originalScore = ((Number) predictions.get("engagementScore")).doubleValue();
                predictions.put("engagementScore", originalScore * weightedScore);
            }
            
            // Add model weight information
            predictions.put("modelWeight", weightedScore);
            
        } catch (Exception e) {
            log.error("Error predicting engagement: {}", e.getMessage());
            return getFallbackPrediction();
        }
        
        return predictions;
    }
    
    public List<String> generateOptimizationSuggestions(String content, String region) {
        log.info("Generating optimization suggestions for region: {}", region);
        
        List<String> suggestions = new ArrayList<>();
        
        try {
            String prompt = String.format(
                "Generate optimization suggestions for the following content in %s region. " +
                "Consider SEO, engagement, cultural relevance, and conversion optimization. " +
                "Provide suggestions in JSON format with the following fields: " +
                "suggestions (array of strings), priorityLevel (array of numbers 1-5), " +
                "impact (array of strings), effort (array of strings)\n\n%s",
                region, content
            );
            
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", "You are a content optimization expert. Respond only in valid JSON format."
            ));
            messages.add(Map.of(
                "role", "user",
                "content", prompt
            ));
            
            Map<String, Object> response = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", 1500
                )
            );
            
            String responseContent = openRouterService.extractContentFromResponse(response);
            Map<String, Object> result = objectMapper.readValue(responseContent, Map.class);
            
            if (result.containsKey("suggestions")) {
                suggestions = (List<String>) result.get("suggestions");
            }
            
        } catch (Exception e) {
            log.error("Error generating optimization suggestions: {}", e.getMessage());
            suggestions.add("Error generating optimization suggestions");
        }
        
        return suggestions;
    }

    public Map<String, Object> predictContentPerformance(Content content) {
        try {
            // Prepare prompt for prediction
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("Analyze the following content and provide performance predictions:\n\n");
            promptBuilder.append("Content Title: ").append(content.getTitle()).append("\n");
            promptBuilder.append("Content Body: ").append(content.getContentBody()).append("\n\n");
            
            // Add trend data if available
            if (content.getTrendData() != null) {
                promptBuilder.append("Trend Analysis: ").append(content.getTrendData()).append("\n");
            }
            
            // Add sentiment data if available
            if (content.getAnalyzedSentiment() != null) {
                promptBuilder.append("Sentiment Analysis: ").append(content.getAnalyzedSentiment()).append("\n");
            }
            
            promptBuilder.append("\nProvide predictions for:\n");
            promptBuilder.append("1. Engagement score (0-1)\n");
            promptBuilder.append("2. Virality potential (0-1)\n");
            promptBuilder.append("3. Relevance score (0-1)\n");
            promptBuilder.append("4. Recommendations for improvement\n");
            
            // Create chat completion request
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", "You are an AI specialized in content performance prediction."
            ));
            messages.add(Map.of(
                "role", "user",
                "content", promptBuilder.toString()
            ));
            
            // Get prediction from OpenRouter
            Map<String, Object> response = openRouterService.createChatCompletion(
                model,
                messages,
                null
            );
            
            // Extract predictions from response
            String predictionText = openRouterService.extractContentFromResponse(response);
            Map<String, Object> predictions = jsonResponseHandler.parseAndValidateJson(
                predictionText,
                Map.of(
                    "engagement", 0.0,
                    "virality", 0.0,
                    "relevance", 0.0,
                    "recommendations", new ArrayList<String>()
                )
            );
            
            log.info("Generated predictions for content ID: {}", content.getId());
            return predictions;
            
        } catch (Exception e) {
            log.error("Error predicting content performance for content ID: {}", content.getId(), e);
            throw new RuntimeException("Failed to predict content performance", e);
        }
    }
    
    private double calculateEngagement(Content content, Map<String, Object> trendData, Map<String, Object> sentimentData) {
        double trendScore = (double) trendData.getOrDefault("relevance", 0.5);
        double sentimentScore = (double) sentimentData.getOrDefault("overall_sentiment", 0.5);
        return (trendScore + sentimentScore) / 2;
    }
    
    private double calculateViralityScore(Content content, Map<String, Object> trendData) {
        return (double) trendData.getOrDefault("virality", 0.0);
    }
    
    private double calculateRelevanceScore(Content content, Map<String, Object> trendData, Map<String, Object> sentimentData) {
        double trendRelevance = (double) trendData.getOrDefault("relevance", 0.5);
        double sentimentConfidence = (double) sentimentData.getOrDefault("confidence_score", 0.5);
        return (trendRelevance + sentimentConfidence) / 2;
    }
}
