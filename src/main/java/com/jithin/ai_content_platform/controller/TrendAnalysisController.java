package com.jithin.ai_content_platform.controller;

import com.jithin.ai_content_platform.model.TrendData;
import com.jithin.ai_content_platform.model.TrendInsight;
import com.jithin.ai_content_platform.service.TrendAnalysisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trends")
@Slf4j
public class TrendAnalysisController {

    @Autowired
    private TrendAnalysisService trendAnalysisService;

    @Autowired
    private ObjectMapper objectMapper;
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("Error in TrendAnalysisController: {}", e.getMessage(), e);
        Map<String, Object> response = new HashMap<>();
        response.put("error", e.getMessage());
        response.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getTrendingTopics() {
        log.info("Fetching current trending topics");
        try {
            List<TrendData> trends = trendAnalysisService.getTrendingTopics();
            Map<String, Object> response = new HashMap<>();
            
            response.put("trends", trends != null ? trends : Collections.emptyList());
            response.put("count", trends != null ? trends.size() : 0);
            response.put("timestamp", LocalDateTime.now());
            response.put("status", "success");
            
            if (trends == null || trends.isEmpty()) {
                response.put("message", "No trending topics available at the moment");
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching trending topics: {}", e.getMessage());
            return ResponseEntity.status(500)
                .body(Collections.singletonMap("error", "Error fetching trending topics: " + e.getMessage()));
        }
    }

    @GetMapping("/predictions")
    public ResponseEntity<List<TrendData>> getPredictedTrends() {
        log.info("Fetching trend predictions");
        List<TrendData> trendDataList = trendAnalysisService.getPredictedTrends();
        return ResponseEntity.ok(trendDataList);
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<TrendData>> getTrendsByCategory(@PathVariable String category) {
        log.info("Fetching trends for category: {}", category);
        List<TrendData> trends = trendAnalysisService.getTrendsByCategory(category);
        return ResponseEntity.ok(trends);
    }

    @GetMapping("/sentiment/{topic}")
    public ResponseEntity<Map<String, Object>> getTrendSentiment(@PathVariable String topic) {
        log.info("Analyzing sentiment for topic: {}", topic);
        try {
            TrendData trendData = trendAnalysisService.analyzeTrendSentiment(topic);
            Map<String, Object> response = new HashMap<>();
            
            if (trendData == null) {
                response.put("message", "No trend data available for topic: " + topic);
                response.put("sentiment", new HashMap<String, Double>());
                return ResponseEntity.ok(response);
            }
            
            response.put("trendScore", trendData.getTrendScore());
            
            try {
                String sentimentDistribution = trendData.getSentimentDistribution();
                Map<String, Object> sentimentMap = objectMapper.readValue(sentimentDistribution, new TypeReference<Map<String, Object>>() {});
                response.put("sentiment", sentimentMap);
            } catch (JsonProcessingException e) {
                log.error("Error processing sentiment data", e);
                response.put("sentiment", Collections.emptyMap());
            }
            
            response.put("topic", topic);
            response.put("analysisTimestamp", trendData.getAnalysisTimestamp());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error analyzing sentiment for topic {}: {}", topic, e.getMessage());
            return ResponseEntity.status(500)
                .body(Collections.singletonMap("error", "Error analyzing sentiment: " + e.getMessage()));
        }
    }

    @GetMapping("/opportunities/{keyword}")
    public ResponseEntity<Map<String, Object>> analyzeTrendOpportunities(@PathVariable String keyword) {
        log.info("Analyzing trend opportunities for keyword: {}", keyword);
        return ResponseEntity.ok(trendAnalysisService.analyzeTrendOpportunities(keyword));
    }

    @GetMapping("/insights")
    public ResponseEntity<List<TrendInsight>> getTrendInsights() {
        log.info("Fetching trend insights");
        try {
            List<TrendInsight> insights = trendAnalysisService.getTrendInsightsList();
            return ResponseEntity.ok(insights);
        } catch (Exception e) {
            log.error("Error fetching trend insights: {}", e.getMessage());
            return ResponseEntity.status(500).body(Collections.emptyList());
        }
    }

    @GetMapping("/insights/detailed")
    public ResponseEntity<TrendInsight> getDetailedTrendInsights() {
        log.info("Fetching detailed trend insights");
        try {
            TrendInsight insights = trendAnalysisService.getDetailedTrendInsights();
            return ResponseEntity.ok(insights);
        } catch (Exception e) {
            log.error("Error fetching detailed trend insights: {}", e.getMessage());
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * Compare trends between two regions
     * @param region1 First region to compare
     * @param region2 Second region to compare
     * @return Comparative analysis of trends between the two regions
     */
    @GetMapping("/compare/{region1}/{region2}")
    public ResponseEntity<Map<String, Object>> compareTrendsBetweenRegions(
            @PathVariable String region1,
            @PathVariable String region2) {
        log.info("Comparing trends between regions: {} and {}", region1, region2);
        try {
            Map<String, Object> comparison = trendAnalysisService.compareTrendsBetweenRegions(region1, region2);
            return ResponseEntity.ok(comparison);
        } catch (Exception e) {
            log.error("Error comparing trends between regions {} and {}: {}", 
                region1, region2, e.getMessage());
            return ResponseEntity.status(500)
                .body(Collections.singletonMap("error", 
                    "Error comparing trends between regions: " + e.getMessage()));
        }
    }

    @PostMapping("/analysis")
    public ResponseEntity<?> analyzeTrends(@RequestBody Map<String, Object> rawData) {
        try {
            Map<String, Map<String, Object>> trendScores = (Map<String, Map<String, Object>>) rawData.get("trendScores");
            Map<String, Double> sentimentScores = (Map<String, Double>) rawData.get("sentimentScores");
            trendAnalysisService.storeTrendAnalysis(trendScores, sentimentScores);
            return ResponseEntity.ok("Trends analyzed successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error analyzing trends: " + e.getMessage());
        }
    }
}
