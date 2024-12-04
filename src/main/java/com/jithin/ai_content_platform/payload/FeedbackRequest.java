// src/main/java/com/jithin/ai_content_platform/payload/FeedbackRequest.java

package com.jithin.ai_content_platform.payload;

import lombok.Data;

@Data
public class FeedbackRequest {
    private Long contentId;
    private Integer rating; // Rating from 1 to 5
    private String comments;
}