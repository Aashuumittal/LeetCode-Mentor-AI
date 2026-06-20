package com.leetcodementor.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetcodementor.dto.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, TokenBucket> limiters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Capacity of 60 requests, refilling 1 token per second (60 per minute)
    private static final long BUCKET_CAPACITY = 60;
    private static final double REFILL_RATE_PER_SECOND = 1.0;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = getClientIp(request);
        TokenBucket bucket = limiters.computeIfAbsent(clientIp, k -> new TokenBucket(BUCKET_CAPACITY, REFILL_RATE_PER_SECOND));

        if (!bucket.tryConsume()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            
            ApiResponse<Void> apiResponse = ApiResponse.error("Too many requests. Please slow down.");
            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    private static class TokenBucket {
        private final long capacity;
        private final double refillRate;
        private double tokens;
        private long lastRefillTime;

        public TokenBucket(long capacity, double refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.tokens = capacity;
            this.lastRefillTime = System.currentTimeMillis();
        }

        public synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            double elapsedSeconds = (now - lastRefillTime) / 1000.0;
            lastRefillTime = now;
            tokens = Math.min(capacity, tokens + (elapsedSeconds * refillRate));
        }
    }
}
