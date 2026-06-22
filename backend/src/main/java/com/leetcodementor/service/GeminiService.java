package com.leetcodementor.service;

import com.leetcodementor.dto.request.AiGenerateRequest;
import com.leetcodementor.dto.response.AiGenerateResponse;
import com.leetcodementor.enums.Approach;
import com.leetcodementor.enums.ContentType;
import com.leetcodementor.enums.Language;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final GeminiProvider geminiProvider;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String STATUS_PREFIX = "prefetch-status:";

    public AiGenerateResponse generate(AiGenerateRequest request) {
        String cacheKey = buildCacheKey(
                request.getProblemSlug(),
                request.getLanguage(),
                request.getApproach(),
                request.getContentType()
        );
        String statusKey = STATUS_PREFIX + cacheKey;

        // 1. Check Redis cache
        String cachedResponse = (String) redisTemplate.opsForValue().get(cacheKey);
        if (cachedResponse != null) {
            log.info("Redis cache hit for Gemini request with key: {}", cacheKey);
            redisTemplate.opsForValue().set(statusKey, "DONE", Duration.ofHours(2));
            return AiGenerateResponse.builder()
                    .status("COMPLETED")
                    .content(cachedResponse)
                    .build();
        }

        // 2. Cache miss: Call Gemini API
        log.info("Redis cache miss for Gemini request with key: {}. Calling Gemini API...", cacheKey);
        try {
            String content = geminiProvider.callBlocking(
                    request.getProblemTitle(),
                    request.getProblemSlug(),
                    request.getProblemDescription(),
                    request.getApproach(),
                    request.getContentType(),
                    request.getLanguage()
            );

            // 3. Save to Redis
            redisTemplate.opsForValue().set(cacheKey, content, Duration.ofDays(30));
            redisTemplate.opsForValue().set(statusKey, "DONE", Duration.ofHours(2));

            return AiGenerateResponse.builder()
                    .status("COMPLETED")
                    .content(content)
                    .build();
        } catch (Exception e) {
            log.error("Failed to generate suggestion via Gemini API for key: {}", cacheKey, e);
            redisTemplate.opsForValue().set(statusKey, "FAILED", Duration.ofHours(2));
            return AiGenerateResponse.builder()
                    .status("FAILED")
                    .build();
        }
    }

    private String buildCacheKey(String slug, Language language, Approach approach, ContentType contentType) {
        return String.format("%s_%s_%s_%s", slug, language.name(), approach.name(), contentType.name()).toLowerCase();
    }
}
