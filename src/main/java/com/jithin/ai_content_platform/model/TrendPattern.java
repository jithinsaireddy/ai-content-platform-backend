package com.jithin.ai_content_platform.model;

/**
 * Enum representing different types of trend patterns that can be detected in time series data.
 * Each pattern has specific characteristics and implications for content strategy.
 */
public enum TrendPattern {
    /**
     * Consistent upward movement with low volatility
     */
    STEADY_RISE,

    /**
     * Consistent downward movement with low volatility
     */
    STEADY_DECLINE,

    /**
     * Upward movement with high volatility
     */
    VOLATILE_RISE,

    /**
     * Indicates insufficient data points for reliable pattern detection
     */
    INSUFFICIENT_DATA,

    /**
     * Downward movement with high volatility
     */
    VOLATILE_DECLINE,

    /**
     * Period of low volatility and minimal directional movement
     */
    CONSOLIDATION,

    /**
     * Sudden movement outside of established range
     */
    BREAKOUT,

    /**
     * Change in trend direction after sustained movement
     */
    REVERSAL,

    /**
     * Regular, cyclical pattern that repeats at fixed intervals
     */
    SEASONAL,

    /**
     * Indicates an error occurred during pattern analysis
     */
    ERROR,

    /**
     * Pattern cannot be clearly identified
     */
    UNDEFINED;

    /**
     * Get the recommended content strategy for this pattern
     */
    public String getRecommendedStrategy() {
        switch (this) {
            case STEADY_RISE:
                return "Capitalize on growing interest with in-depth content";
            case STEADY_DECLINE:
                return "Focus on differentiation and unique angles";
            case VOLATILE_RISE:
                return "Create timely, responsive content with regular updates";
            case VOLATILE_DECLINE:
                return "Monitor closely and prepare pivot strategies";
            case CONSOLIDATION:
                return "Build foundational content and establish authority";
            case BREAKOUT:
                return "Rapidly deploy targeted content to capture momentum";
            case REVERSAL:
                return "Adapt content strategy to align with new direction";
            case SEASONAL:
                return "Create content that aligns with seasonal trends and fluctuations";
            case INSUFFICIENT_DATA:
                return "Gather more data before making content decisions";
            case ERROR:
                return "Review and resolve errors before making content decisions";
            default:
                return "Continue monitoring and gather more data";
        }
    }

    /**
     * Get the confidence level for content planning with this pattern
     */
    public double getConfidenceLevel() {
        switch (this) {
            case STEADY_RISE:
            case STEADY_DECLINE:
                return 0.9;
            case CONSOLIDATION:
                return 0.8;
            case BREAKOUT:
            case REVERSAL:
                return 0.7;
            case VOLATILE_RISE:
            case VOLATILE_DECLINE:
                return 0.6;
            case SEASONAL:
                return 0.85;
            case INSUFFICIENT_DATA:
                return 0.2;
            case ERROR:
                return 0.0;
            default:
                return 0.4;
        }
    }

    /**
     * Get the recommended content update frequency for this pattern
     */
    public String getRecommendedUpdateFrequency() {
        switch (this) {
            case VOLATILE_RISE:
            case VOLATILE_DECLINE:
            case BREAKOUT:
                return "DAILY";
            case STEADY_RISE:
            case REVERSAL:
                return "WEEKLY";
            case STEADY_DECLINE:
            case CONSOLIDATION:
                return "MONTHLY";
            case SEASONAL:
                return "SEASONALLY";
            case INSUFFICIENT_DATA:
            case ERROR:
                return "AS_NEEDED";
            default:
                return "AS_NEEDED";
        }
    }
}
