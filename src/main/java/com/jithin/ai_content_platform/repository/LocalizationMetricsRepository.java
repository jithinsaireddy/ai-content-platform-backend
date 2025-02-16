package com.jithin.ai_content_platform.repository;

import com.jithin.ai_content_platform.model.LocalizationMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocalizationMetricsRepository extends JpaRepository<LocalizationMetrics, Long> {
    List<LocalizationMetrics> findByContentId(String contentId);
    List<LocalizationMetrics> findByRegion(String region);
    LocalizationMetrics findByContentIdAndRegion(String contentId, String region);
}
