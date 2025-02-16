package com.jithin.ai_content_platform.repository;

import com.jithin.ai_content_platform.model.UserTrainingData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserTrainingDataRepository extends JpaRepository<UserTrainingData, Long> {
    // Custom query methods can be defined here if needed
}
