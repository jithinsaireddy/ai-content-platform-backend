package com.jithin.ai_content_platform.repository;

import com.jithin.ai_content_platform.model.CommunityModel;
import com.jithin.ai_content_platform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityModelRepository extends JpaRepository<CommunityModel, Long> {
    
    List<CommunityModel> findByCategory(String category);
    
    @Query("SELECT cm FROM CommunityModel cm WHERE :user MEMBER OF cm.contributors")
    List<CommunityModel> findByContributor(@Param("user") User user);
    
    @Query("SELECT cm FROM CommunityModel cm " +
           "LEFT JOIN cm.trainingData td " +
           "GROUP BY cm " +
           "HAVING COUNT(td) >= :minTrainingData")
    List<CommunityModel> findModelsWithSufficientTrainingData(@Param("minTrainingData") long minTrainingData);
    
    Optional<CommunityModel> findByNameAndCategory(String name, String category);
    
    @Query("SELECT DISTINCT cm.category FROM CommunityModel cm")
    List<String> findAllCategories();
    
    @Query("SELECT cm FROM CommunityModel cm " +
           "WHERE cm.category = :category " +
           "ORDER BY SIZE(cm.contributors) DESC, SIZE(cm.trainingData) DESC")
    List<CommunityModel> findTopModelsByCategory(@Param("category") String category);
}
