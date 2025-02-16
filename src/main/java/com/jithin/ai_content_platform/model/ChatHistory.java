package com.jithin.ai_content_platform.model;

import lombok.Data;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "chat_history")
@org.springframework.data.redis.core.RedisHash("chat_history")
public class ChatHistory implements java.io.Serializable {
    @Id
    private String id;
    private String userId;
    private String message;
    private String response;
    private LocalDateTime timestamp;
    private boolean isAiResponse;
}
