// src/main/java/com/jithin/ai_content_platform/controller/AnalyticsController.java

package com.jithin.ai_content_platform.controller;

import com.jithin.ai_content_platform.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);

    @Autowired
    private AnalyticsService analyticsService;

    @GetMapping("/user-stats")
    public ResponseEntity<?> getUserStats(Authentication authentication) {
        log.info("Fetching user statistics");
        try {
            if (authentication == null) {
                return ResponseEntity.status(401)
                    .body(Collections.singletonMap("error", "User not authenticated"));
            }

            String username = authentication.getName();
            if (username == null || username.trim().isEmpty()) {
                return ResponseEntity.status(401)
                    .body(Collections.singletonMap("error", "Invalid user credentials"));
            }

            Map<String, Object> stats = analyticsService.getUserStats(username);
            if (stats == null || stats.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("message", "No statistics available for user: " + username);
                response.put("stats", Collections.emptyMap());
                return ResponseEntity.ok(response);
            }

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching user statistics: {}", e.getMessage());
            return ResponseEntity.status(500)
                .body(Collections.singletonMap("error", "Error fetching user statistics: " + e.getMessage()));
        }
    }

    @GetMapping("/content-performance")
    public ResponseEntity<?> getContentPerformance(Authentication authentication) {
        log.info("Fetching content performance metrics");
        try {
            if (authentication == null) {
                return ResponseEntity.status(401)
                    .body(Collections.singletonMap("error", "User not authenticated"));
            }

            String username = authentication.getName();
            Map<String, Object> performance = analyticsService.getContentPerformance(username);
            
            if (performance.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("message", "No content performance data available");
                response.put("metrics", Collections.emptyMap());
                return ResponseEntity.ok(response);
            }

            return ResponseEntity.ok(performance);
        } catch (Exception e) {
            log.error("Error fetching content performance: {}", e.getMessage());
            return ResponseEntity.status(500)
                .body(Collections.singletonMap("error", "Error fetching content performance: " + e.getMessage()));
        }
    }
}