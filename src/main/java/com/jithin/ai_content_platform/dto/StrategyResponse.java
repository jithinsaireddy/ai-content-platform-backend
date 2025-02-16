package com.jithin.ai_content_platform.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Map;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StrategyResponse {
    private Map<String, Object> strategy;
    private LocalDateTime generatedAt;
    private String version;
    private Map<String, Object> metrics;
    private Map<String, Object> insights;
    
    public StrategyResponse(Map<String, Object> strategy) {
        this.strategy = strategy;
        this.generatedAt = LocalDateTime.now();
        this.version = "1.0";
    }
    
    public void addMetrics(Map<String, Object> metrics) {
        this.metrics = metrics;
    }
    
    public void addInsights(Map<String, Object> insights) {
        this.insights = insights;
    }
}
