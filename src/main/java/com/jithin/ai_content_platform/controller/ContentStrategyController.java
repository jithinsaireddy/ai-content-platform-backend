package com.jithin.ai_content_platform.controller;

import com.jithin.ai_content_platform.model.User;
import com.jithin.ai_content_platform.service.ContentStrategyService;
import com.jithin.ai_content_platform.exception.UserNotFoundException;
import com.jithin.ai_content_platform.exception.UnauthorizedException;
import com.jithin.ai_content_platform.dto.StrategyRequest;
import com.jithin.ai_content_platform.dto.StrategyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.jithin.ai_content_platform.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/v1/strategy")
@Slf4j
@Validated
public class ContentStrategyController {

    @Autowired
    private ContentStrategyService contentStrategyService;

    @Autowired
    private UserRepository userRepository;

    /**
     * Get comprehensive content strategy advice
     */
    @GetMapping
    public ResponseEntity<StrategyResponse> getContentStrategy(Authentication authentication) {
        User user = validateAndGetUser(authentication);
        log.info("Fetching content strategy for user: {}", user.getUsername());
        
        try {
            Map<String, Object> strategy = contentStrategyService.getStrategyAdvice(user);
            return ResponseEntity.ok(new StrategyResponse(strategy));
        } catch (Exception e) {
            log.error("Error fetching content strategy for user {}: {}", user.getUsername(), e.getMessage(), e);
            throw new RuntimeException("Failed to fetch content strategy", e);
        }
    }

    /**
     * Get best posting times based on ML analysis
     */
    @GetMapping("/posting-times")
    public ResponseEntity<Map<String, Object>> getBestPostingTimes(
            Authentication authentication,
            @RequestParam(required = false) String timezone) {
        User user = validateAndGetUser(authentication);
        log.info("Fetching optimal posting times for user: {}", user.getUsername());
        
        try {
            Map<String, Object> postingTimes = contentStrategyService.getOptimalPostingTimes(user, timezone);
            return ResponseEntity.ok(postingTimes);
        } catch (Exception e) {
            log.error("Error fetching posting times for user {}: {}", user.getUsername(), e.getMessage(), e);
            throw new RuntimeException("Failed to fetch posting times", e);
        }
    }

    /**
     * Get audience insights with ML-based clustering
     */
    @GetMapping("/audience")
    public ResponseEntity<Map<String, Object>> getAudienceInsights(Authentication authentication) {
        User user = validateAndGetUser(authentication);
        log.info("Fetching audience insights for user: {}", user.getUsername());
        
        try {
            Map<String, Object> insights = contentStrategyService.getAudienceInsights(user);
            return ResponseEntity.ok(insights);
        } catch (Exception e) {
            log.error("Error fetching audience insights for user {}: {}", user.getUsername(), e.getMessage(), e);
            throw new RuntimeException("Failed to fetch audience insights", e);
        }
    }

    /**
     * Get content type recommendations with AI suggestions
     */
    @GetMapping("/content-types")
    public ResponseEntity<Map<String, Object>> getContentTypeRecommendations(
            Authentication authentication,
            @RequestParam(required = false) List<String> categories) {
        User user = validateAndGetUser(authentication);
        log.info("Fetching content type recommendations for user: {}", user.getUsername());
        
        try {
            Map<String, Object> recommendations = contentStrategyService.getContentTypeRecommendations(user, categories);
            return ResponseEntity.ok(recommendations);
        } catch (Exception e) {
            log.error("Error fetching content recommendations for user {}: {}", user.getUsername(), e.getMessage(), e);
            throw new RuntimeException("Failed to fetch content recommendations", e);
        }
    }

    /**
     * Get trending topics with AI-powered relevance scoring
     */
    @GetMapping("/trending")
    public ResponseEntity<Map<String, Object>> getTrendingTopics(
            Authentication authentication,
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        User user = validateAndGetUser(authentication);
        log.info("Fetching trending topics for user: {}", user.getUsername());
        
        try {
            Map<String, Object> trendingTopics = contentStrategyService.getTrendingTopicsWithRelevance(user, category, limit);
            return ResponseEntity.ok(trendingTopics);
        } catch (Exception e) {
            log.error("Error fetching trending topics for user {}: {}", user.getUsername(), e.getMessage(), e);
            throw new RuntimeException("Failed to fetch trending topics", e);
        }
    }

    /**
     * Get engagement metrics with ML-based predictions
     */
    @GetMapping("/engagement")
    public ResponseEntity<Map<String, Object>> getEngagementMetrics(
            Authentication authentication,
            @RequestParam(required = false) String timeframe) {
        User user = validateAndGetUser(authentication);
        log.info("Fetching engagement metrics for user: {}", user.getUsername());
        
        try {
            Map<String, Object> metrics = contentStrategyService.getEngagementMetrics(user, timeframe);
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            log.error("Error fetching engagement metrics for user {}: {}", user.getUsername(), e.getMessage(), e);
            throw new RuntimeException("Failed to fetch engagement metrics", e);
        }
    }

    /**
     * Generate personalized content strategy based on specific parameters
     */
    @PostMapping("/generate")
    public ResponseEntity<StrategyResponse> generateStrategy(
            Authentication authentication,
            @RequestBody @Validated StrategyRequest request) {
        User user = validateAndGetUser(authentication);
        log.info("Generating personalized content strategy for user: {}", user.getUsername());
        
        try {
            Map<String, Object> strategy = contentStrategyService.generatePersonalizedStrategy(user, request);
            return ResponseEntity.ok(new StrategyResponse(strategy));
        } catch (Exception e) {
            log.error("Error generating strategy for user {}: {}", user.getUsername(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate strategy", e);
        }
    }

    private User validateAndGetUser(Authentication authentication) {
        if (authentication == null) {
            log.error("Unauthorized access attempt to content strategy");
            throw new UnauthorizedException("Authentication required to access content strategy");
        }

        String username = authentication.getName();
        if (username == null || username.isEmpty()) {
            throw new UnauthorizedException("Invalid authentication");
        }

        return userRepository.findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
    }
}