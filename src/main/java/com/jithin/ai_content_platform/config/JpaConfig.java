package com.jithin.ai_content_platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableJpaRepositories(basePackages = "com.jithin.ai_content_platform.repository")
@EnableTransactionManagement
public class JpaConfig {
    // The configuration is handled by Spring Boot's auto-configuration
    // We just need the annotations to properly enable JPA and transaction management
}
