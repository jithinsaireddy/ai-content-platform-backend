package com.jithin.ai_content_platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
public class AppConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Specify allowed origins
        configuration.setAllowedOrigins(Arrays.asList("https://luxury-druid-505aa1.netlify.app", "http://localhost:3000", "https://sentlyze.xyz"));

        // Specify allowed HTTP methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Specify allowed headers
        configuration.setAllowedHeaders(Arrays.asList("*"));

        // Specify exposed headers
        configuration.setExposedHeaders(Arrays.asList("*"));

        // Allow credentials
        configuration.setAllowCredentials(false);

        // Register the configuration
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}