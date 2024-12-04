// src/main/java/com/jithin/ai_content_platform/controller/AnalyticsController.java

package com.jithin.ai_content_platform.controller;

import com.jithin.ai_content_platform.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @GetMapping("/user-stats")
    public ResponseEntity<?> getUserStats(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(analyticsService.getUserStats(username));
    }
}