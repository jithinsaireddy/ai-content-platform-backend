package com.jithin.ai_content_platform.payload;

import lombok.Data;

@Data
public class EmotionalContentRequest {
    private String prompt;
    private String emotion;
    private String contentType = "text";  // Default to text
}
