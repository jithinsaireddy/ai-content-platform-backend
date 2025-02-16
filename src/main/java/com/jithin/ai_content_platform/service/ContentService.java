package com.jithin.ai_content_platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jithin.ai_content_platform.exception.UserNotFoundException;
import com.jithin.ai_content_platform.model.CommunityModel;
import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.model.User;
import com.jithin.ai_content_platform.payload.ContentRequest;
import com.jithin.ai_content_platform.repository.CommunityModelRepository;
import com.jithin.ai_content_platform.repository.ContentRepository;


import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.langdetect.Language;
import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.langdetect.LanguageDetectorModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ContentService {

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OpenRouterService openRouterService;

    @Value("${openai.model}")
    private String model;

    @Autowired
    private TrendAnalysisService trendAnalysisService;

    @Autowired
    private FeedbackAnalysisService feedbackAnalysisService;

    @Autowired
    private EnhancedContentGenerationService enhancedContentGenerationService;

    @Autowired
    private MLPredictionService mlPredictionService;

    @Autowired
    private DynamicTrendWeightService dynamicTrendWeightService;

    @Autowired
    private ContextAwareSentimentService contextAwareSentimentService;

    @Autowired
    private ABTestingService abTestingService;
    
    @Autowired
    private CommunityAIService communityAIService;
    
    @Autowired
    private CommunityModelService communityModelService;

    @Autowired
    private CommunityModelRepository communityModelRepository;

    private DocumentCategorizerME categorizer;
    private LanguageDetectorME languageDetector;
    
    /**
     * Generates content using a community model
     */
    private Content generateWithCommunityModel(ContentRequest request, User user) {
        try {
            // Get the community model
            CommunityModel model = communityModelRepository.findById(request.getCommunityModelId())
                .orElseThrow(() -> new UserNotFoundException("Community model not found"));
            
            // Generate content using the community model
            String generatedContent = communityAIService.generateContentWithCommunityModel(request, model);
            if (generatedContent == null || generatedContent.trim().isEmpty()) {
                throw new RuntimeException("Failed to generate content with community model");
            }
            
            // Create initial content object with required fields
            Content content = Content.builder()
                .title(request.getTitle())
                .contentBody(generatedContent)
                .category(request.getCategory())
                .description(request.getDescription())
                .contentType("text") // Default to text for now
                .topic(request.getTopic() != null ? request.getTopic() : request.getCategory())
                .user(user)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .status("DRAFT")
                .build();

            try {
                // Add context-aware sentiment analysis
                Map<String, Object> sentimentAnalysis = contextAwareSentimentService.analyzeContextAwareSentiment(generatedContent);
                content.setAnalyzedSentimentMap(sentimentAnalysis != null ? sentimentAnalysis : new HashMap<>());

                // Calculate engagement potential using ML predictions
                Map<String, Object> engagementMetrics = mlPredictionService.predictEngagementMetrics(content);
                content.setEngagement(((Number) engagementMetrics.get("score")).doubleValue());

                // Add trend analysis
                Map<String, Object> trendData = trendAnalysisService.getIndustryTrends(request.getCategory());
                if (trendData != null) {
                    content.setTrendData(objectMapper.writeValueAsString(trendData));
                }

                // Add metadata about the community model and analysis
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("communityModelId", model.getId());
                metadata.put("communityModelName", model.getName());
                metadata.put("communityModelCategory", model.getCategory());
                metadata.put("generationTimestamp", LocalDateTime.now().toString());
                
                // Add analysis results to metadata
                if (sentimentAnalysis != null) {
                    metadata.put("overallSentiment", sentimentAnalysis.get("overall_sentiment"));
                    metadata.put("entitySentiments", sentimentAnalysis.get("entity_sentiments"));
                }
                if (engagementMetrics != null) {
                    metadata.put("predictedPerformance", engagementMetrics.get("score"));
                }
                if (trendData != null) {
                    metadata.put("trendRelevance", trendData.get("relevance_score"));
                    metadata.put("trendAlignment", trendData.get("alignment_score"));
                }
                
                content.setMetadata(objectMapper.writeValueAsString(metadata));

                // Initialize metrics
                Map<String, Integer> metrics = new HashMap<>();
                metrics.put("views", 0);
                metrics.put("likes", 0);
                metrics.put("shares", 0);
                metrics.put("comments", 0);
                content.setMetrics(objectMapper.writeValueAsString(metrics));
                
            } catch (Exception e) {
                log.warn("Error during content analysis: {}", e.getMessage());
                // Initialize empty JSON objects for required fields if analysis fails
                content.setAnalyzedSentimentMap(new HashMap<>());
                content.setTrendData("{}");
                content.setMetadata("{}");
                content.setMetrics("{\"views\": 0, \"likes\": 0, \"shares\": 0, \"comments\": 0}");
                content.setEngagement(0.0);
            }
            
            return contentRepository.save(content);
            
        } catch (Exception e) {
            log.error("Error generating content with community model: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate content with community model: " + e.getMessage(), e);
        }
    }
    private StanfordCoreNLP pipeline;
    private Properties props;

    /**
     * Calculates the distribution of sentiments across sentences
     * @param sentenceAnalysis List of sentence analysis results
     * @return Map containing sentiment distribution metrics
     */
    private Map<String, Object> calculateSentimentDistribution(List<Map<String, Object>> sentenceAnalysis) {
        Map<String, Object> distribution = new HashMap<>();
        
        // Count sentiments by category
        Map<String, Integer> sentimentCounts = new HashMap<>();
        Map<String, Double> weightedSentiments = new HashMap<>();
        
        for (Map<String, Object> analysis : sentenceAnalysis) {
            double sentiment = (double) analysis.get("sentiment");
            double importance = (double) analysis.get("importance");
            
            // Apply optimistic bias to sentiment scores
            sentiment = Math.min(4.0, sentiment * 1.15);  // Increase sentiment by 15%, capped at 4.0
            
            String sentimentCategory = categorizeSentiment(sentiment);
            sentimentCounts.merge(sentimentCategory, 1, Integer::sum);
            weightedSentiments.merge(sentimentCategory, importance * 1.1, Double::sum);  // Increase importance of positive sentiments
        }  // Added missing closing brace
        
        // Calculate percentages and weighted scores
        int totalSentences = sentenceAnalysis.size();
        Map<String, Double> percentages = new HashMap<>();
        Map<String, Double> weightedScores = new HashMap<>();
        
        for (String sentimentType : sentimentCounts.keySet()) {
            double percentage = (double) sentimentCounts.get(sentimentType) / totalSentences * 100;
            double weightedScore = weightedSentiments.get(sentimentType) / totalSentences;
            
            percentages.put(sentimentType, percentage);
            weightedScores.put(sentimentType, weightedScore);
        }
        
        distribution.put("counts", sentimentCounts);
        distribution.put("percentages", percentages);
        distribution.put("weighted_scores", weightedScores);
        
        return distribution;
    }
    
    /**
     * Categorizes a sentiment score into a named category
     * @param sentiment The sentiment score (0-4)
     * @return The sentiment category
     */
    private String categorizeSentiment(double sentiment) {
        // Adjusted thresholds to shift distribution more positive
        if (sentiment <= 0.4) { 
            return "very_negative"; 
        } else if (sentiment <= 1.3) { 
            return "negative"; 
        } else if (sentiment <= 2.2) { 
            return "neutral"; 
        } else if (sentiment <= 3.2) { 
            return "positive"; 
        } else { 
            return "very_positive"; 
        }
    } 

    @PostConstruct
    public void init() {
        try {
            props = new Properties();
            props.setProperty("annotators", "tokenize, ssplit, parse, sentiment");
            pipeline = new StanfordCoreNLP(props);

            try {
                initializeOpenNLPComponents();
            } catch (IOException e) {
                log.warn("OpenNLP models not found. Language detection and categorization will be disabled.");
                languageDetector = null;
                categorizer = null;
            }
        } catch (Exception e) {
            log.error("Error initializing services", e);
            throw new RuntimeException("Failed to initialize services: " + e.getMessage());
        }
    }

    private void initializeOpenNLPComponents() throws IOException {
        // Initialize OpenNLP language detector if model exists
        InputStream langModelIn = getClass().getResourceAsStream("/models/langdetect-183.bin");
        if (langModelIn != null) {
            LanguageDetectorModel model = new LanguageDetectorModel(langModelIn);
            languageDetector = new LanguageDetectorME(model);
            langModelIn.close();
        }

        // Initialize OpenNLP document categorizer if model exists
        InputStream docModelIn = getClass().getResourceAsStream("/models/en-doc-cat.bin");
        if (docModelIn != null) {
            DoccatModel docModel = new DoccatModel(docModelIn);
            categorizer = new DocumentCategorizerME(docModel);
            docModelIn.close();
        }
    }

    @Transactional
    public Content createContent(Content content, User user) {
        content.setUser(user);
        content.setCreatedAt(LocalDateTime.now());
        content.setStatus("DRAFT");

        try {
            // Initialize metrics
            Map<String, Integer> metrics = new HashMap<>();
            metrics.put("views", 0);
            metrics.put("likes", 0);
            metrics.put("shares", 0);
            metrics.put("comments", 0);
            content.setMetrics(objectMapper.writeValueAsString(metrics));
            
            // Convert to ContentRequest for enhanced generation
            ContentRequest request = new ContentRequest();
            request.setTitle(content.getTitle());
            request.setContentType(content.getContentType());
            request.setCategory(content.getCategory());
            request.setKeywords(content.getKeywords()); // Just pass the keywords string directly
            
            // Use enhanced content generation
            Content enhancedContent = enhancedContentGenerationService.generateEnhancedContent(request, user);
            
            // Merge enhanced content properties
            content.setContentBody(enhancedContent.getContentBody());
            content.setTrendData(enhancedContent.getTrendData());
            content.setAnalyzedSentimentMap(enhancedContent.getAnalyzedSentimentMap());
            content.setEngagement(enhancedContent.getEngagement());
            content.setMetadata(enhancedContent.getMetadata());
            
        } catch (JsonProcessingException e) {
            log.error("Error initializing content metrics", e);
        }

        return contentRepository.save(content);
    }

    private void initializeDefaultMetrics(Content content) {
        Map<String, Object> defaultMetrics = new HashMap<>();
        defaultMetrics.put("views", 0);
        defaultMetrics.put("likes", 0);
        defaultMetrics.put("shares", 0);
        defaultMetrics.put("comments", 0);
        defaultMetrics.put("engagement", 0);
        content.setMetricsMap(defaultMetrics);
    }

    @Transactional
    public Content generateContent(ContentRequest request, User user) {
        validateContentRequest(request);

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

        initializeDefaultMetrics(content);

        try {
            // Generate content using OpenAI
            String generatedContent = generateOpenAIContent(request);
            content.setContentBody(generatedContent);

            // Generate SEO suggestions based on the generated content
            String seoSuggestions = generateSEOSuggestions(generatedContent);
            content.setSeoSuggestions(seoSuggestions);

            // Apply feedback-based improvements
            applyFeedbackBasedImprovements(content);

            // Create variations for A/B testing
            List<String> variations = Arrays.asList(
                 "Engaging Headline 1: " + generatedContent,
    "Engaging Headline 2: " + generatedContent
            );
            

            // Invoke A/B Testing Service
            Map<String, Object> abTestResults = abTestingService.createABTest(content, variations);
            content.setAbTestResults(objectMapper.writeValueAsString(abTestResults));

            // Perform detailed sentiment analysis using existing service
            Map<String, Object> sentimentAnalysis = contextAwareSentimentService.analyzeContextAwareSentiment(generatedContent);
            
            // Extract detailed sentiment metrics
            Map<String, Object> detailedSentiment = new HashMap<>();
            detailedSentiment.put("overall_score", sentimentAnalysis.get("overall_sentiment"));
            detailedSentiment.put("entity_sentiments", sentimentAnalysis.get("entity_sentiments"));
            detailedSentiment.put("topic_sentiments", sentimentAnalysis.get("topic_sentiments"));
            detailedSentiment.put("sentence_analysis", sentimentAnalysis.get("sentence_analysis"));
            detailedSentiment.put("confidence_score", sentimentAnalysis.get("confidence_score"));
            
            // Calculate sentiment distribution
            Map<String, Object> sentimentDistribution = calculateSentimentDistribution(
                (List<Map<String, Object>>) sentimentAnalysis.get("sentence_analysis"));
            detailedSentiment.put("sentiment_distribution", sentimentDistribution);
            
            // Perform Stanford sentiment analysis
Map<String, Object> stanfordSentiment = performStanfordSentimentAnalysis(generatedContent);
if (stanfordSentiment != null && stanfordSentiment.containsKey("sentiment")) {
    String sentiment = (String) stanfordSentiment.get("sentiment");
    // Convert sentiment string to numeric value
   // Convert sentiment string to numeric value
double sentimentScore = switch (sentiment) {
    case "very positive" -> 1.0;
    case "positive" -> 0.75;
    case "neutral" -> 0.5;
    case "negative" -> 0.25;
    case "very negative" -> 0.0;
    default -> 0.5;
};

// Convert double to String before setting
content.setStanfordSentiment(String.valueOf(sentimentScore));
}
            content.setAnalyzedSentiment(objectMapper.writeValueAsString(detailedSentiment));

            // Calculate trend weights and patterns
            Map<String, Double> contentWeights = dynamicTrendWeightService.calculateContentWeights(content);
            
            // Get trend patterns and data
            Map<String, Object> trendData = new HashMap<>();
            try {
                Map<String, Object> trendPatterns = trendAnalysisService.analyzeTrends(Collections.singletonList(content));
                trendData.put("trendPatterns", trendPatterns);
                trendData.put("contentWeights", contentWeights);
                trendData.put("expandedKeywords", trendAnalysisService.findRelatedKeywords(content.getTitle()));
                
                // Get historical trend data
                List<Double> historicalValues = trendAnalysisService.getHistoricalTrendValues(content.getTitle());
                List<LocalDateTime> historicalDates = trendAnalysisService.getHistoricalTrendDates(content.getTitle());
                
                Map<String, Double> interestOverTime = new HashMap<>();
                if (historicalDates != null && !historicalDates.isEmpty() && 
                historicalValues != null && !historicalValues.isEmpty()) {
                for (int i = 0; i < historicalDates.size(); i++) {
                    interestOverTime.put(historicalDates.get(i).toString(), historicalValues.get(i));
                }
            } else {
                // Add default current timestamp with neutral value
                interestOverTime.put(LocalDateTime.now().toString(), 0.5);
            }
            trendData.put("interestOverTime", interestOverTime);
        } catch (Exception e) {
            log.warn("No timeline data found for content: {}", content.getTitle());
            trendData.put("interestOverTime", Collections.emptyMap());
        }
            content.setTrendData(objectMapper.writeValueAsString(trendData));

            // Get engagement prediction using existing service
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
            sentimentMetrics.put("overall_score", detailedSentiment.get("overall_score"));
            sentimentMetrics.put("confidence", detailedSentiment.get("confidence_score"));
            sentimentMetrics.put("distribution", detailedSentiment.get("sentiment_distribution"));
            sentimentMetrics.put("entity_analysis", detailedSentiment.get("entity_sentiments"));
            sentimentMetrics.put("topic_analysis", detailedSentiment.get("topic_sentiments"));
            metrics.put("sentiment", sentimentMetrics);
            
            // Trend metrics with detailed breakdown
            Map<String, Object> trendMetrics = new HashMap<>();
            trendMetrics.put("dynamic_weight", contentWeights.get("dynamicWeight"));
            trendMetrics.put("momentum", contentWeights.get("momentum"));
            trendMetrics.put("seasonality", contentWeights.get("seasonality"));
            trendMetrics.put("virality", contentWeights.get("virality"));
            trendMetrics.put("relevance", contentWeights.get("relevance"));
            metrics.put("trends", trendMetrics);
            
            // System metrics
            metrics.put("optimizationVersion", "2.0");
            metrics.put("analysisTimestamp", LocalDateTime.now().toString());
            content.setMetrics(objectMapper.writeValueAsString(metrics));

            // Set engagement score and status
            content.setStatus("COMPLETED");
            return contentRepository.save(content);

        } catch (Exception e) {
            log.error("Error generating content", e);
            throw new RuntimeException("Failed to generate content: " + e.getMessage());
        }
    }

    private String generateOpenAIContent(ContentRequest request) {
        String prompt = String.format(
            "Generate a %s about %s. Title: %s\n" +
            "Writing Style: %s\n" +
            "Emotional Tone: %s\n" +
            "Target Audience: %s\n" +
            "Keywords: %s\n\n" +
            "Please generate detailed, well-structured content that incorporates the specified keywords naturally.",
            request.getContentType(),
            request.getTopic(),
            request.getTitle(),
            request.getWritingStyleSample(),
            request.getEmotionalTone(),
            request.getTargetAudience(),
            request.getKeywords()
        );

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
            "role", "system",
            "content", "You are a professional content writer with expertise in creating engaging and informative content."
        ));
        messages.add(Map.of(
            "role", "user",
            "content", prompt
        ));

        try {
            int maxTokens = 4000; // Define a reasonable token limit for the response
            Map<String, Object> response = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", maxTokens
                )
            );
            
            return openRouterService.extractContentFromResponse(response);
        } catch (Exception e) {
            log.error("Error generating content with OpenRouter", e);
            throw new RuntimeException("Failed to generate content with OpenRouter: " + e.getMessage());
        }
    }

    private void validateContentRequest(ContentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Content request cannot be null");
        }
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (request.getContentType() == null || request.getContentType().trim().isEmpty()) {
            throw new IllegalArgumentException("Content type is required");
        }
    }

    @Transactional
    public Content analyzeContent(Content content) {
        try {
            // Perform OpenAI sentiment analysis
            Map<String, Double> openAiSentiment = analyzeSentiment(content.getContentBody());
            content.setAnalyzedSentiment(objectMapper.writeValueAsString(openAiSentiment));

            // Perform Stanford NLP sentiment analysis
            Map<String, Object> stanfordSentiment = performStanfordSentimentAnalysis(content.getContentBody());
            content.setStanfordSentiment(objectMapper.writeValueAsString(stanfordSentiment));

            // Generate content improvements using EnhancedContentGenerationService
            List<String> improvements = enhancedContentGenerationService.generateContentImprovements(content);
            content.setImprovementSuggestions(objectMapper.writeValueAsString(improvements));

            // Generate improved content
            String improvedContent = generateImprovedContent(content);
            content.setImprovedContent(improvedContent);

            content.setStatus("ANALYZED");
            return contentRepository.save(content);
        } catch (Exception e) {
            log.error("Error analyzing content", e);
            return content;
        }
    }

    @Transactional
    public void generateSampleContent(User user) {
        List<Content> sampleContents = Arrays.asList(
            createContent("Tech Innovation", "Exciting breakthrough in AI technology revolutionizes healthcare diagnostics", "The latest AI developments show promising results in early disease detection.", "TECHNOLOGY", "POSITIVE", user),
            createContent("Market Analysis", "Global markets face uncertainty amid economic challenges", "Investors remain cautious as various factors impact market stability.", "FINANCE", "NEGATIVE", user),
            createContent("Environmental News", "New sustainable energy projects launch worldwide", "Communities embrace renewable energy solutions for a greener future.", "ENVIRONMENT", "POSITIVE", user),
            createContent("Social Media Trends", "Social media usage patterns show concerning trends", "Studies reveal increasing addiction rates among young users.", "SOCIAL_MEDIA", "NEGATIVE", user),
            createContent("Startup Success", "Local startup secures major funding round", "Innovation and persistence lead to significant investment success.", "BUSINESS", "POSITIVE", user)
        );
        
        contentRepository.saveAll(sampleContents);
    }
    
    private Content createContent(String title, String description, String contentText, String category, String sentiment, User user) {
        Content content = new Content();
        content.setTitle(title);
        content.setContentType("text");
        content.setContentBody(contentText);
        content.setCategory(category);
        content.setAnalyzedSentiment(sentiment);
        content.setUser(user);
        content.setCreatedAt(LocalDateTime.now());
        content.setUpdatedAt(LocalDateTime.now());
        content.setStatus("ACTIVE");
        return content;
    }

    @Transactional(readOnly = true)
    public List<Content> getUserContent(User user) {
        return contentRepository.findByUser(user);
    }

    @Transactional(readOnly = true)
    public Optional<Content> getContentById(Long id) {
        return contentRepository.findById(id);
    }

    @Transactional
    public Content updateContent(Content content) {
        return contentRepository.save(content);
    }

    @Transactional
    public void deleteContent(Long id) {
        contentRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getContentStats(User user) {
        List<Content> userContent = contentRepository.findByUser(user);
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalContent", userContent.size());
        
        Map<String, Long> statusCounts = userContent.stream()
            .collect(Collectors.groupingBy(Content::getStatus, Collectors.counting()));
        stats.put("statusCounts", statusCounts);
        
        // Calculate engagement metrics
        double totalEngagement = 0;
        int contentWithMetrics = 0;
        
        for (Content content : userContent) {
            try {
                if (content.getMetrics() != null) {
                    Map<String, Integer> metrics = objectMapper.readValue(
                        content.getMetrics(),
                        new TypeReference<Map<String, Integer>>() {}
                    );
                    
                    int engagement = metrics.getOrDefault("likes", 0) +
                                   metrics.getOrDefault("shares", 0) * 2 +
                                   metrics.getOrDefault("comments", 0) * 3;
                    
                    totalEngagement += engagement;
                    contentWithMetrics++;
                }
            } catch (JsonProcessingException e) {
                log.error("Error parsing content metrics", e);
            }
        }
        
        if (contentWithMetrics > 0) {
            stats.put("averageEngagement", totalEngagement / contentWithMetrics);
        }
        
        return stats;
    }

    @Transactional
    public void updateContentMetrics(Long contentId, String metricType) {
        Content content = contentRepository.findById(contentId)
            .orElseThrow(() -> new IllegalArgumentException("Content not found"));
            
        try {
            Map<String, Integer> metrics = content.getMetrics() != null ?
                objectMapper.readValue(content.getMetrics(), new TypeReference<Map<String, Integer>>() {}) :
                new HashMap<>();
            
            metrics.merge(metricType, 1, Integer::sum);
            content.setMetrics(objectMapper.writeValueAsString(metrics));
            
            contentRepository.save(content);
        } catch (JsonProcessingException e) {
            log.error("Error updating content metrics", e);
        }
    }

    private String detectLanguage(String text) {
        if (languageDetector == null) {
            try {
                initializeOpenNLPComponents();
            } catch (IOException e) {
                log.error("Failed to initialize OpenNLP components", e);
                return "en"; // Default to English if initialization fails
            }
        }

        try {
            Language language = languageDetector.predictLanguage(text);
            return language.getLang();
        } catch (Exception e) {
            log.error("Error detecting language", e);
            return "en"; // Default to English
        }
    }

    private Map<String, Double> analyzeSentiment(String text) {
        Map<String, Double> sentimentMap = new HashMap<>();
        try {
            List<Map<String, String>> messages = Arrays.asList(
                Map.of(
                    "role", "system",
                    "content", "You are a sentiment analysis expert. Respond with exactly one word: positive, negative, or neutral."
                ),
                Map.of(
                    "role", "user",
                    "content", "Analyze the sentiment of this text and respond with ONLY ONE WORD (positive, negative, or neutral):\n\n" + text
                )
            );

            Map<String, Object> response = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.3,
                    "max_tokens", 10
                )
            );

            String sentiment = openRouterService.extractContentFromResponse(response).trim().toLowerCase();
            if (sentiment.equals("positive")) {
                sentimentMap.put("positive", 1.0);
                sentimentMap.put("negative", 0.0);
                sentimentMap.put("neutral", 0.0);
            } else if (sentiment.equals("negative")) {
                sentimentMap.put("positive", 0.0);
                sentimentMap.put("negative", 1.0);
                sentimentMap.put("neutral", 0.0);
            } else {
                sentimentMap.put("positive", 0.0);
                sentimentMap.put("negative", 0.0);
                sentimentMap.put("neutral", 1.0);
            }
            return sentimentMap;
        } catch (Exception e) {
            log.error("Error analyzing sentiment", e);
            sentimentMap.put("positive", 0.0);
            sentimentMap.put("negative", 0.0);
            sentimentMap.put("neutral", 1.0);
            return sentimentMap;
        }
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

    private double[] getCategoryProbabilities(String text) {
        // Tokenize the text
        String[] tokens = text.split(" ");
        
        // Get category probabilities using OpenNLP document categorizer
        return categorizer.categorize(tokens);
    }

    private String categorizeContent(String text) {
        double[] categoryProbs = getCategoryProbabilities(text);
        return categorizer.getBestCategory(categoryProbs);
    }

    private Map<String, Double> calculateReadabilityMetrics(String text) {
        Map<String, Double> metrics = new HashMap<>();
        
        try {
            // Split text into sentences and words
            String[] sentences = text.split("[.!?]+");
            String[] words = text.split("\\s+");
            
            // Calculate basic metrics
            int totalSentences = sentences.length;
            int totalWords = words.length;
            int totalSyllables = countSyllables(text);
            int complexWords = countComplexWords(words);
            
            // Calculate Flesch Reading Ease
            double fleschScore = 206.835 - 1.015 * ((double) totalWords / totalSentences) 
                - 84.6 * ((double) totalSyllables / totalWords);
            metrics.put("fleschReadingEase", Math.max(0, Math.min(100, fleschScore)));
            
            // Calculate Gunning Fog Index
            double gunningFog = 0.4 * (((double) totalWords / totalSentences) + 100 * ((double) complexWords / totalWords));
            metrics.put("gunningFogIndex", gunningFog);
            
            // Calculate average sentence length
            double avgSentenceLength = (double) totalWords / totalSentences;
            metrics.put("averageSentenceLength", avgSentenceLength);
            
            // Calculate average syllables per word
            double avgSyllablesPerWord = (double) totalSyllables / totalWords;
            metrics.put("averageSyllablesPerWord", avgSyllablesPerWord);
            
            // Calculate percentage of complex words
            double complexWordPercentage = (double) complexWords / totalWords * 100;
            metrics.put("complexWordPercentage", complexWordPercentage);
            
            return metrics;
        } catch (Exception e) {
            log.error("Error calculating readability metrics", e);
            metrics.put("error", -1.0);
            return metrics;
        }
    }

    private int countSyllables(String text) {
        int count = 0;
        text = text.toLowerCase().replaceAll("[^a-zA-Z ]", "");
        String[] words = text.split("\\s+");
        
        for (String word : words) {
            count += countWordSyllables(word);
        }
        
        return count;
    }
    
    private int countWordSyllables(String word) {
        int count = 0;
        boolean isPrevVowel = false;
        
        for (int i = 0; i < word.length(); i++) {
            boolean isVowel = "aeiouy".indexOf(word.charAt(i)) != -1;
            
            if (isVowel && !isPrevVowel) {
                count++;
            }
            
            isPrevVowel = isVowel;
        }
        
        // Handle silent e at the end
        if (word.length() > 1 && word.endsWith("e")) {
            count--;
        }
        
        return Math.max(1, count); // Every word has at least one syllable
    }

    private int countComplexWords(String[] words) {
        int count = 0;
        for (String word : words) {
            if (countWordSyllables(word) >= 3) {
                count++;
            }
        }
        return count;
    }

    private void analyzeContentQuality(Content content) {
        try {
            // Basic sentiment analysis
            Map<String, Double> sentiment = analyzeSentiment(content.getContentBody());
            content.setAnalyzedSentiment(objectMapper.writeValueAsString(sentiment));

            // Adjust content tone based on sentiment
            String adjustedContent = adjustContentTone(content.getContentBody(), sentiment);
            content.setContentBody(adjustedContent);

            // Stanford sentiment analysis
            if (pipeline != null) {
                Map<String, Object> stanfordSentiment = performStanfordSentimentAnalysis(content.getContentBody());
                content.setStanfordSentiment(objectMapper.writeValueAsString(stanfordSentiment));
            }

            // Language detection
            if (languageDetector != null && content.getContentBody() != null) {
                Language detectedLang = languageDetector.predictLanguage(content.getContentBody());
                content.setLanguage(detectedLang.getLang());
            }

            // Content categorization
            if (categorizer != null && content.getContentBody() != null) {
                double[] categoryProbs = getCategoryProbabilities(content.getContentBody());
                String category = categorizer.getBestCategory(categoryProbs);
                content.setCategory(category);
            }

            // Readability metrics
            Map<String, Double> readabilityMetrics = calculateReadabilityMetrics(content.getContentBody());
            content.setReadabilityScore(objectMapper.writeValueAsString(readabilityMetrics));

            // SEO suggestions if requested
            if (content.isOptimizeForSeo()) {
                String seoSuggestions = generateSEOSuggestions(content.getContentBody());
                content.setSeoMetadata(seoSuggestions);
            }

            // Generate improved content
            String improvedContent = generateImprovedContent(content);
            content.setImprovedContent(improvedContent);

            // Generate improvement suggestions
            List<String> suggestions = generateContentImprovements(content);
            content.setImprovementSuggestions(objectMapper.writeValueAsString(suggestions));

            // Initialize comments as empty array
            content.setComments(objectMapper.writeValueAsString(new ArrayList<>()));

            // Set initial rating
            content.setRating(0); // Changed from 0.0 to 0
        } catch (Exception e) {
            log.error("Error analyzing content quality", e);
        }
    }

    private String adjustContentTone(String contentBody, Map<String, Double> sentiment) {
        // Logic to adjust content tone based on sentiment analysis
        // For example, if sentiment indicates negativity, modify the content to be more positive
        // This is a placeholder for actual implementation
        return contentBody; // Return adjusted content
    }

    /**
     * Applies improvements to content based on feedback analysis
     * @param content The content to improve based on feedback
     */
    private void applyFeedbackBasedImprovements(Content content) {
        try {
            // Get feedback-based recommendations
            List<String> recommendations = feedbackAnalysisService.getFeedbackBasedRecommendations(content);
            
            // Analyze feedback patterns
            Map<String, Object> patterns = feedbackAnalysisService.analyzeFeedbackPatterns(content);
            
            // Extract success factors
            Map<String, Double> successFactors = (Map<String, Double>) patterns.get("successFactors");
            if (successFactors != null && !successFactors.isEmpty()) {
                // Adjust content tone based on emotional score
                if (successFactors.containsKey("emotionalToneScore")) {
                    double targetToneScore = successFactors.get("emotionalToneScore");
                    String adjustedContent = adjustContentToneBasedOnFeedback(content.getContentBody(), targetToneScore);
                    content.setContentBody(adjustedContent);
                }
                
                // Apply readability improvements if needed
                if (successFactors.containsKey("readabilityScore")) {
                    double readabilityTarget = successFactors.get("readabilityScore");
                    String improvedContent = improveContentReadability(content.getContentBody(), readabilityTarget);
                    content.setContentBody(improvedContent);
                }
                
                // Enhance engagement elements
                if (successFactors.containsKey("engagementScore")) {
                    double engagementTarget = successFactors.get("engagementScore");
                    String engagingContent = enhanceContentEngagement(content.getContentBody(), engagementTarget);
                    content.setContentBody(engagingContent);
                }
            }
            
            // Store recommendations for future reference
            content.setImprovementSuggestions(objectMapper.writeValueAsString(recommendations));
            
            // Store feedback patterns for analytics
            content.setFeedbackAnalysis(objectMapper.writeValueAsString(patterns));
            
            log.info("Successfully applied feedback-based improvements for content ID: {}", content.getId());
        } catch (Exception e) {
            log.error("Error applying feedback-based improvements for content ID: {}", content.getId(), e);
        }
    }

    /**
     * Adjusts content tone based on feedback analysis
     */
    private String adjustContentToneBasedOnFeedback(String content, double targetToneScore) {
        try {
            List<Map<String, String>> messages = Arrays.asList(
                Map.of(
                    "role", "system",
                    "content", "You are an expert content editor that adjusts content tone while preserving the original meaning."
                ),
                Map.of(
                    "role", "user",
                    "content", String.format(
                        "Adjust the tone of this content to achieve a target emotional score of %.2f (0-1 scale). " +
                        "Maintain the core message but adjust the language to match the desired tone.\n\n%s",
                        targetToneScore, content
                    )
                )
            );

            Map<String, Object> response = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", 2000
                )
            );
            
            return openRouterService.extractContentFromResponse(response);
        } catch (Exception e) {
            log.error("Error adjusting content tone", e);
            return content; // Return original content if adjustment fails
        }
    }

    /**
     * Improves content readability based on feedback analysis
     */
    private String improveContentReadability(String content, double targetReadabilityScore) {
        try {
            List<Map<String, String>> messages = Arrays.asList(
                Map.of(
                    "role", "system",
                    "content", "You are an expert in improving content readability while maintaining the original message."
                ),
                Map.of(
                    "role", "user",
                    "content", String.format(
                        "Improve the readability of this content to achieve a target score of %.2f (0-1 scale). " +
                        "Use simpler language, shorter sentences, and better structure where appropriate.\n\n%s",
                        targetReadabilityScore, content
                    )
                )
            );

            Map<String, Object> response = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", 2000
                )
            );
            
            return openRouterService.extractContentFromResponse(response);
        } catch (Exception e) {
            log.error("Error improving content readability", e);
            return content; // Return original content if improvement fails
        }
    }

    /**
     * Enhances content engagement based on feedback analysis
     */
    private String enhanceContentEngagement(String content, double targetEngagementScore) {
        try {
            List<Map<String, String>> messages = Arrays.asList(
                Map.of(
                    "role", "system",
                    "content", "You are an expert in making content more engaging while preserving its core message."
                ),
                Map.of(
                    "role", "user",
                    "content", String.format(
                        "Enhance the engagement level of this content to achieve a target score of %.2f (0-1 scale). " +
                        "Add engaging elements, examples, and interactive components where appropriate.\n\n%s",
                        targetEngagementScore, content
                    )
                )
            );

            Map<String, Object> response = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", 2000
                )
            );
            
            return openRouterService.extractContentFromResponse(response);
        } catch (Exception e) {
            log.error("Error enhancing content engagement", e);
            return content; // Return original content if enhancement fails
        }
    }

    /**
     * Generates improved content based on the original content and improvement suggestions
     * @param content The content object containing the original content and improvement suggestions
     * @return The improved content text
     */
    private String generateImprovedContent(Content content) {
        try {
            List<String> improvements = objectMapper.readValue(
                content.getImprovementSuggestions(),
                new TypeReference<List<String>>() {}
            );

            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("Please improve the following content by implementing these specific suggestions:\n\n");
            promptBuilder.append("Original Content:\n").append(content.getContentBody()).append("\n\n");
            promptBuilder.append("Improvement Suggestions:\n");
            for (String improvement : improvements) {
                promptBuilder.append("- ").append(improvement).append("\n");
            }
            promptBuilder.append("\nProvide the improved version while maintaining the original message and intent.");

            List<Map<String, String>> messages = Arrays.asList(
                Map.of(
                    "role", "system",
                    "content", "You are an expert content improver. Enhance the content while maintaining its core message and implementing the provided suggestions."
                ),
                Map.of(
                    "role", "user",
                    "content", promptBuilder.toString()
                )
            );

            int maxTokens = 2000; // Define a reasonable token limit for the response

            Map<String, Object> response = openRouterService.createChatCompletion(
                model,
                messages,
                Map.of(
                    "temperature", 0.7,
                    "max_tokens", maxTokens
                )
            );

            return openRouterService.extractContentFromResponse(response);

        } catch (Exception e) {
            log.error("Error generating improved content", e);
            return content.getContentBody(); // Return original content if improvement fails
        }
    }

    /**
     * Generates a list of improvement suggestions for the given content
     * @param content The content to analyze and generate improvements for
     * @return List of improvement suggestions
     */
    private List<String> generateContentImprovements(Content content) {
        List<String> improvements = new ArrayList<>();
        
        try {
            // Get content metrics
            Map<String, Double> metrics = calculateReadabilityMetrics(content.getContentBody());
            double fleschScore = metrics.get("fleschReadingEase");
            
            // Add readability-based suggestions
            if (fleschScore < 60.0) {
                improvements.add("Consider simplifying the language to improve readability");
            }
            
            // Analyze sentence structure
            String[] sentences = content.getContentBody().split("[.!?]+");
            if (sentences.length > 0) {
                double avgSentenceLength = content.getContentBody().split("\\s+").length / (double) sentences.length;
                if (avgSentenceLength > 20) {
                    improvements.add("Consider breaking down longer sentences for better clarity");
                }
            }
            
            // Check content length
            int wordCount = content.getContentBody().split("\\s+").length;
            if (wordCount < 300) {
                improvements.add("Consider expanding the content for better depth and coverage");
            }
            
            // Add SEO-related suggestions if optimization is enabled
            if (content.isOptimizeForSeo()) {
                improvements.add("Ensure key phrases are naturally distributed throughout the content");
                improvements.add("Consider adding relevant internal and external links");
            }
            
            // Use AI service for advanced suggestions if available
            if (enhancedContentGenerationService != null) {
                try {
                    List<String> aiSuggestions = enhancedContentGenerationService.generateContentImprovements(content);
                    if (aiSuggestions != null && !aiSuggestions.isEmpty()) {
                        improvements.addAll(aiSuggestions);
                    }
                } catch (Exception e) {
                    log.warn("Unable to generate AI-powered suggestions", e);
                }
            }
            
        } catch (Exception e) {
            log.error("Error generating content improvements", e);
            improvements.add("Unable to generate detailed improvements due to an error");
        }
        
        return improvements;
    }

    /**
     * Generates SEO suggestions for the given content text
     * @param contentText The content text to analyze for SEO optimization
     * @return JSON string containing SEO suggestions and metadata
     */
    private String generateSEOSuggestions(String contentText) throws JsonProcessingException {
        Map<String, Object> seoAnalysis = new HashMap<>();
        
        List<Map<String, String>> messages = Arrays.asList(
            Map.of(
                "role", "system",
                "content", "You are an SEO expert. Analyze content and provide suggestions in JSON format."
            ),
            Map.of(
                "role", "user",
                "content", String.format(
                    "Analyze the following content for SEO and provide suggestions in JSON format including:\n" +
                    "1. Keywords: List of 5-7 relevant keywords/phrases\n" +
                    "2. Title suggestions: 3 SEO-optimized title options\n" +
                    "3. Meta description: 2-3 meta description options (under 160 characters)\n" +
                    "4. Content suggestions: List of improvements for better SEO\n" +
                    "\nContent: %s", contentText)
            )
        );

        int maxTokens = 1000; // Define a reasonable token limit for the response

        Map<String, Object> response = openRouterService.createChatCompletion(
            model,
            messages,
            Map.of(
                "temperature", 0.7,
                "max_tokens", maxTokens
            )
        );

        String seoResponse = openRouterService.extractContentFromResponse(response);
        
        try {
            // Parse the response into a Map
            seoAnalysis = objectMapper.readValue(seoResponse, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            // If parsing fails, create a simple structure with the raw response
            seoAnalysis.put("rawSuggestions", seoResponse);
        }
        
        return objectMapper.writeValueAsString(seoAnalysis);
    }
}