package com.jithin.ai_content_platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableScheduling
public class AdaptiveWeightConfig {
    private final Map<String, Map<String, Double>> contentTypeWeights = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> contentTypePerformance = new ConcurrentHashMap<>();
    
    // Default weights for different content types
    private static final Map<String, Map<String, Double>> DEFAULT_WEIGHTS = new HashMap<>();
    
    static {
        // Blog posts
        Map<String, Double> blogWeights = new HashMap<>();
        blogWeights.put("trend", 0.4);
        blogWeights.put("sentiment", 0.3);
        blogWeights.put("engagement", 0.3);
        DEFAULT_WEIGHTS.put("blog", blogWeights);
        
        // Social media posts
        Map<String, Double> socialWeights = new HashMap<>();
        socialWeights.put("trend", 0.5);
        socialWeights.put("sentiment", 0.3);
        socialWeights.put("engagement", 0.2);
        DEFAULT_WEIGHTS.put("social", socialWeights);
        
        // News articles
        Map<String, Double> newsWeights = new HashMap<>();
        newsWeights.put("trend", 0.6);
        newsWeights.put("sentiment", 0.2);
        newsWeights.put("engagement", 0.2);
        DEFAULT_WEIGHTS.put("news", newsWeights);
    }
    
    public Map<String, Double> getWeightsForContentType(String contentType) {
        return contentTypeWeights.computeIfAbsent(contentType, 
            k -> new HashMap<>(DEFAULT_WEIGHTS.getOrDefault(k, DEFAULT_WEIGHTS.get("blog"))));
    }
    
    public void updateWeights(String contentType, String metric, double performance) {
        Map<String, Integer> performanceMap = contentTypePerformance.computeIfAbsent(contentType, k -> new HashMap<>());
        Map<String, Double> weights = getWeightsForContentType(contentType);
        
        // Update performance count
        performanceMap.merge(metric, 1, Integer::sum);
        
        // Calculate new weight based on performance
        double totalPerformance = performanceMap.values().stream()
            .mapToDouble(Integer::doubleValue)
            .sum();
        
        double newWeight = performance / totalPerformance;
        
        // Apply smoothing to avoid dramatic changes
        double currentWeight = weights.getOrDefault(metric, DEFAULT_WEIGHTS.get("blog").get(metric));
        double smoothedWeight = (currentWeight * 0.7) + (newWeight * 0.3);
        
        // Update weight
        weights.put(metric, smoothedWeight);
        
        // Normalize weights
        normalizeWeights(weights);
    }
    
    private void normalizeWeights(Map<String, Double> weights) {
        double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        weights.replaceAll((k, v) -> v / sum);
    }
    
    public void resetWeights(String contentType) {
        contentTypeWeights.put(contentType, new HashMap<>(DEFAULT_WEIGHTS.getOrDefault(contentType, DEFAULT_WEIGHTS.get("blog"))));
        contentTypePerformance.remove(contentType);
    }
}
