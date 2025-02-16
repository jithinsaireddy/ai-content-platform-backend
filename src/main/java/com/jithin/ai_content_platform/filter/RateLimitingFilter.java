// src/main/java/com/jithin/ai_content_platform/filter/RateLimitingFilter.java

package com.jithin.ai_content_platform.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter implements Filter {

    private static final long MAX_REQUESTS_PER_MINUTE = 60;
    private ConcurrentHashMap<String, Long> requestCounts = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Long> timestamps = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        String clientIp = request.getRemoteAddr();
        long currentTime = System.currentTimeMillis();

        requestCounts.putIfAbsent(clientIp, 0L);
        timestamps.putIfAbsent(clientIp, currentTime);

        if (currentTime - timestamps.get(clientIp) > 60000) {
            timestamps.put(clientIp, currentTime);
            requestCounts.put(clientIp, 0L);
        }

        requestCounts.put(clientIp, requestCounts.get(clientIp) + 1);

        if (requestCounts.get(clientIp) > MAX_REQUESTS_PER_MINUTE) {
            ((HttpServletResponse) response).setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
            return;
        }

        chain.doFilter(request, response);
    }
}