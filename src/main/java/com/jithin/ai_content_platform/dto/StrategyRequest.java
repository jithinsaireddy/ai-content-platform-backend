package com.jithin.ai_content_platform.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Data
public class StrategyRequest {
    @NotNull
    private String industry;
    
    @NotNull
    private List<String> targetPlatforms;
    
    private List<String> contentTypes;
    
    private String timezone;
    
    private Map<String, Object> preferences;
    
    private List<String> targetAudience;
    
    private Integer contentFrequency;
    
    private String budgetRange;
    
    private List<String> competitors;
    
    private Map<String, Double> categoryWeights;
    
    private String timeframe;
}
