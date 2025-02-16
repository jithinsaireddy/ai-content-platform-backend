package com.jithin.ai_content_platform.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class EnhancedTrendPattern {
    private TrendPattern basePattern;
    private double momentum;
    private double volatility;
    private double seasonality;
    private double breakoutProbability;
    private double reversalProbability;
    private List<Double> historicalValues;
    private List<LocalDateTime> timestamps;
    private double confidenceScore;
    private String dominantCycle;
    private double trendStrength;
    private double supportLevel;
    private double resistanceLevel;
    
    public enum PatternType {
        BREAKOUT,
        REVERSAL,
        CONSOLIDATION,
        CONTINUATION,
        SEASONAL_PEAK,
        SEASONAL_TROUGH
    }
    
    private PatternType patternType;
    
    public boolean isSignificant() {
        return confidenceScore >= 0.7 && trendStrength >= 0.6;
    }
    
    public boolean isBreakoutCandidate() {
        return breakoutProbability > 0.8 && volatility < 0.3;
    }
    
    public boolean isReversalCandidate() {
        return reversalProbability > 0.8 && trendStrength < 0.4;
    }
    
    public void calculateMetrics() {
        if (historicalValues == null || historicalValues.isEmpty()) {
            return;
        }

        // Calculate momentum (rate of change)
        momentum = calculateMomentum();
        
        // Calculate volatility
        volatility = calculateVolatility();
        
        // Calculate trend strength
        trendStrength = calculateTrendStrength();
        
        // Calculate breakout and reversal probabilities
        breakoutProbability = calculateBreakoutProbability();
        reversalProbability = calculateReversalProbability();
        
        // Calculate seasonality
        seasonality = calculateSeasonality();
        
        // Calculate confidence score
        confidenceScore = calculateConfidenceScore();
        
        // Calculate support and resistance levels
        calculateSupportAndResistanceLevels();
    }

    private double calculateMomentum() {
        // Filter out null values and ensure we have enough data
        List<Double> validValues = historicalValues.stream()
            .filter(value -> value != null)
            .collect(Collectors.toList());
            
        if (validValues.size() < 2) {
            return 0.0;
        }
        
        double recent = validValues.get(validValues.size() - 1);
        double previous = validValues.get(validValues.size() - 2);
        
        // Avoid division by zero
        if (previous == 0.0) {
            return 0.0;
        }
        
        return (recent - previous) / previous;
    }

    private double calculateVolatility() {
        if (historicalValues.size() < 2) return 0.0;
        
        double sum = 0.0;
        double mean = historicalValues.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
            
        for (Double value : historicalValues) {
            sum += Math.pow(value - mean, 2);
        }
        
        return Math.sqrt(sum / (historicalValues.size() - 1));
    }

    private double calculateTrendStrength() {
        if (historicalValues.size() < 2) return 0.0;
        
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        int n = historicalValues.size();
        
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += historicalValues.get(i);
            sumXY += i * historicalValues.get(i);
            sumX2 += i * i;
        }
        
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        return Math.abs(slope) / (1 + Math.abs(slope)); // Normalize to [0,1]
    }

    private double calculateBreakoutProbability() {
        if (historicalValues.size() < 5) return 0.0;
        
        double recentAvg = historicalValues.subList(historicalValues.size() - 5, historicalValues.size())
            .stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
            
        double historicalAvg = historicalValues.subList(0, historicalValues.size() - 5)
            .stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
            
        return recentAvg > historicalAvg ? 
            Math.min(1.0, (recentAvg - historicalAvg) / historicalAvg) : 0.0;
    }

    private double calculateReversalProbability() {
        if (historicalValues.size() < 5) return 0.0;
        
        double recentAvg = historicalValues.subList(historicalValues.size() - 5, historicalValues.size())
            .stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
            
        double historicalAvg = historicalValues.subList(0, historicalValues.size() - 5)
            .stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
            
        return recentAvg < historicalAvg ? 
            Math.min(1.0, (historicalAvg - recentAvg) / historicalAvg) : 0.0;
    }

    private double calculateSeasonality() {
        if (historicalValues.size() < 30 || timestamps.size() < 30) return 0.0;
        
        // Simple seasonality detection using autocorrelation
        double[] values = historicalValues.stream().mapToDouble(Double::doubleValue).toArray();
        double mean = historicalValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        double sumSquared = 0.0;
        for (double value : values) {
            sumSquared += Math.pow(value - mean, 2);
        }
        
        double variance = sumSquared / values.length;
        if (variance == 0.0) return 0.0;
        
        // Calculate autocorrelation with 7-day lag (weekly pattern)
        double autoCorr = 0.0;
        for (int i = 7; i < values.length; i++) {
            autoCorr += (values[i] - mean) * (values[i - 7] - mean);
        }
        
        autoCorr /= (values.length - 7) * variance;
        return Math.max(0.0, autoCorr); // Normalize to [0,1]
    }

    private double calculateConfidenceScore() {
        // Combine multiple factors for confidence score
        double momentumFactor = Math.abs(momentum) < 0.5 ? 1.0 : 0.5;
        double volatilityFactor = volatility < 0.3 ? 1.0 : 0.5;
        double strengthFactor = trendStrength;
        double seasonalityFactor = seasonality > 0.5 ? 1.0 : seasonality;
        
        return (momentumFactor + volatilityFactor + strengthFactor + seasonalityFactor) / 4.0;
    }

    private void calculateSupportAndResistanceLevels() {
        if (historicalValues.size() < 10) return;
        
        List<Double> sortedValues = historicalValues.stream()
            .sorted()
            .collect(Collectors.toList());
            
        int size = sortedValues.size();
        supportLevel = sortedValues.get(size / 4); // 25th percentile
        resistanceLevel = sortedValues.get(3 * size / 4); // 75th percentile
    }

    public String getRecommendedAction() {
        if (isBreakoutCandidate()) {
            return "MONITOR_FOR_BREAKOUT";
        } else if (isReversalCandidate()) {
            return "PREPARE_FOR_REVERSAL";
        } else if (seasonality > 0.7) {
            return "FOLLOW_SEASONAL_PATTERN";
        } else if (trendStrength > 0.8 && confidenceScore > 0.7) {
            return "STRONG_TREND_CONTINUE";
        } else if (volatility > 0.5) {
            return "HIGH_VOLATILITY_CAUTION";
        } else {
            return "MAINTAIN_CURRENT_STRATEGY";
        }
    }
}
