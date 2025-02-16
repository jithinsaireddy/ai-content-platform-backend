package com.jithin.ai_content_platform.payload;

import com.jithin.ai_content_platform.model.CommunityModel;
import lombok.Data;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class CommunityModelResponse {
    private Long id;
    private String name;
    private String category;
    private String description;
    private Map<String, Object> styleGuide;
    private int contributorsCount;
    private int trainingDataCount;
    private double averageQualityScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static CommunityModelResponse fromModel(
        CommunityModel model,
        double averageQualityScore
    ) {
        return CommunityModelResponse.builder()
            .id(model.getId())
            .name(model.getName())
            .category(model.getCategory())
            .description(model.getDescription())
            .styleGuide(model.getStyleGuide() != null ? 
                Map.of("styleGuide", model.getStyleGuide()) : 
                Map.of())
            .contributorsCount(model.getContributors().size())
            .trainingDataCount(model.getTrainingData().size())
            .averageQualityScore(averageQualityScore)
            .createdAt(model.getCreatedAt())
            .updatedAt(model.getUpdatedAt())
            .build();
    }
}
