// src/main/java/com/jithin/ai_content_platform/controller/NotificationController.java

package com.jithin.ai_content_platform.controller;

import com.jithin.ai_content_platform.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @PostMapping("/test")
    public void sendTestNotification(@AuthenticationPrincipal User user) {
        messagingTemplate.convertAndSendToUser(
                user.getUsername(), // The user's identifier
                "/queue/updates", // The destination
                "This is a test notification."
        );
    }
}