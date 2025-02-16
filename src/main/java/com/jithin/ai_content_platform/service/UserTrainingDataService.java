package com.jithin.ai_content_platform.service;

import com.jithin.ai_content_platform.model.UserTrainingData;
import com.jithin.ai_content_platform.repository.UserTrainingDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserTrainingDataService {

    @Autowired
    private UserTrainingDataRepository userTrainingDataRepository;

    public UserTrainingData submitTrainingData(UserTrainingData trainingData) {
        return userTrainingDataRepository.save(trainingData);
    }

    public List<UserTrainingData> getTrainingDataByUser(Long userId) {
        return userTrainingDataRepository.findAll().stream()
            .filter(data -> data.getUser().getId().equals(userId))
            .toList(); // Assuming Java 16 or above
    }
}
