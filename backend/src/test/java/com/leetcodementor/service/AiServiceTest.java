package com.leetcodementor.service;

import com.leetcodementor.dto.request.AiGenerateRequest;
import com.leetcodementor.dto.response.AiGenerateResponse;
import com.leetcodementor.enums.Approach;
import com.leetcodementor.enums.ContentType;
import com.leetcodementor.enums.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private AiOrchestratorService aiOrchestratorService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private AiService aiService;

    private AiGenerateRequest request;

    @BeforeEach
    void setUp() {
        request = AiGenerateRequest.builder()
                .problemSlug("two-sum")
                .problemTitle("Two Sum")
                .problemDescription("Find two numbers that add up to target.")
                .approach(Approach.BRUTEFORCE)
                .contentType(ContentType.HINT_1)
                .language(Language.JAVA)
                .build();
    }

    @Test
    void generate_CacheHit_ReturnsSuccess() {
        String cacheKey = "two-sum_java_bruteforce_hint_1";
        String cachedValue = "This is a cached hint.";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(cachedValue);

        AiGenerateResponse response = aiService.generate(request);

        assertEquals("COMPLETED", response.getStatus());
        assertEquals(cachedValue, response.getContent());
        verify(aiOrchestratorService, never()).prefetchAll(anyString(), anyString(), anyString(), any(Language.class));
    }

    @Test
    void generate_CacheMiss_NoStatus_TriggersPrefetchAndReturnsPending() {
        String cacheKey = "two-sum_java_bruteforce_hint_1";
        String statusKey = "prefetch-status:" + cacheKey;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(valueOperations.get(statusKey)).thenReturn(null);

        AiGenerateResponse response = aiService.generate(request);

        assertEquals("PENDING", response.getStatus());
        verify(aiOrchestratorService, times(1)).prefetchAll(
                eq("Two Sum"), eq("two-sum"), eq("Find two numbers that add up to target."), eq(Language.JAVA)
        );
    }

    @Test
    void generate_CacheMiss_StatusPending_ReturnsPending() {
        String cacheKey = "two-sum_java_bruteforce_hint_1";
        String statusKey = "prefetch-status:" + cacheKey;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(valueOperations.get(statusKey)).thenReturn("PENDING");

        AiGenerateResponse response = aiService.generate(request);

        assertEquals("PENDING", response.getStatus());
        verify(aiOrchestratorService, never()).prefetchAll(anyString(), anyString(), anyString(), any(Language.class));
    }

    @Test
    void generate_CacheMiss_StatusFailed_ReturnsFailed() {
        String cacheKey = "two-sum_java_bruteforce_hint_1";
        String statusKey = "prefetch-status:" + cacheKey;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(valueOperations.get(statusKey)).thenReturn("FAILED");

        AiGenerateResponse response = aiService.generate(request);

        assertEquals("FAILED", response.getStatus());
        verify(aiOrchestratorService, never()).prefetchAll(anyString(), anyString(), anyString(), any(Language.class));
    }
}
