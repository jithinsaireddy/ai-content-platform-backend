package com.jithin.ai_content_platform.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ContentStrategyException extends RuntimeException {
    public ContentStrategyException(String message) {
        super(message);
    }
    
    public ContentStrategyException(String message, Throwable cause) {
        super(message, cause);
    }
}
