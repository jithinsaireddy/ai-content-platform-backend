package com.jithin.ai_content_platform.repository;

import com.jithin.ai_content_platform.model.TrendData;
import com.jithin.ai_content_platform.model.TrendData.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
// import org.springframework.data.jpa.repository.Transactional;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

@Repository
// @Transactional(readOnly = false)
public interface TrendDataRepository extends JpaRepository<TrendData, Long> {
    
    @Query("SELECT t FROM TrendData t WHERE t.topic = :topic ORDER BY t.analysisTimestamp DESC")
    List<TrendData> findByTopic(String topic);

    default TrendData findLatestByTopic(String topic) {
        List<TrendData> trends = findByTopic(topic);
        return trends.isEmpty() ? null : trends.get(0);
    }
    
    @Query("SELECT t FROM TrendData t WHERE t.analysisTimestamp >= :cutoff ORDER BY t.analysisTimestamp DESC")
    List<TrendData> findLatestTrends(@Param("cutoff") LocalDateTime cutoff);
    
    default List<TrendData> findLatestTrends() {
        return findLatestTrends(LocalDateTime.now().minusHours(24));
    }
    List<TrendData> findTop24ByOrderByAnalysisTimestampDesc();
    List<TrendData> findByRegionOrderByAnalysisTimestampDesc(Region region);
    TrendData findTop1ByOrderByAnalysisTimestampDesc();
    Page<TrendData> findByTopicOrderByAnalysisTimestampDesc(String topic, Pageable pageable);
    List<TrendData> findByTopicOrderByAnalysisTimestampDesc(String topic);
    
    List<TrendData> findByAnalysisTimestampBetween(LocalDateTime start, LocalDateTime end);
    
    @Query("SELECT t FROM TrendData t WHERE CAST(t.trendScore AS double) > :minScore")
    List<TrendData> findTrendingTopics(@Param("minScore") double minScore);
    
    List<TrendData> findByTopicContainingIgnoreCase(String keyword);
    
    @Query("SELECT t FROM TrendData t WHERE t.category = :category AND CAST(t.trendScore AS double) > :minScore")
    List<TrendData> findTrendingByCategory(@Param("category") String category, @Param("minScore") double minScore);
}
