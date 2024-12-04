package com.jithin.ai_content_platform.payload;

import lombok.Data;

@Data
public class ContentRequest {
    private String contentType;    // e.g., "text", "image", "video"
    private String topic;
    private String emotionalTone;  // Optional
    // In ContentRequest.java
    private String writingStyleSample;
    private String imageSize;
    private String keywords; // Comma-separated keywords
    private boolean optimizeForSEO; // Flag to optimize for SEO

    // In ContentRequest.java
    public String getWritingStyleSample() {
        return writingStyleSample;
    }

    public void setWritingStyleSample(String writingStyleSample) {
        this.writingStyleSample = writingStyleSample;
    }
    // Additional fields as needed
}