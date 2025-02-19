// src/main/java/com/jithin/ai_content_platform/controller/ContentController.java

package com.jithin.ai_content_platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.model.TrendData;
import com.jithin.ai_content_platform.model.User;
import com.jithin.ai_content_platform.payload.ContentRequest;
import com.jithin.ai_content_platform.payload.FeedbackRequest;
import com.jithin.ai_content_platform.repository.ContentRepository;
import com.jithin.ai_content_platform.repository.UserRepository;
import com.jithin.ai_content_platform.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/content")
public class ContentController {

    private static final Logger logger = LoggerFactory.getLogger(ContentController.class);

    @Autowired
    private ContentService contentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private TrendAnalysisService trendAnalysisService;

    @Autowired
    private ImageGenerationService imageGenerationService;

    @Autowired
    private ComplianceService complianceService;

    @Autowired
    private PublishingService publishingService;

    @Autowired
    private ContentStrategyService contentStrategyService;

    @Autowired
    private DynamicTrendWeightService dynamicTrendWeightService;

    @Autowired
    private PerformancePredictionService performancePredictionService;

    @Autowired
    EnhancedContentGenerationService enhancedContentGenerationService;

    @Autowired
    MLPredictionService mlPredictionService;

    @Autowired
    private CompetitorAnalysisService competitorAnalysisService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping("/generate")
    public ResponseEntity<?> generateContent(@RequestBody ContentRequest request, Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(401).body("Authentication required");
            }

            String username = authentication.getName();
            if (username == null || username.isEmpty()) {
                return ResponseEntity.status(401).body("Invalid authentication");
            }

            User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

            // Generate initial content
            Content content = contentService.generateContent(request, user);

            // Initialize metrics map if null
            if (content.getMetrics() == null) {
                content.setMetricsMap(new HashMap<>());
            }
            Map<String, Object> metrics = content.getMetricsMap();

            // Combine all predictions and analysis
            Map<String, Object> enrichedContent = new HashMap<>();
            enrichedContent.put("content", content);

            try {
                // Analyze trends
                Map<String, Double> trendWeights = dynamicTrendWeightService.calculateContentWeights(content);
                enrichedContent.put("trendAnalysis", trendWeights);
                metrics.put("trendWeights", trendWeights);
            } catch (Exception e) {
                logger.error("Error calculating trend weights: {}", e.getMessage());
                metrics.put("trendWeights", Map.of("error", "Failed to calculate trend weights"));
            }

            try {
                // Predict performance
                Map<String, Object> performancePrediction = performancePredictionService.predictContentPerformance(content);
                enrichedContent.put("performancePrediction", performancePrediction);
                metrics.put("predictedPerformance", performancePrediction);
            } catch (Exception e) {
                logger.error("Error predicting performance: {}", e.getMessage());
                metrics.put("predictedPerformance", Map.of("error", "Failed to predict performance"));
            }
                       // Prepare response map
                       Map<String, Object> response = new HashMap<>();
                       response.put("content", content.getContentBody());
                       response.put("id", content.getId());
                       response.put("status", content.getStatus());
                       
                       // Include metricsMap directly in the response
                       if (content.getMetricsMap() != null) {
                           response.put("metricsMap", content.getMetricsMap());
                       }

                    // Include other existing metrics if needed
            if (content.getMetrics() != null) {
                response.put("metrics", content.getMetrics());
            }

            try {
                // Predict engagement
                Map<String, Object> engagementPrediction = mlPredictionService.predictEngagementMetrics(content);
                enrichedContent.put("engagementPrediction", engagementPrediction);
                metrics.put("predictedEngagement", engagementPrediction);
            } catch (Exception e) {
                logger.error("Error predicting engagement: {}", e.getMessage());
                metrics.put("predictedEngagement", Map.of("error", "Failed to predict engagement"));
            }

            try {
                // Analyze sensitivity
                Map<String, Object> sensitivityAnalysis = mlPredictionService.analyzeSensitivity(
                    content.getContentBody(), 
                    content.getRegion()
                );
                enrichedContent.put("sensitivityAnalysis", sensitivityAnalysis);
                metrics.put("sensitivityAnalysis", sensitivityAnalysis);
            } catch (Exception e) {
                logger.error("Error analyzing sensitivity: {}", e.getMessage());
                metrics.put("sensitivityAnalysis", Map.of("error", "Failed to analyze sensitivity"));
            }
            
