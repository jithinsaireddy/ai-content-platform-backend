package com.jithin.ai_content_platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jithin.ai_content_platform.model.Content;
import com.jithin.ai_content_platform.model.User;
import com.jithin.ai_content_platform.payload.ContentRequest;
import com.jithin.ai_content_platform.payload.FeedbackRequest;
import com.jithin.ai_content_platform.repository.ContentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.sentiment.*;
import edu.stanford.nlp.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class ContentService {

    @Autowired
    private ContentRepository contentRepository;

    @Value("${openai.api.key}")
    private String openaiApiKey;

    private static final Logger logger = LoggerFactory.getLogger(ContentService.class);

//    public String generatePersonalizedContent(String prompt, User user) {
//        // Fetch user's past content, preferences, etc.
//        String userStyle = getUserWritingStyle(user);
//
//        String personalizedPrompt = userStyle + "\n\n" + prompt;
//
//        // Call OpenAI API as before using personalizedPrompt
//        // ...
//    }
//
//    private String getUserWritingStyle(User user) {
//        // Analyze user's past content to determine style
//        // For simplicity, return a placeholder string
//        return "User's writing style and preferences";
//    }
//

    private boolean isContentPlagiarized(String content) {
        // Implement API call to plagiarism detection service
        // Return true if content is plagiarized, false otherwise
        return false; // Placeholder implementation
    }

    public Content generateContent(ContentRequest request, User user) {
        String prompt = buildPrompt(request, user);

        String generatedContent;
        if ("text".equalsIgnoreCase(request.getContentType())) {
            generatedContent = generateTextContent(prompt);
        } else if ("image".equalsIgnoreCase(request.getContentType())) {
            String size = request.getImageSize() != null ? request.getImageSize() : "1024x1024";
            generatedContent = generateImageContent(prompt, size);
        } else {
            throw new UnsupportedOperationException("Content type not supported yet.");
        }


        // Perform sentiment analysis on the generated content
        String analyzedSentiment = analyzeSentiment(generatedContent);

        // Create and save the content entity
        Content content = new Content();
        content.setContentType(request.getContentType());
        content.setEmotionalTone(request.getEmotionalTone());
        content.setStatus("generated");
        content.setUser(user);
        content.setContentBody(generatedContent);
        content.setAnalyzedSentiment(analyzedSentiment); // Set the analyzed sentiment

        contentRepository.save(content);
        return content;

//        boolean isPlagiarized = isContentPlagiarized(generatedContent);
//        if (isPlagiarized) {
//            throw new RuntimeException("Generated content failed plagiarism check.");
//        }
    }

    private String generateTextContent(String prompt) {
        try {
            HttpClient client = HttpClient.newHttpClient();

            String requestBody = buildRequestBody(prompt);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("OpenAI API returned error: " + response.body());
            }

            return parseResponse(response.body());
        } catch (Exception e) {
            throw new RuntimeException("Error during OpenAI API call: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(String prompt) {
        return "{"
                + "\"model\": \"gpt-4\","
                + "\"messages\": [{\"role\": \"user\", \"content\": \"" + escapeJson(prompt) + "\"}],"
                + "\"max_tokens\": 500,"
                + "\"temperature\": 0.7"
                + "}";
    }

    private String parseResponse(String response) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            return root.get("choices").get(0).get("message").get("content").asText().trim();
        } catch (Exception e) {
            throw new RuntimeException("Error parsing OpenAI response: " + e.getMessage(), e);
        }
    }

    private String buildPrompt(ContentRequest request, User user) {
        String prompt = "Write a " + request.getEmotionalTone() + " article about " + request.getTopic() + ".";
        if (request.isOptimizeForSEO()) {
            prompt += " Ensure the content is optimized for SEO.";
            if (request.getKeywords() != null && !request.getKeywords().isEmpty()) {
                prompt += " Include the following keywords: " + request.getKeywords() + ".";
            }
        }
        if (user.getWritingStyleSample() != null && !user.getWritingStyleSample().isEmpty()) {
            prompt += " Please write in a style similar to: \"" + escapeJson(user.getWritingStyleSample()) + "\"";
        }
        return prompt;
    }

    // Utility method to escape JSON special characters in the prompt
    private String escapeJson(String text) {
        return text.replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String generateImageContent(String prompt, String size) {
        try {
            HttpClient client = HttpClient.newHttpClient();

            String requestBody = buildImageRequestBody(prompt, size);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/images/generations"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("OpenAI API returned error: " + response.body());
            }

            return parseImageResponse(response.body());
        } catch (Exception e) {
            throw new RuntimeException("Error during OpenAI API call: " + e.getMessage(), e);
        }
    }

    private String buildImageRequestBody(String prompt, String size) {
        return "{"
                + "\"prompt\": \"" + escapeJson(prompt) + "\","
                + "\"n\": 1,"
                + "\"size\": \"" + size + "\""
                + "}";
    }

    private String parseImageResponse(String response) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            return root.get("data").get(0).get("url").asText();
        } catch (Exception e) {
            throw new RuntimeException("Error parsing OpenAI image response: " + e.getMessage(), e);
        }
    }

    private String analyzeSentiment(String text) {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,parse,sentiment");

        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        // Create an empty Annotation with the given text
        Annotation annotation = new Annotation(text);

        // Run all Annotators on this text
        pipeline.annotate(annotation);

        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);

        int totalSentiment = 0;
        for (CoreMap sentence : sentences) {
            String sentiment = sentence.get(SentimentCoreAnnotations.SentimentClass.class);
            totalSentiment += sentimentToScore(sentiment);
        }

        int averageSentiment = totalSentiment / sentences.size();
        return scoreToSentiment(averageSentiment);
    }

    private int sentimentToScore(String sentiment) {
        switch (sentiment.toLowerCase()) {
            case "very negative":
                return 0;
            case "negative":
                return 1;
            case "neutral":
                return 2;
            case "positive":
                return 3;
            case "very positive":
                return 4;
            default:
                return 2;
        }
    }

    private String scoreToSentiment(int score) {
        switch (score) {
            case 0:
                return "Very Negative";
            case 1:
                return "Negative";
            case 2:
                return "Neutral";
            case 3:
                return "Positive";
            case 4:
                return "Very Positive";
            default:
                return "Neutral";
        }
    }

    public void processFeedback(FeedbackRequest feedbackRequest, User user) {
        // Implement logic to process feedback
        // For now, we're updating the content entity directly in the controller
    }
}