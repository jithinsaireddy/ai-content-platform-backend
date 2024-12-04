// src/main/java/com/jithin/ai_content_platform/config/WebSocketAuthChannelInterceptorAdapter.java

package com.jithin.ai_content_platform.config;

import com.jithin.ai_content_platform.service.WebSocketAuthenticatorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class WebSocketAuthChannelInterceptorAdapter implements ChannelInterceptor {

    @Autowired
    private WebSocketAuthenticatorService authenticatorService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authToken = accessor.getFirstNativeHeader("Authorization");
            if (authToken != null && authToken.startsWith("Bearer ")) {
                authToken = authToken.substring(7);
            }
            Authentication user = authenticatorService.getAuthenticatedOrFail(authToken);
            accessor.setUser(user);
            SecurityContextHolder.getContext().setAuthentication(user);
        }

        return message;
    }
}