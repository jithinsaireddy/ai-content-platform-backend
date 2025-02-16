package com.jithin.ai_content_platform.model;

import lombok.Data;
import jakarta.persistence.*;
import java.util.Map;
import java.util.List;

@Data
@Entity
@Table(name = "localization_metrics")
public class LocalizationMetrics {
    @Id
    private String contentId;
    
    @Column(nullable = false)
    private String region;
    
    @Column
    private double culturalRelevanceScore;
    
    @Column
    private double engagementScore;
    
    @Column
    private double seoPerformanceScore;
    
    @Column
    private double sentimentScore;
    
    // Real-time metrics
    @ElementCollection
    @CollectionTable(name = "real_time_engagement", joinColumns = @JoinColumn(name = "content_id"))
    @MapKeyColumn(name = "metric_key")
    @Column(name = "metric_value")
    private Map<String, Double> realTimeEngagement;
    
    @ElementCollection
    @CollectionTable(name = "ab_test_results", joinColumns = @JoinColumn(name = "content_id"))
    @MapKeyColumn(name = "test_key")
    @Column(name = "test_value")
    private Map<String, Double> abTestResults;
    
    @ElementCollection
    @CollectionTable(name = "cultural_sensitivity_flags", joinColumns = @JoinColumn(name = "content_id"))
    @Column(name = "flag")
    private List<String> culturalSensitivityFlags;
    
    // Performance metrics
    @ElementCollection
    @CollectionTable(name = "platform_specific_metrics", joinColumns = @JoinColumn(name = "content_id"))
    @MapKeyColumn(name = "platform")
    @Column(name = "metric_value")
    private Map<String, Double> platformSpecificMetrics;
    
    @ElementCollection
    @CollectionTable(name = "demographic_engagement", joinColumns = @JoinColumn(name = "content_id"))
    @MapKeyColumn(name = "demographic")
    @Column(name = "engagement_value")
    private Map<String, Double> demographicEngagement;
    
    @ElementCollection
    @CollectionTable(name = "time_based_performance", joinColumns = @JoinColumn(name = "content_id"))
    @MapKeyColumn(name = "time_period")
    @Column(name = "performance_value")
    private Map<String, Double> timeBasedPerformance;
    
    // ML predictions
    @Column
    private double predictedViralityScore;
    
    @Column
    private double predictedROI;
    
    @ElementCollection
    @CollectionTable(name = "optimization_suggestions", joinColumns = @JoinColumn(name = "content_id"))
    @Column(name = "suggestion")
    private List<String> optimizationSuggestions;
    
    @ElementCollection
    @CollectionTable(name = "competitor_comparison", joinColumns = @JoinColumn(name = "content_id"))
    @MapKeyColumn(name = "competitor")
    @Column(name = "comparison_value")
    private Map<String, Double> competitorComparison;
    
    // Cultural adaptation metrics
    @Column
    private double idiomAccuracyScore;
    
    @Column
    private double referenceRelevanceScore;
    
    @Column
    private double brandVoiceConsistency;
    
    @ElementCollection
    @CollectionTable(name = "cultural_context_warnings", joinColumns = @JoinColumn(name = "content_id"))
    @Column(name = "warning")
    private List<String> culturalContextWarnings;
    
    // SEO metrics
    @ElementCollection
    @CollectionTable(name = "regional_keyword_performance", joinColumns = @JoinColumn(name = "content_id"))
    @MapKeyColumn(name = "keyword")
    @Column(name = "performance_value")
    private Map<String, Double> regionalKeywordPerformance;
    
    @Column
    private double localSearchVisibility;
    
    @ElementCollection
    @CollectionTable(name = "regional_seo_suggestions", joinColumns = @JoinColumn(name = "content_id"))
    @Column(name = "suggestion")
    private List<String> regionalSeoSuggestions;
    
    // Audience metrics
    @ElementCollection
    @CollectionTable(name = "audience_reach_by_demographic", joinColumns = @JoinColumn(name = "content_id"))
    @MapKeyColumn(name = "demographic")
    @Column(name = "reach_value")
    private Map<String, Double> audienceReachByDemographic;
    
    @ElementCollection
    @CollectionTable(name = "audience_engagement_by_platform", joinColumns = @JoinColumn(name = "content_id"))
    @MapKeyColumn(name = "platform")
    @Column(name = "engagement_value")
    private Map<String, Double> audienceEngagementByPlatform;
    
    @ElementCollection
    @CollectionTable(name = "target_audience_insights", joinColumns = @JoinColumn(name = "content_id"))
    @Column(name = "insight")
    private List<String> targetAudienceInsights;
}
