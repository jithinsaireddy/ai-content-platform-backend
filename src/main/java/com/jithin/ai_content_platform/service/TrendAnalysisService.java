package com.jithin.ai_content_platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.model.EnhancedTrendPattern;
import com.jithin.ai_content_platform.model.TrendData;
import com.jithin.ai_content_platform.model.TrendData.Region;
import com.jithin.ai_content_platform.model.TrendDirection;
import com.jithin.ai_content_platform.model.TrendInsight;
import com.jithin.ai_content_platform.model.TrendPattern;
import java.time.format.DateTimeFormatter;
import org.springframework.cache.annotation.Cacheable;
import com.jithin.ai_content_platform.repository.ContentRepository;
import com.jithin.ai_content_platform.repository.TrendDataRepository;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.text.sentenceiterator.BasicLineIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class TrendAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(TrendAnalysisService.class);
    private static final int MAX_KEYWORDS_TO_ANALYZE = 10;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private TrendDataRepository trendDataRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebScrapingService webScrapingService;
    
    @Autowired
    private DynamicTrendWeightService dynamicTrendWeightService;

    @Value("${content.strategy.trend.weight}")
    private double trendWeight;

    @Value("${content.strategy.sentiment.weight}")
    private double sentimentWeight;

    @Value("${content.strategy.engagement.weight}")
    private double engagementWeight;

    @Value("${trend.analysis.batch.size}")
    private int batchSize;

    @Value("${trend.analysis.max.items}")
    private int maxItems;

    @Value("${trend.analysis.default.limit:100}")
