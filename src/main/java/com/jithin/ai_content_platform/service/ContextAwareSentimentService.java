package com.jithin.ai_content_platform.service;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ContextAwareSentimentService {

    private final StanfordCoreNLP pipeline;
    
    @Autowired
    public ContextAwareSentimentService(StanfordCoreNLP pipeline) {
        this.pipeline = pipeline;
    }
    
    public Map<String, Object> analyzeContextAwareSentiment(String text) {
        Map<String, Object> analysis = new HashMap<>();
        
        try {
            Annotation document = new Annotation(text);
            pipeline.annotate(document);
            
            // Extract entities and their sentiments
            Map<String, List<Double>> entitySentiments = extractEntitySentiments(document);
            
            // Extract topic-based sentiments
            Map<String, Double> topicSentiments = extractTopicSentiments(document);
            
            // Calculate sentence importance
            List<Map<String, Object>> sentenceAnalysis = analyzeSentenceImportance(document);
            
            // Calculate overall sentiment with context weights
            double overallSentiment = calculateWeightedSentiment(sentenceAnalysis);
            
            // Prepare the analysis result
            analysis.put("overall_sentiment", overallSentiment);
            analysis.put("entity_sentiments", entitySentiments);
            analysis.put("topic_sentiments", topicSentiments);
            analysis.put("sentence_analysis", sentenceAnalysis);
            analysis.put("confidence_score", calculateConfidenceScore(sentenceAnalysis));
            
        } catch (Exception e) {
            log.error("Error in context-aware sentiment analysis", e);
            analysis.put("error", e.getMessage());
        }
        
        return analysis;
    }
    
    private Map<String, List<Double>> extractEntitySentiments(Annotation document) {
        Map<String, List<Double>> entitySentiments = new HashMap<>();
        
        // Get coreference information
        Map<Integer, CorefChain> corefChains = document.get(CorefCoreAnnotations.CorefChainAnnotation.class);
        
        // Process each sentence
        for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
            double sentenceSentiment = RNNCoreAnnotations.getPredictedClass(
                sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class));
            
            // Extract named entities and their sentiment
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                String ne = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
                if (!"O".equals(ne)) {
                    String entity = token.originalText();
                    entitySentiments.computeIfAbsent(entity, k -> new ArrayList<>())
                        .add(sentenceSentiment);
                }
            }
        }
        
        return entitySentiments;
    }
    
    private Map<String, Double> extractTopicSentiments(Annotation document) {
        Map<String, Double> topicSentiments = new HashMap<>();
        
        // Extract topics using NER and dependency parsing
        for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
            double sentenceSentiment = RNNCoreAnnotations.getPredictedClass(
                sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class));
            
            Set<String> topics = extractTopicsFromSentence(sentence);
            topics.forEach(topic -> 
                topicSentiments.merge(topic, sentenceSentiment, (old, newVal) -> (old + newVal) / 2));
        }
        
        return topicSentiments;
    }
    
    private Set<String> extractTopicsFromSentence(CoreMap sentence) {
        Set<String> topics = new HashSet<>();
        
        // Extract noun phrases as potential topics
        for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
            String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
            if (pos.startsWith("NN")) {
                topics.add(token.originalText().toLowerCase());
            }
        }
        
        return topics;
    }
    
    private List<Map<String, Object>> analyzeSentenceImportance(Annotation document) {
        List<Map<String, Object>> sentenceAnalysis = new ArrayList<>();
        
        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        int totalSentences = sentences.size();
        
        for (int i = 0; i < sentences.size(); i++) {
            CoreMap sentence = sentences.get(i);
            Map<String, Object> analysis = new HashMap<>();
            
            // Get basic sentiment
            double sentiment = RNNCoreAnnotations.getPredictedClass(
                sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class));
            
            // Calculate position weight (sentences at start and end often more important)
            double positionWeight = calculatePositionWeight(i, totalSentences);
            
            // Calculate content weight based on presence of important elements
            double contentWeight = calculateContentWeight(sentence);
            
            // Calculate final importance score
            double importance = (positionWeight + contentWeight) / 2.0;
            
            analysis.put("text", sentence.toString());
            analysis.put("sentiment", sentiment);
            analysis.put("importance", importance);
            analysis.put("position_weight", positionWeight);
            analysis.put("content_weight", contentWeight);
            
            sentenceAnalysis.add(analysis);
        }
        
        return sentenceAnalysis;
    }
    
    private double calculatePositionWeight(int position, int total) {
        if (position == 0 || position == total - 1) {
            return 1.0; // First and last sentences
        }
        return 0.5 + (Math.sin(Math.PI * position / total) * 0.5);
    }
    
    private double calculateContentWeight(CoreMap sentence) {
        double weight = 0.5; // Base weight
        
        // Check for named entities
        boolean hasNamedEntity = sentence.get(CoreAnnotations.TokensAnnotation.class).stream()
            .anyMatch(token -> !"O".equals(token.get(CoreAnnotations.NamedEntityTagAnnotation.class)));
        if (hasNamedEntity) weight += 0.2;
        
        // Check for quotations
        if (sentence.toString().contains("\"")) weight += 0.15;
        
        // Check for numerical data
        boolean hasNumbers = sentence.get(CoreAnnotations.TokensAnnotation.class).stream()
            .anyMatch(token -> token.get(CoreAnnotations.PartOfSpeechAnnotation.class).equals("CD"));
        if (hasNumbers) weight += 0.15;
        
        return Math.min(weight, 1.0);
    }
    
    private double calculateWeightedSentiment(List<Map<String, Object>> sentenceAnalysis) {
        double weightedSum = 0;
        double weightSum = 0;
        
        for (Map<String, Object> analysis : sentenceAnalysis) {
            double sentiment = (double) analysis.get("sentiment");
            double importance = (double) analysis.get("importance");
            
            weightedSum += sentiment * importance;
            weightSum += importance;
        }
        
        return weightSum > 0 ? weightedSum / weightSum : 0;
    }
    
    private double calculateConfidenceScore(List<Map<String, Object>> sentenceAnalysis) {
        // Calculate standard deviation of sentiments
        List<Double> sentiments = sentenceAnalysis.stream()
            .map(a -> (Double) a.get("sentiment"))
            .collect(Collectors.toList());
        
        double mean = sentiments.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = sentiments.stream()
            .mapToDouble(s -> Math.pow(s - mean, 2))
            .average()
            .orElse(0.0);
        double stdDev = Math.sqrt(variance);
        
        // Higher consistency (lower stdDev) means higher confidence
        double consistencyScore = Math.max(0, 1 - (stdDev / 2));
        
        // Consider the amount of data
        double dataScore = Math.min(1.0, sentenceAnalysis.size() / 10.0);
        
        // Combine scores
        return (consistencyScore * 0.7) + (dataScore * 0.3);
    }
}
