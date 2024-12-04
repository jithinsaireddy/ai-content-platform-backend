// src/main/java/com/jithin/ai_content_platform/controller/SharingController.java

package com.jithin.ai_content_platform.controller;

import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.model.User;
import com.jithin.ai_content_platform.repository.ContentRepository;
import com.jithin.ai_content_platform.service.SocialSharingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/share")
public class SharingController {

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private SocialSharingService socialSharingService;

    @PostMapping("/content/{id}")
    public ResponseEntity<?> shareContent(
            @PathVariable Long id,
            @RequestParam String platform,
            @AuthenticationPrincipal User user) {

        // Fetch the content by ID
        Content content = contentRepository.findById(id).orElseThrow(() ->
                new RuntimeException("Content not found"));

        // Ensure the content belongs to the authenticated user
        if (!content.getUser().equals(user)) {
            return ResponseEntity.status(403).body("You can only share your own content.");
        }

        // Use the SocialSharingService to share the content
        boolean success = socialSharingService.shareContentToPlatform(content, platform, user);

        if (success) {
            return ResponseEntity.ok("Content shared successfully to " + platform + ".");
        } else {
            return ResponseEntity.status(500).body("Failed to share content to " + platform + ".");
        }
    }
}