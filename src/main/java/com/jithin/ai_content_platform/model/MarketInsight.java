package com.jithin.ai_content_platform.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketInsight {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String marketSegment;
    private String competitorName;
    private String insightType; // e.g., "CONTENT_STRATEGY", "ENGAGEMENT_METRICS", "AUDIENCE_PREFERENCE"
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    private Double relevanceScore; // 0.0 to 1.0
    private Double confidenceLevel; // 0.0 to 1.0
    
    @ElementCollection
    @CollectionTable(name = "market_insight_metrics")
    @MapKeyColumn(name = "metric_name")
    @Column(name = "metric_value")
    private Map<String, Double> metrics;
    
    private LocalDateTime discoveredAt;
    private LocalDateTime validUntil;
    private boolean isActive;
    
    @ManyToOne
    @JoinColumn(name = "content_id")
    private Content relatedContent;
    
    @PrePersist
    protected void onCreate() {
        discoveredAt = LocalDateTime.now();
        if (validUntil == null) {
            validUntil = discoveredAt.plusMonths(3); // Default validity of 3 months
        }
        isActive = true;
    }
}
