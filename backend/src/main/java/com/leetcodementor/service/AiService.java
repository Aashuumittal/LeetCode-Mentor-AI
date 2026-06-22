package com.leetcodementor.service;

import com.leetcodementor.dto.request.AiGenerateRequest;
import com.leetcodementor.dto.response.AiGenerateResponse;
import com.leetcodementor.enums.Approach;
import com.leetcodementor.enums.ContentType;
import com.leetcodementor.enums.Language;
import com.leetcodementor.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final GroqService groqService;
    private final GeminiProvider geminiProvider;
    private final RedisTemplate<String, Object> redisTemplate;

    public AiGenerateResponse generate(AiGenerateRequest request) {
        String cacheKey = buildCacheKey(
                request.getProblemSlug(),
                request.getLanguage(),
                request.getApproach(),
                request.getContentType()
        );

        // 1. Read only from Redis
        String cachedResponse = (String) redisTemplate.opsForValue().get(cacheKey);
        if (cachedResponse != null) {
            log.info("Redis cache hit for key: {}", cacheKey);
            return AiGenerateResponse.builder()
                    .status("DONE")
                    .content(cachedResponse)
                    .build();
        }

        // 2. Cache miss: Call Groq ONLY (llama-3.3-70b-versatile, blocking request)
        log.info("Redis cache miss for key: {}. Calling Groq...", cacheKey);
        try {
            String content = callGroq(request);
            // Store in Redis
            redisTemplate.opsForValue().set(cacheKey, content, Duration.ofDays(30));
            return AiGenerateResponse.builder()
                    .status("DONE")
                    .content(content)
                    .build();
        } catch (Exception e) {
            log.warn("Groq failed for key: {}. Falling back to Gemini...", cacheKey, e);
            // 3. Fallback: Call Gemini
            try {
                String content = geminiProvider.callBlocking(
                        request.getProblemTitle(),
                        request.getProblemSlug(),
                        request.getProblemDescription(),
                        request.getApproach(),
                        request.getContentType(),
                        request.getLanguage()
                );
                // Store in Redis
                redisTemplate.opsForValue().set(cacheKey, content, Duration.ofDays(30));
                return AiGenerateResponse.builder()
                        .status("DONE")
                        .content(content)
                        .build();
            } catch (Exception ex) {
                log.error("Gemini fallback also failed for key: {}", cacheKey, ex);
                throw new BadRequestException("Unable to generate answer right now. Please try again.");
            }
        }
    }

    private String callGroq(AiGenerateRequest request) {
        String title = request.getProblemTitle();
        String slug = request.getProblemSlug();
        String desc = request.getProblemDescription();
        Language lang = request.getLanguage();
        Approach approach = request.getApproach();
        ContentType ct = request.getContentType();

        return switch (ct) {
            case EXPLAIN -> groqService.blockingExplainQuestion(title, slug, desc);
            case HINT_1 -> groqService.blockingGenerateHint(title, slug, desc, approach, 1);
            case HINT_2 -> groqService.blockingGenerateHint(title, slug, desc, approach, 2);
            case HINT_3 -> groqService.blockingGenerateHint(title, slug, desc, approach, 3);
            case HINT_4 -> groqService.blockingGenerateHint(title, slug, desc, approach, 4);
            case SOLUTION -> {
                yield switch (approach) {
                    case BRUTEFORCE -> groqService.blockingBruteForce(title, slug, desc, lang);
                    case OPTIMIZED -> groqService.blockingOptimizedApproach(title, slug, desc, lang);
                    case OPTIMAL -> groqService.blockingOptimalApproach(title, slug, desc, lang);
                };
            }
        };
    }

    private String buildCacheKey(String slug, Language language, Approach approach, ContentType contentType) {
        return String.format("%s_%s_%s_%s", slug, language.name(), approach.name(), contentType.name()).toLowerCase();
    }
}
