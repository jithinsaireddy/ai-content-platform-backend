// src/main/java/com/jithin/ai_content_platform/config/WebSocketConfig.java

package com.jithin.ai_content_platform.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.*;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.*;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private WebSocketAuthChannelInterceptorAdapter webSocketAuthChannelInterceptorAdapter;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins.toArray(new String[0])) // Using allowed origins from properties
                .withSockJS()
                .setDisconnectDelay(30 * 1000) // 30 seconds disconnect delay
                .setHeartbeatTime(25000)
                .setClientLibraryUrl("https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app"); // Prefix for application destinations
        registry.enableSimpleBroker("/queue", "/topic")
                .setHeartbeatValue(new long[]{20000, 20000}) // Set broker heartbeat to 20 seconds for both directions
                .setTaskScheduler(heartbeatScheduler()); // Custom scheduler for heartbeats
        registry.setUserDestinationPrefix("/user"); // Prefix for user-specific destinations
        registry.setPreservePublishOrder(true); // Maintain message order
    }

    @Bean
    public TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ws-heartbeat-scheduler-");
        scheduler.setDaemon(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setErrorHandler(t -> {
            if (!(t instanceof InterruptedException)) {
                log.error("Unexpected error occurred in scheduler", t);
            }
        });
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthChannelInterceptorAdapter);
    }
}