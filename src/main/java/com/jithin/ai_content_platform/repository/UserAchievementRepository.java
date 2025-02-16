package com.jithin.ai_content_platform.repository;

import com.jithin.ai_content_platform.model.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAchievementRepository extends JpaRepository<UserAchievement, Long> {
    // Custom query methods can be defined here if needed
}
