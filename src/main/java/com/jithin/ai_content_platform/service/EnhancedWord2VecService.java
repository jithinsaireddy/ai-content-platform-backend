package com.jithin.ai_content_platform.service;

import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.text.sentenceiterator.BasicLineIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EnhancedWord2VecService {

    @Value("${word2vec.vector.size}")
    private int vectorSize;

    @Value("${word2vec.window.size}")
    private int windowSize;

    @Value("${word2vec.min.word.frequency}")
    private int minWordFrequency;

    @Value("${word2vec.model.path}")
    private String modelPath;

    private Word2Vec vec;
    private final Map<String, Double> wordFrequencies = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> wordTimestamps = new ConcurrentHashMap<>();
    private LocalDateTime lastTrainingTime;
    private static final long TRAINING_INTERVAL = 24 * 60 * 60 * 1000; // 24 hours

    @PostConstruct
    public void init() {
        try {
            // Create model directory if it doesn't exist
            File modelDir = new File(modelPath).getParentFile();
            if (!modelDir.exists()) {
                modelDir.mkdirs();
                log.info("Created model directory at: {}", modelDir.getAbsolutePath());
            }

            // Initialize or load model
            initializeModel();


            // If no model exists, create a basic one with default data
            if (vec == null) {
                log.info("Creating initial Word2Vec model with default healthcare data");
                List<String> defaultData = Arrays.asList(
                        "king queen prince princess royal monarchy",
                        "man woman boy girl child adult",
                        "apple banana mango fruit orange grape",
                        "dog cat pet animal puppy kitten",
                        "car bus train vehicle transport road",
                        "science physics chemistry biology research experiment",
                        "artificial intelligence machine learning deep neural",
                        "computer software hardware programming coding algorithm",
                        "bank money finance investment stocks economy",
                        "city town village urban rural population",
                        "music song melody instrument piano guitar",
                        "war battle soldier weapon army military",
                        "love affection romance relationship heart emotion",
                        "education school college university student teacher",
                        "doctor hospital medicine treatment health nurse",
                        "food cooking recipe kitchen meal chef",
                        "game player sport team competition win",
                        "earth planet space universe star galaxy",
                        "technology innovation internet digital data cloud",
                        "history past event civilization ancient heritage"
                );
                addTrainingData(defaultData);
                trainModel(exportWordFrequenciesToFile());
            }
        } catch (Exception e) {
            log.error("Error initializing Word2Vec service", e);
        }
    }

    public void initializeModel() {
        try {
            File modelFile = new File(modelPath);
            if (modelFile.exists()) {
                vec = WordVectorSerializer.readWord2VecModel(modelFile);
                log.info("Loaded existing Word2Vec model from {}", modelPath);
            } else {
                log.info("No existing model found. Will create new model when training data is available.");
            }
        } catch (Exception e) {
            log.error("Error initializing Word2Vec model", e);
        }
    }

    public void addTrainingData(List<String> contents) {
        try {
            // Create temporary file for training data
            Path tempFile = Files.createTempFile("word2vec_training", ".txt");
            try (FileWriter writer = new FileWriter(tempFile.toFile())) {
                for (String content : contents) {
                    writer.write(content + "\n");
                    updateWordStatistics(content);
                }
            }

            // Check if we should retrain the model
            if (shouldRetrain()) {
                trainModel(tempFile);
            }

            // Cleanup
            Files.delete(tempFile);
        } catch (Exception e) {
            log.error("Error adding training data", e);
        }
    }

    private void updateWordStatistics(String content) {
        TokenizerFactory tokenizerFactory = new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(new CommonPreprocessor());

        tokenizerFactory.create(content).getTokens().forEach(word -> {
            wordFrequencies.merge(word.toLowerCase(), 1.0, Double::sum);
            wordTimestamps.put(word.toLowerCase(), LocalDateTime.now());
        });
    }

    private boolean shouldRetrain() {
        if (lastTrainingTime == null) return true;

        LocalDateTime now = LocalDateTime.now();
        long hoursSinceLastTraining = java.time.Duration.between(lastTrainingTime, now).toHours();

        // Check if enough time has passed
        if (hoursSinceLastTraining < 24) return false;

        // Check if we have enough new words
        long newWordsCount = wordTimestamps.entrySet().stream()
            .filter(e -> e.getValue().isAfter(lastTrainingTime))
            .count();

        return newWordsCount >= 1000; // Arbitrary threshold
    }

    @Scheduled(fixedRate = TRAINING_INTERVAL)
    public void scheduledRetraining() {
        try {
            if (wordFrequencies.isEmpty()) {
                log.info("No training data available. Skipping scheduled retraining.");
                return;
            }

            if (shouldRetrain()) {
                log.info("Starting scheduled retraining with {} unique words", wordFrequencies.size());
                // Export current word frequencies to a file
                Path trainingFile = exportWordFrequenciesToFile();
                trainModel(trainingFile);
                Files.deleteIfExists(trainingFile);
            }
        } catch (Exception e) {
            log.error("Error in scheduled retraining", e);
        }
    }

    private Path exportWordFrequenciesToFile() throws Exception {
        Path tempFile = Files.createTempFile("word2vec_training", ".txt");
        try (FileWriter writer = new FileWriter(tempFile.toFile())) {
            // Sort words by frequency
            List<Map.Entry<String, Double>> sortedWords = wordFrequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());

            // Write words to file, repeating based on frequency
            for (Map.Entry<String, Double> entry : sortedWords) {
                int repetitions = Math.min(100, (int) Math.ceil(entry.getValue()));
                for (int i = 0; i < repetitions; i++) {
                    writer.write(entry.getKey() + " ");
                }
                writer.write("\n");
            }
        }
        return tempFile;
    }

    private void trainModel(Path trainingFile) {
        try {
            if (!Files.exists(trainingFile) || Files.size(trainingFile) == 0) {
                log.warn("Training file is empty or does not exist. Skipping training.");
                return;
            }

            // Ensure the model directory exists
            File modelDir = new File(modelPath).getParentFile();
            if (!modelDir.exists()) {
                modelDir.mkdirs();
            }

            log.info("Starting Word2Vec model training");

            // Check if we have enough data
            long lineCount = Files.lines(trainingFile).count();
            if (lineCount < 10) { // Minimum threshold for training
                log.warn("Insufficient training data ({}). Minimum 10 lines required.", lineCount);
                return;
            }

            SentenceIterator iterator = new BasicLineIterator(trainingFile.toFile());
            TokenizerFactory tokenizerFactory = new DefaultTokenizerFactory();
            tokenizerFactory.setTokenPreProcessor(new CommonPreprocessor());

            // Configure Word2Vec with safe defaults
            vec = new Word2Vec.Builder()
                .minWordFrequency(Math.max(1, minWordFrequency)) // Ensure minimum word frequency is at least 1
                .iterations(5)
                .layerSize(vectorSize)
                .seed(42)
                .windowSize(Math.min(windowSize, (int)lineCount)) // Ensure window size doesn't exceed data size
                .iterate(iterator)
                .tokenizerFactory(tokenizerFactory)
                .build();

            vec.fit();

            // Verify the model has words before saving
            if (vec.getVocab() != null && vec.getVocab().numWords() > 0) {
                // Save the model
                WordVectorSerializer.writeWord2VecModel(vec, new File(modelPath));
                lastTrainingTime = LocalDateTime.now();
                log.info("Word2Vec model training completed with {} words and saved to {}", 
                    vec.getVocab().numWords(), modelPath);
            } else {
                log.warn("Training completed but no words in vocabulary. Model not saved.");
            }
        } catch (Exception e) {
            log.error("Error training Word2Vec model", e);
            // Keep the previous model if training fails
        }
    }

    public double[] getWordVector(String word) {
        if (vec == null || !vec.hasWord(word)) {
            return new double[vectorSize]; // Return zero vector if word not found
        }
        return vec.getWordVector(word);
    }

    public List<String> findSimilarWords(String word, int n) {
        if (vec == null) {
            log.warn("Word2Vec model not initialized when searching for: {}", word);
            return Collections.emptyList();
        }

        // Preprocess the input word
        word = word.toLowerCase().trim();
        
        // Handle multi-word phrases
        if (word.contains(" ")) {
            String[] words = word.split(" ");
            Set<String> allSimilar = new HashSet<>();
            
            // Find similar words for each word in the phrase
            for (String w : words) {
                if (vec.hasWord(w)) {
                    allSimilar.addAll(vec.wordsNearest(w, n));
                }
            }
            
            return new ArrayList<>(allSimilar);
        }
        
        // Single word case
        if (!vec.hasWord(word)) {
            log.debug("Word not found in vocabulary: {}", word);
            return Collections.emptyList();
        }
        
        return new ArrayList<>(vec.wordsNearest(word, n));
    }

    public double calculateCosineSimilarity(String word1, String word2) {
        if (vec == null || !vec.hasWord(word1) || !vec.hasWord(word2)) {
            return 0.0;
        }
        return vec.similarity(word1, word2);
    }

    public Map<String, Double> getWordFrequencies() {
        return new HashMap<>(wordFrequencies);
    }

    public LocalDateTime getLastTrainingTime() {
        return lastTrainingTime;
    }

    public void clearOldStatistics() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        wordTimestamps.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        wordFrequencies.keySet().retainAll(wordTimestamps.keySet());
    }

    
}
