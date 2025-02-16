package com.jithin.ai_content_platform.repository;

import com.jithin.ai_content_platform.model.CommunityModel;
import com.jithin.ai_content_platform.model.CommunityModelTrainingData;
import com.jithin.ai_content_platform.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CommunityModelTrainingDataRepository extends JpaRepository<CommunityModelTrainingData, Long> {
    
    List<CommunityModelTrainingData> findByModelOrderByQualityScoreDesc(CommunityModel model);
    
    List<CommunityModelTrainingData> findByContributorOrderBySubmittedAtDesc(User contributor);
    
    @Query("SELECT td FROM CommunityModelTrainingData td " +
           "WHERE td.model = :model " +
           "AND td.approved = true " +
           "AND td.qualityScore >= :minQualityScore " +
           "ORDER BY td.qualityScore DESC")
    List<CommunityModelTrainingData> findHighQualityTrainingData(
        @Param("model") CommunityModel model,
        @Param("minQualityScore") double minQualityScore
    );
    
    Page<CommunityModelTrainingData> findByModelAndApprovedFalseOrderBySubmittedAtAsc(
        CommunityModel model,
        Pageable pageable
    );
    
    @Query("SELECT COUNT(td) FROM CommunityModelTrainingData td " +
           "WHERE td.model = :model " +
           "AND td.submittedAt >= :since")
    long countRecentSubmissions(
        @Param("model") CommunityModel model,
        @Param("since") LocalDateTime since
    );
    
    @Query("SELECT AVG(td.qualityScore) FROM CommunityModelTrainingData td " +
           "WHERE td.model = :model AND td.approved = true")
    Double getAverageQualityScore(@Param("model") CommunityModel model);
}
