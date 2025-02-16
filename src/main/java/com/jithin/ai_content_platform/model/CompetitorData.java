package com.jithin.ai_content_platform.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String competitorName;
    private String industry;
    private String contentStrategy;
    private double marketShare;
    private String contentTypes;
    private int postingFrequency;
    private double averageEngagement;
    private LocalDateTime lastAnalyzed;
}
