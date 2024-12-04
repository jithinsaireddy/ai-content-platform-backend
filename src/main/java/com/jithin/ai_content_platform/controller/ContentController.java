// src/main/java/com/jithin/ai_content_platform/controller/ContentController.java

package com.jithin.ai_content_platform.controller;

import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.model.User;
import com.jithin.ai_content_platform.payload.ContentRequest;
import com.jithin.ai_content_platform.payload.FeedbackRequest;
import com.jithin.ai_content_platform.repository.ContentRepository;
import com.jithin.ai_content_platform.repository.UserRepository;
import com.jithin.ai_content_platform.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/content")
public class ContentController {

    @Autowired
    private ContentService contentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    TrendAnalysisService trendAnalysisService;

    @Autowired
    ImageGenerationService imageGenerationService;

    @Autowired
    ComplianceService complianceService;

    @Autowired
    PublishingService publishingService;

    @Autowired
    ContentStrategyService contentStrategyService;

    @PostMapping("/generate")
    public Content generateContent(@RequestBody ContentRequest request, Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username).orElseThrow(() ->
                new RuntimeException("User not found"));

        Content content = contentService.generateContent(request, user);

        // Notify the user that content generation is complete
        messagingTemplate.convertAndSendToUser(user.getUsername(), "/queue/updates", "Content generation completed.");

        return contentService.generateContent(request, user);

//        if (user.getSubscriptionLevel().equals("FREE")) {
//            // Limit the number of content generations per day
//            long contentCountToday = contentRepository.countByUserAndDate(user, LocalDate.now());
//            if (contentCountToday >= 5) {
//                throw new RuntimeException("Daily content generation limit reached for FREE users.");
//            }
//        }
//
//        return contentService.generateContent(request, user);
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

    @GetMapping("/trends")
    public ResponseEntity<?> getTrendingTopics() {
        List<String> trends = trendAnalysisService.getTrendingTopics();
        return ResponseEntity.ok(trends);
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

    @PostMapping("/publish")
    public ResponseEntity<?> publishContent(@RequestBody Map<String, String> request,
                                            @AuthenticationPrincipal User user) {
        String content = request.get("content");
        String title = request.get("title");
        boolean success = publishingService.publishToWordPress(content, title, user);
        return ResponseEntity.ok(success);
    }

    @GetMapping("/strategy")
    public ResponseEntity<?> getContentStrategy(@AuthenticationPrincipal User user) {
        Map<String, Object> advice = contentStrategyService.getStrategyAdvice(user);
        return ResponseEntity.ok(advice);
    }
}