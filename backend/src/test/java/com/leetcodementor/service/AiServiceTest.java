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
    private GeminiService geminiService;

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
        String statusKey = "prefetch-status:" + cacheKey;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(cachedValue);

        AiGenerateResponse response = aiService.generate(request);

        assertEquals("COMPLETED", response.getStatus());
        assertEquals(cachedValue, response.getContent());
        verify(redisTemplate.opsForValue()).set(statusKey, "DONE", Duration.ofHours(2));
        verify(geminiService, never()).generate(any(AiGenerateRequest.class));
    }

    @Test
    void generate_CacheMiss_RescuesWithGemini() {
        String cacheKey = "two-sum_java_bruteforce_hint_1";
        AiGenerateResponse expectedRescueResponse = AiGenerateResponse.builder()
                .status("COMPLETED")
                .content("Gemini rescued content")
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(geminiService.generate(request)).thenReturn(expectedRescueResponse);

        AiGenerateResponse response = aiService.generate(request);

        assertEquals("COMPLETED", response.getStatus());
        assertEquals("Gemini rescued content", response.getContent());
        verify(geminiService, times(1)).generate(request);
    }
}
