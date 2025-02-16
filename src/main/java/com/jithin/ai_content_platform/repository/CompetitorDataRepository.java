package com.jithin.ai_content_platform.repository;

import com.jithin.ai_content_platform.model.CompetitorData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompetitorDataRepository extends JpaRepository<CompetitorData, Long> {
    List<CompetitorData> findByIndustry(String industry);
    CompetitorData findByCompetitorName(String competitorName);
    List<CompetitorData> findByIndustryAndMarketShareGreaterThan(String industry, double marketShare);
}
