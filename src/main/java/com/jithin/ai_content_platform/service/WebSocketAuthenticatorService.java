// src/main/java/com/jithin/ai_content_platform/service/WebSocketAuthenticatorService.java

package com.jithin.ai_content_platform.service;

import com.jithin.ai_content_platform.model.User;
import com.jithin.ai_content_platform.repository.UserRepository;
import com.jithin.ai_content_platform.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class WebSocketAuthenticatorService {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Autowired
    public WebSocketAuthenticatorService(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    // ... rest of the code ...

//    @Autowired
//    private JwtTokenProvider jwtTokenProvider;
//
//    @Autowired
//    private UserRepository userRepository;

    public Authentication getAuthenticatedOrFail(String jwtToken) {
        if (jwtToken != null && jwtTokenProvider.validateToken(jwtToken)) {
            String username = jwtTokenProvider.getUsernameFromJWT(jwtToken);
            User user = userRepository.findByUsername(username).orElseThrow(() ->
                    new UsernameNotFoundException("User not found"));
            return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        } else {
            throw new BadCredentialsException("Invalid JWT Token");
        }
    }
}