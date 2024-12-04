// src/main/java/com/jithin/ai_content_platform/model/Content.java

package com.jithin.ai_content_platform.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "contents")
public class Content {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String contentType;
    private String emotionalTone;
    private String status;

    @Column(columnDefinition = "TEXT")
    private String contentBody;

    @Column(columnDefinition = "TEXT")
    private String analyzedSentiment;

    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @CreationTimestamp
    private LocalDateTime createdDate;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}