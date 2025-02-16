package com.jithin.ai_content_platform.service;

import com.jithin.ai_content_platform.model.UserAchievement;
import com.jithin.ai_content_platform.repository.UserAchievementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserAchievementService {

    @Autowired
    private UserAchievementRepository userAchievementRepository;

    public UserAchievement createAchievement(UserAchievement achievement) {
        return userAchievementRepository.save(achievement);
    }

    public List<UserAchievement> getAchievementsByUser(Long userId) {
        return userAchievementRepository.findAll().stream()
            .filter(achievement -> achievement.getUser().getId().equals(userId))
            .toList(); // Assuming Java 16 or above
    }
}
