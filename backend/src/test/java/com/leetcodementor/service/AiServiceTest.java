package com.leetcodementor.service;

import com.leetcodementor.dto.request.AiGenerateRequest;
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
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private GroqService groqService;

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
    void generate_CacheHit_StreamsCachedResponse() {
        String cacheKey = "two-sum_java_bruteforce_hint_1";
        String cachedValue = "This is a cached hint.";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(cachedValue);

        Flux<ServerSentEvent<String>> resultFlux = aiService.generate(request);

        StepVerifier.create(resultFlux)
                .expectNextCount(9) // Splits by spaces/words: "This", " ", "is", " ", "a", " ", "cached", " ", "hint."
                .expectNextMatches(sse -> "done".equals(sse.event()) && "[DONE]".equals(sse.data()))
                .verifyComplete();

        verify(groqService, never()).generateHint(anyString(), anyString(), anyString(), any(Approach.class), anyInt());
    }

    @Test
    void generate_CacheMiss_CallsGroqAndCaches() {
        String cacheKey = "two-sum_java_bruteforce_hint_1";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);

        Flux<ServerSentEvent<String>> groqFlux = Flux.just(
                ServerSentEvent.<String>builder().data("Try ").build(),
                ServerSentEvent.<String>builder().data("nested ").build(),
                ServerSentEvent.<String>builder().data("loops.").build()
        );

        when(groqService.generateHint(eq("Two Sum"), eq("two-sum"), eq("Find two numbers that add up to target."), eq(Approach.BRUTEFORCE), eq(1)))
                .thenReturn(groqFlux);

        Flux<ServerSentEvent<String>> resultFlux = aiService.generate(request);

        StepVerifier.create(resultFlux)
                .expectNextMatches(sse -> "Try ".equals(sse.data()))
                .expectNextMatches(sse -> "nested ".equals(sse.data()))
                .expectNextMatches(sse -> "loops.".equals(sse.data()))
                .verifyComplete();

        // Verify aggregation and caching in Redis happened
        verify(valueOperations).set(eq(cacheKey), eq("Try nested loops."), any(Duration.class));
    }
}
