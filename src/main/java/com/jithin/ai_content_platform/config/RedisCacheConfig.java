package com.jithin.ai_content_platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Create ObjectMapper with proper configuration
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // Create key serializer
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        
        // Create value serializer
        Jackson2JsonRedisSerializer<Object> valueSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        valueSerializer.setObjectMapper(objectMapper);

        // Default configuration with 1 hour TTL
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .serializeKeysWith(SerializationPair.fromSerializer(keySerializer))
            .serializeValuesWith(SerializationPair.fromSerializer(valueSerializer));

        // Special configuration for trending topics with 24 hour TTL
        RedisCacheConfiguration trendingTopicsConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(24))
            .serializeKeysWith(SerializationPair.fromSerializer(keySerializer))
            .serializeValuesWith(SerializationPair.fromSerializer(valueSerializer));

        // Configuration for OpenRouter responses with 6 hour TTL
        RedisCacheConfiguration openRouterConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(6))
            .serializeKeysWith(SerializationPair.fromSerializer(keySerializer))
            .serializeValuesWith(SerializationPair.fromSerializer(valueSerializer));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withCacheConfiguration("trendingTopics", trendingTopicsConfig)
            .withCacheConfiguration("openRouterResponses", openRouterConfig)
            .build();
    }
}

