// src/main/java/com/jithin/ai_content_platform/service/AnalyticsService.java

package com.jithin.ai_content_platform.service;

import com.jithin.ai_content_platform.model.User;
import com.jithin.ai_content_platform.repository.ContentRepository;
import com.jithin.ai_content_platform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AnalyticsService {

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private UserRepository userRepository;

    public Map<String, Object> getUserStats(String username) {
        User user = userRepository.findByUsername(username).orElseThrow(() ->
                new RuntimeException("User not found"));

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalContentGenerated", contentRepository.countByUser(user));
        stats.put("averageRating", contentRepository.averageRatingByUser(user));
        // Placeholder implementation
        stats.put("totalViews", 1000);
        stats.put("engagementRate", 5.5);
        // Add more stats as needed
        return stats;
    }
}