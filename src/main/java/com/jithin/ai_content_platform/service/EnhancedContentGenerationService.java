package com.jithin.ai_content_platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jithin.ai_content_platform.config.AdaptiveWeightConfig;
import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.util.JsonResponseHandler;
import org.springframework.beans.factory.annotation.Value;
import com.jithin.ai_content_platform.model.EnhancedTrendPattern;
import com.jithin.ai_content_platform.model.TrendData;
import com.jithin.ai_content_platform.model.User;
import com.jithin.ai_content_platform.payload.ContentRequest;
import com.jithin.ai_content_platform.repository.ContentRepository;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.text.BreakIterator;

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

    @Autowired
    private StanfordCoreNLP pipeline;  // Stanford NLP pipeline

    @Autowired
    private MLPredictionService mlPredictionService;

    @Autowired
    private ContentLocalizationService localizationService;

    @Autowired
    private ContentRepository contentRepository;

    private String extractContentFromMap(Map<String, Object> contentMap) {
        if (contentMap == null || contentMap.isEmpty()) {
            log.warn("Empty content map provided");
            return "";
        }

        // Try multiple possible keys for content
        String[] contentKeys = {"content", "text", "body", "result", "output", "generated_content"};

        for (String key : contentKeys) {
            Object content = contentMap.get(key);
            if (content instanceof String && !((String) content).isEmpty()) {
                return (String) content;
            }
        }

        // If no content found, log the entire map for debugging
        log.warn("No standard content key found. Full map: {}", contentMap);
        return "";
    }

    private Map<String, Object> performStanfordSentimentAnalysis(String text) {
        Map<String, Object> sentimentMap = new HashMap<>();
        if (pipeline != null) {
            try {
                Annotation annotation = new Annotation(text);
                pipeline.annotate(annotation);

                List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
                if (sentences != null && !sentences.isEmpty()) {
                    // Get the sentiment of the first sentence as overall sentiment
                    String sentiment = sentences.get(0).get(SentimentCoreAnnotations.SentimentClass.class);
                    sentimentMap.put("sentiment", sentiment.toLowerCase());
                }
            } catch (Exception e) {
                log.error("Error performing Stanford sentiment analysis", e);
            }
        }
        return sentimentMap;
    }

    private double convertSentimentToScore(String sentiment) {
        return switch (sentiment) {
            case "very positive" -> 1.0;
            case "positive" -> 0.75;
            case "neutral" -> 0.5;
            case "negative" -> 0.25;
            case "very negative" -> 0.0;
            default -> 0.5;
        };
    }

    @Transactional
    public Content generateEnhancedContent(ContentRequest request, User user) {
        try {
            // Create initial content object
            Content content = new Content();
            content.setTitle(request.getTitle());
            content.setContentType(request.getContentType());
            content.setCategory(request.getCategory());
            content.setKeywords(request.getKeywords());
            content.setEmotionalTone(request.getEmotionalTone());
            content.setWritingStyle(request.getWritingStyleSample());
            content.setRegion(request.getRegion());
            content.setOptimizeForSeo(request.isOptimizeForSEO());
            content.setUser(user);
            content.setCreatedAt(LocalDateTime.now());
            content.setUpdatedAt(LocalDateTime.now());
            content.setStatus("GENERATING");

            // Generate initial content using OpenRouter
            String prompt = buildEnhancedPrompt(request, new HashMap<>());
            Map<String, Object> generationResponse = openRouterService.createChatCompletion(
                model,
                Arrays.asList(
                    Map.of(
                        "role", "system",
                        "content", "You are an expert content creator specializing in creating engaging, informative, and well-researched content. Your writing is clear, engaging, and optimized for both human readers and search engines."
                    ),
                    Map.of(
                        "role", "user",
                        "content", prompt
                    )
                ),
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", maxTokens
                )
            );

            String generatedContent = openRouterService.extractContentFromResponse(generationResponse);
            if (generatedContent == null || generatedContent.trim().isEmpty()) {
                throw new RuntimeException("Failed to generate content");
            }

            // Set the generated content
            content.setContentBody(generatedContent);

            // Get trend analysis
            Map<String, Object> trendAnalysis = analyzeTrendsForContent(request);
            
            // Get sentiment analysis
            Map<String, Object> sentimentAnalysis = sentimentService.analyzeContextAwareSentiment(generatedContent);

            // Optimize content based on trend and sentiment analysis
            String optimizedContent = cleanAndFormatContent(generatedContent);
            content.setContentBody(optimizedContent);

            // Analyze sentiment using both Stanford NLP and existing sentiment service
            Map<String, Object> stanfordSentiment = performStanfordSentimentAnalysis(optimizedContent);

            // Combine sentiment analyses
            if (stanfordSentiment != null && stanfordSentiment.containsKey("sentiment")) {
                String sentiment = (String) stanfordSentiment.get("sentiment");
                double stanfordScore = convertSentimentToScore(sentiment);

                // Add Stanford sentiment to the analysis
                sentimentAnalysis.put("stanford_sentiment", sentiment);
                sentimentAnalysis.put("stanford_sentiment_score", stanfordScore);

                // Average with existing sentiment if available
                double existingScore = (double) sentimentAnalysis.getOrDefault("overall_sentiment", stanfordScore);
                double combinedScore = (existingScore + stanfordScore) / 2.0;
                sentimentAnalysis.put("overall_sentiment", combinedScore);
            }

            double overallSentiment = (double) sentimentAnalysis.getOrDefault("overall_sentiment", 0.0);
            log.info("Sentiment analysis completed. Overall sentiment score: {}", String.format("%.2f", overallSentiment));

            // Create enhanced content with all metrics
            Content enhancedContent = createEnhancedContent(request, user, optimizedContent, trendAnalysis, sentimentAnalysis);
            log.info("Enhanced content created with engagement score: {}", String.format("%.2f", enhancedContent.getEngagement()));

            // 7. Adapt content in real-time based on latest trends
            List<TrendData> latestTrends = trendAnalysisService.getLatestTrends();
            Content adaptedContent = realTimeAdapter.adaptContent(enhancedContent, latestTrends);
            log.info("Final content adaptation completed. Content ID: {}, Final engagement score: {}", adaptedContent.getId(), String.format("%.2f", adaptedContent.getEngagement()));

            return adaptedContent;

        } catch (Exception e) {
            log.error("Error generating enhanced content", e);
            throw new RuntimeException("Failed to generate enhanced content", e);
        }
    }

    private String buildEnhancedPrompt(ContentRequest request, Map<String, Object> trendAnalysis) {
        StringBuilder promptBuilder = new StringBuilder();

        // Add content requirements
        promptBuilder.append("Generate high-quality content with the following requirements:\n\n");

        // Core content specifications
        promptBuilder.append("Content Specifications:\n");
        promptBuilder.append("Topic: ").append(request.getTopic()).append("\n");
        if (request.getTitle() != null) {
            promptBuilder.append("Title: ").append(request.getTitle()).append("\n");
        }
        promptBuilder.append("Type: ").append(request.getContentType()).append("\n");
        promptBuilder.append("Emotional Tone: ").append(request.getEmotionalTone()).append("\n");
        promptBuilder.append("Target Audience: ").append(request.getTargetAudience()).append("\n");
        promptBuilder.append("Writing Style: ").append(request.getWritingStyleSample()).append("\n");
        if (request.getRegion() != null && !request.getRegion().isEmpty()) {
            promptBuilder.append("Region: ").append(request.getRegion()).append("\n");
            promptBuilder.append("- Use region-appropriate language and cultural references\n");
            promptBuilder.append("- Consider local trends and preferences\n");
            promptBuilder.append("- Ensure content resonates with regional audience\n");
        }
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

        // Add keywords and trending topics
        if (request.getKeywords() != null && !request.getKeywords().isEmpty()) {
            promptBuilder.append("\nPrimary Keywords:\n");
            // Convert keywords to List if it's not already
            List<String> keywords = new ArrayList<>(Arrays.asList(request.getKeywords().split(",\\s*")));
            keywords.forEach(keyword ->
                promptBuilder.append("* ").append(keyword.trim()).append("\n")
            );
        }

        // Add trend-aware suggestions
        @SuppressWarnings("unchecked")
        List<String> expandedKeywords = (List<String>) trendAnalysis.getOrDefault("expandedKeywords", new ArrayList<>());
        if (!expandedKeywords.isEmpty()) {
            promptBuilder.append("\nTrending Keywords (incorporate naturally):\n");
            expandedKeywords.stream()
                .limit(5)
                .forEach(keyword -> promptBuilder.append("* ").append(keyword).append("\n"));
        }

        // Add trending topics and their patterns
        @SuppressWarnings("unchecked")
        Map<String, EnhancedTrendPattern> patterns =
            (Map<String, EnhancedTrendPattern>) trendAnalysis.getOrDefault("trendPatterns", new HashMap<>());

        if (!patterns.isEmpty()) {
            promptBuilder.append("\nTrending Topics to Emphasize:\n");
            patterns.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().isSignificant())
                .sorted((e1, e2) -> Double.compare(
                    e2.getValue().getConfidenceScore(),
                    e1.getValue().getConfidenceScore()))
                .limit(3) // Top 3 most significant trends
                .forEach(entry -> promptBuilder.append("* ")
                    .append(entry.getKey())
                    .append(" (")
                    .append(entry.getValue().getPatternType())
                    .append(", Score: ")
                    .append(String.format("%.2f", entry.getValue().getConfidenceScore()))
                    .append(")\n"));
        }

        // Add sentiment and emotional requirements
        promptBuilder.append("\nTone and Sentiment Requirements:\n");
        promptBuilder.append("1. Maintain the specified emotional tone: ").append(request.getEmotionalTone()).append("\n");
        promptBuilder.append("2. Focus on positive sentiment while maintaining authenticity\n");
        promptBuilder.append("3. Emphasize high-impact topics with positive associations\n");

        // Add formatting requirements
        promptBuilder.append("\nFormatting Requirements:\n");
        promptBuilder.append("1. Use proper markdown formatting\n");
        promptBuilder.append("2. Start with an engaging introduction\n");
        promptBuilder.append("3. Use ## for main sections\n");
        promptBuilder.append("4. Use ### for subsections\n");
        promptBuilder.append("5. Use * for bullet points\n");
        promptBuilder.append("6. Include a clear conclusion\n");
        promptBuilder.append("7. Keep paragraphs concise and well-structured\n");
        promptBuilder.append("8. Maintain consistent tone and style throughout\n");

        // Add metadata-based requirements
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            promptBuilder.append("\nAdditional Requirements:\n");
            request.getMetadata().forEach((key, value) ->
                promptBuilder.append("* ").append(key).append(": ").append(value).append("\n")
            );
        }

        return promptBuilder.toString();
    }

    private Map<String, Object> analyzeTrendsForContent(ContentRequest request) {
        Map<String, Object> analysis = new HashMap<>();

        try {
            // Analyze keywords using Word2Vec
            List<String> expandedKeywords = new ArrayList<>(Arrays.asList(request.getKeywords().split("\\s*,\\s*")));
            log.info("Starting Word2Vec analysis for {} initial keywords", expandedKeywords.size());

            for (String keyword : Arrays.asList(request.getKeywords().split("\\s*,\\s*"))) {
                List<String> similarWords = word2VecService.findSimilarWords(keyword, 3);
                log.debug("Word2Vec found {} similar words for keyword: {}", similarWords.size(), keyword);
                expandedKeywords.addAll(similarWords);
            }
            log.info("Word2Vec analysis completed. Expanded {} initial keywords to {} total keywords",
                request.getKeywords().split("\\s*,\\s*").length, expandedKeywords.size());

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

    private double calculateSemanticRelevance(List<String> keywords, String content) {
        // Split content into words
        String[] contentWords = content.toLowerCase().split("\\W+");

        return keywords.stream()
            .mapToDouble(keyword -> {
                // Find similar words for the keyword
                List<String> similarWords = word2VecService.findSimilarWords(keyword, 3);

                // Calculate similarity with content words
                double maxSimilarity = similarWords.stream()
                    .flatMapToDouble(similarWord ->
                        Arrays.stream(contentWords)
                            .mapToDouble(word -> word2VecService.calculateCosineSimilarity(similarWord, word))
                    )
                    .max()
                    .orElse(0.0);

                return maxSimilarity;
            })
            .average()
            .orElse(0.0);
    }

    // Add this new method at the end of the class
    private void initializeRequiredMetrics(Content content) {
        try {
            // Get existing metrics if any
            Map<String, Object> metrics = content.getMetrics() != null && !content.getMetrics().isEmpty() ?
                objectMapper.readValue(content.getMetrics(), new TypeReference<Map<String, Object>>() {}) :
                new HashMap<>();

            // Initialize required metrics only if they don't exist
            metrics.putIfAbsent("timeDecay", 1.0);
            metrics.putIfAbsent("engagement", 0.0);
            metrics.putIfAbsent("marketPotential", 0.5);
            metrics.putIfAbsent("competitor", 0.5);
            metrics.putIfAbsent("seasonality", 0.5);
            metrics.putIfAbsent("relevance", 0.5);
            metrics.putIfAbsent("virality", 0.0);
            metrics.putIfAbsent("momentum", 0.0);

            // Save metrics back to content
            content.setMetrics(objectMapper.writeValueAsString(metrics));

            log.info("Initialized required metrics for content: {}", content.getId());
        } catch (JsonProcessingException e) {
            log.error("Error initializing metrics for content: {}", content.getId(), e);
        }
    }

    private String getSentimentDescription(double sentimentScore) {
        if (sentimentScore > 0.8) return "Very Positive";
        if (sentimentScore > 0.6) return "Positive";
        if (sentimentScore > 0.4) return "Neutral";
        if (sentimentScore > 0.2) return "Negative";
        return "Very Negative";
    }

    private void addSentimentGuidance(StringBuilder promptBuilder, double overallSentiment) {
        // Define sentiment ranges
        String sentimentDescription = getSentimentDescription(overallSentiment);

        promptBuilder.append("Sentiment Tone Guidance:\n");
        promptBuilder.append("1. Target Sentiment Range: ").append(sentimentDescription).append("\n");
        promptBuilder.append("2. Aim to maintain a tone that aligns with the following characteristics:\n");

        switch (sentimentDescription) {
            case "Very Positive":
                promptBuilder.append("   - Enthusiastic and optimistic language\n");
                promptBuilder.append("   - Emphasize potential and opportunities\n");
                break;
            case "Positive":
                promptBuilder.append("   - Constructive and encouraging tone\n");
                promptBuilder.append("   - Focus on solutions and positive outcomes\n");
                break;
            case "Neutral":
                promptBuilder.append("   - Balanced and objective language\n");
                promptBuilder.append("   - Present information without strong emotional bias\n");
                break;
            case "Negative":
                promptBuilder.append("   - Constructive criticism\n");
                promptBuilder.append("   - Highlight challenges with potential improvements\n");
                break;
            case "Very Negative":
                promptBuilder.append("   - Cautionary and analytical tone\n");
                promptBuilder.append("   - Emphasize critical analysis and potential risks\n");
                break;
        }
    }

    private Content createEnhancedContent(
        ContentRequest request,
        User user,
        String optimizedContent,
        Map<String, Object> trendAnalysis,
        Map<String, Object> sentimentAnalysis
    ) {
        try {
            // Validate inputs
            if (request == null) {
                throw new IllegalArgumentException("Content request cannot be null");
            }
            if (user == null) {
                throw new IllegalArgumentException("User cannot be null");
            }
            if (optimizedContent == null || optimizedContent.trim().isEmpty()) {
                throw new IllegalArgumentException("Content body cannot be null or empty");
            }

            Content content = new Content();
            content.setTitle(request.getTitle());
            content.setContentBody(optimizedContent);
            content.setUser(user);
            content.setCategory(request.getCategory() != null ? request.getCategory() : "general");
            content.setContentType(request.getContentType() != null ? request.getContentType() : "article");
            content.setCreatedAt(LocalDateTime.now());
            content.setStatus("GENERATED");

            // Get engagement prediction and set score
            Map<String, Object> engagementPrediction = mlPredictionService.predictEngagementMetrics(content);
            content.setEngagement(((Number) engagementPrediction.get("score")).doubleValue());

            // Set comprehensive metrics
            Map<String, Object> metrics = new HashMap<>();

            // Engagement metrics with detailed breakdown
            Map<String, Object> engagementMetrics = new HashMap<>();
            engagementMetrics.put("score", engagementPrediction.get("score"));
            engagementMetrics.put("confidence", engagementPrediction.get("confidence"));
            engagementMetrics.put("factors", engagementPrediction.get("factors"));
            metrics.put("engagement", engagementMetrics);

            // Sentiment metrics with detailed breakdown
            Map<String, Object> sentimentMetrics = new HashMap<>();
            sentimentMetrics.put("overall_score", sentimentAnalysis.get("overall_score"));
            sentimentMetrics.put("confidence", sentimentAnalysis.get("confidence_score"));
            sentimentMetrics.put("distribution", sentimentAnalysis.get("sentiment_distribution"));
            sentimentMetrics.put("entity_analysis", sentimentAnalysis.get("entity_sentiments"));
            sentimentMetrics.put("topic_analysis", sentimentAnalysis.get("topic_sentiments"));

            // Add Stanford sentiment if available
            if (sentimentAnalysis.containsKey("stanford_sentiment")) {
                sentimentMetrics.put("stanford_sentiment", sentimentAnalysis.get("stanford_sentiment"));
                sentimentMetrics.put("stanford_score", sentimentAnalysis.get("stanford_sentiment_score"));
            }
            metrics.put("overall_sentiment", sentimentAnalysis.getOrDefault("overall_sentiment", 0.5));
            metrics.put("sentiment", sentimentMetrics);

            // Trend metrics with detailed breakdown
            Map<String, Object> trendMetrics = new HashMap<>();
            Map<String, Object> contentWeights = (Map<String, Object>) trendAnalysis.get("contentWeights");
            if (contentWeights != null) {
                trendMetrics.put("dynamic_weight", contentWeights.get("dynamicWeight"));
                trendMetrics.put("momentum", contentWeights.get("momentum"));
                trendMetrics.put("seasonality", contentWeights.get("seasonality"));
                trendMetrics.put("virality", contentWeights.get("virality"));
                trendMetrics.put("relevance", contentWeights.get("relevance"));
            }
            trendMetrics.put("expanded_keywords", trendAnalysis.get("expandedKeywords"));
            trendMetrics.put("trend_patterns", trendAnalysis.get("trendPatterns"));
            metrics.put("trends", trendMetrics);

            // Add content quality metrics
            metrics.put("content_quality", Map.of(
                "readability_score", calculateReadabilityScore(optimizedContent),
                "structure_score", assessContentStructure(optimizedContent),
                "keyword_optimization", analyzeKeywordOptimization(optimizedContent, request)
            ));

            // Add performance predictions
            Map<String, Object> predictions = mlPredictionService.predictContentPerformance(content);
            metrics.put("performance_predictions", predictions);
            metrics.put("performance_indicators", Map.of(
                "expected_reach", predictions.getOrDefault("expected_reach", 0.0),
                "conversion_potential", predictions.getOrDefault("conversion_potential", 0.0),
                "audience_retention", predictions.getOrDefault("audience_retention", 0.0),
                "social_share_probability", predictions.getOrDefault("social_share_probability", 0.0)
            ));

            // Add system metrics
            metrics.put("generation_timestamp", LocalDateTime.now().toString());
            metrics.put("content_version", "1.0");
            metrics.put("content_type", request.getContentType());
            metrics.put("target_audience", request.getTargetAudience());
            if (request.getRegion() != null) {
                metrics.put("region", request.getRegion());
            }

            // Add sensitivity and localization if region is specified
            if (request.getRegion() != null) {
                Map<String, Object> sensitivityAnalysis = mlPredictionService.analyzeSensitivity(
                    optimizedContent,
                    request.getRegion()
                );
                Map<String, Object> localizedContent = localizationService.localizeContent(
                    content,
                    Collections.singletonList(request.getRegion())
                );
                metrics.put("sensitivity_analysis", sensitivityAnalysis);
                metrics.put("localization_metrics", localizedContent);
            }

            // Set content fields
            content.setContentBody(optimizedContent);
            content.setMetrics(objectMapper.writeValueAsString(metrics));
            content.setTrendData(objectMapper.writeValueAsString(trendAnalysis));
            content.setAnalyzedSentiment(objectMapper.writeValueAsString(sentimentAnalysis));
            content.setEngagementPredictions(objectMapper.writeValueAsString(predictions));
            content.setStatus("COMPLETED");

            // Save and return
            return contentRepository.save(content);

        } catch (Exception e) {
            log.error("Error creating enhanced content", e);
            throw new RuntimeException("Failed to create enhanced content: " + e.getMessage(), e);
        }
    }

    private double calculateEngagementScore(Map<String, Object> metrics) {
        double trendRelevance = ((Number) metrics.getOrDefault("trend_relevance", 0.5)).doubleValue();
        double sentimentScore = ((Number) metrics.getOrDefault("overall_sentiment", 0.5)).doubleValue();
        double predictedEngagement = ((Number) metrics.getOrDefault("predicted_engagement", 0.5)).doubleValue();

        // Weighted average of different factors
        return (trendRelevance * 0.3) + (sentimentScore * 0.3) + (predictedEngagement * 0.4);
    }

    private double calculateMarketPotential(Map<String, Object> trendAnalysis) {
        if (trendAnalysis == null) return 0.5;

        double relevance = ((Number) trendAnalysis.getOrDefault("relevance_score", 0.5)).doubleValue();
        double momentum = ((Number) trendAnalysis.getOrDefault("momentum_score", 0.0)).doubleValue();
        double seasonality = ((Number) trendAnalysis.getOrDefault("seasonality_score", 0.5)).doubleValue();

        return (relevance * 0.4) + (momentum * 0.4) + (seasonality * 0.2);
    }

    public Map<String, Object> calculateReadabilityScore(String content) {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            if (StringUtils.isBlank(content)) {
                return getDefaultReadabilityMetrics();
            }

            // Calculate basic text statistics
            String[] words = content.split("\\s+");
            int totalWords = words.length;
            int totalSyllables = countTotalSyllables(words);
            int totalSentences = countSentences(content);
            
            // Calculate Flesch Reading Ease Score
            double fleschScore = 206.835 - 1.015 * ((double) totalWords / totalSentences)
                                      - 84.6 * ((double) totalSyllables / totalWords);
            fleschScore = Math.max(0, Math.min(100, fleschScore));
            
            // Calculate average sentence length
            double avgSentenceLength = (double) totalWords / totalSentences;
            
            // Calculate average word length
            double avgWordLength = (double) content.replaceAll("\\s+", "").length() / totalWords;
            
            // Calculate paragraph coherence
            double paragraphCoherence = calculateParagraphCoherence(content);
            
            // Store all metrics
            metrics.put("flesch_reading_ease", fleschScore);
            metrics.put("avg_sentence_length", avgSentenceLength);
            metrics.put("avg_word_length", avgWordLength);
            metrics.put("paragraph_coherence", paragraphCoherence);
            metrics.put("total_words", totalWords);
            metrics.put("total_sentences", totalSentences);
            metrics.put("readability_level", getReadabilityLevel(fleschScore));
            
            log.debug("Calculated readability metrics for content with {} words", totalWords);
            
        } catch (Exception e) {
            log.error("Error calculating readability score", e);
            return getDefaultReadabilityMetrics();
        }
        
        return metrics;
    }

    public Map<String, Object> assessContentStructure(String content) {
        Map<String, Object> assessment = new HashMap<>();
        
        try {
            if (StringUtils.isBlank(content)) {
                return getDefaultStructureMetrics();
            }

            // Analyze heading structure
            Map<String, Object> headingMetrics = analyzeHeadingStructure(content);
            
            // Analyze paragraph distribution
            Map<String, Object> paragraphMetrics = analyzeParagraphDistribution(content);
            
            // Analyze formatting consistency
            Map<String, Object> formattingMetrics = analyzeFormattingConsistency(content);
            
            // Combine all metrics
            assessment.putAll(headingMetrics);
            assessment.putAll(paragraphMetrics);
            assessment.putAll(formattingMetrics);
            
            // Calculate overall structure score
            double overallScore = calculateOverallStructureScore(headingMetrics, paragraphMetrics, formattingMetrics);
            assessment.put("overall_structure_score", overallScore);
            
            log.debug("Completed content structure assessment");
            
        } catch (Exception e) {
            log.error("Error assessing content structure", e);
            return getDefaultStructureMetrics();
        }
        
        return assessment;
    }

    private Map<String, Object> getDefaultReadabilityMetrics() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("flesch_reading_ease", 0.0);
        defaults.put("avg_sentence_length", 0.0);
        defaults.put("avg_word_length", 0.0);
        defaults.put("paragraph_coherence", 0.0);
        defaults.put("total_words", 0);
        defaults.put("total_sentences", 0);
        defaults.put("readability_level", "Unknown");
        return defaults;
    }

        private Map<String, Object> analyzeKeywordOptimization(String content, ContentRequest request) {
        Map<String, Object> optimization = new HashMap<>();
        optimization.put("keyword_density", 0.8);
        optimization.put("keyword_placement", 0.9);
        optimization.put("natural_usage", 0.85);
        return optimization;
    }
    private Map<String, Object> getDefaultStructureMetrics() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("heading_hierarchy_score", 0.0);
        defaults.put("paragraph_distribution_score", 0.0);
        defaults.put("formatting_consistency_score", 0.0);
        defaults.put("overall_structure_score", 0.0);
        return defaults;
    }

    private int countSyllables(String word) {
        word = word.toLowerCase().replaceAll("[^a-zA-Z]", "");
        if (word.length() <= 3) return 1;
        
        int count = 0;
        boolean isPreviousVowel = false;
        
        for (int i = 0; i < word.length(); i++) {
            boolean isVowel = "aeiouy".indexOf(word.charAt(i)) != -1;
            if (isVowel && !isPreviousVowel) {
                count++;
            }
            isPreviousVowel = isVowel;
        }
        
        if (word.endsWith("e")) count--;
        if (word.endsWith("le") && word.length() > 2) count++;
        return Math.max(1, count);
    }

    private int countTotalSyllables(String[] words) {
        int total = 0;
        for (String word : words) {
            total += countSyllables(word);
        }
        return total;
    }

    private int countSentences(String text) {
        if (StringUtils.isBlank(text)) return 0;
        
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
        iterator.setText(text);
        
        int count = 0;
        while (iterator.next() != BreakIterator.DONE) {
            count++;
        }
        return Math.max(1, count);
    }

    private double calculateParagraphCoherence(String content) {
        String[] paragraphs = content.split("\n\n");
        if (paragraphs.length <= 1) return 1.0;
        
        double totalCoherence = 0.0;
        for (String paragraph : paragraphs) {
            String[] sentences = paragraph.split("[.!?]+");
            if (sentences.length > 1) {
                totalCoherence += calculateSentenceCoherence(sentences);
            }
        }
        
        return totalCoherence / paragraphs.length;
    }

    private double calculateSentenceCoherence(String[] sentences) {
        double coherence = 0.0;
        for (int i = 1; i < sentences.length; i++) {
            Set<String> prevWords = new HashSet<>(Arrays.asList(sentences[i-1].toLowerCase().split("\\s+")));
            Set<String> currWords = new HashSet<>(Arrays.asList(sentences[i].toLowerCase().split("\\s+")));
            
            Set<String> intersection = new HashSet<>(prevWords);
            intersection.retainAll(currWords);
            
            coherence += (double) intersection.size() / Math.max(prevWords.size(), currWords.size());
        }
        return coherence / (sentences.length - 1);
    }

    private String getReadabilityLevel(double fleschScore) {
        if (fleschScore >= 90) return "Very Easy";
        if (fleschScore >= 80) return "Easy";
        if (fleschScore >= 70) return "Fairly Easy";
        if (fleschScore >= 60) return "Standard";
        if (fleschScore >= 50) return "Fairly Difficult";
        if (fleschScore >= 30) return "Difficult";
        return "Very Difficult";
    }

    private Map<String, Object> analyzeHeadingStructure(String content) {
        Map<String, Object> metrics = new HashMap<>();
        Pattern headingPattern = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = headingPattern.matcher(content);
        
        Map<Integer, Integer> headingLevels = new HashMap<>();
        List<String> headingOrder = new ArrayList<>();
        
        while (matcher.find()) {
            int level = matcher.group(1).length();
            headingLevels.merge(level, 1, Integer::sum);
            headingOrder.add(String.valueOf(level));
        }
        
        double hierarchyScore = calculateHeadingHierarchyScore(headingLevels, headingOrder);
        metrics.put("heading_hierarchy_score", hierarchyScore);
        metrics.put("heading_count", headingLevels.values().stream().mapToInt(Integer::intValue).sum());
        metrics.put("heading_levels_used", headingLevels.keySet().size());
        
        return metrics;
    }

    private double calculateHeadingHierarchyScore(Map<Integer, Integer> headingLevels, List<String> headingOrder) {
        if (headingOrder.isEmpty()) return 0.0;
        
        double score = 1.0;
        
        // Check if H1 is used exactly once
        if (headingLevels.getOrDefault(1, 0) != 1) {
            score -= 0.2;
        }
        
        // Check for proper nesting
        for (int i = 1; i < headingOrder.size(); i++) {
            int prev = Integer.parseInt(headingOrder.get(i-1));
            int curr = Integer.parseInt(headingOrder.get(i));
            if (curr - prev > 1) {
                score -= 0.1;
            }
        }
        
        return Math.max(0.0, score);
    }

    private Map<String, Object> analyzeParagraphDistribution(String content) {
        Map<String, Object> metrics = new HashMap<>();
        String[] paragraphs = content.split("\n\n");
        
        if (paragraphs.length == 0) {
            return Map.of("paragraph_distribution_score", 0.0,
                         "avg_paragraph_length", 0.0,
                         "paragraph_count", 0);
        }
        
        double avgLength = Arrays.stream(paragraphs)
            .mapToInt(p -> p.split("\\s+").length)
            .average()
            .orElse(0.0);
        
        double distribution = calculateParagraphDistributionScore(paragraphs);
        
        metrics.put("paragraph_distribution_score", distribution);
        metrics.put("avg_paragraph_length", avgLength);
        metrics.put("paragraph_count", paragraphs.length);
        
        return metrics;
    }

    private double calculateParagraphDistributionScore(String[] paragraphs) {
        if (paragraphs.length <= 1) return 1.0;
        
        int[] lengths = Arrays.stream(paragraphs)
            .mapToInt(p -> p.split("\\s+").length)
            .toArray();
        
        double mean = Arrays.stream(lengths).average().orElse(0.0);
        double variance = Arrays.stream(lengths)
            .mapToDouble(l -> Math.pow(l - mean, 2))
            .average()
            .orElse(0.0);
        
        // Normalize variance to a 0-1 score (lower variance is better)
        return 1.0 / (1.0 + Math.sqrt(variance) / mean);
    }

    private Map<String, Object> analyzeFormattingConsistency(String content) {
        Map<String, Object> metrics = new HashMap<>();
        
        // Check for consistent list formatting
        double listConsistency = analyzeListConsistency(content);
        
        // Check for consistent emphasis usage
        double emphasisConsistency = analyzeEmphasisConsistency(content);
        
        // Check for consistent link formatting
        double linkConsistency = analyzeLinkConsistency(content);
        
        double overallConsistency = (listConsistency + emphasisConsistency + linkConsistency) / 3.0;
        
        metrics.put("formatting_consistency_score", overallConsistency);
        metrics.put("list_consistency_score", listConsistency);
        metrics.put("emphasis_consistency_score", emphasisConsistency);
        metrics.put("link_consistency_score", linkConsistency);
        
        return metrics;
    }

    private double analyzeListConsistency(String content) {
        Pattern bulletList = Pattern.compile("^\\s*[-*+]\\s+", Pattern.MULTILINE);
        Pattern numberList = Pattern.compile("^\\s*\\d+\\.\\s+", Pattern.MULTILINE);
        
        Matcher bulletMatcher = bulletList.matcher(content);
        Matcher numberMatcher = numberList.matcher(content);
        
        int bulletCount = 0;
        int numberCount = 0;
        
        while (bulletMatcher.find()) bulletCount++;
        while (numberMatcher.find()) numberCount++;
        
        if (bulletCount + numberCount == 0) return 1.0;
        
        // Prefer consistent list style (either all bullet or all number)
        return Math.abs(bulletCount - numberCount) / (double) (bulletCount + numberCount);
    }

    private double analyzeEmphasisConsistency(String content) {
        Pattern bold1 = Pattern.compile("\\*\\*[^*]+\\*\\*");
        Pattern bold2 = Pattern.compile("__[^_]+__");
        Pattern italic1 = Pattern.compile("\\*[^*]+\\*");
        Pattern italic2 = Pattern.compile("_[^_]+_");
        
        int bold1Count = countMatches(content, bold1);
        int bold2Count = countMatches(content, bold2);
        int italic1Count = countMatches(content, italic1);
        int italic2Count = countMatches(content, italic2);
        
        if (bold1Count + bold2Count + italic1Count + italic2Count == 0) return 1.0;
        
        // Prefer consistent emphasis style
        double boldConsistency = Math.abs(bold1Count - bold2Count) / (double) Math.max(1, bold1Count + bold2Count);
        double italicConsistency = Math.abs(italic1Count - italic2Count) / (double) Math.max(1, italic1Count + italic2Count);
        
        return (boldConsistency + italicConsistency) / 2.0;
    }

    private double analyzeLinkConsistency(String content) {
        Pattern mdLink = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
        Pattern bareUrl = Pattern.compile("https?://\\S+");
        
        int mdLinkCount = countMatches(content, mdLink);
        int bareUrlCount = countMatches(content, bareUrl);
        
        if (mdLinkCount + bareUrlCount == 0) return 1.0;
        
        // Prefer markdown-style links over bare URLs
        return mdLinkCount / (double) (mdLinkCount + bareUrlCount);
    }

    private int countMatches(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private double calculateOverallStructureScore(
            Map<String, Object> headingMetrics,
            Map<String, Object> paragraphMetrics,
            Map<String, Object> formattingMetrics) {
        
        double headingScore = (double) headingMetrics.get("heading_hierarchy_score");
        double paragraphScore = (double) paragraphMetrics.get("paragraph_distribution_score");
        double formattingScore = (double) formattingMetrics.get("formatting_consistency_score");
        
        // Weighted average of all scores
        return (headingScore * 0.4) + (paragraphScore * 0.4) + (formattingScore * 0.2);
    }

    private double calculateOverallEngagement(Map<String, Object> metrics) {
        double engagement = ((Number) metrics.get("engagement")).doubleValue();
        double marketPotential = ((Number) metrics.get("market_potential")).doubleValue();
        double contentQuality = ((Map<String, Number>) metrics.get("content_quality"))
            .values()
            .stream()
            .mapToDouble(Number::doubleValue)
            .average()
            .orElse(0.5);

        return (engagement * 0.4) + (marketPotential * 0.3) + (contentQuality * 0.3);
    }

    private double calculateOverallConfidence(Map<String, Object> predictions) {
        double engagementConf = ((Number) predictions.get("engagement_confidence")).doubleValue();
        double viralityConf = ((Number) predictions.get("virality_confidence")).doubleValue();
        double relevanceConf = ((Number) predictions.get("relevance_confidence")).doubleValue();

        return (engagementConf + viralityConf + relevanceConf) / 3.0;
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
