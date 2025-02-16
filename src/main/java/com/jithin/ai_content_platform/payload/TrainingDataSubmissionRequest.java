package com.jithin.ai_content_platform.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class TrainingDataSubmissionRequest {
    
    @NotNull(message = "Model ID is required")
    private Long modelId;
    
    @NotBlank(message = "Content is required")
    private String content;
    
    private Map<String, Object> metadata;
    
    private String reviewComments;
}
