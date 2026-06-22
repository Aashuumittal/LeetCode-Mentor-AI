package com.leetcodementor.service;

import com.leetcodementor.dto.request.AiGenerateRequest;
import com.leetcodementor.dto.response.AiGenerateResponse;
import com.leetcodementor.enums.Approach;
import com.leetcodementor.enums.ContentType;
import com.leetcodementor.enums.Language;
import com.leetcodementor.exception.BadRequestException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private GroqService groqService;

    @Mock
    private GeminiProvider geminiProvider;

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

        assertEquals("DONE", response.getStatus());
        assertEquals(cachedValue, response.getContent());
        verify(groqService, never()).blockingGenerateHint(anyString(), anyString(), anyString(), any(), anyInt());
        verify(geminiProvider, never()).callBlocking(anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void generate_CacheMiss_GroqSuccess_ReturnsSuccess() {
        String cacheKey = "two-sum_java_bruteforce_hint_1";
        String generatedValue = "This is a hint from Groq.";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(groqService.blockingGenerateHint(
                request.getProblemTitle(),
                request.getProblemSlug(),
                request.getProblemDescription(),
                request.getApproach(),
                1
        )).thenReturn(generatedValue);

        AiGenerateResponse response = aiService.generate(request);

        assertEquals("DONE", response.getStatus());
        assertEquals(generatedValue, response.getContent());
        verify(valueOperations).set(cacheKey, generatedValue, Duration.ofDays(30));
        verify(geminiProvider, never()).callBlocking(anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void generate_CacheMiss_GroqFail_GeminiSuccess_ReturnsSuccess() {
        String cacheKey = "two-sum_java_bruteforce_hint_1";
        String generatedValue = "This is a fallback hint from Gemini.";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(groqService.blockingGenerateHint(
                request.getProblemTitle(),
                request.getProblemSlug(),
                request.getProblemDescription(),
                request.getApproach(),
                1
        )).thenThrow(new RuntimeException("Groq error"));

        when(geminiProvider.callBlocking(
                request.getProblemTitle(),
                request.getProblemSlug(),
                request.getProblemDescription(),
                request.getApproach(),
                request.getContentType(),
                request.getLanguage()
        )).thenReturn(generatedValue);

        AiGenerateResponse response = aiService.generate(request);

        assertEquals("DONE", response.getStatus());
        assertEquals(generatedValue, response.getContent());
        verify(valueOperations).set(cacheKey, generatedValue, Duration.ofDays(30));
    }

    @Test
    void generate_CacheMiss_BothFail_ThrowsBadRequestException() {
        String cacheKey = "two-sum_java_bruteforce_hint_1";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(groqService.blockingGenerateHint(
                request.getProblemTitle(),
                request.getProblemSlug(),
                request.getProblemDescription(),
                request.getApproach(),
                1
        )).thenThrow(new RuntimeException("Groq error"));

        when(geminiProvider.callBlocking(
                request.getProblemTitle(),
                request.getProblemSlug(),
                request.getProblemDescription(),
                request.getApproach(),
                request.getContentType(),
                request.getLanguage()
        )).thenThrow(new RuntimeException("Gemini error"));

        assertThrows(BadRequestException.class, () -> aiService.generate(request));
    }
}
