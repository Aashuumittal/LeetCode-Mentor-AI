package com.leetcodementor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetcodementor.dto.request.AiGenerateRequest;
import com.leetcodementor.dto.response.AiGenerateResponse;
import com.leetcodementor.entity.AiRequestMetadata;
import com.leetcodementor.enums.Approach;
import com.leetcodementor.enums.ContentType;
import com.leetcodementor.enums.Language;
import com.leetcodementor.exception.BadRequestException;
import com.leetcodementor.repository.AiRequestMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final GeminiProvider geminiProvider;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AiRequestMetadataRepository aiRequestMetadataRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                    .status("DONE")
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

            // 4. Save metadata in Redis
            try {
                Map<String, String> meta = Map.of(
                        "provider", geminiProvider.getProviderName(),
                        "model", geminiProvider.getModelName(),
                        "cacheKey", cacheKey,
                        "createdAt", java.time.Instant.now().toString()
                );
                redisTemplate.opsForValue().set("metadata:" + cacheKey, objectMapper.writeValueAsString(meta), Duration.ofDays(30));
            } catch (Exception ex) {
                log.error("Failed to save metadata to Redis for key: {}", cacheKey, ex);
            }

            // 5. Save metadata in PostgreSQL
            try {
                AiRequestMetadata metadata = aiRequestMetadataRepository.findByCacheKey(cacheKey)
                        .orElseGet(() -> AiRequestMetadata.builder().cacheKey(cacheKey).build());
                metadata.setProvider(geminiProvider.getProviderName());
                metadata.setModel(geminiProvider.getModelName());
                metadata.setCreatedAt(LocalDateTime.now());
                aiRequestMetadataRepository.save(metadata);
                log.info("Saved Gemini rescue metadata in DB for key: {}", cacheKey);
            } catch (Exception ex) {
                log.error("Failed to save metadata to PostgreSQL for key: {}", cacheKey, ex);
            }

            return AiGenerateResponse.builder()
                    .status("DONE")
                    .content(content)
                    .build();
        } catch (Exception e) {
            log.error("Failed to generate suggestion via Gemini API for key: {}", cacheKey, e);
            redisTemplate.opsForValue().set(statusKey, "FAILED", Duration.ofHours(2));
            throw new BadRequestException("Unable to generate answer right now. Please try again.");
        }
    }

    public String codeReview(String code, Language language, String problemSlug) {
        return geminiProvider.codeReview(code, language, problemSlug);
    }

    private String buildCacheKey(String slug, Language language, Approach approach, ContentType contentType) {
        return String.format("%s_%s_%s_%s", slug, language.name(), approach.name(), contentType.name()).toLowerCase();
    }
}
