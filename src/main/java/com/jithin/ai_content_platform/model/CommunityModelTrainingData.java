package com.jithin.ai_content_platform.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "community_model_training_data")
public class CommunityModelTrainingData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id", nullable = false)
    private CommunityModel model;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contributor_id", nullable = false)
    private User contributor;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;
    
    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;
    
    @Column(nullable = false)
    private LocalDateTime submittedAt;
    
    @Column(nullable = false)
    private boolean approved;
    
    @Column(columnDefinition = "TEXT")
    private String reviewComments;
    
    @Column(nullable = false)
    private double qualityScore;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        submittedAt = LocalDateTime.now();
        if (qualityScore == 0.0) {
            qualityScore = 0.5; // Default neutral score
        }
    }
}
