package com.jithin.ai_content_platform.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class CommunityModelRequest {
    
    @NotBlank(message = "Model name is required")
    @Size(min = 3, max = 100, message = "Model name must be between 3 and 100 characters")
    private String name;
    
    @NotBlank(message = "Category is required")
    @Size(min = 3, max = 50, message = "Category must be between 3 and 50 characters")
    private String category;
    
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;
    
    private Map<String, Object> styleGuide;
    
    private Map<String, Object> modelParameters;
}
