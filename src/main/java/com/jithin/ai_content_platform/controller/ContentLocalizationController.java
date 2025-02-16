package com.jithin.ai_content_platform.controller;

import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.model.LocalizationMetrics;
import com.jithin.ai_content_platform.service.ContentLocalizationService;
import com.jithin.ai_content_platform.service.RealTimeLocalizationService;
import com.jithin.ai_content_platform.service.LocalizationAnalytics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/localization")
@Slf4j
public class ContentLocalizationController {

    @Autowired
    private ContentLocalizationService localizationService;
    
    @Autowired
    private RealTimeLocalizationService realTimeService;
    
    @Autowired
    private LocalizationAnalytics analyticsService;

    @PostMapping("/localize")
    public ResponseEntity<?> localizeContent(
            @RequestBody Content content,
            @RequestParam List<String> targetRegions,
            @RequestParam(required = false) Boolean enableRealTimeMonitoring) {
        try {
            Map<String, Object> localizedVersions = localizationService.localizeContent(content, targetRegions);
            
            // Start real-time monitoring if requested
            if (Boolean.TRUE.equals(enableRealTimeMonitoring)) {
                targetRegions.forEach(region -> 
                    realTimeService.monitorPerformance(String.valueOf(content.getId()), region)
                );
            }
            
            return ResponseEntity.ok(localizedVersions);
        } catch (Exception e) {
            log.error("Error localizing content: ", e);
            return ResponseEntity.internalServerError().body("Error localizing content: " + e.getMessage());
        }
    }

    @GetMapping("/performance/{contentId}")
    public ResponseEntity<?> getRegionalPerformance(
            @PathVariable String contentId,
            @RequestParam List<String> regions) {
        try {
            Map<String, Object> performance = localizationService.analyzeRegionalPerformance(contentId, regions);
            return ResponseEntity.ok(performance);
        } catch (Exception e) {
            log.error("Error analyzing regional performance: ", e);
            return ResponseEntity.internalServerError().body("Error analyzing performance: " + e.getMessage());
        }
    }

    @GetMapping("/strategy/{region}")
    public ResponseEntity<?> getRegionalStrategy(
            @PathVariable String region,
            @RequestParam String industry) {
        try {
            Map<String, Object> strategy = localizationService.generateRegionalContentStrategy(region, industry);
            return ResponseEntity.ok(strategy);
        } catch (Exception e) {
            log.error("Error generating regional strategy: ", e);
            return ResponseEntity.internalServerError().body("Error generating strategy: " + e.getMessage());
        }
    }
    
    @GetMapping("/analytics/engagement/{region}")
    public ResponseEntity<?> getEngagementAnalytics(@PathVariable String region) {
        try {
            Map<String, Object> patterns = analyticsService.trackEngagementPatterns(region);
            return ResponseEntity.ok(patterns);
        } catch (Exception e) {
            log.error("Error getting engagement analytics: ", e);
            return ResponseEntity.internalServerError().body("Error getting analytics: " + e.getMessage());
        }
    }
    
    @GetMapping("/analytics/effectiveness/{contentId}")
    public ResponseEntity<?> getContentEffectiveness(
            @PathVariable String contentId,
            @RequestParam String region) {
        try {
            double effectiveness = analyticsService.analyzeEffectiveness(contentId, region);
            return ResponseEntity.ok(Map.of("effectiveness", effectiveness));
        } catch (Exception e) {
            log.error("Error analyzing content effectiveness: ", e);
            return ResponseEntity.internalServerError().body("Error analyzing effectiveness: " + e.getMessage());
        }
    }
    
    @GetMapping("/analytics/recommendations/{contentId}")
    public ResponseEntity<?> getOptimizationRecommendations(
            @PathVariable String contentId,
            @RequestParam String region) {
        try {
            List<String> recommendations = analyticsService.generateRecommendations(contentId, region);
            return ResponseEntity.ok(recommendations);
        } catch (Exception e) {
            log.error("Error generating recommendations: ", e);
            return ResponseEntity.internalServerError().body("Error generating recommendations: " + e.getMessage());
        }
    }
    
    @PostMapping("/monitor/{contentId}")
    public ResponseEntity<?> startRealTimeMonitoring(
            @PathVariable String contentId,
            @RequestParam List<String> regions) {
        try {
            regions.forEach(region -> 
                realTimeService.monitorPerformance(contentId, region)
            );
            return ResponseEntity.ok("Real-time monitoring started");
        } catch (Exception e) {
            log.error("Error starting monitoring: ", e);
            return ResponseEntity.internalServerError().body("Error starting monitoring: " + e.getMessage());
        }
    }
    
    @GetMapping("/monitor/timing/{contentId}")
    public ResponseEntity<?> getUpdateTiming(
            @PathVariable String contentId,
            @RequestParam String region) {
        try {
            Map<String, Object> timing = realTimeService.predictUpdateTiming(contentId, region);
            return ResponseEntity.ok(timing);
        } catch (Exception e) {
            log.error("Error predicting update timing: ", e);
            return ResponseEntity.internalServerError().body("Error predicting timing: " + e.getMessage());
        }
    }
}
