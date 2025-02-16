package com.jithin.ai_content_platform.controller;

import com.jithin.ai_content_platform.exception.UserNotFoundException;
import com.jithin.ai_content_platform.model.CommunityModel;
import com.jithin.ai_content_platform.model.CommunityModelTrainingData;
import com.jithin.ai_content_platform.model.User;
import com.jithin.ai_content_platform.payload.CommunityModelRequest;
import com.jithin.ai_content_platform.payload.CommunityModelResponse;
import com.jithin.ai_content_platform.payload.ContentRequest;
import com.jithin.ai_content_platform.payload.TrainingDataSubmissionRequest;
import com.jithin.ai_content_platform.repository.UserRepository;
import com.jithin.ai_content_platform.service.CommunityModelService;
import com.jithin.ai_content_platform.service.ContentService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/community-models")
@Slf4j
public class CommunityModelController {

    @Autowired
    private CommunityModelService communityModelService;

    @Autowired
    private ContentService contentService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> createModel(@Valid @RequestBody CommunityModelRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
            
            CommunityModelResponse model = communityModelService.createModel(request, user);
            return ResponseEntity.ok(model);
        } catch (UserNotFoundException e) {
            log.error("User not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(createErrorResponse("User not found"));
        } catch (Exception e) {
            log.error("Error creating community model: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to create community model: " + e.getMessage()));
        }
    }

    @GetMapping("/{modelId}")
    public ResponseEntity<?> getModel(@PathVariable Long modelId) {
        try {
            CommunityModelResponse model = communityModelService.getModel(modelId);
            return ResponseEntity.ok(model);
        } catch (Exception e) {
            log.error("Error retrieving community model: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to retrieve community model"));
        }
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<CommunityModelResponse>> getModelsByCategory(
        @PathVariable String category
    ) {
        return ResponseEntity.ok(communityModelService.getModelsByCategory(category));
    }

    @PostMapping("/{modelId}/training-data")
    public ResponseEntity<?> submitTrainingData(
        @PathVariable Long modelId,
        @Valid @RequestBody TrainingDataSubmissionRequest request
    ) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
            
            request.setModelId(modelId);
            CommunityModelTrainingData trainingData = communityModelService.submitTrainingData(request, user);
            return ResponseEntity.ok(trainingData);
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(createErrorResponse("User not found"));
        } catch (Exception e) {
            log.error("Error submitting training data: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to submit training data"));
        }
    }

    @PostMapping("/{modelId}/generate")
    public ResponseEntity<?> generateContent(
        @PathVariable Long modelId,
        @Valid @RequestBody ContentRequest request
    ) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
            
            request.setCommunityModelId(modelId);
            return ResponseEntity.ok(contentService.generateContent(request, user));
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(createErrorResponse("User not found"));
        } catch (Exception e) {
            log.error("Error generating content with community model: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to generate content: " + e.getMessage()));
        }
    }

    @PostMapping("/training-data/{trainingDataId}/approve")
    public ResponseEntity<?> approveTrainingData(@PathVariable Long trainingDataId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
            
            communityModelService.approveTrainingData(trainingDataId, user);
            return ResponseEntity.ok(createSuccessResponse("Training data approved successfully"));
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(createErrorResponse("User not found"));
        } catch (Exception e) {
            log.error("Error approving training data: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to approve training data"));
        }
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return response;
    }

    private Map<String, Object> createSuccessResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        return response;
    }
}
