package com.jithin.ai_content_platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.repository.ContentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for analyzing and optimizing keyword usage in content.
 * This service provides detailed analysis of keyword placement, density,
 * and natural usage within content.
 */
@Service
@Slf4j
public class KeywordOptimizationService {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private ContentRepository contentRepository;

    /**
     * Analyzes keyword optimization in content, checking density, placement, and natural usage
     * @param content The content text to analyze
     * @param keywords List of target keywords
     * @return Map containing keyword optimization metrics
     */
    public Map<String, Object> analyzeKeywordOptimization(String content, String keywords) {
        Map<String, Object> optimization = new HashMap<>();
        
        try {
            if (content == null || content.isEmpty() || keywords == null || keywords.isEmpty()) {
                log.warn("Content or keywords is empty for keyword optimization analysis");
                return getDefaultOptimizationMetrics();
            }

            String[] keywordArray = keywords.split("\\s*,\\s*");
            String contentLower = content.toLowerCase();
            
            // Calculate keyword density
            double density = calculateKeywordDensity(contentLower, keywordArray);
            optimization.put("keyword_density", density);
            
            // Analyze keyword placement (title, headings, first/last paragraphs)
            double placement = analyzeKeywordPlacement(contentLower, keywordArray);
            optimization.put("keyword_placement", placement);
            
            // Check natural usage (context and readability)
            double naturalUsage = analyzeNaturalUsage(content, keywordArray);
            optimization.put("natural_usage", naturalUsage);
            
            // Add detailed metrics
            optimization.put("keyword_frequency", getKeywordFrequency(contentLower, keywordArray));
            optimization.put("keyword_distribution", calculateKeywordDistribution(contentLower, keywordArray));
            
            log.debug("Completed keyword optimization analysis for content with {} keywords", keywordArray.length);
            
        } catch (Exception e) {
            log.error("Error during keyword optimization analysis", e);
            return getDefaultOptimizationMetrics();
        }
        
        return optimization;
    }

    /**
     * Updates keyword optimization metrics for existing content
     * @param content The content to update metrics for
     * @return Updated content with new keyword optimization metrics
     */
    @Transactional
    public Content updateKeywordOptimizationMetrics(Content content) {
        try {
            Map<String, Object> optimization = analyzeKeywordOptimization(
                content.getContentBody(),
                content.getKeywords()
            );
            
            // Get existing metrics or create new ones
            Map<String, Object> metrics = content.getMetricsMap();
            if (metrics == null) {
                metrics = new HashMap<>();
            }
            
            // Update keyword optimization metrics
            metrics.put("keyword_optimization", optimization);
            content.setMetricsMap(metrics);
            
            // Save updated content
            return contentRepository.save(content);
            
        } catch (Exception e) {
            log.error("Error updating keyword optimization metrics for content: {}", content.getId(), e);
            return content;
        }
    }

    private Map<String, Object> getDefaultOptimizationMetrics() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("keyword_density", 0.0);
        defaults.put("keyword_placement", 0.0);
        defaults.put("natural_usage", 0.0);
        defaults.put("keyword_frequency", new HashMap<String, Integer>());
        defaults.put("keyword_distribution", new HashMap<String, Double>());
        return defaults;
    }

    private double calculateKeywordDensity(String content, String[] keywords) {
        int totalWords = content.split("\\s+").length;
        if (totalWords == 0) return 0.0;
        
        int keywordCount = 0;
        for (String keyword : keywords) {
            keywordCount += countKeywordOccurrences(content, keyword.toLowerCase());
        }
        
        return (double) keywordCount / totalWords;
    }

    private double analyzeKeywordPlacement(String content, String[] keywords) {
        double score = 0.0;
        String[] paragraphs = content.split("\n\n");
        
        // Check first paragraph (higher weight)
        if (paragraphs.length > 0) {
            for (String keyword : keywords) {
                if (paragraphs[0].toLowerCase().contains(keyword.toLowerCase())) {
                    score += 0.4;
                }
            }
        }
        
        // Check headings (marked with # in markdown)
        if (content.contains("#")) {
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.startsWith("#")) {
                    for (String keyword : keywords) {
                        if (line.toLowerCase().contains(keyword.toLowerCase())) {
                            score += 0.3;
                        }
                    }
                }
            }
        }
        
        // Normalize score to 0-1 range
        return Math.min(1.0, score);
    }

    private double analyzeNaturalUsage(String content, String[] keywords) {
        double score = 0.8; // Start with a base score
        
        // Check for keyword stuffing
        for (String keyword : keywords) {
            int occurrences = countKeywordOccurrences(content.toLowerCase(), keyword.toLowerCase());
            double density = (double) occurrences / content.split("\\s+").length;
            
            if (density > 0.1) { // More than 10% density for any keyword
                score -= 0.2;
            }
        }
        
        // Check for natural sentence flow
        String[] sentences = content.split("[.!?]+");
        for (String sentence : sentences) {
            for (String keyword : keywords) {
                if (sentence.toLowerCase().contains(keyword.toLowerCase())) {
                    if (sentence.split("\\s+").length < 5) {
                        score -= 0.1;
                    }
                }
            }
        }
        
        return Math.max(0.0, Math.min(1.0, score));
    }

    private int countKeywordOccurrences(String content, String keyword) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(keyword, index)) != -1) {
            count++;
            index += keyword.length();
        }
        return count;
    }

    private Map<String, Integer> getKeywordFrequency(String content, String[] keywords) {
        Map<String, Integer> frequency = new HashMap<>();
        for (String keyword : keywords) {
            frequency.put(keyword, countKeywordOccurrences(content, keyword.toLowerCase()));
        }
        return frequency;
    }

    private Map<String, Double> calculateKeywordDistribution(String content, String[] keywords) {
        Map<String, Double> distribution = new HashMap<>();
        int contentLength = content.length();
        
        for (String keyword : keywords) {
            List<Integer> positions = new ArrayList<>();
            int index = 0;
            while ((index = content.indexOf(keyword.toLowerCase(), index)) != -1) {
                positions.add(index);
                index += keyword.length();
            }
            
            double evenness = positions.isEmpty() ? 0.0 : 
                calculateDistributionEvenness(positions, contentLength);
            distribution.put(keyword, evenness);
        }
        
        return distribution;
    }

    private double calculateDistributionEvenness(List<Integer> positions, int contentLength) {
        if (positions.size() < 2) return 1.0;
        
        double expectedGap = (double) contentLength / positions.size();
        double totalDeviation = 0.0;
        
        for (int i = 1; i < positions.size(); i++) {
            double gap = positions.get(i) - positions.get(i-1);
            totalDeviation += Math.abs(gap - expectedGap) / expectedGap;
        }
        
        return 1.0 - (totalDeviation / positions.size());
    }
}
