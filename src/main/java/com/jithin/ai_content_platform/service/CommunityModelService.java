package com.jithin.ai_content_platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jithin.ai_content_platform.exception.UserNotFoundException;
import com.jithin.ai_content_platform.model.CommunityModel;
import com.jithin.ai_content_platform.model.CommunityModelTrainingData;
import com.jithin.ai_content_platform.model.User;
import com.jithin.ai_content_platform.payload.CommunityModelRequest;
import com.jithin.ai_content_platform.payload.CommunityModelResponse;
import com.jithin.ai_content_platform.payload.TrainingDataSubmissionRequest;
import com.jithin.ai_content_platform.repository.CommunityModelRepository;
import com.jithin.ai_content_platform.repository.CommunityModelTrainingDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class CommunityModelService {
    
    @Autowired
    private CommunityModelRepository communityModelRepository;
    
    @Autowired
    private CommunityModelTrainingDataRepository trainingDataRepository;
    
    @Autowired
    private OpenRouterService openRouterService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    public CommunityModelResponse createModel(CommunityModelRequest request, User creator) {
        // Check if model with same name and category exists
        if (communityModelRepository.findByNameAndCategory(request.getName(), request.getCategory()).isPresent()) {
            throw new IllegalArgumentException("A model with this name already exists in the category");
        }
        
        CommunityModel model = CommunityModel.builder()
            .name(request.getName())
            .category(request.getCategory())
            .description(request.getDescription())
            .styleGuide(objectMapper.valueToTree(request.getStyleGuide()).toString())
            .modelParameters(objectMapper.valueToTree(request.getModelParameters()).toString())
            .contributors(new HashSet<>(Collections.singletonList(creator)))
            .build();
        
        model = communityModelRepository.save(model);
        log.info("Created new community model: {} in category: {}", model.getName(), model.getCategory());
        
        return CommunityModelResponse.fromModel(model, 0.0);
    }
    
    public CommunityModelResponse getModel(Long modelId) {
        CommunityModel model = findModelById(modelId);
        double avgQualityScore = trainingDataRepository.getAverageQualityScore(model) != null ?
            trainingDataRepository.getAverageQualityScore(model) : 0.0;
            
        return CommunityModelResponse.fromModel(model, avgQualityScore);
    }
    
    public List<CommunityModelResponse> getModelsByCategory(String category) {
        return communityModelRepository.findByCategory(category).stream()
            .map(model -> {
                double avgQualityScore = trainingDataRepository.getAverageQualityScore(model) != null ?
                    trainingDataRepository.getAverageQualityScore(model) : 0.0;
                return CommunityModelResponse.fromModel(model, avgQualityScore);
            })
            .collect(Collectors.toList());
    }
    
    public CommunityModelTrainingData submitTrainingData(
        TrainingDataSubmissionRequest request,
        User contributor
    ) {
        CommunityModel model = findModelById(request.getModelId());
        
        CommunityModelTrainingData trainingData = CommunityModelTrainingData.builder()
            .model(model)
            .contributor(contributor)
            .content(request.getContent())
            .metadata(objectMapper.valueToTree(request.getMetadata()).toString())
            .approved(false) // Requires approval before being used
            .reviewComments(request.getReviewComments())
            .build();
        
        trainingData = trainingDataRepository.save(trainingData);
        
        // Add contributor to the model if not already present
        if (!model.getContributors().contains(contributor)) {
            model.getContributors().add(contributor);
            communityModelRepository.save(model);
        }
        
        log.info("Submitted new training data for model: {} by user: {}", model.getName(), contributor.getUsername());
        return trainingData;
    }
    
    public void approveTrainingData(Long trainingDataId, User approver) {
        CommunityModelTrainingData trainingData = trainingDataRepository.findById(trainingDataId)
            .orElseThrow(() -> new RuntimeException("Training data not found"));
            
        trainingData.setApproved(true);
        trainingData.setQualityScore(calculateQualityScore(trainingData));
        
        trainingDataRepository.save(trainingData);
        log.info("Approved training data ID: {} for model: {}", trainingDataId, trainingData.getModel().getName());
    }
    
    private double calculateQualityScore(CommunityModelTrainingData trainingData) {
        // TODO: Implement quality scoring logic
        // This should consider factors like:
        // - Content length and complexity
        // - Adherence to style guide
        // - Similarity to existing high-quality examples
        // For now, return a default score
        return 0.75;
    }
    
    private CommunityModel findModelById(Long modelId) {
        return communityModelRepository.findById(modelId)
            .orElseThrow(() -> new RuntimeException("Community model not found"));
    }
}