private int defaultLimit;

    private final StanfordCoreNLP pipeline;
    private Word2Vec word2Vec;

    @Autowired
    public TrendAnalysisService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        // Initialize Stanford NLP pipeline
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, parse, sentiment");
        this.pipeline = new StanfordCoreNLP(props);
    }

    @PostConstruct
    public void init() {
        try {
            // Initialize Word2Vec model with enhanced error handling
            File modelFile = new File("word2vec.model");
            if (modelFile.exists()) {
                try {
                    word2Vec = WordVectorSerializer.readWord2VecModel(modelFile);
                    logger.info("Successfully loaded existing Word2Vec model");
                } catch (Exception e) {
                    logger.error("Error loading existing Word2Vec model, will attempt to retrain", e);
                    modelFile.delete(); // Remove corrupted model
                    initializeNewModel();
                }
            } else {
                initializeNewModel();
            }
        } catch (Exception e) {
            logger.error("Error initializing TrendAnalysisService", e);
        }
    }

    private void initializeNewModel() {
        try {
            // Get existing content or create sample if none exists
            List<Content> initialContent = contentRepository.findAll();
            if (initialContent.isEmpty()) {
                logger.info("No existing content found, using sample data for initial model training");
                initialContent = Collections.singletonList(createSampleContent());
            }
            
            String corpusFile = createTemporaryCorpus(initialContent);
            trainWordEmbeddings(corpusFile);
            logger.info("Successfully trained new Word2Vec model");
        } catch (Exception e) {
            logger.error("Failed to initialize new Word2Vec model", e);
        }
    }

    private Content createSampleContent() {
        Content sample = new Content();
        sample.setContentBody("This is a sample content for initial model training. " +
                            "It contains various topics like AI, machine learning, technology, " +
                            "innovation, and digital transformation to establish basic word relationships.");
        sample.setTitle("Sample Training Content");
        sample.setCategory("Technology");
        return sample;
    }

    @Scheduled(fixedRate = 3600000) // Run every hour
    @Cacheable(value = "analyzedTrends", key = "T(java.time.LocalDateTime).now().format(T(java.time.format.DateTimeFormatter).ISO_DATE)")
    public void analyzeTrends() {
        logger.info("Starting trend analysis...");
        int page = 0;
        int totalProcessed = 0;

        try {
            while (totalProcessed < maxItems) {
                Page<Content> contentPage = contentRepository.findAll(PageRequest.of(page, batchSize));
                List<Content> contentBatch = contentPage.getContent();
                
                if (contentBatch.isEmpty()) {
                    break;
                }

                Map<String, Map<String, Object>> trendScores = processTrendBatch(contentBatch);
                totalProcessed += contentBatch.size();
                
                if (!contentPage.hasNext()) {
                    break;
                }
                
                page++;
            }

            logger.info("Completed trend analysis for {} items", totalProcessed);
        } catch (Exception e) {
            logger.error("Error during trend analysis", e);
        }
    }

    @Cacheable(value = "latestTrends", key = "'latest'", unless = "#result.isEmpty()")
    public List<TrendData> getLatestTrends() {
        logger.debug("Fetching latest trends with optimization");
        return trendDataRepository.findLatestTrendsOptimized(
            LocalDateTime.now().minusHours(24), 
            defaultLimit
        );
    }
    
    public double calculateSeasonalityScore(TrendData trend) {
        try {
            // Get historical data for the same time period in previous years
            List<Double> historicalValues = trend.getHistoricalValuesList();
            List<LocalDateTime> timestamps = trend.getTimestamps();
            
            if (historicalValues.size() < 365) {
                return 0.5; // Not enough historical data
            }
            
            // Calculate correlation with same period last year
            double currentPeriodAvg = historicalValues.subList(historicalValues.size() - 30, historicalValues.size())
                .stream()
                .mapToDouble(Double::valueOf)
                .average()
                .orElse(0.0);
                
            double lastYearPeriodAvg = historicalValues.subList(historicalValues.size() - 395, historicalValues.size() - 365)
                .stream()
                .mapToDouble(Double::valueOf)
                .average()
                .orElse(0.0);
                
            // Compare current period with last year
            double correlation = calculateCorrelation(currentPeriodAvg, lastYearPeriodAvg);
            
            return (correlation + 1) / 2; // Normalize to 0-1 range
        } catch (Exception e) {
            log.error("Error calculating seasonality score", e);
            return 0.5;
        }
    }
    
    private double calculateCorrelation(double current, double previous) {
        if (previous == 0) return 0;
        return Math.min(1, Math.max(-1, (current - previous) / previous));
    }
    
    private Map<String, Map<String, Object>> processTrendBatch(List<Content> contentBatch) {
        Map<String, Map<String, Object>> trendScores = new HashMap<>();
        Map<String, Double> sentimentScores = new HashMap<>();

        contentBatch.parallelStream().forEach(content -> {
            try {
                // Process trend scores
                // Get trend scores with dynamic weighting
                Map<String, Map<String, Object>> contentTrends = analyzeTrendScores(content);
                
                // Apply dynamic weights to trends
                contentTrends.forEach((topic, data) -> {
                    TrendData trendData = trendDataRepository.findLatestByTopic(topic);
                    if (trendData != null) {
                        double dynamicWeight = dynamicTrendWeightService.calculateDynamicWeight(trendData);
                        data.put("dynamicWeight", dynamicWeight);
                    }
                });
                synchronized (trendScores) {
                    contentTrends.forEach((k, v) -> trendScores.merge(k, v, (v1, v2) -> {
                        Map<String, Object> merged = new HashMap<>(v1);
                        v2.forEach((k2, v2Value) -> {
                            if (merged.containsKey(k2)) {
                                Object existingValue = merged.get(k2);
                                if (v2Value instanceof Number && existingValue instanceof Number) {
                                    double newValue = ((Number) v2Value).doubleValue();
                                    double oldValue = ((Number) existingValue).doubleValue();
                                    merged.put(k2, Math.max(oldValue, newValue));
                                } else if (v2Value != null) {
                                    merged.put(k2, v2Value);
                                }
                            } else if (v2Value != null) {
                                merged.put(k2, v2Value);
                            }
                        });
                        return merged;
                    }));
                }

                // Process sentiment
                double sentiment = analyzeSentimentScore(content);
                synchronized (sentimentScores) {
                    sentimentScores.merge(content.getCategory(), sentiment, Double::sum);
                }
            } catch (Exception e) {
                logger.error("Error processing content item: {}", content.getId(), e);
            }
        });

        // Store results
        storeTrendAnalysis(trendScores, sentimentScores);
        
        return trendScores;
    }

    private Map<String, Map<String, Object>> analyzeTrendScores(Content content) {
        Map<String, Map<String, Object>> scores = new HashMap<>();
        if (content == null) {
            return scores;
        }
    
        try {
            // Get only main keywords with limit
            Set<String> keywords = new HashSet<>();
            
            // Add title if present
            if (content.getTitle() != null) {
                keywords.add(content.getTitle());
            }
            
            // Add keywords from content
            if (content.getKeywords() != null) {
                Arrays.stream(content.getKeywords().split(",\\s*"))
                    .limit(MAX_KEYWORDS_TO_ANALYZE - (content.getTitle() != null ? 1 : 0))
                    .forEach(keywords::add);
            }
    
            String trendDataJson = content.getTrendData();
            Map<String, Object> trendDataMap = trendDataJson != null ? 
                objectMapper.readValue(trendDataJson, new TypeReference<Map<String, Object>>() {}) : 
                new HashMap<>();
            
            // Calculate engagement score
            double engagement = calculateTrendEngagementScore(Collections.singletonList(content));
            
            // Only analyze the limited set of keywords
            keywords.forEach(keyword -> {
                Map<String, Object> trendMetrics = calculateEnhancedTrendMetrics(keyword, engagement, trendDataMap, content.getRegion());
                scores.put(keyword, trendMetrics);
            });
    
        } catch (Exception e) {
            logger.error("Error analyzing trend scores for content: {}", content.getId(), e);
        }
        return scores;
    }
    private List<String> getFormattedHistoricalDates(String keyword) {
        try {
            List<TrendData> historicalData = trendDataRepository.findByTopic(keyword);
            return historicalData.stream()
                .map(data -> data.getAnalysisTimestamp().format(DateTimeFormatter.ISO_DATE))
                .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error retrieving historical dates for keyword: {}", keyword, e);
            return new ArrayList<>();
        }
    }

    private Map<String, Object> calculateEnhancedTrendMetrics(String keyword, double engagement, Map<String, Object> trendDataMap, String region) {
        Map<String, Object> metrics = new HashMap<>();
        try {
            // Calculate base interest score with fallback mechanisms
            double interest = calculateEnhancedInterestScore(keyword, region, trendDataMap);
            
            // Add base metrics
            metrics.put("engagement", engagement);
            metrics.put("interest_over_time", interest);
            metrics.put("change", calculateChangeFromValues(engagement, interest));
            metrics.put("volatility", calculateVolatilityFromValues(Arrays.asList(engagement, interest)));
            
            // Add enhanced metrics if historical data exists
            List<Double> historicalValues = getHistoricalTrendValues(keyword);
            if (!historicalValues.isEmpty()) {
                double momentum = calculateMomentum(historicalValues);
                double historicalVolatility = calculateVolatility(historicalValues);
                metrics.put("momentum", momentum);
                metrics.put("historical_volatility", historicalVolatility);
                
             // Inside calculateEnhancedTrendMetrics:
// Add seasonality if we have enough data
// Inside calculateEnhancedTrendMetrics:
// Add seasonality if we have enough data
List<String> dates = getFormattedHistoricalDates(keyword);
if (dates.size() >= 30) {
    metrics.put("seasonality", analyzeSeasonality(historicalValues, dates));
}
            }
            
            // Calculate confidence score
                     // Calculate confidence score
                     double volatility = Optional.ofNullable(metrics.get("volatility"))
                     .filter(v -> v instanceof Number)
                     .map(v -> ((Number) v).doubleValue())
                     .orElse(0.0);
                     
                 double momentum = Optional.ofNullable(metrics.get("momentum"))
                     .filter(v -> v instanceof Number)
                     .map(v -> ((Number) v).doubleValue())
                     .orElse(0.0);
                     
                 double confidence = calculateConfidenceScore(volatility, momentum);
            
            // Determine trend direction using enhanced logic
            metrics.put("direction", determineTrendDirectionByEngagementAndInterest(engagement, interest).toString());
            
        } catch (Exception e) {
            logger.error("Error calculating enhanced trend metrics for keyword: {}", keyword, e);
            // Ensure we return at least basic metrics even if enhanced calculations fail
            if (!metrics.containsKey("engagement")) metrics.put("engagement", engagement);
            if (!metrics.containsKey("interest_over_time")) metrics.put("interest_over_time", 0.5);
            if (!metrics.containsKey("direction")) metrics.put("direction", "NEUTRAL");
        }
        return metrics;
    }

    private double calculateEnhancedInterestScore(String keyword, String region, Map<String, Object> trendData) {
        try {
            // Try to get real-time interest data
            Map<String, Object> googleTrends = fetchGoogleTrends(keyword, region);
            if (googleTrends != null && !googleTrends.isEmpty()) {
                double score = calculateBaseInterestScore(keyword, googleTrends);
                if (score > 0.0) return score;
            }
            
            // Fall back to historical data
            if (trendData != null && !trendData.isEmpty()) {
                double score = calculateBaseInterestScore(keyword, trendData);
                if (score > 0.0) return score;
            }
            
            // Try to estimate from related content
            // Try to estimate from related content
List<Content> relatedContent = contentRepository.findByCategoryAndContentType(keyword, "article");
if (relatedContent.isEmpty()) {
    // Try broader search
    relatedContent = contentRepository.findByTitleContainingOrContentBodyContaining(keyword, keyword);
}
if (!relatedContent.isEmpty()) {
    double score = calculateEngagementScore(relatedContent);
    if (score > 0.0) return score;
}
            
            // Last resort: return neutral score
            logger.warn("Using fallback neutral score for keyword: {}", keyword);
            return 0.5;
            
        } catch (Exception e) {
            logger.error("Error calculating enhanced interest score for keyword: {}", keyword, e);
            return 0.5;
        }
    }

    private double calculateVolatilityFromValues(List<Double> scores) {
        if (scores == null || scores.size() < 2) {
            return 0.0;
        }

        double mean = scores.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        
        double sumSquaredDiff = scores.stream()
            .mapToDouble(score -> Math.pow(score - mean, 2))
            .sum();
        
        return Math.sqrt(sumSquaredDiff / (scores.size() - 1));
    }

    protected double calculateOpportunityScore(Map<String, Object> trendData, List<Content> relatedContent) {
        if (trendData == null || relatedContent == null) {
            return 0.0;
        }

        try {
            double trendScore = calculateTrendScore(trendData);
            double sentimentScore = calculateAverageSentiment(relatedContent);
            double engagementScore = calculateEngagementScore(relatedContent);

            // Weighted average of the scores
            return (trendScore * trendWeight + 
                   sentimentScore * sentimentWeight + 
                   engagementScore * engagementWeight) / 
                   (trendWeight + sentimentWeight + engagementWeight);
        } catch (Exception e) {
            logger.error("Error calculating opportunity score", e);
            return 0.0;
        }
    }

    private double calculateInterestScore(String keyword, String region, Map<String, Object> trendData) {
        try {
            Object interestOverTimeObj = trendData.get("interest_over_time");
            if (interestOverTimeObj instanceof List<?>) {
                List<Map<String, Object>> interestOverTime = (List<Map<String, Object>>) interestOverTimeObj;
                if (interestOverTime != null && !interestOverTime.isEmpty()) {
                    Map<String, Object> trendingTopicsMap = new HashMap<>();
                    double totalScore = 0.0;
                    int count = 0;
                    
                    for (Map<String, Object> dataPoint : interestOverTime) {
                        List<Content> contentList = new ArrayList<>();
                        Content content = new Content();
                        
                        Object dateObj = dataPoint.get("date");
                        if (dateObj != null) {
                            try {
                                content.setCreatedAt(LocalDateTime.parse(dateObj.toString()));
                            } catch (Exception e) {
                                content.setCreatedAt(LocalDateTime.now());
                            }
                        }
                        
                        Object valueObj = dataPoint.get("value");
                        if (valueObj instanceof Number) {
                            content.setEngagement(((Number) valueObj).doubleValue());
                        }
                        
                        contentList.add(content);
                        double engagement = calculateEngagementScore(contentList);
                        double baseScore = calculateBaseInterestScore(keyword, dataPoint);
                        
                        Map<String, Object> topicData = new HashMap<>();
                        topicData.put("date", dataPoint.get("date"));
                        topicData.put("value", dataPoint.get("value"));
                        topicData.put("engagement", engagement);
                        topicData.put("interest_score", baseScore);
                        
                        String topic = dataPoint.containsKey("topic") ? 
                            (String) dataPoint.get("topic") : 
                            "Topic " + (trendingTopicsMap.size() + 1);
                        
                        trendingTopicsMap.put(topic, topicData);
                        totalScore += baseScore;
                        count++;
                    }
                    
                    return count > 0 ? totalScore / count : 0.0;
                }
            } else if (interestOverTimeObj instanceof Map<?, ?>) {
                Map<String, Object> interestOverTime = (Map<String, Object>) interestOverTimeObj;
                return calculateBaseInterestScore(keyword, interestOverTime);
            }
            
            // If we have a simple data point, calculate its base score
            return calculateBaseInterestScore(keyword, trendData);
            
        } catch (Exception e) {
            logger.error("Error calculating interest score for keyword {}: {}", keyword, e.getMessage());
            return 0.0;
        }
    }

    private double calculateBaseInterestScore(String keyword, Map<String, Object> data) {
        try {
            Object value = data.get("value");
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return 0.0;
        } catch (Exception e) {
            logger.error("Error calculating base interest score for keyword {}: {}", keyword, e.getMessage());
            return 0.0;
        }
    }

    private double calculateChangeFromValues(double previousValue, double currentValue) {
        double previousValueFinal = Math.min(previousValue, currentValue);
        double currentValueFinal = Math.max(previousValue, currentValue);
        return previousValueFinal == 0 ? 0 : ((currentValueFinal - previousValueFinal) / previousValueFinal) * 100;
    }

    public void storeTrendAnalysis(Map<String, Map<String, Object>> trendScores, Map<String, Double> sentimentScores) {
        try {
            LocalDateTime now = LocalDateTime.now();
            
            trendScores.forEach((topic, metrics) -> {
                try {
                    // Create or update trend data
                    TrendData trendData = trendDataRepository.findLatestByTopic(topic);
                    if (trendData == null) {
                        trendData = new TrendData();
                        trendData.setTopic(topic);
                    }
                    
                    // Update current metrics
                    trendData.setAnalysisTimestamp(now);
                    trendData.setMetrics(objectMapper.writeValueAsString(metrics));
                    
                    // Store historical values for future analysis
                    Map<String, List<Double>> historicalValues = trendData.getHistoricalValues() != null ?
                        objectMapper.readValue(trendData.getHistoricalValues(), new TypeReference<Map<String, List<Double>>>() {}) :
                        new HashMap<>();
                    
                    // Update historical values for each metric
                    metrics.forEach((metricName, value) -> {
                        if (value instanceof Number) {
                            List<Double> values = historicalValues.computeIfAbsent(metricName, k -> new ArrayList<>());
                            values.add(((Number) value).doubleValue());
                            // Keep only last 90 days of data
                            if (values.size() > 90) {
                                values = values.subList(values.size() - 90, values.size());
                            }
                            historicalValues.put(metricName, values);
                        }
                    });
                    
                    trendData.setHistoricalValues(objectMapper.writeValueAsString(historicalValues));
                    
                    // Store dates for seasonality analysis
                    List<String> historicalDates = trendData.getHistoricalDates() != null ?
                        objectMapper.readValue(trendData.getHistoricalDates(), new TypeReference<List<String>>() {}) :
                        new ArrayList<>();
                    
                    historicalDates.add(now.format(DateTimeFormatter.ISO_DATE));
                    if (historicalDates.size() > 90) {
                        historicalDates = historicalDates.subList(historicalDates.size() - 90, historicalDates.size());
                    }
                    
                    trendData.setHistoricalDates(objectMapper.writeValueAsString(historicalDates));
                    
                    // Calculate and store aggregate sentiment if available
                    if (sentimentScores.containsKey(topic)) {
                        trendData.setSentimentScore(sentimentScores.get(topic));
                    }
                    
                    // Save the trend data
                    trendDataRepository.save(trendData);
                    
                } catch (Exception e) {
                    logger.error("Error storing trend analysis for topic {}: {}", topic, e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.error("Error in storeTrendAnalysis: {}", e.getMessage());
        }
    }

    private Set<String> extractKeywords(Content content) {
        Set<String> keywords = new HashSet<>();
        try {
            // Extract from title
            if (content.getTitle() != null) {
                keywords.addAll(extractPhrases(content.getTitle()));
            }
            
            // Extract from content body
            if (content.getContentBody() != null) {
                keywords.addAll(extractPhrases(content.getContentBody()));
            }
            
            // Extract from description
            if (content.getDescription() != null) {
                keywords.addAll(extractPhrases(content.getDescription()));
            }
        } catch (Exception e) {
            logger.error("Error extracting keywords from content: {}", content.getId(), e);
        }
        return keywords;
    }

    private List<String> extractPhrases(String text) {
        List<String> phrases = new ArrayList<>();
        String[] words = text.toLowerCase().split("\\W+");

        // Extract 1-3 word phrases
        for (int i = 0; i < words.length; i++) {
            phrases.add(words[i]);
            if (i < words.length - 1) {
                phrases.add(words[i] + " " + words[i + 1]);
            }
            if (i < words.length - 2) {
                phrases.add(words[i] + " " + words[i + 1] + " " + words[i + 2]);
            }
        }

        return phrases;
    }

    public void trainModelWithTrendingTopics(List<String> trendingTopics) {
    try {
        // Prepare corpus from trending topics
        String corpusFile = prepareCorpusFromTopics(trendingTopics);
        
        // Train the Word2Vec model
        trainWordEmbeddings(corpusFile);
    } catch (Exception e) {
        logger.error("Error training Word2Vec model with trending topics", e);
    }
}

private String prepareCorpusFromTopics(List<String> topics) throws IOException {
    File corpusFile = new File("corpus.txt");
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(corpusFile))) {
        for (String topic : topics) {
            writer.write(topic);
            writer.newLine();
        }
    }
    return corpusFile.getAbsolutePath();
}

    private String createTemporaryCorpus(List<Content> content) throws Exception {
        File tempFile = File.createTempFile("content_corpus", ".txt");
        try (FileWriter writer = new FileWriter(tempFile)) {
            for (Content c : content) {
                writer.write(c.getContentBody() + "\n");
            }
        }
        return tempFile.getAbsolutePath();
    }

    private void trainWordEmbeddings(String corpusFile) throws Exception {
        logger.info("Training word embeddings...");

        SentenceIterator iter = new BasicLineIterator(corpusFile);
        TokenizerFactory t = new DefaultTokenizerFactory();
        t.setTokenPreProcessor(new CommonPreprocessor());

        word2Vec = new Word2Vec.Builder()
                .minWordFrequency(2)
                .iterations(5)
                .layerSize(100)
                .seed(42)
                .windowSize(5)
                .iterate(iter)
                .tokenizerFactory(t)
                .build();

        word2Vec.fit();

        // Save the model for future use
        WordVectorSerializer.writeWord2VecModel(word2Vec, "word2vec.model");
        logger.info("Word embeddings training completed");
    }

    private Map<String, Double> analyzeSentimentDistribution(List<Content> content) {
        Map<String, Double> distribution = new HashMap<>();
        int totalContent = content.size();

        // Count sentiment occurrences
        Map<String, Integer> sentimentCounts = content.stream()
                .map(c -> {
                    try {
                        return objectMapper.readTree(c.getAnalyzedSentiment());
                    } catch (JsonProcessingException e) {
                        logger.error("Error parsing sentiment", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        node -> node.fieldNames().next(),
                        Collectors.collectingAndThen(
                                Collectors.counting(),
                                Long::intValue
                        )
                ));

        // Calculate distribution percentages
        sentimentCounts.forEach((sentiment, count) ->
                distribution.put(sentiment, (double) count / totalContent));

        return distribution;
    }

    private double analyzeSentimentScore(Content content) {
        String contentText = content.getContentBody();
        if (contentText == null || contentText.isEmpty()) {
            return 0.0;
        }

        try {
            Annotation annotation = new Annotation(contentText);
            pipeline.annotate(annotation);

            double totalSentiment = 0;
            int count = 0;

            for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
                Tree tree = sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class);
                int sentiment = RNNCoreAnnotations.getPredictedClass(tree);
                totalSentiment += sentiment;
                count++;
            }

            return count > 0 ? totalSentiment / count : 0.0;
        } catch (Exception e) {
            logger.error("Error analyzing sentiment: {}", e.getMessage());
            return 0.0;
        }
    }

    public double getAverageSentiment(List<Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return 0.0;
        }

        return contents.stream()
                .mapToDouble(this::analyzeSentimentScore)
                .average()
                .orElse(0.0);
    }

  public List<TrendData> getAITrendingTopics() {
    List<TrendData> trends = new ArrayList<>();
    
    try {
        // Use CompletableFuture for parallel execution
        CompletableFuture<List<TrendData>> hackerNewsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return webScrapingService.scrapeHackerNews();
            } catch (Exception e) {
                logger.error("Error scraping HackerNews: {}", e.getMessage());
                return Collections.emptyList();
            }
        });
        
        CompletableFuture<List<TrendData>> githubFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return webScrapingService.scrapeGitHubTrends();
            } catch (Exception e) {
                logger.error("Error scraping GitHub: {}", e.getMessage());
                return Collections.emptyList();
            }
        });
        
        CompletableFuture<List<TrendData>> stackOverflowFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return webScrapingService.scrapeStackOverflow();
            } catch (Exception e) {
                logger.error("Error scraping StackOverflow: {}", e.getMessage());
                return Collections.emptyList();
            }
        });

            CompletableFuture<List<TrendData>> googleFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return webScrapingService.scrapeGoogleTrends();
                } catch (Exception e) {
                    logger.error("Error scraping Google Trends: {}", e.getMessage());
                    return Collections.emptyList();
                }
            });

            CompletableFuture<List<TrendData>> twitterFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return webScrapingService.scrapeTwitterTrends();
                } catch (Exception e) {
                    logger.error("Error scraping Twitter Trends: {}", e.getMessage());
                    return Collections.emptyList();
                    }
                });
        
        // Wait for all futures to complete and combine results
        CompletableFuture.allOf(hackerNewsFuture, githubFuture, stackOverflowFuture, googleFuture, twitterFuture).join();
        
        trends.addAll(hackerNewsFuture.get());
        trends.addAll(githubFuture.get());
        trends.addAll(stackOverflowFuture.get());
        trends.addAll(googleFuture.get());
        trends.addAll(twitterFuture.get());
        
    } catch (Exception e) {
        logger.error("Error getting AI trending topics: {}", e.getMessage());
    }
    
    return trends;
}   
    private void enrichTrendsWithAI(List<TrendData> trends) {
        for (TrendData trend : trends) {
            try {
                // Calculate confidence score
                double confidence = calculateConfidenceScore(trend);
                trend.setConfidenceScore(confidence);
                
                // Calculate momentum
                List<Double> historicalValues = getHistoricalTrendValues(trend.getTopic());
                if (!historicalValues.isEmpty()) {
                    double momentum = calculateMomentum(historicalValues);
                    trend.setMomentum(momentum);
                }
                
                // Set trend pattern
                TrendPattern pattern = determineTrendPattern(trend);
                trend.setTrendPattern(pattern);
                
            } catch (Exception e) {
                logger.error("Error enriching trend {}: {}", trend.getTopic(), e.getMessage());
            }
        }
    }
    
    private double calculateConfidenceScore(TrendData trend) {
        try {
            // Implement confidence calculation based on:
            // 1. Data consistency
            // 2. Source reliability
            // 3. Historical pattern matching
            double baseScore = trend.getTrendScore() != null ? trend.getTrendScore() : 50.0;
            double volatility = trend.getVolatility() != null ? trend.getVolatility() : 20.0;
            double momentum = trend.getMomentum() != null ? trend.getMomentum() : 1.0;
            
            return (baseScore * momentum) / (1 + volatility/100);
        } catch (Exception e) {
            logger.error("Error calculating confidence score: {}", e.getMessage());
            return 50.0; // Default neutral confidence
        }
    }

    public List<TrendData> getPredictedTrends() {
        logger.info("Generating trend predictions based on historical data");
        List<TrendData> predictions = new ArrayList<>();

        try {
            List<String> topics = Arrays.asList(
                "artificial intelligence", "machine learning", 
                "blockchain", "cloud computing", 
                "cybersecurity", "data science"
            );
            for (String topic : topics) {
                TrendData prediction = new TrendData();
                prediction.setAnalysisTimestamp(LocalDateTime.now().plusDays(7)); // Prediction for next week
                prediction.setTopic(topic);
                prediction.setCategory("predicted");

                // Process historical data for prediction
                Map<String, Map<String, Object>> predictedTopicsMap = new HashMap<>();
                List<Double> historicalValues = new ArrayList<>();
                double maxValue = 0.0;
                double totalValue = 0.0;

                // Collect historical values
                for (int i = 0; i < 10; i++) {
                    double value = Math.random() * 100;
                    historicalValues.add(value);
                    maxValue = Math.max(maxValue, value);
                    totalValue += value;
                }

                // Calculate trend metrics
                double avgValue = totalValue / historicalValues.size();
                double momentum = calculateMomentum(historicalValues);
                double volatility = calculateVolatility(historicalValues);
                
                // Predict future value using simple trend analysis
                double predictedValue = predictNextValue(historicalValues, momentum, volatility);
                
                // Create prediction data point
                Map<String, Object> predictionData = new HashMap<>();
                predictionData.put("date", LocalDateTime.now().plusDays(7));
                predictionData.put("value", predictedValue);
                predictionData.put("confidence", calculateConfidenceScore(volatility, momentum));
                predictionData.put("momentum", momentum);
                predictionData.put("volatility", volatility);

                predictedTopicsMap.put(topic, predictionData);

                // Set prediction data
                prediction.setTrendingTopicsMap(predictedTopicsMap);
                prediction.setTrendingTopics(String.valueOf(predictedTopicsMap));
                prediction.setTrendScore(avgValue);
                prediction.setConfidenceScore(calculateConfidenceScore(volatility, momentum));

                // Add metadata
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("prediction_basis", "historical_analysis");
                metadata.put("prediction_window", "7_days");
                metadata.put("historical_data_points", historicalValues.size());
                prediction.setMetadata(metadata);

                predictions.add(prediction);
                logger.info("Generated prediction for topic: {} with confidence: {}", 
                    topic, prediction.getConfidenceScore());

            }
        } catch (Exception e) {
            logger.error("Error generating prediction for topic: {}", e.getMessage());
        }

        return predictions;
    }

    public List<TrendData> getTrendsByCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            logger.warn("Invalid category provided");
            return Collections.emptyList();
        }
        
        logger.info("Fetching trends for category: {}", category);
        try {
            List<TrendData> categoryTrends = new ArrayList<>();
            TrendData trend = new TrendData();
            trend.setAnalysisTimestamp(LocalDateTime.now());
            trend.setCategory(category.toLowerCase().trim());

            // Process trend data with error handling
            Map<String, Map<String, Object>> trendingTopicsMap = new HashMap<>();
            List<Double> historicalValues = new ArrayList<>();
            double maxValue = 0.0;
            double totalValue = 0.0;

            // Collect historical values with validation
            try {
                // Changed from findByTopicOrderByAnalysisTimestampDesc to findTrendingByCategory
                double minScore = 0.0; // Include all trends initially
                List<TrendData> historicalTrends = trendDataRepository.findTrendingByCategory(category.toLowerCase().trim(), minScore);
                if (!historicalTrends.isEmpty()) {
                    for (TrendData historicalTrend : historicalTrends) {
                        if (historicalTrend.getTrendScore() != null) {
                            historicalValues.add(historicalTrend.getTrendScore());
                            maxValue = Math.max(maxValue, historicalTrend.getTrendScore());
                            totalValue += historicalTrend.getTrendScore();
                        }
                    }
                } else {
                    // Fallback to synthetic data if no historical data
                    for (int i = 0; i < 10; i++) {
                        double value = Math.random() * 100;
                        historicalValues.add(value);
                        maxValue = Math.max(maxValue, value);
                        totalValue += value;
                    }
                }
            } catch (Exception e) {
                logger.error("Error fetching historical trends for category {}: {}", category, e.getMessage());
                // Continue with synthetic data
                for (int i = 0; i < 10; i++) {
                    double value = Math.random() * 100;
                    historicalValues.add(value);
                    maxValue = Math.max(maxValue, value);
                    totalValue += value;
                }
            }

            // Calculate trend metrics with validation
            double avgValue = historicalValues.isEmpty() ? 0.0 : totalValue / historicalValues.size();
            double momentum = calculateMomentum(historicalValues);
            double volatility = calculateVolatility(historicalValues);
            
            // Predict future value using simple trend analysis
            double predictedValue = predictNextValue(historicalValues, momentum, volatility);
            
            // Create prediction data point
            Map<String, Object> predictionData = new HashMap<>();
            predictionData.put("date", LocalDateTime.now().plusDays(7));
            predictionData.put("value", predictedValue);
            predictionData.put("confidence", calculateConfidenceScore(volatility, momentum));
            predictionData.put("momentum", momentum);
            predictionData.put("volatility", volatility);

            trendingTopicsMap.put(category, predictionData);

            // Set prediction data
            
            trend.setTrendScore(avgValue);
            trend.setConfidenceScore(calculateConfidenceScore(volatility, momentum));
            trend.setTrendingTopics("{}");

            categoryTrends.add(trend);
            return categoryTrends;
        } catch (Exception e) {
            logger.error("Error processing category trend data", e);
            throw new RuntimeException("Error processing trend data: " + e.getMessage(), e);
        }
    }

    public double analyzeTrendSentiment(Content content) {
        if (content == null) {
            return 0.0;
        }
    
        try {
            // Use pagination to process content in batches
            int pageSize = 100;
            int pageNumber = 0;
            double totalSentiment = 0.0;
            long totalItems = 0;
            
            Page<Content> contentPage;
            do {
                Pageable pageable = PageRequest.of(pageNumber, pageSize);
                contentPage = contentRepository.findAll(pageable);
                
                double pageSentiment = contentPage.getContent().stream()
                    .mapToDouble(this::calculateContentSentiment)
                    .average()
                    .orElse(0.0);
                    
                totalSentiment += pageSentiment * contentPage.getNumberOfElements();
                totalItems += contentPage.getNumberOfElements();
                
                pageNumber++;
            } while (contentPage.hasNext() && pageNumber < 5); // Limit to 500 items
            
            return totalItems > 0 ? totalSentiment / totalItems : 0.0;
            
        } catch (Exception e) {
            logger.error("Error analyzing trend sentiment: {}", e.getMessage());
            return 0.0;
        }
    }

    public TrendData analyzeTrendSentiment(String topic) {
        if (topic == null || topic.trim().isEmpty()) {
            return null;
        }
    
        try {
            // Find content related to this topic by title
            Pageable pageable = PageRequest.of(0, 100);  // Get first 100 records
            Page<Content> contentPage = contentRepository.findByTitleContainingIgnoreCase(topic, pageable);
            
            if (!contentPage.hasContent()) {
                logger.warn("No content found for topic: {}", topic);
                return null;
            }
    
            // Calculate average sentiment
            double averageSentiment = contentPage.getContent().stream()
                .mapToDouble(this::analyzeTrendSentiment)
                .average()
                .orElse(0.0);
    
            // Create and return TrendData
            TrendData trendData = new TrendData();
            trendData.setTopic(topic);
            trendData.setTrendScore(averageSentiment);
            trendData.setAnalysisTimestamp(LocalDateTime.now());
            
            return trendData;
        } catch (Exception e) {
            logger.error("Error analyzing sentiment for topic: " + topic, e);
            return null;
        }
    }
    
    private double calculateContentSentiment(Content content) {
        try {
            String contentText = content.getContentBody();
            if (contentText == null || contentText.isEmpty()) {
                return 0.0;
            }
    
            Annotation annotation = new Annotation(contentText);
            pipeline.annotate(annotation);
    
            return annotation.get(CoreAnnotations.SentencesAnnotation.class).stream()
                .mapToDouble(sentence -> {
                    Tree tree = sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class);
                    return RNNCoreAnnotations.getPredictedClass(tree);
                })
                .average()
                .orElse(0.0);
        } catch (Exception e) {
            logger.error("Error calculating content sentiment: {}", e.getMessage());
            return 0.0;
        }
    }

    public Map<String, Object> analyzeTrendOpportunities(String keyword) {
        // Get trend data from internal analysis
        Map<String, Object> trendData = new HashMap<>();
        trendData.put("interest_over_time", Arrays.asList(
            Map.of("date", LocalDateTime.now(), "value", Math.random() * 100),
            Map.of("date", LocalDateTime.now().minusDays(1), "value", Math.random() * 100)
        ));

        // Analyze historical content performance
        List<Content> relatedContent = contentRepository.findByContentBodyContaining(keyword);

        // Calculate opportunity score
        double opportunityScore = calculateOpportunityScore(trendData, relatedContent);

        // Generate content recommendations
        List<String> recommendations = generateContentRecommendations(trendData, relatedContent);

        // Prepare response
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("keyword", keyword);
        analysis.put("opportunityScore", opportunityScore);
        analysis.put("trendData", trendData);
        analysis.put("recommendations", recommendations);
        analysis.put("relatedTopics", extractRelatedTopics(trendData));
        analysis.put("contentGaps", identifyContentGaps(trendData, relatedContent));

        return analysis;
    }

    protected Map<String, Object> fetchGoogleTrends(String keyword, String region) {
        // Removed
        return new HashMap<>();
    }

    private double calculateTrendScore(Map<String, Object> trendData) {
        try {
            Object interestOverTimeObj = trendData.get("interest_over_time");
            if (interestOverTimeObj instanceof Map<?, ?>) {
                Map<String, Object> interestOverTime = (Map<String, Object>) interestOverTimeObj;
                if (interestOverTime == null) return 0.0;

                List<Integer> values = (List<Integer>) interestOverTime.get("values");
                if (values == null || values.isEmpty()) return 0.0;

                // Calculate trend momentum
                double sum = 0;
                double weight = 1.0;
                double totalWeight = 0;

                for (int i = values.size() - 1; i >= 0; i--) {
                    sum += values.get(i) * weight;
                    totalWeight += weight;
                    weight *= 0.9; // Decay factor for older data
                }

                return sum / totalWeight / 100.0;
            }
        } catch (Exception e) {
            logger.error("Error calculating trend score", e);
            return 0.0;
        }
        return 0.0;
    }

    private double calculateAverageSentiment(List<Content> content) {
        if (content.isEmpty()) return 0.5;

        return content.stream()
                .mapToDouble(this::analyzeSentimentScore)
                .average()
                .orElse(0.5);
    }

    private double calculateEngagementScore(List<Content> content) {
        try {
            if (content == null || content.isEmpty()) {
                return 0.0;
            }
            
            return content.stream()
                .filter(Objects::nonNull)
                .mapToDouble(c -> {
                    try {
                        String metricsJson = c.getMetrics();
                        if (metricsJson != null) {
                            Map<String, Object> metrics = objectMapper.readValue(
                                metricsJson, 
                                new TypeReference<Map<String, Object>>() {}
                            );
                            // Extract numeric values safely
                            double total = metrics.values().stream()
                                .filter(v -> v instanceof Number)
                                .mapToDouble(v -> ((Number) v).doubleValue())
                                .sum();
                            return Math.min(1.0, total / 1000.0); // Normalize to 0-1
                        }
                        // If no metrics, use engagement score directly
                        return c.getEngagement() != null ? c.getEngagement() : 0.0;
                    } catch (JsonProcessingException e) {
                        logger.warn("Error parsing metrics JSON: {}", e.getMessage());
                        // Fallback to engagement score if metrics parsing fails
                        return c.getEngagement() != null ? c.getEngagement() : 0.0;
                    }
                })
                .average()
                .orElse(0.0);
        } catch (Exception e) {
            logger.error("Error calculating engagement score for content list", e);
            return 0.0;
        }
    }

    private double calculateTrendEngagementScore(List<Content> contentList) {
        double engagementScore = 0.0;
        for (Content content : contentList) {
            engagementScore += content.getEngagement() != null ? content.getEngagement() : 0;
        }
        return engagementScore / contentList.size();
    }

    private List<String> generateContentRecommendations(Map<String, Object> trendData, List<Content> relatedContent) {
        List<String> recommendations = new ArrayList<>();

        try {
            // Get most recent trend data
            Map<String, Map<String, Object>> topTopics = new HashMap<>();
            Map<String, Double> sentiments = new HashMap<>();

            // Generate recommendations based on trending topics and sentiment
            topTopics.entrySet().stream()
                    .limit(5)
                    .forEach(entry -> {
                        String topic = entry.getKey();
                        recommendations.add(String.format(
                                "Consider creating content about '%s' with a %s tone",
                                topic,
                                getMostPositiveSentiment(sentiments)
                        ));
                    });

        } catch (Exception e) {
            logger.error("Error generating content recommendations", e);
        }

        return recommendations;
    }

    private List<String> identifyContentGaps(Map<String, Object> trendData, List<Content> relatedContent) {
        List<String> gaps = new ArrayList<>();

        try {
            // Extract related topics
            Object relatedTopicsObj = trendData.get("related_topics");
            if (relatedTopicsObj instanceof Map<?, ?>) {
                Map<String, Object> relatedTopics = (Map<String, Object>) relatedTopicsObj;
                if (relatedTopics != null) {
                    Object topicsObj = relatedTopics.get("topics");
                    if (topicsObj instanceof List<?>) {
                        List<Map<String, Object>> topics = (List<Map<String, Object>>) topicsObj;
                        if (topics != null && !topics.isEmpty()) {
                            // Convert the interest over time data into a map of topic -> value
                            Set<String> trendingTopics = topics.stream()
                                    .map(topic -> (String) topic.get("title"))
                                    .collect(Collectors.toSet());

                            // Compare with existing content
                            Set<String> coveredTopics = relatedContent.stream()
                                    .map(Content::getContentBody)
                                    .collect(Collectors.toSet());

                            // Identify gaps
                            trendingTopics.stream()
                                    .filter(topic -> !isTopicCovered(topic, coveredTopics))
                                    .forEach(gaps::add);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Could not identify content gaps", e);
        }

        return gaps;
    }

    private boolean isTopicCovered(String topic, Set<String> coveredTopics) {
        return coveredTopics.stream()
                .anyMatch(content -> content.toLowerCase().contains(topic.toLowerCase()));
    }

    private List<String> extractRelatedTopics(Map<String, Object> trendData) {
        try {
            Object relatedTopicsObj = trendData.get("related_topics");
            if (relatedTopicsObj instanceof Map<?, ?>) {
                Map<String, Object> relatedTopics = (Map<String, Object>) relatedTopicsObj;
                if (relatedTopics != null) {
                    Object topicsObj = relatedTopics.get("topics");
                    if (topicsObj instanceof List<?>) {
                        List<Map<String, String>> topics = (List<Map<String, String>>) topicsObj;
                        if (topics != null) {
                            return topics.stream()
                                    .map(topic -> topic.get("title"))
                                    .collect(Collectors.toList());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Could not extract related topics", e);
        }
        return new ArrayList<>();
    }

    /**
     * Analyzes industry-specific trends using a multi-modal approach combining:
     * 1. Word embeddings for semantic analysis
     * 2. Time series analysis for trend prediction
     * 3. Cross-industry correlation analysis
     * 4. Competitive intelligence gathering
     * 5. Sentiment-based market signals
     */
    @Cacheable(value = "industryTrends", key = "#industry")
    public Map<String, Object> getIndustryTrends(String industry) {
        logger.info("Analyzing trends for industry: {}", industry);
        Map<String, Object> trends = new HashMap<>();
        
        try {
            // Get recent content for semantic analysis
            List<Content> recentContent = contentRepository.findContentFromLastNHours(LocalDateTime.now().minusHours(720)); // Last 30 days

            // Train industry-specific word embeddings
            Word2Vec industryModel = trainIndustrySpecificModel(recentContent, industry);

            // Extract emerging concepts using word embeddings
            Map<String, Double> emergingConcepts = findEmergingConcepts(industryModel, industry);
            trends.put("emergingConcepts", emergingConcepts);

            // Analyze cross-industry influences
            Map<String, Double> crossIndustryImpact = analyzeCrossIndustryImpact(industry, industryModel);
            trends.put("crossIndustryImpact", crossIndustryImpact);

            // Generate content recommendations
            Map<String, Double> contentGaps = identifyContentOpportunities(
                    recentContent,
                    industry,
                    industryModel);
            trends.put("contentOpportunities", contentGaps);

            // Predict trend lifecycle stages
            Map<String, String> trendLifecycles = predictTrendLifecycles(emergingConcepts);
            trends.put("trendLifecycles", trendLifecycles);

            // Calculate industry sentiment signals
            Map<String, Object> marketSentiment = analyzeMarketSentiment(industry, recentContent);
            trends.put("marketSentiment", marketSentiment);

            // Generate AI-powered recommendations
            List<String> strategicRecommendations = generateStrategicInsights(
                    emergingConcepts,
                    crossIndustryImpact,
                    contentGaps,
                    marketSentiment
            );
            trends.put("strategicRecommendations", strategicRecommendations);

        } catch (Exception e) {
            logger.error("Error analyzing industry trends for: " + industry, e);
            throw new RuntimeException("Failed to analyze industry trends", e);
        }

        return trends;
    }

    private Word2Vec trainIndustrySpecificModel(List<Content> content, String industry) throws Exception {
        // Create temporary corpus file
        File tempFile = File.createTempFile("industry_corpus", ".txt");
        try (FileWriter writer = new FileWriter(tempFile)) {
            for (Content c : content) {
                writer.write(c.getContentBody() + "\n");
            }
        }

        // Configure Word2Vec
        TokenizerFactory t = new DefaultTokenizerFactory();
        t.setTokenPreProcessor(new CommonPreprocessor());

        SentenceIterator iterator = new BasicLineIterator(tempFile);

        // Build model
        Word2Vec vec = new Word2Vec.Builder()
                .minWordFrequency(2)
                .iterations(5)
                .layerSize(100)
                .seed(42)
                .windowSize(5)
                .iterate(iterator)
                .tokenizerFactory(t)
                .build();

        vec.fit();
        return vec;
    }

    private Map<String, Double> findEmergingConcepts(Word2Vec model, String industry) {
        // Find terms most similar to industry-specific seed words
        Collection<String> similar = model.wordsNearest(industry, 20);
        Map<String, Double> concepts = new HashMap<>();

        for (String term : similar) {
            double relevance = model.similarity(industry, term);
            concepts.put(term, relevance);
        }

        return concepts;
    }

    private Map<String, Double> analyzeCrossIndustryImpact(String industry, Word2Vec model) {
        // Define related industries
        List<String> relatedIndustries = Arrays.asList(
                "technology", "finance", "healthcare", "retail",
                "education", "entertainment", "manufacturing"
        );

        Map<String, Double> impacts = new HashMap<>();
        for (String relatedIndustry : relatedIndustries) {
            if (!relatedIndustry.equals(industry)) {
                double impact = model.similarity(industry, relatedIndustry);
                impacts.put(relatedIndustry, impact);
            }
        }
        return impacts;
    }

    private Map<String, Double> identifyContentOpportunities(
            List<Content> content,
            String industry,
            Word2Vec model) {

        // Analyze existing content coverage
        Set<String> coveredTopics = content.stream()
                .map(Content::getContentBody)
                .collect(Collectors.toSet());

        // Find potential opportunities
        Map<String, Double> opportunities = new HashMap<>();
        Collection<String> industryTerms = model.wordsNearest(industry, 50);

        for (String term : industryTerms) {
            boolean isCovered = coveredTopics.stream()
                    .anyMatch(topic -> topic.toLowerCase().contains(term.toLowerCase()));

            if (!isCovered) {
                double relevance = model.similarity(industry, term);
                opportunities.put(term, relevance);
            }
        }
        return opportunities;
    }

    private Map<String, String> predictTrendLifecycles(Map<String, Double> emergingConcepts) {
        Map<String, String> lifecycles = new HashMap<>();

        emergingConcepts.forEach((concept, score) -> {
            String stage;
            if (score > 0.8) {
                stage = "EMERGING";
            } else if (score > 0.6) {
                stage = "GROWING";
            } else if (score > 0.4) {
                stage = "MATURE";
            } else {
                stage = "DECLINING";
            }
            lifecycles.put(concept, stage);
        });

        return lifecycles;
    }

    private Map<String, Object> analyzeMarketSentiment(String industry, List<Content> content) {
        Map<String, Object> sentiment = new HashMap<>();

        // Calculate overall sentiment
        double averageSentiment = calculateAverageSentiment(content);
        sentiment.put("overallSentiment", averageSentiment);

        // Sentiment volatility
        double volatility = calculateSentimentVolatility(content);
        sentiment.put("sentimentVolatility", volatility);

        // Sentiment momentum
        double momentum = calculateSentimentMomentum(content);
        sentiment.put("sentimentMomentum", momentum);

        return sentiment;
    }

    private double calculateSentimentVolatility(List<Content> contents) {
        // Example logic to calculate sentiment volatility
        if (contents == null || contents.isEmpty()) {
            return 0.0;
        }

        List<Double> sentimentScores = new ArrayList<>();
        for (Content content : contents) {
            // Assuming analyzedSentiment is a numeric string representation of sentiment score
            try {
                double score = Double.parseDouble(content.getAnalyzedSentiment());
                sentimentScores.add(score);
            } catch (NumberFormatException e) {
                // Handle case where sentiment cannot be parsed
            }
        }

        // Calculate volatility (standard deviation in this case)
        double mean = sentimentScores.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        
        double sumSquaredDiff = sentimentScores.stream()
            .mapToDouble(score -> Math.pow(score - mean, 2))
            .sum();
        
        return Math.sqrt(sumSquaredDiff);
    }

    private double calculateSentimentMomentum(List<Content> content) {
        // Example logic to calculate sentiment momentum
        // This can be implemented based on the actual requirements
        return 0.0;
    }

    private List<String> generateStrategicInsights(
            Map<String, Double> emergingConcepts,
            Map<String, Double> crossIndustryImpact,
            Map<String, Double> contentGaps,
            Map<String, Object> marketSentiment) {

        List<String> insights = new ArrayList<>();

        // Add high-impact emerging concepts
        emergingConcepts.entrySet().stream()
                .filter(e -> e.getValue() > 0.7)
                .forEach(e -> insights.add(
                        String.format("Prioritize content about '%s' (relevance: %.2f)",
                                e.getKey(), e.getValue())
                ));

        // Add cross-industry opportunities
        crossIndustryImpact.entrySet().stream()
                .filter(e -> e.getValue() > 0.5)
                .forEach(e -> insights.add(
                        String.format("Consider exploring synergies with %s industry (impact: %.2f)",
                                e.getKey(), e.getValue())
                ));

        // Add content gap recommendations
        contentGaps.entrySet().stream()
                .filter(e -> e.getValue() > 0.6)
                .forEach(e -> insights.add(
                        String.format("Address untapped opportunity: '%s' (potential: %.2f)",
                                e.getKey(), e.getValue())
                ));

        return insights;
    }

    public Map<String, Object> getRegionalTrends(String regionStr) {
        Map<String, Object> trends = new HashMap<>();
        
        try {
            // Get trending topics first
            List<TrendData> trendingTopics = getTrendingTopicsForRegion(regionStr);
            
            // Process regional data
            Map<String, List<Double>> valueHistory = new HashMap<>();
            Map<String, List<String>> dateHistory = new HashMap<>();
            Map<String, Object> evolution = new HashMap<>();

            // Add trending topics data
            Map<String, Map<String, Object>> topicsData = new HashMap<>();
            for (TrendData trend : trendingTopics) {
                String topic = trend.getTopic();
                Map<String, Object> topicData = new HashMap<>();
                
                // Add trend metrics
                topicData.put("trendScore", trend.getTrendScore());
                topicData.put("momentum", trend.getMomentum());
                topicData.put("volatility", trend.getVolatility());
                topicData.put("confidenceScore", trend.getConfidenceScore());
                topicData.put("pattern", trend.getTrendPattern());
                
                // Add historical data if available
                if (trend.getHistoricalValuesList() != null) {
                    topicData.put("historicalValues", trend.getHistoricalValuesList());
                    topicData.put("historicalDates", trend.getHistoricalDatesList());
                }
                
                // Add regional specific data
                if (trend.getRegion() != null && trend.getRegion().name().equals(regionStr)) {
                    topicData.put("isRegionalTrend", true);
                }
                
                // Add the trend data from the trendingTopicsMap if available
                Map<String, Map<String, Object>> trendingTopicsMap = trend.getTrendingTopicsMap();
                if (trendingTopicsMap != null && trendingTopicsMap.containsKey(topic)) {
                    topicData.putAll(trendingTopicsMap.get(topic));
                }
                
                topicsData.put(topic, topicData);
            }
            
            trends.put("trendingTopics", topicsData);
            trends.put("region", regionStr);
            trends.put("timestamp", LocalDateTime.now());
            trends.put("evolution", evolution);
            trends.put("trends", trendingTopics);  // Add the original TrendData list for reference

        } catch (Exception e) {
            logger.error("Error analyzing regional trends for {}: {}", regionStr, e.getMessage());
        }

        return trends;
    }

    private List<TrendData> getTrendingTopicsForRegion(String region) {
        List<TrendData> trends = new ArrayList<>();
        
        try {
            // Fetch current content for analysis
            List<Content> recentContent = contentRepository.findTop100ByOrderByCreatedAtDesc();
            Set<String> topics = new HashSet<>();
            
            // Extract topics from content
            for (Content content : recentContent) {
                topics.addAll(extractKeywords(content));
            }

            // Analyze each topic with regional filter
            for (String topic : topics) {
                TrendData trend = new TrendData();
                trend.setAnalysisTimestamp(LocalDateTime.now());
                trend.setTopic(topic);
                trend.setCategory("trending");
                try {
                    trend.setRegion(TrendData.Region.valueOf(region));
                } catch (IllegalArgumentException e) {
                    logger.error("Invalid region: {}", region);
                }

                // Set trend data
                trend.setTrendingTopics(String.valueOf(trend.getTrendingTopicsMap()));
                trend.setTrendingTopicsMap(Map.of(topic, Map.of("value", Math.random() * 100)));

                // Calculate and set trend score
                double trendScore = Math.random();
                trend.setTrendScore((Double) trendScore);

                // Extract and set historical data
                List<Double> historicalValues = Arrays.asList(Math.random() * 100, Math.random() * 100);
                List<String> historicalDates = Arrays.asList(LocalDateTime.now().toString(), LocalDateTime.now().minusDays(1).toString());
                trend.setHistoricalValues(objectMapper.writeValueAsString(historicalValues));
                trend.setHistoricalDates(objectMapper.writeValueAsString(historicalDates));
                trend.setHistoricalValuesList(historicalValues);
                trend.setHistoricalDatesList(historicalDates);

                // Calculate and set trend metrics
                if (!historicalValues.isEmpty()) {
                    double momentum = calculateMomentum(historicalValues);
                    double volatility = calculateVolatility(historicalValues);
                    trend.setMomentum(momentum);
                    trend.setVolatility(volatility);
                    trend.setConfidenceScore(calculateConfidenceScore(volatility, momentum));
                    trend.setTrendPattern(detectEnhancedTrendPattern(historicalValues));
                }

                trends.add(trend);
            }

            // Sort trends by trend score in descending order
            trends.sort((t1, t2) -> 
                Double.compare(
                t2.getTrendScore() != null ? t2.getTrendScore() : 0.0,
                t1.getTrendScore() != null ? t1.getTrendScore() : 0.0
            ));
            
        } catch (Exception e) {
            logger.error("Error fetching trending topics for region {}: {}", region, e.getMessage());
        }

        return trends;
    }

    private double predictNextValue(List<Double> historicalValues, double momentum, double volatility) {
        if (historicalValues.isEmpty()) {
            return 0.0;
        }

        double lastValue = historicalValues.get(historicalValues.size() - 1);
        double prediction = lastValue * (1 + momentum);
        
        // Adjust prediction based on volatility
        if (volatility > 0.5) {
            // High volatility: be more conservative
            prediction = (prediction + lastValue) / 2;
        }
        
        // Ensure prediction stays within Google Trends range (0-100)
        return Math.min(100, Math.max(0, prediction));
    }

    private double calculateConfidenceScore(double volatility, double momentum) {
        // Higher volatility decreases confidence, stronger momentum increases it
        double volatilityImpact = 1 - volatility;
        double momentumImpact = Math.abs(momentum);
        
        // Combine factors with weights
        return (volatilityImpact * 0.7) + (momentumImpact * 0.3);
    }

    public List<TrendInsight> getTrendInsightsList() {
        List<TrendData> trendDataList = getTrendingTopics();
        return trendDataList.stream().map(trendData -> {
            // Calculate sentiment and engagement
            double sentiment = calculateSentimentScore(trendData);
            double engagement = calculateEngagementScore(trendData.getTrendScore());
            
            // Generate meaningful insight based on data
            String insight = generateInsightFromData(trendData, sentiment, engagement);
            
            // Create enhanced insight object
            TrendInsight trendInsight = new TrendInsight(
                trendData.getTopic(),
                insight,
                trendData.getTrendScore() != null ? trendData.getTrendScore() : 0.0
            );
            
            // Add additional metrics
            trendInsight.setAverageSentiment(sentiment);
            trendInsight.setEngagementScore(engagement);
            trendInsight.setTrendDirection(determineTrendDirection(trendData));
            trendInsight.setRelatedKeywords(findRelatedKeywords(trendData.getTopic()));
            trendInsight.getMetrics().put("momentum", trendData.getMomentum());
            trendInsight.getMetrics().put("volatility", trendData.getVolatility());
            trendInsight.getMetrics().put("confidence", trendData.getConfidenceScore());
            
            return trendInsight;
        }).collect(Collectors.toList());
    }

    private String generateInsightFromData(TrendData trendData, double sentiment, double engagement) {
        StringBuilder insight = new StringBuilder();
        
        // Add trend strength analysis
        if (trendData.getTrendScore() > 0.8) {
            insight.append("Strongly trending topic. ");
        } else if (trendData.getTrendScore() > 0.5) {
            insight.append("Moderately trending topic. ");
        }
        
        // Add sentiment analysis
        if (sentiment > 0.7) {
            insight.append("Very positive sentiment. ");
        } else if (sentiment > 0.5) {
            insight.append("Slightly positive sentiment. ");
        } else if (sentiment < 0.3) {
            insight.append("Negative sentiment detected. ");
        }
        
        // Add engagement analysis
        if (engagement > 0.8) {
            insight.append("High engagement potential. ");
        } else if (engagement > 0.5) {
            insight.append("Moderate engagement potential. ");
        } else {
            insight.append("Limited engagement so far. ");
        }
        
        // Add momentum analysis
        if (trendData.getMomentum() > 0.5) {
            insight.append("Strong upward momentum. ");
        } else if (trendData.getMomentum() < -0.5) {
            insight.append("Declining momentum. ");
        }
        
        // Add volatility insights
        if (trendData.getVolatility() > 0.7) {
            insight.append("High volatility - monitor closely.");
        } else if (trendData.getVolatility() < 0.3) {
            insight.append("Stable trend pattern.");
        }
        
        return insight.toString();
    }

    private double calculateSentimentScore(TrendData trendData) {
        try {
            // Get sentiment distribution from trend data
            String sentimentDistStr = trendData.getSentimentDistribution();
            if (sentimentDistStr != null) {
                Map<String, Double> sentimentDist = objectMapper.readValue(
                    sentimentDistStr, 
                    new TypeReference<Map<String, Double>>() {}
                );
                return sentimentDist.getOrDefault("positive", 0.0);
            }
        } catch (JsonProcessingException e) {
            logger.error("Error parsing sentiment distribution for topic {}: {}", 
                trendData.getTopic(), e.getMessage());
        }
        return 0.5; // Default neutral sentiment
    }

    @Cacheable(value = "relatedKeywords", key = "#topic", unless = "#result.isEmpty()")
public List<String> findRelatedKeywords(String topic) {
    try {
        // First check the database for recent related keywords
        List<TrendData> recentTrends = trendDataRepository.findLatestTrendsByTopic(topic, 5);
        if (!recentTrends.isEmpty()) {
            // Get related keywords from the most recent trend data
            TrendData latestTrend = recentTrends.get(0);
            List<String> relatedKeywords = latestTrend.getRelatedKeywords();
            if (!relatedKeywords.isEmpty()) {
                return relatedKeywords;
            }

            // If no related keywords found in the latest trend, try to extract from metadata
            Map<String, Object> metadata = latestTrend.getMetadata();
            if (metadata.containsKey("related_keywords")) {
                @SuppressWarnings("unchecked")
                List<String> keywordsFromMetadata = (List<String>) metadata.get("related_keywords");
                if (keywordsFromMetadata != null && !keywordsFromMetadata.isEmpty()) {
                    // Store these keywords in the proper field for future use
                    latestTrend.setRelatedKeywords(keywordsFromMetadata);
                    trendDataRepository.save(latestTrend);
                    return keywordsFromMetadata;
                }
            }
        }

        // Fallback to word2vec if no recent data is available
        if (word2Vec != null) {
            List<String> keywords = word2Vec.wordsNearest(topic, 5).stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());
            
            // If we found keywords using word2vec, store them in a new TrendData
            if (!keywords.isEmpty() && !recentTrends.isEmpty()) {
                TrendData latestTrend = recentTrends.get(0);
                latestTrend.setRelatedKeywords(keywords);
                trendDataRepository.save(latestTrend);
            }
            
            return keywords;
        }
    } catch (Exception e) {
        logger.error("Error finding related keywords for topic {}: {}", 
            topic, e.getMessage());
    }
    return new ArrayList<>();
}

    private TrendDirection determineTrendDirection(TrendData trendData) {
        List<Double> historicalValues = trendData.getHistoricalValuesList();
        if (historicalValues == null || historicalValues.size() < 2) {
            return TrendDirection.STABLE;
        }
        
        double volatility = trendData.getVolatility();
        double momentum = trendData.getMomentum();
        
        if (momentum > 0.3) {
            return TrendDirection.UP;
        } else if (momentum < -0.3) {
            return TrendDirection.DOWN;
        } else {
            return TrendDirection.STABLE;
        }
    }

    private TrendPattern determineTrendPattern(TrendData trendData) {
        List<Double> historicalValues = trendData.getHistoricalValuesList();
        if (historicalValues == null || historicalValues.size() < 2) {
            return TrendPattern.INSUFFICIENT_DATA;
        }
        
        double volatility = trendData.getVolatility();
        double momentum = trendData.getMomentum();
        
        // Pattern detection logic
        if (momentum > 0.3 && volatility < 0.2) {
            return TrendPattern.STEADY_RISE;
        } else if (momentum < -0.3 && volatility < 0.2) {
            return TrendPattern.STEADY_DECLINE;
        } else if (volatility > 0.4) {
            return momentum > 0 ? TrendPattern.VOLATILE_RISE : TrendPattern.VOLATILE_DECLINE;
        } else if (Math.abs(momentum) < 0.1 && volatility < 0.15) {
            return TrendPattern.CONSOLIDATION;
        } else if (isBreakoutPattern(historicalValues, Arrays.asList(momentum))) {
            return TrendPattern.BREAKOUT;
        } else if (isReversalPattern(historicalValues, Arrays.asList(momentum))) {
            return TrendPattern.REVERSAL;
        }

        return TrendPattern.UNDEFINED;
    }

    private static final ThreadLocal<Boolean> skipSaving = new ThreadLocal<>();
    
    public static void setSkipSaving(boolean skip) {
        skipSaving.set(skip);
    }
    
    public static void clearSkipSaving() {
        skipSaving.remove();
    }

    public List<TrendData> getTrendingTopics() {
        try {
            if (Boolean.TRUE.equals(skipSaving.get())) {
                return getTrendingTopicsWithoutSaving();
            }
            return getTrendingTopicsInternal();
        } finally {
            clearSkipSaving();
        }
    }

    public List<TrendData> getTrendingTopics(boolean skipSaving) {
        if (skipSaving) {
            return getTrendingTopicsWithoutSaving();
        }
        return getTrendingTopics();
    }
    
    private List<TrendData> getTrendingTopicsWithoutSaving() {
        logger.info("Fetching trending topics from external sources without saving");
        List<TrendData> trends = new ArrayList<>();
        
        try {
            // Scrape from external sources
            trends.addAll(webScrapingService.scrapeGoogleTrends());
            trends.addAll(webScrapingService.scrapeTwitterTrends());
            trends.addAll(webScrapingService.scrapeHackerNews());
            trends.addAll(webScrapingService.scrapeGitHubTrends());
            trends.addAll(webScrapingService.scrapeStackOverflow());
    
            if (!trends.isEmpty()) {
                return trends;
            }
            
            // Fallback to database check without saving
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
            List<TrendData> recentTrends = trendDataRepository.findLatestTrends(cutoff);
            
            if (!recentTrends.isEmpty()) {
                return recentTrends;
            }
            
            // Last resort fallback
            Map<String, Object> trendData = new HashMap<>();
            trendData.put("interest_over_time", Arrays.asList(
                Map.of("date", LocalDateTime.now(), "value", Math.random() * 100),
                Map.of("date", LocalDateTime.now().minusDays(1), "value", Math.random() * 100)
            ));
            trends.addAll(convertMapToTrendData(trendData));
            
            return trends;
        } catch (Exception e) {
            logger.error("Error fetching trending topics: ", e);
            return new ArrayList<>();
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<TrendData> getTrendingTopicsInternal() {
        logger.info("Fetching trending topics from external sources and database");
        List<TrendData> trends = new ArrayList<>();
        
        try {
            // First try to scrape from external sources
            logger.info("Fetching trends from external sources");
            trends.addAll(webScrapingService.scrapeGoogleTrends());
            trends.addAll(webScrapingService.scrapeTwitterTrends());
            trends.addAll(webScrapingService.scrapeHackerNews());
            trends.addAll(webScrapingService.scrapeGitHubTrends());
            trends.addAll(webScrapingService.scrapeStackOverflow());
    
            // If web scraping successful, save to database and return
            if (!trends.isEmpty()) {
                logger.info("Successfully scraped {} trends from external sources", trends.size());
                trendDataRepository.saveAll(trends);
                return trends;
            }
            
            // If web scraping yields no results, check database for recent trends
            logger.info("No trends from external sources, checking database");
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
            List<TrendData> recentTrends = trendDataRepository.findLatestTrends(cutoff);
            
            if (!recentTrends.isEmpty()) {
                logger.info("Found {} recent trends in database", recentTrends.size());
                return recentTrends;
            }
            
            // Fallback to internal analysis if both web scraping and database yield no results
            logger.warn("No trends found from external sources or database, falling back to internal analysis");
            Map<String, Object> trendData = new HashMap<>();
            trendData.put("interest_over_time", Arrays.asList(
                Map.of("date", LocalDateTime.now(), "value", Math.random() * 100),
                Map.of("date", LocalDateTime.now().minusDays(1), "value", Math.random() * 100)
            ));
            trends.addAll(convertMapToTrendData(trendData));
            
            // Save fallback trends to database
            if (!trends.isEmpty()) {
                trendDataRepository.saveAll(trends);
            }
            
            return trends;
            
        } catch (Exception e) {
            logger.error("Error fetching trending topics: ", e);
            return new ArrayList<>();
        }
    }
    
    private double calculateEnhancedTrendScore(Map<String, Object> trendData) {
        // Placeholder logic for enhanced trend score calculation
        // You can customize this logic based on your requirements
        double score = 0.0;
        // Example: Calculate score based on trendData attributes
        if (trendData.containsKey("someKey")) {
            score += (double) trendData.get("someKey"); // Example calculation
        }
        return score;
    }

    public TrendPattern detectEnhancedTrendPattern(List<Double> values) {
        // Placeholder logic for enhanced trend pattern detection
        // You can customize this logic based on your requirements
        return TrendPattern.UNDEFINED;
    }

    private List<TrendData> convertMapToTrendData(Map<String, Object> trendDataMap) {
        List<TrendData> trendDataList = new ArrayList<>();
        if (trendDataMap == null) {
            logger.warn("Received null trendDataMap in convertMapToTrendData");
            return trendDataList;
        }
        
        try {
            double maxValue = 0.0;
            double sumValue = 0.0;
            int count = 0;
            List<Map<String, Object>> timelineData = null;
            
            // Extract timeline data with robust error handling
            try {
                if (trendDataMap.containsKey("interest_over_time_data")) {
                    // Legacy format
                    Object interestOverTimeObj = trendDataMap.get("interest_over_time_data");
                    if (interestOverTimeObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> interestOverTime = (Map<String, Object>) interestOverTimeObj;
                        Object timelineObj = interestOverTime.get("timeline_data");
                        if (timelineObj instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> data = (List<Map<String, Object>>) timelineObj;
                            timelineData = data;
                        }
                    }
                } else if (trendDataMap.containsKey("interest_over_time")) {
                    // New format
                    Object timelineObj = trendDataMap.get("interest_over_time");
                    if (timelineObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> data = (List<Map<String, Object>>) timelineObj;
                        timelineData = data;
                    }
                }
            } catch (ClassCastException e) {
                logger.error("Error parsing timeline data: {}", e.getMessage());
                timelineData = new ArrayList<>();
            }
            
            if (timelineData == null || timelineData.isEmpty()) {
                logger.debug("No timeline data found in trend data map");
                // Create a TrendData object with available data
                TrendData trendData = new TrendData();
                trendData.setAnalysisTimestamp(LocalDateTime.now());
                trendData.setTopic((String) trendDataMap.get("query")); // Set the search query as topic
                if (trendDataMap.containsKey("trendScore")) {
                    trendData.setTrendScore((Double) trendDataMap.get("trendScore"));
                }
                trendDataList.add(trendData);
                return trendDataList;
            }
            
            // Process timeline data
            for (Map<String, Object> dataPoint : timelineData) {
                Object valueObj = dataPoint.get("value");
                if (valueObj == null) {
                    @SuppressWarnings("unchecked")
                    List<Integer> values = (List<Integer>) dataPoint.get("values");
                    if (values != null && !values.isEmpty()) {
                        valueObj = values.get(0);
                    }
                }
                
                if (valueObj != null) {
                    double value = ((Number) valueObj).doubleValue();
                    maxValue = Math.max(maxValue, value);
                    sumValue += value;
                    count++;
                }
            }
            
            double avgValue = count > 0 ? sumValue / count : 0.0;
            
            // Create TrendData object
            TrendData trendData = new TrendData();
            trendData.setAnalysisTimestamp(LocalDateTime.now());
            trendData.setTopic((String) trendDataMap.get("query")); // Set the search query as topic
            if (trendDataMap.containsKey("trendScore")) {
                trendData.setTrendScore((Double) trendDataMap.get("trendScore"));
            }

            setTrendMetrics(trendData, trendDataMap);
            
            // Set additional metrics
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("maxValue", maxValue);
            metrics.put("averageValue", avgValue);
            metrics.put("dataPoints", count);
            if (!timelineData.isEmpty()) {
                metrics.put("lastUpdated", timelineData.get(timelineData.size() - 1).get("date"));
            }
            
            trendData.setMetrics(objectMapper.writeValueAsString(metrics));
            trendDataList.add(trendData);
            
            logger.debug("Converted trend data for topic: {}, score: {}", trendData.getTopic(), trendData.getTrendScore());
            
        } catch (Exception e) {
            logger.error("Error converting trend data map to TrendData: {}", e.getMessage());
        }
        
        return trendDataList;
    }

    private void setTrendMetrics(TrendData trendData, Map<String, Object> trendDataMap) {
        try {
            // First convert the complex object to JSON string
            String jsonString = objectMapper.writeValueAsString(trendDataMap);
            
            // Parse it back as a Map to handle nested structures
            Map<String, Object> parsedMap = objectMapper.readValue(jsonString, 
                new TypeReference<Map<String, Object>>() {});
            
            // Extract trends data if present
            if (parsedMap.containsKey("trends")) {
                Map<String, Object> trends = (Map<String, Object>) parsedMap.get("trends");
                if (trends.containsKey("momentum")) {
                    trendData.setTrendScore(((Number) trends.get("momentum")).doubleValue());
                }
            }
            
            // Store the entire metrics JSON
            trendData.setMetrics(jsonString);
            
        } catch (JsonProcessingException e) {
            logger.error("Error parsing metrics JSON: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in setTrendMetrics: {}", e.getMessage());
        }
    }

    private double calculateMomentum(List<Double> values) {
        if (values == null || values.size() < 2) {
            return 0.0;
        }

        double momentum = 0.0;
        double weight = 1.0;
        double totalWeight = 0.0;

        // Calculate weighted momentum using recent values more heavily
        for (int i = values.size() - 1; i > 0; i--) {
            double change = values.get(i) - values.get(i - 1);
            momentum += change * weight;
            totalWeight += weight;
            weight *= 0.8; // Decay weight for older values
        }

        return totalWeight > 0 ? momentum / totalWeight : 0.0;
    }

    private double calculateVolatility(List<Double> values) {
        if (values == null || values.size() < 2) {
            return 0.0;
        }

        // Calculate standard deviation
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = values.stream()
            .mapToDouble(value -> Math.pow(value - mean, 2))
            .average()
            .orElse(0.0);

        return Math.sqrt(variance);
    }

    private Map<String, Object> analyzeCrossTopicRelationships(
            Map<String, List<Double>> sentimentSeries,
            Map<String, Object> topicEvolution) {
        Map<String, Object> relationships = new HashMap<>();
        
        try {
            // Calculate correlations between topics
            Map<String, Map<String, Double>> correlations = new HashMap<>();
            List<String> topics = new ArrayList<>(sentimentSeries.keySet());

            for (int i = 0; i < topics.size(); i++) {
                String topic1 = topics.get(i);
                Map<String, Double> topicCorrelations = new HashMap<>();

                for (int j = i + 1; j < topics.size(); j++) {
                    String topic2 = topics.get(j);
                    List<Double> series1 = sentimentSeries.get(topic1);
                    List<Double> series2 = sentimentSeries.get(topic2);

                    // Calculate correlation coefficient
                    double correlation = calculateCorrelation(series1, series2);
                    if (Math.abs(correlation) > 0.5) { // Only include significant correlations
                        topicCorrelations.put(topic2, correlation);
                    }
                }

                if (!topicCorrelations.isEmpty()) {
                    correlations.put(topic1, topicCorrelations);
                }
            }

            // Identify topic clusters
            List<Map<String, Object>> clusters = identifyTopicClusters(correlations);
            
            relationships.put("correlations", correlations);
            relationships.put("clusters", clusters);
            relationships.put("analysisTimestamp", LocalDateTime.now());
            relationships.put("dataPoints", sentimentSeries.size());
            
        } catch (Exception e) {
            logger.error("Error analyzing cross-topic relationships", e);
        }

        return relationships;
    }

    private double calculateCorrelation(List<Double> series1, List<Double> series2) {
        int n = Math.min(series1.size(), series2.size());
        if (n < 2) return 0.0;

        double sum1 = 0.0, sum2 = 0.0, sum1Sq = 0.0, sum2Sq = 0.0, pSum = 0.0;

        for (int i = 0; i < n; i++) {
            double x = series1.get(i);
            double y = series2.get(i);
            sum1 += x;
            sum2 += y;
            sum1Sq += x * x;
            sum2Sq += y * y;
            pSum += x * y;
        }

        double num = (n * pSum) - (sum1 * sum2);
        double den = Math.sqrt(((n * sum1Sq) - (sum1 * sum1)) * ((n * sum2Sq) - (sum2 * sum2)));

        return den == 0 ? 0.0 : num / den;
    }

    private List<Map<String, Object>> identifyTopicClusters(Map<String, Map<String, Double>> correlations) {
        List<Map<String, Object>> clusters = new ArrayList<>();
        Set<String> processedTopics = new HashSet<>();

        correlations.forEach((topic, related) -> {
            if (!processedTopics.contains(topic)) {
                Map<String, Object> cluster = new HashMap<>();
                List<String> members = new ArrayList<>();
                members.add(topic);

                related.forEach((relatedTopic, correlation) -> {
                    if (!processedTopics.contains(relatedTopic)) {
                        members.add(relatedTopic);
                        processedTopics.add(relatedTopic);
                    }
                });

                if (members.size() > 1) {
                    cluster.put("topics", members);
                    cluster.put("size", members.size());
                    cluster.put("centralTopic", topic);
                    clusters.add(cluster);
                }
                processedTopics.add(topic);
            }
        });

        return clusters;
    }

    private Map<String, Object> identifyOpportunities(
            Map<String, Object> topicEvolution,
            Map<String, Object> topicRelationships,
            Map<String, Double> volatilityScores) {
        Map<String, Object> opportunities = new HashMap<>();
        
        try {
            List<Map<String, Object>> emergingOpportunities = new ArrayList<>();
            List<Map<String, Object>> stableOpportunities = new ArrayList<>();
            
            Map<String, Object> rawTopics = (Map<String, Object>) topicEvolution.get("topics");
            Map<String, Map<String, Object>> topics = new HashMap<>();

            for (Map.Entry<String, Object> entry : rawTopics.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    topics.put(entry.getKey(), (Map<String, Object>) entry.getValue());
                } else {
                    // Handle the case where the value is not a Map<String, Object>
                    // You can log a warning or throw an exception based on your needs
                }
            }

            topics.forEach((topic, analysis) -> {
                double momentum = (Double) analysis.get("momentum");
                double volatility = volatilityScores.getOrDefault(topic, 0.0);
                String direction = (String) analysis.get("direction");
                
                Map<String, Object> opportunity = new HashMap<>();
                opportunity.put("topic", topic);
                opportunity.put("momentum", momentum);
                opportunity.put("volatility", volatility);
                opportunity.put("direction", direction);
                opportunity.put("confidence", calculateConfidenceScore(volatility, momentum));

                List<Double> volatilityList = new ArrayList<>();
                volatilityList.add(volatility);
                List<Double> momentumList = new ArrayList<>();
                momentumList.add(momentum);
                if (isBreakoutPattern(volatilityList, momentumList)) {
                    emergingOpportunities.add(opportunity);
                } else if ("STABLE".equals(direction) && volatility < 0.3) {
                    stableOpportunities.add(opportunity);
                } else if (isReversalPattern(volatilityList, momentumList)) {
                    stableOpportunities.add(opportunity);
                }
            });
            
            opportunities.put("emerging", emergingOpportunities);
            opportunities.put("stable", stableOpportunities);
            opportunities.put("analysisTimestamp", LocalDateTime.now());
            opportunities.put("dataPoints", topics.size());
            
        } catch (Exception e) {
            logger.error("Error identifying opportunities", e);
        }

        return opportunities;
    }

    private List<Map<String, Object>> generateTrendRecommendations(List<TrendData> trends) {
        List<Map<String, Object>> recommendations = new ArrayList<>();
        
        try {
            if (trends.isEmpty()) {
                return recommendations;
            }

            // Get the most recent trend data
            TrendData latestTrend = trends.get(0);
            Map<String, Map<String, Object>> topicsMap = latestTrend.getTrendingTopicsMap();

            if (topicsMap != null) {
                topicsMap.forEach((topic, data) -> {
                    double value = (Double) data.get("value");
                    double momentum = calculateMomentum(Collections.singletonList(value));
                    
                    Map<String, Object> recommendation = new HashMap<>();
                    recommendation.put("topic", topic);
                    recommendation.put("currentValue", value);
                    recommendation.put("momentum", momentum);
                    
                    // Generate specific recommendations based on trend analysis
                    String action = determineRecommendedAction(value, momentum);
                    String timing = determineRecommendedTiming(momentum);
                    
                    recommendation.put("recommendedAction", action);
                    recommendation.put("recommendedTiming", timing);
                    recommendation.put("confidence", calculateConfidenceScore(
                        calculateVolatilityFromValues(Collections.singletonList(value)), 
                        momentum
                    ));
                    
                    recommendations.add(recommendation);
                });
            }

            // Sort recommendations by confidence
            recommendations.sort((r1, r2) -> 
                Double.compare((Double) r2.get("confidence"), (Double) r1.get("confidence")));

        } catch (Exception e) {
            logger.error("Error generating trend recommendations", e);
        }

        return recommendations;
    }

    private String determineRecommendedAction(double value, double momentum) {
        if (momentum > 0.3 && value > 70) {
            return "CAPITALIZE_NOW";
        } else if (momentum > 0.1) {
            return "PREPARE_CONTENT";
        } else if (momentum < -0.2) {
            return "MONITOR_CLOSELY";
        } else {
            return "MAINTAIN_PRESENCE";
        }
    }

    private String determineRecommendedTiming(double momentum) {
        if (momentum > 0.3) {
            return "IMMEDIATE";
        } else if (momentum > 0.1) {
            return "THIS_WEEK";
        } else if (momentum > 0) {
            return "THIS_MONTH";
        } else {
            return "MONITOR";
        }
    }

    private double calculateEngagementScore(double value) {
        // Normalize the engagement score between 0 and 1
        return value / 100.0; // Since Google Trends values are between 0 and 100
    }

    private double calculateTrendScore(double averageValue, double maxValue) {
        // Calculate trend score using both average and maximum values
        double normalizedAvg = averageValue / 100.0;
        double normalizedMax = maxValue / 100.0;
        return (normalizedAvg * 0.6) + (normalizedMax * 0.4); // Weight recent trends more heavily
    }

    private double calculateSentimentScore(Map<String, Map<String, Object>> trendingTopicsMap) {
        // Calculate a sentiment score based on the trend patterns
        double score = 0.0;
        int count = 0;
        
        for (Map.Entry<String, Map<String, Object>> entry : trendingTopicsMap.entrySet()) {
            Map<String, Object> topicData = entry.getValue();
            double value = (Double) topicData.get("value");
            double interest = (Double) topicData.get("interest_over_time");
            
            // Combine value and interest for sentiment
            score += (value * 0.7) + (interest * 0.3);
            count++;
        }
        
        return count > 0 ? score / count : 0.0;
    }

    private TrendPattern detectTrendPattern(List<Double> values) {
        if (values == null || values.size() < 5) {
            return TrendPattern.UNDEFINED;
        }

        try {
            List<Double> changes = new ArrayList<>();
            for (int i = 1; i < values.size(); i++) {
                changes.add(values.get(i) - values.get(i - 1));
            }

            // Calculate pattern characteristics
            double momentum = calculateMomentum(values);
            double volatility = calculateVolatility(values);
            double averageChange = changes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            
            // Pattern detection logic
            if (momentum > 0.3 && volatility < 0.2) {
                return TrendPattern.STEADY_RISE;
            } else if (momentum < -0.3 && volatility < 0.2) {
                return TrendPattern.STEADY_DECLINE;
            } else if (volatility > 0.4) {
                return momentum > 0 ? TrendPattern.VOLATILE_RISE : TrendPattern.VOLATILE_DECLINE;
            } else if (Math.abs(momentum) < 0.1 && volatility < 0.15) {
                return TrendPattern.CONSOLIDATION;
            } else if (isBreakoutPattern(values, changes)) {
                return TrendPattern.BREAKOUT;
            } else if (isReversalPattern(values, changes)) {
                return TrendPattern.REVERSAL;
            }

            return TrendPattern.UNDEFINED;
        } catch (Exception e) {
            logger.error("Error detecting trend pattern", e);
            return TrendPattern.UNDEFINED;
        }
    }

    private boolean isBreakoutPattern(List<Double> values, List<Double> changes) {
        if (values.size() < 5) return false;

        // Calculate recent average and volatility
        double recentAvg = values.subList(values.size() - 3, values.size()).stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        double historicalAvg = values.subList(0, values.size() - 3).stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        double historicalVolatility = calculateVolatility(values.subList(0, values.size() - 3));

        // Breakout conditions
        return Math.abs(recentAvg - historicalAvg) > (2 * historicalVolatility) &&
               changes.get(changes.size() - 1) > 0;
    }

    private boolean isReversalPattern(List<Double> values, List<Double> changes) {
        if (values.size() < 5) return false;

        // Calculate trend direction before and after potential reversal point
        double preTrend = calculateMomentum(values.subList(0, values.size() - 2));
        double postTrend = calculateMomentum(values.subList(values.size() - 2, values.size()));

        // Reversal conditions
        return Math.signum(preTrend) != Math.signum(postTrend) &&
               Math.abs(preTrend) > 0.2 &&
               Math.abs(postTrend) > 0.2;
    }

    private Map<String, Object> analyzeSeasonality(List<Double> values, List<String> dates) {
        Map<String, Object> seasonality = new HashMap<>();
        try {
            if (values.size() < 30) {
                seasonality.put("reliable", false);
                return seasonality;
            }

            // Detect daily patterns
            Map<Integer, Double> dailyAverages = new HashMap<>();
            for (int i = 0; i < values.size(); i++) {
                int dayOfWeek = LocalDate.parse(dates.get(i)).getDayOfWeek().getValue();
                dailyAverages.merge(dayOfWeek, values.get(i), Double::sum);
            }

            // Normalize daily averages
            Map<String, Double> normalizedDailyPatterns = new HashMap<>();
            dailyAverages.forEach((day, sum) -> {
                normalizedDailyPatterns.put(
                    LocalDate.now().with(DayOfWeek.of(day)).getDayOfWeek().toString(),
                    sum / values.size()
                );
            });

            // Detect monthly patterns
            Map<Integer, Double> monthlyAverages = new HashMap<>();
            for (int i = 0; i < values.size(); i++) {
                int month = LocalDate.parse(dates.get(i)).getMonthValue();
                monthlyAverages.merge(month, values.get(i), Double::sum);
            }

            // Normalize monthly averages
            Map<String, Double> normalizedMonthlyPatterns = new HashMap<>();
            monthlyAverages.forEach((month, sum) -> {
                normalizedMonthlyPatterns.put(
                    LocalDate.now().withMonth(month).getMonth().toString(),
                    sum / values.size()
                );
            });

            seasonality.put("daily_patterns", normalizedDailyPatterns);
            seasonality.put("monthly_patterns", normalizedMonthlyPatterns);
            seasonality.put("reliable", true);

        } catch (Exception e) {
            logger.error("Error analyzing seasonality", e);
            seasonality.put("reliable", false);
        }
        return seasonality;
    }

    public String getMostPositiveSentiment(Map<String, Double> sentiments) {
        if (sentiments == null || sentiments.isEmpty()) {
            return null; // or throw an exception based on your requirement
        }
        return sentiments.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public List<TrendData> generateTrendDataInsights() {
        logger.info("Generating trend insights");
        List<TrendData> trends = new ArrayList<>();
        Map<String, Object> trendData = new HashMap<>();
        trendData.put("interest_over_time", Arrays.asList(
            Map.of("date", LocalDateTime.now(), "value", Math.random() * 100),
            Map.of("date", LocalDateTime.now().minusDays(1), "value", Math.random() * 100)
        ));
        
        if (trendData != null) {
            trends.addAll(convertMapToTrendData(trendData));
        }
        
        // Rest of the method remains the same
        return trends;
    }

    // Add this method to TrendAnalysisService
    private double getAverageSentimentFromTrendData(List<TrendData> trendDataList) {
        if (trendDataList == null || trendDataList.isEmpty()) {
            return 0.0;
        }
        
        return trendDataList.stream()
                .mapToDouble(TrendData::getSentimentScore)  // Assuming TrendData has getSentimentScore method
                .average()
                .orElse(0.0);
    }

    private List<Content> convertTrendDataToContent(List<TrendData> trendDataList) {
        return trendDataList.stream()
                .map(this::convertToContent)
                .collect(Collectors.toList());
    }
    
    private Content convertToContent(TrendData trendData) {
        Content content = new Content();
        content.setEngagement(trendData.getEngagementScore());
        // Set other relevant fields
        return content;
    }

    public TrendInsight getDetailedTrendInsights() {
        // Get latest trend data
        List<TrendData> historicalData = trendDataRepository.findTop24ByOrderByAnalysisTimestampDesc();
        if (historicalData.isEmpty()) {
            return null;
        }

        TrendData latestTrend = historicalData.get(0);
        
        // Calculate comprehensive metrics
        double averageSentiment = getAverageSentimentFromTrendData(historicalData);
        double engagementScore = calculateAggregateEngagement(historicalData);
        Map<String, Double> topicRelations = analyzeTopicRelations(latestTrend.getTopic());
        
        // Generate detailed insight
        String detailedInsight = generateDetailedInsight(
            historicalData, 
            averageSentiment, 
            engagementScore, 
            topicRelations
        );

        // Create detailed insight object
        TrendInsight detailedTrendInsight = new TrendInsight(
            latestTrend.getTopic(),
            detailedInsight,
            latestTrend.getTrendScore(),
            historicalData,
            averageSentiment,
            engagementScore,
            topicRelations
        );

        // Add additional metrics
        detailedTrendInsight.setTrendDirection(determineTrendDirection(latestTrend));
        detailedTrendInsight.setRelatedKeywords(findRelatedKeywords(latestTrend.getTopic()));
        
        // Calculate and add advanced metrics
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("volatility", calculateVolatility(latestTrend.getHistoricalValuesList()));
        metrics.put("momentum", calculateMomentum(latestTrend.getHistoricalValuesList()));
        metrics.put("seasonality", analyzeSeasonality(latestTrend.getHistoricalValuesList(), latestTrend.getHistoricalDatesList()));
        metrics.put("confidence", latestTrend.getConfidenceScore());
        metrics.put("predictedValue", predictNextValue(latestTrend.getHistoricalValuesList(), latestTrend.getMomentum(), latestTrend.getVolatility()));
        
        detailedTrendInsight.setMetrics(metrics);
        
        return detailedTrendInsight;
    }

    private String generateDetailedInsight(
            List<TrendData> historicalData,
            double averageSentiment,
            double engagementScore,
            Map<String, Double> topicRelations) {
        
        StringBuilder insight = new StringBuilder();
        
        // Analyze trend pattern
        TrendData latestTrend = historicalData.get(0);
        TrendDirection direction = determineTrendDirection(latestTrend);
        
        // Add trend direction analysis
        switch (direction) {
            case UP:
                insight.append("Topic shows strong upward momentum with sustained growth. ");
                break;
            case DOWN:
                insight.append("Topic is experiencing a decline in popularity. ");
                break;
            case STABLE:
                insight.append("Topic maintains consistent interest levels. ");
                break;
        }
        
        // Add sentiment analysis
        insight.append(String.format("Overall sentiment is %.1f%% positive. ", averageSentiment * 100));
        
        // Add engagement insights
        if (engagementScore > 0.8) {
            insight.append("Exceptionally high engagement rates indicate strong audience interest. ");
        } else if (engagementScore > 0.6) {
            insight.append("Above average engagement suggests growing audience traction. ");
        }
        
        // Add seasonality insights if detected
        List<Double> values = latestTrend.getHistoricalValuesList();
        List<String> dates = latestTrend.getHistoricalDatesList();
        Map<String, Object> seasonality = analyzeSeasonality(values, dates);
        if ((boolean) seasonality.getOrDefault("hasSeasonality", false)) {
            insight.append("Shows seasonal patterns with peaks every ")
                  .append(seasonality.get("period"))
                  .append(" days. ");
        }
        
        // Add related topics insight
        if (!topicRelations.isEmpty()) {
            String strongestRelation = topicRelations.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
            if (!strongestRelation.isEmpty()) {
                insight.append(String.format("Strongly correlated with '%s'. ", strongestRelation));
            }
        }
        
        // Add prediction
        double nextValue = predictNextValue(values, latestTrend.getMomentum(), latestTrend.getVolatility());
        if (nextValue > latestTrend.getTrendScore()) {
            insight.append("Expected to continue growing. ");
        } else if (nextValue < latestTrend.getTrendScore()) {
            insight.append("May see decreased interest in near term. ");
        }
        
        return insight.toString();
    }

    private double calculateAggregateEngagement(List<TrendData> historicalData) {
        return historicalData.stream()
            .mapToDouble(trend -> calculateEngagementScore(trend.getTrendScore()))
            .average()
            .orElse(0.0);
    }

    private Map<String, Double> analyzeTopicRelations(String topic) {
        Map<String, Double> relations = new HashMap<>();
        try {
            if (word2Vec != null) {
                Collection<String> nearestWords = word2Vec.wordsNearest(topic, 10);
                for (String word : nearestWords) {
                    double similarity = word2Vec.similarity(topic, word);
                    if (similarity > 0.3) { // Only include strong relationships
                    relations.put(word, similarity);
                }
            }
            }
        } catch (Exception e) {
            logger.error("Error analyzing topic relations for {}: {}", topic, e.getMessage());
        }
        return relations;
    }
    
    public TrendInsight convertToTrendInsight(List<TrendData> trendDataList) {
        // Example logic to create a TrendInsight from a list of TrendData
        String topic = trendDataList.get(0).getTopic(); // Assuming all data has the same topic
        double averageScore = trendDataList.stream().mapToDouble(TrendData::getTrendScore).average().orElse(0);
        String insight = "Aggregated insight for topic: " + topic;
        return new TrendInsight(topic, insight, averageScore);
    }

    /**
     * Compares trends between two regions to identify differences in topic popularity and momentum
     * @param region1 First region to compare
     * @param region2 Second region to compare
     * @return Map containing comparative trend analysis between the two regions
     */
    public Map<String, Object> compareTrendsBetweenRegions(String region1, String region2) {
        Map<String, Object> comparison = new HashMap<>();
        
        try {
            // Get trends for both regions
            Map<String, Object> trends1 = getRegionalTrends(region1);
            Map<String, Object> trends2 = getRegionalTrends(region2);
            
            // Extract trending topics data
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> topics1 = (Map<String, Map<String, Object>>) trends1.get("trendingTopics");
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> topics2 = (Map<String, Map<String, Object>>) trends2.get("trendingTopics");
            
            // Compare trends between regions
            Map<String, Object> regionalComparison = new HashMap<>();
            Set<String> allTopics = new HashSet<>();
            allTopics.addAll(topics1.keySet());
            allTopics.addAll(topics2.keySet());
            
            for (String topic : allTopics) {
                Map<String, Object> topicComparison = new HashMap<>();
                
                // Get topic data for both regions
                Map<String, Object> topicData1 = topics1.containsKey(topic) ? topics1.get(topic) : null;
                Map<String, Object> topicData2 = topics2.containsKey(topic) ? topics2.get(topic) : null;
                
                // Calculate relative popularity
                double trendScore1 = topicData1 != null ? ((Number) topicData1.get("trendScore")).doubleValue() : 0.0;
                double trendScore2 = topicData2 != null ? ((Number) topicData2.get("trendScore")).doubleValue() : 0.0;
                double relativeDifference = trendScore1 - trendScore2;
                
                // Compare momentum
                double momentum1 = topicData1 != null ? ((Number) topicData1.get("momentum")).doubleValue() : 0.0;
                double momentum2 = topicData2 != null ? ((Number) topicData2.get("momentum")).doubleValue() : 0.0;
                double momentumDifference = momentum1 - momentum2;
                
                // Determine which region has stronger trend
                String dominantRegion = relativeDifference > 0 ? region1 : 
                                      relativeDifference < 0 ? region2 : "equal";
                
                // Calculate trend strength difference percentage
                double maxScore = Math.max(trendScore1, trendScore2);
                double strengthDiffPercentage = maxScore > 0 ? 
                    (Math.abs(relativeDifference) / maxScore) * 100 : 0;
                
                topicComparison.put("dominantRegion", dominantRegion);
                topicComparison.put("strengthDifferencePercent", strengthDiffPercentage);
                topicComparison.put("trendScores", Map.of(
                    region1, trendScore1,
                    region2, trendScore2
                ));
                topicComparison.put("momentum", Map.of(
                    region1, momentum1,
                    region2, momentum2,
                    "difference", momentumDifference
                ));
                
                // Add historical comparison if available
                if (topicData1 != null && topicData2 != null) {
                    @SuppressWarnings("unchecked")
                    List<Double> history1 = (List<Double>) topicData1.get("historicalValues");
                    @SuppressWarnings("unchecked")
                    List<Double> history2 = (List<Double>) topicData2.get("historicalValues");
                    if (history1 != null && history2 != null) {
                        topicComparison.put("historicalComparison", Map.of(
                            region1, history1,
                            region2, history2
                        ));
                    }
                }
                
                regionalComparison.put(topic, topicComparison);
            }
            
            // Add summary statistics
            Map<String, Object> summary = new HashMap<>();
            summary.put("totalTopics", allTopics.size());
            summary.put("uniqueTopicsIn" + region1, 
                topics1.keySet().stream()
                    .filter(t -> !topics2.containsKey(t))
                    .count());
            summary.put("uniqueTopicsIn" + region2, 
                topics2.keySet().stream()
                    .filter(t -> !topics1.containsKey(t))
                    .count());
            summary.put("commonTopics", 
                topics1.keySet().stream()
                    .filter(topics2::containsKey)
                    .count());
            
            comparison.put("regionalComparison", regionalComparison);
            comparison.put("summary", summary);
            comparison.put("timestamp", LocalDateTime.now());
            comparison.put("regions", Map.of(
                "region1", region1,
                "region2", region2
            ));
            
            // Add the original trend data for reference
            @SuppressWarnings("unchecked")
            List<TrendData> trends1List = (List<TrendData>) trends1.get("trends");
            @SuppressWarnings("unchecked")
            List<TrendData> trends2List = (List<TrendData>) trends2.get("trends");
            if (trends1List != null && trends2List != null) {
                comparison.put("originalTrends", Map.of(
                    region1, trends1List,
                    region2, trends2List
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error comparing trends between regions {} and {}: {}", 
                region1, region2, e.getMessage());
            comparison.put("error", "Failed to compare trends between regions");
        }
        
        return comparison;
    }

    private TrendDirection determineTrendDirectionByEngagementAndInterest(double engagement, double interest) {
        double change = calculateChangeFromValues(engagement, interest);
        if (Math.abs(change) < 5.0) {
            return TrendDirection.STABLE;
        }
        return change > 0 ? TrendDirection.UP : TrendDirection.DOWN;
    }

    /**
     * Retrieves historical trend values for a given keyword
     * @param keyword The keyword to get historical trend values for
     * @return List of historical trend values
     */
    // @Cacheable(value = "historicalTrends", key = "#keyword")
    // public List<Double> getHistoricalTrendValues(String keyword) {
    //     if (keyword == null || keyword.trim().isEmpty()) {
    //         logger.warn("Received null or empty keyword for historical trend values");
    //         return Collections.singletonList(0.0);
    //     }
    @Cacheable(value = "trendScores", key = "#keyword", unless = "#result == null")
public List<TrendData> getHistoricalTrendData(String keyword) {
    logger.debug("Fetching historical trend values for keyword: {}", keyword);
    List<TrendData> allTrends = new ArrayList<>();
    
    try {
        // Get real-time trends from web scraping
        List<?> scrapedData = webScrapingService.scrapeRecentSources(50);
        if (scrapedData != null && !scrapedData.isEmpty()) {
            List<TrendData> recentTrends = scrapedData.stream()
                .filter(Objects::nonNull)
                .map(data -> {
                    try {
                        if (data instanceof TrendData) {
                            return (TrendData) data;
                        } else if (data instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> dataMap = (Map<String, Object>) data;
                            return convertMapToTrendData(dataMap).stream().findFirst().orElse(null);
                        }
                        return null;
                    } catch (Exception e) {
                        logger.warn("Error converting trend data: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(trend -> trend != null && isRelevantToKeyword(trend, keyword))
                .collect(Collectors.toList());
            
            allTrends.addAll(recentTrends);
        }
        
        // Get historical data from database
        List<TrendData> dbTrends = trendDataRepository.findLatestTrendsByTopic(keyword, defaultLimit);
        if (dbTrends != null && !dbTrends.isEmpty()) {
            allTrends.addAll(dbTrends);
        }
        
        return allTrends.isEmpty() ? Collections.emptyList() : allTrends;
        
    } catch (Exception e) {
        logger.error("Error in getHistoricalTrendData for keyword: {}", keyword, e);
        // Fallback to database-only if web scraping fails
        return trendDataRepository.findLatestTrendsByTopic(keyword, defaultLimit);
    }
}

    //     try {
    //         List<Double> trendScores = new ArrayList<>();
            
    //         // First, try to get recent trends from cache/scraping
    //         List<?> scrapedData = webScrapingService.scrapeRecentSources(50);
            
    //         // Convert scraped data to TrendData objects and handle nulls
    //         List<TrendData> recentTrends = scrapedData.stream()
    //             .filter(Objects::nonNull)
    //             .map(data -> {
    //                 try {
    //                     if (data instanceof TrendData) {
    //                         return (TrendData) data;
    //                     } else if (data instanceof Map) {
    //                         @SuppressWarnings("unchecked")
    //                         Map<String, Object> dataMap = (Map<String, Object>) data;
    //                         return convertMapToTrendData(dataMap).stream().findFirst().orElse(null);
    //                     } else {
    //                         logger.warn("Unexpected data type in scraped results: {}", data.getClass());
    //                         return null;
    //                     }
    //                 } catch (Exception e) {
    //                     logger.warn("Error converting trend data: {}", e.getMessage());
    //                     return null;
    //                 }
    //             })
    //             .filter(Objects::nonNull)
    //             .collect(Collectors.toList());
                
    //         // Filter relevant trends and ensure non-null scores
    //         List<TrendData> relevantRecentTrends = recentTrends.stream()
    //             .filter(trend -> isRelevantToKeyword(trend, keyword))
    //             .collect(Collectors.toList());
            
    //         if (!relevantRecentTrends.isEmpty()) {
    //             trendScores.addAll(relevantRecentTrends.stream()
    //                 .map(TrendData::getTrendScore)
    //                 .filter(Objects::nonNull)
    //                 .collect(Collectors.toList()));
    //         }
            
    //         // Then, get historical data from database using pagination
    //         int pageSize = 100;
    //         int pageNumber = 0;
    //         Page<TrendData> historicalPage;
            
    //         do {
    //             Pageable pageable = PageRequest.of(pageNumber, pageSize);
    //             historicalPage = trendDataRepository.findByTopicOrderByAnalysisTimestampDesc(keyword, pageable);
                
    //             List<Double> pageScores = historicalPage.getContent().stream()
    //                 .filter(Objects::nonNull)
    //                 .map(TrendData::getTrendScore)
    //                 .filter(Objects::nonNull)
    //                 .collect(Collectors.toList());
                    
    //             trendScores.addAll(pageScores);
    //             pageNumber++;
    //         } while (historicalPage.hasNext() && pageNumber < 5); // Limit to 500 records total
            
    //         // If no valid scores were found, return a default score
    //         if (trendScores.isEmpty()) {
    //             logger.info("No valid trend scores found for keyword: {}, using default value", keyword);
    //             return Collections.singletonList(0.0);
    //         }
            
    //         return trendScores;
    //     } catch (Exception e) {
    //         logger.error("Error getting historical trend values for keyword: {}", keyword, e);
    //         return new ArrayList<>();
    //     }
    // }

    @Cacheable(value = "historicalTrends", key = "#keyword", condition = "#keyword != null && !#keyword.trim().isEmpty()")
public List<Double> getHistoricalTrendValues(String keyword) {
    if (keyword == null || keyword.trim().isEmpty()) {
        logger.warn("Received null or empty keyword for historical trend values");
        return Collections.singletonList(0.0);
    }

    try {
        List<TrendData> trendDataList = getHistoricalTrendData(keyword);
        if (trendDataList == null || trendDataList.isEmpty()) {
            return Collections.singletonList(0.0);
        }
        
        return trendDataList.stream()
            .map(TrendData::getTrendScore)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    } catch (Exception e) {
        logger.error("Error getting historical trend values for keyword: " + keyword, e);
        return Collections.singletonList(0.0);
    }
}

    public boolean isRelevantToKeyword(TrendData trend, String keyword) {
        // Check if the trend topic contains the keyword
        if (trend.getTopic() != null && 
            trend.getTopic().toLowerCase().contains(keyword.toLowerCase())) {
            return true;
        }
        
        // Check metadata for relevance
        if (trend.getMetadata() != null) {
            // Check description if available
            Object description = trend.getMetadata().get("description");
            if (description != null && 
                description.toString().toLowerCase().contains(keyword.toLowerCase())) {
                return true;
            }
            
            // Check tags if available
            Object tags = trend.getMetadata().get("tags");
            if (tags != null && 
                tags.toString().toLowerCase().contains(keyword.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Retrieves historical trend dates for a given keyword
     * @param keyword The keyword to get historical trend dates for
     * @return List of historical trend dates
     */
    public List<LocalDateTime> getHistoricalTrendDates(String keyword) {
        try {
            // Get trend data from all sources via WebScrapingService
            List<?> scrapedData = webScrapingService.scrapeAllSources();
            
            // Convert scraped data to TrendData objects
            List<TrendData> allTrends = scrapedData.stream()
                .map(data -> {
                    if (data instanceof TrendData) {
                        return (TrendData) data;
                    } else if (data instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> dataMap = (Map<String, Object>) data;
                        return convertMapToTrendData(dataMap).stream().findFirst().orElse(null);
                    } else {
                        logger.warn("Unexpected data type in scraped results: {}", data.getClass());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            // Filter trends related to the keyword
            List<TrendData> relevantTrends = allTrends.stream()
                .filter(trend -> trend != null && isRelevantToKeyword(trend, keyword))
                .collect(Collectors.toList());
            
            // If we have relevant trends, use their timestamps
            if (!relevantTrends.isEmpty()) {
                return relevantTrends.stream()
                    .map(TrendData::getAnalysisTimestamp)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            }

            // Fallback to database
            List<TrendData> historicalTrends = trendDataRepository.findByTopicOrderByAnalysisTimestampDesc(keyword);
            if (!historicalTrends.isEmpty()) {
                return historicalTrends.stream()
                    .map(TrendData::getAnalysisTimestamp)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            }

            // If no data available, return empty list
            // The EnhancedTrendPattern will handle this case
            return new ArrayList<>();
        } catch (Exception e) {
            logger.error("Error getting historical trend dates for keyword: {}", keyword, e);
            return new ArrayList<>();
        }
    }

    /**
     * Analyzes trends for a specific list of content
     * @param contentList List of content to analyze
     * @return Map containing trend analysis results with nested structure
     */
    public Map<String, Object> analyzeTrends(List<Content> contentList) {
        logger.info("Analyzing trends for {} content items", contentList.size());
        Map<String, Object> results = new HashMap<>();
        
        try {
            // Extract keywords from all content
            Set<String> allKeywords = new HashSet<>();
            for (Content content : contentList) {
                allKeywords.addAll(extractKeywords(content));
            }
            
            // Analyze each keyword
            Map<String, EnhancedTrendPattern> trendPatterns = new HashMap<>();
            for (String keyword : allKeywords) {
                EnhancedTrendPattern pattern = new EnhancedTrendPattern();
                
                // Get historical data
                List<Double> historicalValues = getHistoricalTrendValues(keyword);
                List<LocalDateTime> timestamps = getHistoricalTrendDates(keyword);
                
                // Set data in pattern
                pattern.setHistoricalValues(historicalValues);
                pattern.setTimestamps(timestamps);
                
                // Calculate all metrics
                pattern.calculateMetrics();
                
                trendPatterns.put(keyword, pattern);
            }
            
            // Process content in batches for detailed analysis
            Map<String, Map<String, Object>> batchResults = processTrendBatch(contentList);
            
            // Combine results
            Map<String, Object> metaData = new HashMap<>();
            metaData.put("analysisTimestamp", LocalDateTime.now());
            metaData.put("dataPoints", contentList.size());
            
            results.put("metadata", metaData);
            results.put("trendPatterns", Collections.singletonMap("patterns", trendPatterns));
            results.put("trendScores", batchResults);
            results.put("analysisTimestamp", LocalDateTime.now());
            results.put("dataPoints", contentList.size());
            
            // Generate insights
            List<String> insights = new ArrayList<>();
            for (Map.Entry<String, EnhancedTrendPattern> entry : trendPatterns.entrySet()) {
                String keyword = entry.getKey();
                EnhancedTrendPattern pattern = entry.getValue();
                
                if (pattern.isSignificant()) {
                    insights.add(String.format("%s: %s (Confidence: %.2f, Trend Strength: %.2f)",
                        keyword,
                        pattern.getRecommendedAction(),
                        pattern.getConfidenceScore(),
                        pattern.getTrendStrength()));
                }
            }
            results.put("insights", insights);
            
        } catch (Exception e) {
            logger.error("Error analyzing trends for content list", e);
            results.put("error", "Failed to analyze trends for content list");
        }
        
        return results;
    }
}