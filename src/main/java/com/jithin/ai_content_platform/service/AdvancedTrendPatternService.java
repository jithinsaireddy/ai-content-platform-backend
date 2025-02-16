package com.jithin.ai_content_platform.service;

import com.jithin.ai_content_platform.model.EnhancedTrendPattern;
import com.jithin.ai_content_platform.model.TrendPattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.TransformType;
import org.springframework.stereotype.Service;
import org.apache.commons.math3.complex.Complex;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class AdvancedTrendPatternService {

    private static final int MIN_DATA_POINTS = 10;
    private static final double BREAKOUT_THRESHOLD = 2.0;
    private static final double REVERSAL_THRESHOLD = -1.5;
    private static final int FFT_PADDING = 32;

    public EnhancedTrendPattern analyzeTrendPattern(List<Double> values, List<LocalDateTime> timestamps) {
        EnhancedTrendPattern pattern = new EnhancedTrendPattern();
        
        try {
            if (values.size() < MIN_DATA_POINTS) {
                pattern.setConfidenceScore(0.5);
                pattern.setBasePattern(TrendPattern.INSUFFICIENT_DATA);
                return pattern;
            }

            DescriptiveStatistics stats = new DescriptiveStatistics();
            values.forEach(stats::addValue);

            // Calculate basic statistics
            double mean = stats.getMean();
            double stdDev = stats.getStandardDeviation();
            double momentum = calculateMomentum(values);
            double volatility = calculateVolatility(values);

            // Detect trend strength and direction
            double trendStrength = calculateTrendStrength(values);
            pattern.setTrendStrength(trendStrength);

            // Calculate support and resistance levels
            double[] levels = calculateSupportResistanceLevels(values);
            pattern.setSupportLevel(levels[0]);
            pattern.setResistanceLevel(levels[1]);

            // Analyze seasonality
            double[] seasonalityInfo = analyzeSeasonality(values);
            pattern.setSeasonality(seasonalityInfo[0]);
            pattern.setDominantCycle(String.format("%.1f periods", seasonalityInfo[1]));

            // Calculate probabilities
            double breakoutProb = calculateBreakoutProbability(values, mean, stdDev);
            double reversalProb = calculateReversalProbability(values, trendStrength);
            pattern.setBreakoutProbability(breakoutProb);
            pattern.setReversalProbability(reversalProb);

            // Set basic metrics
            pattern.setMomentum(momentum);
            pattern.setVolatility(volatility);
            pattern.setHistoricalValues(values);
            pattern.setTimestamps(timestamps);

            // Determine pattern type
            determinePatternType(pattern);

            // Calculate confidence score
            double confidence = calculateConfidenceScore(pattern, values.size());
            pattern.setConfidenceScore(confidence);

        } catch (Exception e) {
            log.error("Error analyzing trend pattern", e);
            pattern.setConfidenceScore(0.0);
            pattern.setBasePattern(TrendPattern.ERROR);
        }

        return pattern;
    }

    private double calculateMomentum(List<Double> values) {
        if (values.size() < 2) return 0.0;

        // Calculate rate of change over different periods
        double shortTerm = calculateROC(values, 1);
        double mediumTerm = calculateROC(values, Math.min(5, values.size() - 1));
        double longTerm = calculateROC(values, Math.min(20, values.size() - 1));

        // Weight the different terms
        return (shortTerm * 0.5) + (mediumTerm * 0.3) + (longTerm * 0.2);
    }

    private double calculateROC(List<Double> values, int period) {
        if (values.size() <= period) return 0.0;
        double current = values.get(values.size() - 1);
        double previous = values.get(values.size() - 1 - period);
        return (current - previous) / previous;
    }

    private double calculateVolatility(List<Double> values) {
        if (values.size() < 2) return 0.0;

        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (int i = 1; i < values.size(); i++) {
            double returns = Math.log(values.get(i) / values.get(i - 1));
            stats.addValue(returns);
        }

        return Math.sqrt(stats.getVariance()) * Math.sqrt(252); // Annualized volatility
    }

    private double calculateTrendStrength(List<Double> values) {
        if (values.size() < 2) return 0.0;

        // Calculate linear regression
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        int n = values.size();

        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += values.get(i);
            sumXY += i * values.get(i);
            sumX2 += i * i;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double intercept = (sumY - slope * sumX) / n;

        // Calculate R-squared
        double meanY = sumY / n;
        double totalSS = 0, residualSS = 0;

        for (int i = 0; i < n; i++) {
            double predicted = slope * i + intercept;
            double actual = values.get(i);
            residualSS += Math.pow(actual - predicted, 2);
            totalSS += Math.pow(actual - meanY, 2);
        }

        return 1 - (residualSS / totalSS);
    }

    private double[] calculateSupportResistanceLevels(List<Double> values) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        values.forEach(stats::addValue);

        double median = stats.getPercentile(50);
        double support = stats.getPercentile(25);
        double resistance = stats.getPercentile(75);

        // Adjust levels based on recent price action
        int recentPeriod = Math.min(20, values.size());
        List<Double> recent = values.subList(values.size() - recentPeriod, values.size());
        DescriptiveStatistics recentStats = new DescriptiveStatistics();
        recent.forEach(recentStats::addValue);

        support = Math.min(support, recentStats.getMin());
        resistance = Math.max(resistance, recentStats.getMax());

        return new double[]{support, resistance};
    }

    private double[] analyzeSeasonality(List<Double> values) {
        // Pad the series to the next power of 2 for FFT
        int n = FFT_PADDING;
        while (n < values.size()) n *= 2;
        
        double[] paddedSeries = new double[n];
        for (int i = 0; i < values.size(); i++) {
            paddedSeries[i] = values.get(i);
        }
        for (int i = values.size(); i < n; i++) {
            paddedSeries[i] = values.get(values.size() - 1);
        }

        // Apply FFT
        FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);
        try {
            org.apache.commons.math3.complex.Complex[] fft = 
                transformer.transform(paddedSeries, TransformType.FORWARD);

            // Find dominant frequencies
            double maxMagnitude = 0.0;
            int dominantPeriod = 0;
            
            for (int i = 1; i < fft.length / 2; i++) {
                double magnitude = fft[i].abs();
                if (magnitude > maxMagnitude) {
                    maxMagnitude = magnitude;
                    dominantPeriod = i;
                }
            }

            // Calculate seasonality strength
            double totalPower = 0.0;
            double seasonalPower = 0.0;
            
            for (int i = 1; i < fft.length / 2; i++) {
                double magnitude = fft[i].abs();
                totalPower += magnitude;
                if (Math.abs(i - dominantPeriod) <= 1) {
                    seasonalPower += magnitude;
                }
            }

            double seasonalityStrength = totalPower > 0 ? seasonalPower / totalPower : 0.0;
            return new double[]{seasonalityStrength, n / (double) dominantPeriod};

        } catch (Exception e) {
            log.error("Error in FFT calculation", e);
            return new double[]{0.0, 0.0};
        }
    }

    private double calculateBreakoutProbability(List<Double> values, double mean, double stdDev) {
        if (values.size() < 2) return 0.0;

        double latest = values.get(values.size() - 1);
        double zScore = (latest - mean) / stdDev;
        
        // Calculate probability based on recent momentum and volatility
        double momentum = calculateMomentum(values);
        double volatility = calculateVolatility(values);
        
        double probBase = 1.0 / (1.0 + Math.exp(-zScore)); // Sigmoid function
        double momentumFactor = Math.max(0, momentum) * 0.3;
        double volatilityFactor = Math.max(0, 1 - volatility) * 0.2;
        
        return Math.min(1.0, probBase + momentumFactor + volatilityFactor);
    }

    private double calculateReversalProbability(List<Double> values, double trendStrength) {
        if (values.size() < 2) return 0.0;

        // Calculate overbought/oversold conditions
        DescriptiveStatistics stats = new DescriptiveStatistics();
        values.forEach(stats::addValue);
        
        double latest = values.get(values.size() - 1);
        double percentile = stats.getPercentile(75);
        double momentum = calculateMomentum(values);
        
        // Higher probability of reversal if:
        // 1. Price is at extreme levels (high percentile)
        // 2. Momentum is weakening
        // 3. Trend strength is high (suggesting potential exhaustion)
        
        double extremeLevel = Math.max(0, (latest - percentile) / percentile);
        double momentumWeakness = Math.max(0, -momentum);
        double trendExhaustion = Math.max(0, trendStrength - 0.7);
        
        return Math.min(1.0, (extremeLevel * 0.4) + (momentumWeakness * 0.3) + (trendExhaustion * 0.3));
    }

    private void determinePatternType(EnhancedTrendPattern pattern) {
        if (pattern.getBreakoutProbability() > 0.8) {
            pattern.setPatternType(EnhancedTrendPattern.PatternType.BREAKOUT);
            pattern.setBasePattern(TrendPattern.BREAKOUT);
        } else if (pattern.getReversalProbability() > 0.8) {
            pattern.setPatternType(EnhancedTrendPattern.PatternType.REVERSAL);
            pattern.setBasePattern(TrendPattern.REVERSAL);
        } else if (pattern.getSeasonality() > 0.7) {
            pattern.setPatternType(EnhancedTrendPattern.PatternType.SEASONAL_PEAK);
            pattern.setBasePattern(TrendPattern.SEASONAL);
        } else if (pattern.getTrendStrength() > 0.7) {
            pattern.setPatternType(EnhancedTrendPattern.PatternType.CONTINUATION);
            pattern.setBasePattern(TrendPattern.VOLATILE_RISE);
        } else {
            pattern.setPatternType(EnhancedTrendPattern.PatternType.CONSOLIDATION);
            pattern.setBasePattern(TrendPattern.CONSOLIDATION);
        }
    }

    private double calculateConfidenceScore(EnhancedTrendPattern pattern, int dataPoints) {
        // Factors affecting confidence:
        // 1. Amount of data
        double dataScore = Math.min(1.0, dataPoints / (double) MIN_DATA_POINTS);
        
        // 2. Pattern clarity
        double patternClarity = Math.max(
            pattern.getBreakoutProbability(),
            Math.max(pattern.getReversalProbability(), pattern.getSeasonality())
        );
        
        // 3. Trend strength
        double trendScore = pattern.getTrendStrength();
        
        // 4. Volatility penalty
        double volatilityScore = Math.max(0, 1 - pattern.getVolatility());
        
        // Weighted average of factors
        return (dataScore * 0.3) +
               (patternClarity * 0.3) +
               (trendScore * 0.2) +
               (volatilityScore * 0.2);
    }
}
