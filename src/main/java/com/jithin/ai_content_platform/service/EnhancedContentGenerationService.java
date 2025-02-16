package com.jithin.ai_content_platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jithin.ai_content_platform.config.AdaptiveWeightConfig;
import com.jithin.ai_content_platform.util.JsonResponseHandler;
import org.springframework.beans.factory.annotation.Value;
import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.model.EnhancedTrendPattern;
import com.jithin.ai_content_platform.model.TrendData;
import com.jithin.ai_content_platform.model.User;
import com.jithin.ai_content_platform.payload.ContentRequest;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EnhancedContentGenerationService {

    @Autowired
    private OpenRouterService openRouterService;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.max-tokens}")
    private Integer maxTokens;

    @Autowired
    private TrendAnalysisService trendAnalysisService;

    @Autowired
    private ContextAwareSentimentService sentimentService;

    @Autowired
    private EnhancedWord2VecService word2VecService;

    @Autowired
    private AdaptiveWeightConfig weightConfig;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JsonResponseHandler jsonResponseHandler;
    
    @Autowired
    private RealTimeContentAdapter realTimeAdapter;

    public Content generateEnhancedContent(ContentRequest request, User user) {
        log.info("Generating enhanced content for user: {}", user.getUsername());
        try {
            // 1. Analyze current trends and patterns
            Map<String, Object> trendAnalysis = analyzeTrendsForContent(request);
            
            // 2. Generate initial content with trend-aware prompt
            String initialContent = generateInitialContent(request, trendAnalysis);
            
            // Clean and format the initial content
            initialContent = cleanAndFormatContent(initialContent);
            
            // 3. Analyze sentiment and context
            Map<String, Object> sentimentAnalysis = sentimentService.analyzeContextAwareSentiment(initialContent);
            
            // 4. Optimize content based on analysis
            String optimizedContent = optimizeContent(initialContent, trendAnalysis, sentimentAnalysis);
            
            // Clean and format the optimized content
            optimizedContent = cleanAndFormatContent(optimizedContent);
            
            // 5. Apply real-time adaptation
            Content enhancedContent = createEnhancedContent(request, user, optimizedContent, trendAnalysis, sentimentAnalysis);
            
            // 6. Adapt content in real-time based on latest trends
            List<TrendData> latestTrends = trendAnalysisService.getLatestTrends();
            Content adaptedContent = realTimeAdapter.adaptContent(enhancedContent, latestTrends);
            
            // 7. Return the final enhanced and adapted content
            return adaptedContent;
        } catch (Exception e) {
            log.error("Error generating enhanced content", e);
            throw new RuntimeException("Failed to generate enhanced content", e);
        }
    }

    private Map<String, Object> analyzeTrendsForContent(ContentRequest request) {
        Map<String, Object> analysis = new HashMap<>();
        
        try {
            // Analyze keywords using Word2Vec
            List<String> expandedKeywords = new ArrayList<>(Arrays.asList(request.getKeywords().split("\\s*,\\s*")));
            for (String keyword : Arrays.asList(request.getKeywords().split("\\s*,\\s*"))) {
                List<String> similarWords = word2VecService.findSimilarWords(keyword, 3);
                expandedKeywords.addAll(similarWords);
            }

            // Get trend patterns for each keyword
            Map<String, EnhancedTrendPattern> patterns = new HashMap<>();
            for (String keyword : expandedKeywords) {
                // Get historical data for the keyword
                List<Double> historicalValues = trendAnalysisService.getHistoricalTrendValues(keyword);
                List<java.time.LocalDateTime> timestamps = trendAnalysisService.getHistoricalTrendDates(keyword);
                
                if (!historicalValues.isEmpty()) {
                    EnhancedTrendPattern pattern = new AdvancedTrendPatternService()
                        .analyzeTrendPattern(historicalValues, timestamps);
                    patterns.put(keyword, pattern);
                }
            }

            // Calculate content weights based on trends
            Map<String, Double> contentWeights = weightConfig.getWeightsForContentType(request.getContentType());

            analysis.put("expandedKeywords", expandedKeywords);
            analysis.put("trendPatterns", patterns);
            analysis.put("contentWeights", contentWeights);
            
        } catch (Exception e) {
            log.error("Error analyzing trends for content", e);
        }
        
        return analysis;
    }

    private String generateInitialContent(ContentRequest request, Map<String, Object> trendAnalysis) {
        try {
            // Build an enhanced prompt using trend analysis
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("Generate high-quality content with the following specifications:\n\n");
            
            // Core content specifications
            promptBuilder.append("Content Specifications:\n");
            promptBuilder.append("Title: ").append(request.getTitle()).append("\n");
            promptBuilder.append("Type: ").append(request.getContentType()).append("\n");
            promptBuilder.append("Emotional Tone: ").append(request.getEmotionalTone()).append("\n");
            promptBuilder.append("Target Audience: ").append(request.getTargetAudience()).append("\n");
            promptBuilder.append("Writing Style: ").append(request.getWritingStyleSample()).append("\n");
            if (request.getContentLength() != null) {
                promptBuilder.append("Content Length: ").append(request.getContentLength()).append("\n");
            }

            // Add SEO optimization if requested
            if (request.isOptimizeForSEO()) {
                promptBuilder.append("\nSEO Requirements:\n");
                promptBuilder.append("- Optimize for search engines while maintaining natural flow\n");
                promptBuilder.append("- Include relevant keywords naturally\n");
                promptBuilder.append("- Use proper header hierarchy\n");
            }

            // Add trend-aware instructions
            @SuppressWarnings("unchecked")
            List<String> expandedKeywords = (List<String>) trendAnalysis.getOrDefault("expandedKeywords", new ArrayList<>());
            if (!expandedKeywords.isEmpty()) {
                promptBuilder.append("\nKey Topics to Cover:\n");
                expandedKeywords.stream()
                    .distinct()
                    .forEach(keyword -> promptBuilder.append("- ").append(keyword).append("\n"));
            }

            @SuppressWarnings("unchecked")
            Map<String, EnhancedTrendPattern> patterns = 
                (Map<String, EnhancedTrendPattern>) trendAnalysis.getOrDefault("trendPatterns", new HashMap<>());
            
            // Add trending topics and their patterns
            if (!patterns.isEmpty()) {
                promptBuilder.append("\nTrending Topics to Incorporate:\n");
                patterns.entrySet().stream()
                    .filter(entry -> entry.getValue() != null && entry.getValue().isSignificant())
                    .sorted((e1, e2) -> Double.compare(
                        e2.getValue().getConfidenceScore(),
                        e1.getValue().getConfidenceScore()))
                    .limit(5) // Focus on top 5 trends
                    .forEach(entry -> promptBuilder.append("- ").append(entry.getKey())
                        .append(" (").append(entry.getValue().getPatternType())
                        .append(", Score: ").append(String.format("%.2f", entry.getValue().getConfidenceScore()))
                        .append(")\n"));
            }

            // Add formatting and structure requirements
            promptBuilder.append("\nContent Structure and Formatting:\n");
            promptBuilder.append("1. Use Markdown formatting\n");
            promptBuilder.append("2. Start with an engaging introduction\n");
            promptBuilder.append("3. Use ## for main sections\n");
            promptBuilder.append("4. Use ### for subsections\n");
            promptBuilder.append("5. Use * for bullet points\n");
            promptBuilder.append("6. Include a clear conclusion\n");

            // Enhanced metadata handling
            if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
                promptBuilder.append("\nAdditional Requirements:\n");
                request.getMetadata().forEach((key, value) -> {
                    if (value != null) {
                        promptBuilder.append("- ").append(key).append(": ").append(value).append("\n");
                    }
                });
            }

            // Create chat completion request with enhanced system message
            ChatMessage systemMessage = new ChatMessage("system",
                "You are an expert content creator with deep understanding of current trends, " +
                "audience engagement, and content quality. Create content that is timely, " +
                "engaging, and valuable while maintaining proper formatting and structure. " +
                "Focus on creating authentic, well-researched content that resonates with " +
                "the target audience. Use proper Markdown formatting and ensure all text is " +
                "properly encoded without special characters.");

            ChatMessage userMessage = new ChatMessage("user", promptBuilder.toString());

            List<Map<String, String>> messages = Arrays.asList(
                Map.of("role", "system", "content", systemMessage.getContent()),
                Map.of("role", "user", "content", userMessage.getContent())
            );

            Map<String, Object> response = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.8,
                    "max_tokens", maxTokens,
                    "presence_penalty", 0.6,
                    "frequency_penalty", 0.3
                )
            );

            String rawContent = openRouterService.extractContentFromResponse(response);
            
            // For content generation, we want to preserve some markdown formatting
            // but still clean up any response artifacts
            String cleanedContent = jsonResponseHandler.cleanJsonResponse(rawContent);
            
            // Additional content-specific formatting
            return cleanAndFormatContent(cleanedContent);

        } catch (Exception e) {
            log.error("Error generating initial content", e);
            throw new RuntimeException("Failed to generate initial content", e);
        }
    }

    private String optimizeContent(
            String content,
            Map<String, Object> trendAnalysis,
            Map<String, Object> sentimentAnalysis) {
        log.debug("Optimizing content with trend and sentiment analysis");
        try {
            // Extract key metrics from sentiment analysis
            double overallSentiment = (double) sentimentAnalysis.getOrDefault("overall_sentiment", 0.0);
            @SuppressWarnings("unchecked")
            Map<String, Double> topicSentiments = 
                (Map<String, Double>) sentimentAnalysis.getOrDefault("topic_sentiments", new HashMap<>());

            // Build optimization prompt
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("Optimize the following content while maintaining its core message and formatting:\n\n");
            promptBuilder.append(content).append("\n\n");
            promptBuilder.append("Optimization Requirements:\n");

            // Add sentiment-based optimization instructions
            promptBuilder.append("1. Adjust emotional tone to achieve a sentiment score closer to ")
                .append(String.format("%.2f", overallSentiment))
                .append(" while maintaining authenticity\n");

            // Add topic-specific optimization instructions
            promptBuilder.append("2. Emphasize these high-impact topics with positive sentiment:\n");
            topicSentiments.entrySet().stream()
                .filter(entry -> entry.getValue() > 0.6) // Focus on strongly positive topics
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5) // Top 5 most positive topics
                .forEach(entry -> promptBuilder.append("   - ")
                    .append(entry.getKey())
                    .append(" (sentiment: ")
                    .append(String.format("%.2f", entry.getValue()))
                    .append(")\n"));

            // Add trend-aware optimization instructions
            @SuppressWarnings("unchecked")
            Map<String, EnhancedTrendPattern> patterns = 
                (Map<String, EnhancedTrendPattern>) trendAnalysis.getOrDefault("trendPatterns", new HashMap<>());
            
            if (!patterns.isEmpty()) {
                promptBuilder.append("\n3. Incorporate these trending topics naturally:\n");
                patterns.entrySet().stream()
                    .filter(entry -> entry.getValue() != null && entry.getValue().isSignificant())
                    .sorted((e1, e2) -> Double.compare(
                        e2.getValue().getConfidenceScore(),
                        e1.getValue().getConfidenceScore()))
                    .limit(3) // Top 3 most significant trends
                    .forEach(entry -> promptBuilder.append("   - ")
                        .append(entry.getKey())
                        .append(" (")
                        .append(entry.getValue().getPatternType())
                        .append(")\n"));
            }

            // Add quality and formatting requirements
            promptBuilder.append("\nQuality Requirements:\n");
            promptBuilder.append("1. Maintain proper Markdown formatting\n");
            promptBuilder.append("2. Ensure clear section headers (##)\n");
            promptBuilder.append("3. Use bullet points (*) for lists\n");
            promptBuilder.append("4. Keep paragraphs concise and well-structured\n");
            promptBuilder.append("5. Maintain consistent tone and style\n");

            // Create chat completion request with enhanced system message
            ChatMessage systemMessage = new ChatMessage("system",
                "You are an expert content optimizer with deep understanding of audience engagement " +
                "and content quality. Optimize the content while preserving its core message, " +
                "maintaining proper formatting, and ensuring natural integration of trends and topics. " +
                "Focus on readability, engagement, and maintaining the author's authentic voice.");

            ChatMessage userMessage = new ChatMessage("user", promptBuilder.toString());

            List<Map<String, String>> messages = Arrays.asList(
                Map.of("role", "system", "content", systemMessage.getContent()),
                Map.of("role", "user", "content", userMessage.getContent())
            );

            Map<String, Object> response = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", maxTokens
                )
            );

            String optimizedContent = openRouterService.extractContentFromResponse(response);

            // Clean and format the optimized content
            return cleanAndFormatContent(optimizedContent);

        } catch (Exception e) {
            log.error("Error optimizing content", e);
            return content; // Return original content if optimization fails
        }
    }

    private Content createEnhancedContent(
            ContentRequest request,
            User user,
            String optimizedContent,
            Map<String, Object> trendAnalysis,
            Map<String, Object> sentimentAnalysis) {
        
        Content content = new Content();
        content.setTitle(request.getTitle());
        content.setContentBody(optimizedContent);
        content.setContentType(request.getContentType());
        content.setUser(user);
        content.setCreatedAt(java.time.LocalDateTime.now());
        content.setStatus("GENERATED");
        content.setCategory(request.getCategory() != null ? request.getCategory() : "general");

        try {
            // Store analysis results
            content.setTrendData(objectMapper.writeValueAsString(trendAnalysis));
            content.setAnalyzedSentimentMap(sentimentAnalysis);

            // Calculate and store engagement prediction
            double predictedEngagement = calculatePredictedEngagement(
                trendAnalysis,
                sentimentAnalysis
            );
            content.setEngagement(predictedEngagement);

            // Store optimization metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("optimizationVersion", "2.0");
            metadata.put("trendConfidence", calculateTrendConfidence(trendAnalysis));
            metadata.put("sentimentConfidence", sentimentAnalysis.get("confidence_score"));
            metadata.put("predictedEngagement", predictedEngagement);
            content.setMetrics(objectMapper.writeValueAsString(metadata));

        } catch (Exception e) {
            log.error("Error creating enhanced content", e);
        }

        return content;
    }

    private double calculatePredictedEngagement(
            Map<String, Object> trendAnalysis,
            Map<String, Object> sentimentAnalysis) {
        try {
            double trendScore = calculateTrendScore(trendAnalysis);
            double sentimentScore = (double) sentimentAnalysis.get("overall_sentiment");
            
            // Weight the scores (can be adjusted based on historical performance)
            return (trendScore * 0.6) + (sentimentScore * 0.4);
        } catch (Exception e) {
            log.error("Error calculating predicted engagement", e);
            return 0.5; // Default middle value
        }
    }

    private double calculateTrendScore(Map<String, Object> trendAnalysis) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, EnhancedTrendPattern> patterns = 
                (Map<String, EnhancedTrendPattern>) trendAnalysis.get("trendPatterns");
            
            return patterns.values().stream()
                .mapToDouble(pattern -> 
                    pattern.getTrendStrength() * pattern.getConfidenceScore())
                .average()
                .orElse(0.5);
        } catch (Exception e) {
            log.error("Error calculating trend score", e);
            return 0.5;
        }
    }

    private double calculateTrendConfidence(Map<String, Object> trendAnalysis) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, EnhancedTrendPattern> patterns = 
                (Map<String, EnhancedTrendPattern>) trendAnalysis.get("trendPatterns");
            
            return patterns.values().stream()
                .mapToDouble(EnhancedTrendPattern::getConfidenceScore)
                .average()
                .orElse(0.5);
        } catch (Exception e) {
            log.error("Error calculating trend confidence", e);
            return 0.5;
        }
    }

    /**
     * Generates a list of content improvement suggestions using OpenAI and various content analysis metrics
     * @param content The content to analyze and generate improvements for
     * @return List of improvement suggestions
     */
    public List<String> generateContentImprovements(Content content) {
        try {
            // 1. Get sentiment and trend analysis for context
            Map<String, Object> sentimentAnalysis = sentimentService.analyzeContextAwareSentiment(content.getContentBody());
            Map<String, Object> trendAnalysis = analyzeTrendsForContent(new ContentRequest(content));

            // Build a comprehensive prompt using all available data
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("As an expert content analyst, provide specific, actionable improvements for the following content.\n\n");
            promptBuilder.append("Content:\n").append(content.getContentBody()).append("\n\n");
            
            // Add sentiment context
            promptBuilder.append("Current Content Analysis:\n");
            promptBuilder.append("- Overall Sentiment: ").append(sentimentAnalysis.get("overall_sentiment")).append("\n");
            
            // Add trend context
            @SuppressWarnings("unchecked")
            Map<String, EnhancedTrendPattern> patterns = 
                (Map<String, EnhancedTrendPattern>) trendAnalysis.get("trendPatterns");
            if (patterns != null && !patterns.isEmpty()) {
                promptBuilder.append("- Current Trends:\n");
                patterns.forEach((keyword, pattern) -> {
                    if (pattern.isSignificant()) {
                        promptBuilder.append("  * ").append(keyword)
                            .append(" (").append(pattern.getPatternType()).append(")\n");
                    }
                });
            }

            promptBuilder.append("\nProvide 3-5 specific, actionable improvements that will enhance this content's effectiveness, ");
            promptBuilder.append("considering current trends, sentiment analysis, and engagement potential. ");
            promptBuilder.append("Focus on clarity, engagement, and impact.");

            // Create chat completion request
            List<ChatMessage> messages = Arrays.asList(
                new ChatMessage("system", 
                    "You are an expert content improvement specialist. Provide clear, specific, and actionable suggestions."),
                new ChatMessage("user", promptBuilder.toString())
            );

            List<Map<String, String>> messagesList = messages.stream()
                .map(msg -> Map.of("role", msg.getRole(), "content", msg.getContent()))
                .collect(Collectors.toList());

            Map<String, Object> response = openRouterService.createChatCompletion(
                model,
                messagesList,
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", maxTokens
                )
            );

            String responseContent = openRouterService.extractContentFromResponse(response);

            // Process and format the suggestions
            return Arrays.stream(responseContent.split("\n"))
                .filter(line -> line.trim().startsWith("-") || line.trim().matches("\\d+\\..*"))
                .map(line -> line.replaceFirst("^[-\\d.\\s]+", "").trim())
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error generating content improvements: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Cleans and formats content to ensure proper encoding and formatting
     * @param content The raw content to clean and format
     * @return Properly formatted and encoded content
     */
    private String cleanAndFormatContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            log.warn("Received null or empty content to clean");
            return "";
        }

        try {
            // Step 1: Basic text normalization
            content = content.replaceAll("[\\p{Cc}\\p{Cf}\\p{Co}\\p{Cn}]", "");
            content = content.replaceAll("\\r\\n", "\\n");
            content = content.replaceAll("\\r", "\\n");
            content = content.replaceAll("\\n{3,}", "\\n\\n");
            
            // Step 2: Fix Markdown formatting
            content = content.replaceAll("(?m)^(#+)(\\w)", "$1 $2");
            content = content.replaceAll("(?m)^(#+.*?)\\n([^\\n])", "$1\\n\\n$2");
            content = content.replaceAll("(?m)^([*-])(\\w)", "$1 $2");
            content = content.replaceAll("(?m)^\\s*[-*]\\s*([^\\n]*?)\\s*$", "* $1");
            
            // Step 3: Fix character encoding
            content = content.replaceAll("[“”]", "\"");
            content = content.replaceAll("[‘’]", "'");
            content = content.replaceAll("…", "...");
            content = content.replaceAll("–", "-");
            content = content.replaceAll("—", "--");
            content = content.replaceAll("•", "*");
            
            // Step 4: Clean up spacing and punctuation
            content = content.replaceAll("\\s+([.,!?;:])", "$1");
            content = content.replaceAll("([.,!?;:])(?!\\s|$)", "$1 ");
            content = content.replaceAll("\\s+", " ");
            content = content.replaceAll("\\.(\\S)", ". $1");
            
            return content.trim();
            
        } catch (Exception e) {
            log.error("Error cleaning content: {}", e.getMessage());
            return content.trim(); // Return original content if cleaning fails
        }
    }
}
