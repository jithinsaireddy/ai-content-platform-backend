package com.jithin.ai_content_platform.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JsonResponseHandler {
    
    private final ObjectMapper objectMapper;
    
    public JsonResponseHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Extracts an array from a JSON string with proper handling of various value types
     * @param jsonString The JSON string to parse
     * @param arrayField The field name containing the array
     * @return List of strings or empty list if extraction fails
     */
    public List<String> extractArray(String jsonString, String arrayField) {
        try {
            // Pattern that handles nested objects, arrays, and various value types
            Pattern arrayPattern = Pattern.compile(
                String.format("\"%s\"\\s*:\\s*\\[\\s*((?:"
                + "(?:\"{1,2}(?:[^\"\\\\]|\\\\.)*\"{1,2}|" // Strings with escaped quotes
                + "[0-9.]+|" // Numbers
                + "true|false|null|" // Booleans and null
                + "\\{[^{}]*\\}" // Objects
                + ")\\s*,?\\s*)*" // Multiple values
                + ")\\s*\\]", arrayField)
            );

            Matcher arrayMatcher = arrayPattern.matcher(jsonString);
            if (arrayMatcher.find()) {
                String arrayContent = arrayMatcher.group(1);
                return Arrays.stream(arrayContent.split("\\s*,\\s*"))
                    .map(s -> s.trim())
                    .map(s -> s.replaceAll("^\"|\"|\\\\\"|\"", ""))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Error extracting array from JSON response: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Safely parses and validates JSON response from OpenAI/OpenRouter
     * @param jsonString The JSON string to parse
     * @param defaultValue Default value to return if parsing fails
     * @return Parsed Map or default value if parsing fails
     */
    public Map<String, Object> parseAndValidateJson(String jsonString, Map<String, Object> defaultValue) {
        try {
            // Clean and validate JSON response
            String cleaned = cleanJsonResponse(jsonString);
            
            // Ensure we have a valid JSON object
            if (!isValidJsonObject(cleaned)) {
                log.error("Invalid JSON response format: {}", cleaned);
                return defaultValue;
            }

            // Parse the JSON
            return objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});
            
        } catch (Exception e) {
            log.error("Error parsing JSON response: {}", jsonString, e);
            return defaultValue;
        }
    }
    
    /**
     * Extracts a numeric score from a JSON response with validation
     * @param jsonString The JSON string to parse
     * @param scoreField The field name containing the score
     * @param defaultScore Default score to return if extraction fails
     * @return The extracted score or default value
     */
    public double extractScore(String jsonString, String scoreField, double defaultScore) {
        try {
            Map<String, Object> result = parseAndValidateJson(jsonString, new HashMap<>());
            
            Object scoreObj = result.get(scoreField);
            if (scoreObj instanceof Number) {
                double score = ((Number) scoreObj).doubleValue();
                // Ensure score is within valid range
                return Math.max(0.0, Math.min(1.0, score));
            }
            
            log.warn("Score not found or invalid in response for field {}: {}", scoreField, jsonString);
            return defaultScore;
            
        } catch (Exception e) {
            log.error("Error extracting score from JSON response: {}", jsonString, e);
            return defaultScore;
        }
    }
    
    /**
     * Cleans a JSON response string by removing markdown and other formatting,
     * and extracts the JSON object from any surrounding text
     * @param response The response string to clean
     * @return Cleaned JSON string
     */
    public String cleanJsonResponse(String response) {
        if (response == null) return "";
        
        String cleaned = response.trim();
        
        // Remove any text before the first '{' and after the last '}'
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1);
        }
        
        // Handle truncated JSON by completing the structure
        if (cleaned.contains("{") && !cleaned.endsWith("}")) {
            cleaned = completeJson(cleaned);
        }
        
        // First try to find a complete JSON object with proper formatting
        Pattern jsonPattern = Pattern.compile("\\{(?:[^{}]*|\\{(?:[^{}]*|\\{[^{}]*\\})*\\})*\\}");
        Matcher jsonMatcher = jsonPattern.matcher(cleaned);
        
        // Keep track of all potential JSON matches
        StringBuilder lastValidJson = new StringBuilder();
        while (jsonMatcher.find()) {
            String potentialJson = jsonMatcher.group();
            try {
                // Try to fix truncated arrays by adding missing brackets and braces
                potentialJson = fixTruncatedJson(potentialJson);
                // Verify this is valid JSON by attempting to parse it
                objectMapper.readTree(potentialJson);
                // If we find a valid JSON that's larger than our last valid one, use it
                if (potentialJson.length() > lastValidJson.length()) {
                    lastValidJson.setLength(0);
                    lastValidJson.append(potentialJson);
                }
            } catch (Exception e) {
                // If parsing fails, try to extract any valid JSON objects within this match
                Pattern innerJsonPattern = Pattern.compile("\\{[^{}]*\\}");
                Matcher innerJsonMatcher = innerJsonPattern.matcher(potentialJson);
                while (innerJsonMatcher.find()) {
                    String innerJson = innerJsonMatcher.group();
                    try {
                        objectMapper.readTree(innerJson);
                        if (innerJson.length() > lastValidJson.length()) {
                            lastValidJson.setLength(0);
                            lastValidJson.append(innerJson);
                        }
                    } catch (Exception innerE) {
                        // Ignore invalid inner JSON
                    }
                }
            }
        }
        
        // If we found a valid JSON object, return it
        if (lastValidJson.length() > 0) {
            return lastValidJson.toString();
        }
        
        // If we haven't found a valid JSON object yet, try cleaning the input
        cleaned = cleaned.replaceAll("`{1,3}(?:json)?\\n?", "") // Remove markdown code blocks
                        .replaceAll("`{1,3}", "") // Remove any remaining backticks
                        .replaceAll("(?m)^\\s*```.*$", "") // Remove markdown language specifiers
                        .replaceAll("[\\r\\n]+", " ") // Replace newlines with spaces
                        .trim();
        
        // Try to find the last complete JSON object in the text
        Pattern lastJsonPattern = Pattern.compile("\\{(?:[^{}]*|\\{(?:[^{}]*|\\{[^{}]*\\})*\\})*\\}");
        Matcher lastJsonMatcher = lastJsonPattern.matcher(cleaned);
        String lastMatch = null;
        
        while (lastJsonMatcher.find()) {
            lastMatch = lastJsonMatcher.group();
        }
        
        if (lastMatch != null) {
            try {
                String fixedJson = fixTruncatedJson(lastMatch);
                objectMapper.readTree(fixedJson); // Verify it's valid
                return fixedJson;
            } catch (Exception e) {
                // If we can't parse the complete object, try to find the largest valid JSON subset
                Pattern subsetPattern = Pattern.compile("\\{[^{}]+\\}");
                Matcher subsetMatcher = subsetPattern.matcher(lastMatch);
                while (subsetMatcher.find()) {
                    String subset = subsetMatcher.group();
                    try {
                        objectMapper.readTree(subset);
                        return subset;
                    } catch (Exception subsetE) {
                        // Continue searching for valid JSON subsets
                    }
                }
            }
        }
        
        // If all else fails, try to construct a minimal valid JSON object
        try {
            // Extract any key-value pairs from the text using a more generic pattern
            // Extract key-value pairs with proper handling of escaped quotes
            Pattern keyValuePattern = Pattern.compile("\\\"|\"([\\w]+)\"\\s*:\\s*(?:(\"{1,2}(?:[^\"\\\\]|\\\\[\"\\\\])*\"{1,2})|([0-9.]+)|\\[([^\\]]*)\\])");
            Matcher keyValueMatcher = keyValuePattern.matcher(cleaned);
            
            Map<String, Object> resultMap = new HashMap<>();
            while (keyValueMatcher.find()) {
                String key = keyValueMatcher.group(1);
                String strValue = keyValueMatcher.group(2);
                String numValue = keyValueMatcher.group(3);
                String arrayValue = keyValueMatcher.group(4);
                
                if (key != null) {
                    if (strValue != null) {
                        // Handle string value
                        resultMap.put(key, strValue.replaceAll("^\"{1,2}|\"$", "").replace("\\\\\"", "\""));
                    } else if (numValue != null) {
                        // Handle numeric value
                        try {
                            resultMap.put(key, Double.parseDouble(numValue));
                        } catch (NumberFormatException e) {
                            resultMap.put(key, numValue);
                        }
                    } else if (arrayValue != null) {
                        // Handle array value
                        String[] items = arrayValue.split(",");
                        resultMap.put(key, Arrays.stream(items)
                            .map(s -> s.trim().replaceAll("^\"{1,2}|\"$", "").replace("\\\\\"", "\""))
                            .collect(Collectors.toList()));
                    }
                }
            }
            
            if (!resultMap.isEmpty()) {
                return objectMapper.writeValueAsString(resultMap);
            }
        } catch (Exception e) {
            log.debug("Failed to construct minimal JSON object: {}", e.getMessage());
        }
        
        // If everything fails, return an empty JSON object
        return "{}";
    }
    
    /**
     * Attempts to fix truncated JSON by adding missing closing brackets and braces
     * @param json The potentially truncated JSON string
     * @return Fixed JSON string
     */
    private String completeJson(String json) {
        try {
            // Count unclosed braces and brackets
            int braceCount = 0;
            int bracketCount = 0;
            boolean inString = false;
            boolean escaped = false;
            
            for (char c : json.toCharArray()) {
                if (!escaped && c == '\"') {
                    inString = !inString;
                }
                if (!inString) {
                    if (c == '{') braceCount++;
                    if (c == '}') braceCount--;
                    if (c == '[') bracketCount++;
                    if (c == ']') bracketCount--;
                }
                escaped = !escaped && c == '\\';
            }
            
            // Complete the JSON structure
            StringBuilder completed = new StringBuilder(json);
            
            // Close any unclosed arrays
            while (bracketCount > 0) {
                completed.append("]");
                bracketCount--;
            }
            
            // Close any unclosed objects
            while (braceCount > 0) {
                completed.append("}");
                braceCount--;
            }
            
            // Try to parse the completed JSON
            objectMapper.readTree(completed.toString());
            return completed.toString();
            
        } catch (Exception e) {
            log.debug("Failed to complete JSON: {}", e.getMessage());
            return json;
        }
    }

    private String fixTruncatedJson(String json) {
        if (json == null) return "";
        
        try {
            // Count opening and closing braces/brackets
            int braceCount = 0;
            int bracketCount = 0;
            boolean inString = false;
            boolean escaped = false;
            char[] chars = json.toCharArray();
            
            for (int i = 0; i < chars.length; i++) {
                char c = chars[i];
                
                if (escaped) {
                    escaped = false;
                    continue;
                }
                
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                
                if (c == '\"' && !escaped) {
                    inString = !inString;
                    continue;
                }
                
                if (!inString) {
                    if (c == '{') braceCount++;
                    if (c == '}') braceCount--;
                    if (c == '[') bracketCount++;
                    if (c == ']') bracketCount--;
                }
            }
            
            // Add missing closing brackets/braces
            StringBuilder fixed = new StringBuilder(json);
            while (bracketCount > 0) {
                fixed.append("]");
                bracketCount--;
            }
            while (braceCount > 0) {
                fixed.append("}");
                braceCount--;
            }
            
            return fixed.toString();
        } catch (Exception e) {
            log.warn("Error fixing truncated JSON: {}", e.getMessage());
            return json; // Return original if fixing fails
        }
    }
    
    /**
     * Checks if a string represents a valid JSON object
     * @param jsonString The string to validate
     * @return true if string is a valid JSON object
     */
    public boolean isValidJsonObject(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return false;
        }
        
        // Basic structure check
        if (!jsonString.startsWith("{") || !jsonString.endsWith("}")) {
            return false;
        }
        
        // Full validation by attempting to parse with Jackson
        try {
            objectMapper.readTree(jsonString);
            return true;
        } catch (Exception e) {
            log.debug("JSON validation failed: {}", e.getMessage());
            return false;
        }
    }
}
