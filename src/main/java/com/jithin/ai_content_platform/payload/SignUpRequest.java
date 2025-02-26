package com.jithin.ai_content_platform.payload;

import lombok.Data;

@Data
public class SignUpRequest {
    private String username;
    private String email;
    private String password;
    private String industry = "default"; // Setting a default value to satisfy the not-null constraint
}