            // Add content quality analysis
           // Add content quality analysis
           try {
            String qualityAnalysisJson = performancePredictionService.analyzeContentQuality(content);
            Map<String, Object> qualityAnalysis = objectMapper.readValue(qualityAnalysisJson, new TypeReference<Map<String, Object>>() {});
            
            enrichedContent.put("contentQualityScore", qualityAnalysis.get("qualityScore"));
            enrichedContent.put("qualityAnalysis", qualityAnalysis);
        } catch (Exception e) {
            logger.warn("Could not perform detailed content quality analysis", e);
            enrichedContent.put("contentQualityScore", 0.5);
            enrichedContent.put("qualityAnalysis", Map.of("error", "Analysis failed"));
        }
            // Save the updated content with all metrics
            content.setMetricsMap(metrics);
            contentRepository.save(content);

            // Cache the prediction
            // Notify the user that content generation is complete
            messagingTemplate.convertAndSendToUser(user.getUsername(), "/queue/updates", 
                "Content generation completed with predictions and analysis.");

            return ResponseEntity.ok(enrichedContent);
        } catch (RuntimeException e) {
            return ResponseEntity.status(401)
                    .body("Authentication failed: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("Failed to generate content: " + e.getMessage());
        }
    }

    @PostMapping("/feedback")
    public ResponseEntity<?> submitFeedback(@RequestBody FeedbackRequest feedbackRequest, Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username).orElseThrow(() ->
                new RuntimeException("User not found"));

        Content content = contentRepository.findById(feedbackRequest.getContentId()).orElseThrow(() ->
                new RuntimeException("Content not found"));

        if (!content.getUser().equals(user)) {
            return ResponseEntity.status(403).body("You can only provide feedback on your own content.");
        }

        content.setRating(feedbackRequest.getRating());
        content.setComments(feedbackRequest.getComments());
        contentRepository.save(content);

        return ResponseEntity.ok("Feedback submitted successfully.");
    }

    @PostMapping("/generate-image")
    public ResponseEntity<?> generateImage(@RequestBody Map<String, String> request) {
        String prompt = request.get("prompt");
        String imageUrl = imageGenerationService.generateImage(prompt);
        return ResponseEntity.ok(imageUrl);
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validateContent(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        boolean isCompliant = complianceService.isContentCompliant(content);
        return ResponseEntity.ok(isCompliant);
    }

    @PostMapping("/predict-engagement")
    public ResponseEntity<?> predictEngagement(@RequestBody Map<String, Object> request, Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(401).body("Authentication required");
            }

            String content = (String) request.get("content");
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) request.get("metadata");

            if (content == null || content.isEmpty()) {
                return ResponseEntity.badRequest().body("Content is required");
            }

            Map<String, Object> prediction = mlPredictionService.predictEngagementMetrics(content, metadata);
            return ResponseEntity.ok(prediction);
        } catch (Exception e) {
            logger.error("Error predicting engagement: {}", e.getMessage());
            return ResponseEntity.status(500).body("Failed to predict engagement: " + e.getMessage());
        }
    }

    @PostMapping("/publish")
    public ResponseEntity<?> publishContent(@RequestBody Map<String, String> request,
                                            @AuthenticationPrincipal User user) {
        String content = request.get("content");
        String title = request.get("title");
        boolean success = publishingService.publishToWordPress(content, title, user);
        return ResponseEntity.ok(success);
    }

    @GetMapping("/user")
    public ResponseEntity<?> getUserContent(Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(401).body("Authentication required");
            }

            String username = authentication.getName();
            User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

            List<Content> contents = contentService.getUserContent(user);
            List<Map<String, Object>> enrichedContents = new ArrayList<>();
            
            // Calculate freshness score and enrich metrics for each content
            for (Content content : contents) {
                try {
                    Map<String, Object> enrichedContent = new HashMap<>();
                    // Basic content information
                    enrichedContent.put("id", content.getId());
                    enrichedContent.put("title", content.getTitle());
                    enrichedContent.put("contentBody", content.getContentBody());
                    enrichedContent.put("category", content.getCategory());
                    enrichedContent.put("topic", content.getTopic());
                    enrichedContent.put("contentType", content.getContentType());
                    enrichedContent.put("emotionalTone", content.getEmotionalTone());
                    enrichedContent.put("keywords", content.getKeywords());
                    enrichedContent.put("region", content.getRegion());
                    enrichedContent.put("writingStyle", content.getWritingStyle());
                    enrichedContent.put("optimizeForSeo", content.isOptimizeForSeo());
                    
                    // Timestamps
                    enrichedContent.put("createdAt", content.getCreatedAt());
                    enrichedContent.put("updatedAt", content.getUpdatedAt());
                    enrichedContent.put("scheduledPublishTime", content.getScheduledPublishTime());
                    
                    // Initialize or get existing metrics
                    Map<String, Object> metrics = new HashMap<>();
                    if (content.getMetrics() != null) {
                        try {
                            metrics = objectMapper.readValue(content.getMetrics(), new TypeReference<Map<String, Object>>() {});
                        } catch (JsonProcessingException e) {
                            logger.error("Error parsing metrics JSON", e);
                        }
                    }
                    
                    // Calculate freshness and engagement scores
                    double freshnessScore = content.calculateTimeDecayFactor();
                    double engagementScore = content.getEngagement() != null ? content.getEngagement() : 0.0;
                    
                    // Update metrics with new scores
                    metrics.put("freshnessScore", freshnessScore);
                    
                    Map<String, Object> engagementMetrics = new HashMap<>();
                    engagementMetrics.put("score", engagementScore);
                    engagementMetrics.put("likes", content.getLikes() != null ? content.getLikes() : 0);
                    engagementMetrics.put("shares", content.getShares() != null ? content.getShares() : 0);
                    engagementMetrics.put("comments", content.getComments());
                    metrics.put("engagement", engagementMetrics);

                    // Add trend analysis if not present
                    if (!metrics.containsKey("trendWeights")) {
                        try {
                            Map<String, Double> trendWeights = dynamicTrendWeightService.calculateContentWeights(content);
                            metrics.put("trendWeights", trendWeights);
                        } catch (Exception e) {
                            logger.error("Error calculating trend weights for content {}: {}", content.getId(), e.getMessage());
                            metrics.put("trendWeights", Map.of("error", "Failed to calculate trend weights"));
                        }
                    }

                    // Add performance prediction if not present
                    if (!metrics.containsKey("predictedPerformance")) {
                        try {
                            Map<String, Object> performancePrediction = performancePredictionService.predictContentPerformance(content);
                            metrics.put("predictedPerformance", performancePrediction);
                        } catch (Exception e) {
                            logger.error("Error predicting performance for content {}: {}", content.getId(), e.getMessage());
                            metrics.put("predictedPerformance", Map.of("error", "Failed to predict performance"));
                        }
                    }

                    // Add ML predictions if not present
                    if (!metrics.containsKey("predictedEngagement")) {
                        try {
                            Map<String, Object> engagementPrediction = mlPredictionService.predictEngagementMetrics(content);
                            metrics.put("predictedEngagement", engagementPrediction);
                        } catch (Exception e) {
                            logger.error("Error predicting engagement for content {}: {}", content.getId(), e.getMessage());
                            metrics.put("predictedEngagement", Map.of("error", "Failed to predict engagement"));
                        }
                    }

                    enrichedContent.put("metrics", metrics);
                    
                    // Additional analytics
                    enrichedContent.put("analyzedSentiment", content.getAnalyzedSentimentMap());
                    enrichedContent.put("readabilityScore", content.getReadabilityScore());
                    enrichedContent.put("trendData", content.getTrendDataMap());
                    
                    // Update content with new metrics
                    content.setMetricsMap(metrics);
                    contentRepository.save(content);
                    
                    enrichedContents.add(enrichedContent);
                } catch (Exception e) {
                    logger.error("Error enriching content {}: {}", content.getId(), e.getMessage());
                    logger.error("Stack trace:", e);
                }
            }

            return ResponseEntity.ok(enrichedContents);
        } catch (Exception e) {
            logger.error("Failed to retrieve content: {}", e.getMessage());
            logger.error("Stack trace:", e);
            return ResponseEntity.status(500)
                .body("Failed to retrieve content: " + e.getMessage());
        }
    }

}