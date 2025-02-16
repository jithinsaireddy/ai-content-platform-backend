package com.jithin.ai_content_platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
//@EnableCaching
@EnableScheduling
public class AiContentPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiContentPlatformApplication.class, args);
    }
}
