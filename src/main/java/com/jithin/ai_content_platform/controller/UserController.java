// src/main/java/com/jithin/ai_content_platform/controller/UserController.java

package com.jithin.ai_content_platform.controller;

import com.jithin.ai_content_platform.model.User;
import com.jithin.ai_content_platform.payload.WritingStyleRequest;
import com.jithin.ai_content_platform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/writing-style")
    public ResponseEntity<?> updateWritingStyle(@RequestBody WritingStyleRequest request, Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username).orElseThrow(() ->
                new RuntimeException("User not found"));

        user.setWritingStyleSample(request.getWritingSample());
        userRepository.save(user);

        return ResponseEntity.ok("Writing style updated successfully.");
    }
}
