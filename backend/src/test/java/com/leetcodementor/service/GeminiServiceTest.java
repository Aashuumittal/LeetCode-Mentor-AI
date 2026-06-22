package com.leetcodementor.service;

import com.leetcodementor.dto.request.AiGenerateRequest;
import com.leetcodementor.dto.response.AiGenerateResponse;
import com.leetcodementor.enums.Approach;
import com.leetcodementor.enums.ContentType;
import com.leetcodementor.enums.Language;
import com.leetcodementor.exception.BadRequestException;
import com.leetcodementor.repository.AiRequestMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeminiServiceTest {

    @Mock
    private GeminiProvider geminiProvider;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private AiRequestMetadataRepository aiRequestMetadataRepository;

    @InjectMocks
    private GeminiService geminiService;

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
        String statusKey = "prefetch-status:" + cacheKey;
        String cachedValue = "This is a cached Gemini hint.";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(cachedValue);

        AiGenerateResponse response = geminiService.generate(request);

        assertEquals("COMPLETED", response.getStatus());
        assertEquals(cachedValue, response.getContent());
        verify(valueOperations).set(statusKey, "DONE", Duration.ofHours(2));
        verify(geminiProvider, never()).callBlocking(anyString(), anyString(), anyString(), any(Approach.class), any(ContentType.class), any(Language.class));
    }

    @Test
    void generate_CacheMiss_CallsGeminiAndCaches() {
        String cacheKey = "two-sum_java_bruteforce_hint_1";
        String statusKey = "prefetch-status:" + cacheKey;
        String generatedValue = "This is a generated Gemini hint.";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(geminiProvider.callBlocking(
                request.getProblemTitle(),
                request.getProblemSlug(),
                request.getProblemDescription(),
                request.getApproach(),
                request.getContentType(),
                request.getLanguage()
        )).thenReturn(generatedValue);
        when(geminiProvider.getProviderName()).thenReturn("Gemini");
        when(geminiProvider.getModelName()).thenReturn("gemini-2.5-flash");
        when(aiRequestMetadataRepository.findByCacheKey(cacheKey)).thenReturn(Optional.empty());

        AiGenerateResponse response = geminiService.generate(request);

        assertEquals("COMPLETED", response.getStatus());
        assertEquals(generatedValue, response.getContent());
        verify(valueOperations).set(cacheKey, generatedValue, Duration.ofDays(30));
        verify(valueOperations).set(statusKey, "DONE", Duration.ofHours(2));
        verify(aiRequestMetadataRepository, times(1)).save(any());
    }

    @Test
    void generate_CacheMiss_GeminiThrowsException_ThrowsBadRequestException() {
        String cacheKey = "two-sum_java_bruteforce_hint_1";
        String statusKey = "prefetch-status:" + cacheKey;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(geminiProvider.callBlocking(
                request.getProblemTitle(),
                request.getProblemSlug(),
                request.getProblemDescription(),
                request.getApproach(),
                request.getContentType(),
                request.getLanguage()
        )).thenThrow(new RuntimeException("Gemini service unavailable"));

        assertThrows(BadRequestException.class, () -> geminiService.generate(request));
        verify(valueOperations).set(statusKey, "FAILED", Duration.ofHours(2));
    }
}
