package com.leetcodementor.service;

import com.leetcodementor.dto.request.AiGenerateRequest;
import com.leetcodementor.dto.request.PrefetchRequest;
import com.leetcodementor.dto.response.AiGenerateResponse;
import com.leetcodementor.enums.Approach;
import com.leetcodementor.enums.ContentType;
import com.leetcodementor.enums.Language;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final AiOrchestratorService aiOrchestratorService;
    private final GeminiService geminiService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String STATUS_PREFIX = "prefetch-status:";

    // ─── Generate (Reads only from Redis cache / status) ──────────────────────

    public AiGenerateResponse generate(AiGenerateRequest request) {
        String cacheKey = buildCacheKey(
                request.getProblemSlug(),
                request.getLanguage(),
                request.getApproach(),
                request.getContentType()
        );
        String statusKey = STATUS_PREFIX + cacheKey;

        // 1. Read only from Redis
        String cachedResponse = (String) redisTemplate.opsForValue().get(cacheKey);
        if (cachedResponse != null) {
            log.info("Redis cache hit for key: {}", cacheKey);
            redisTemplate.opsForValue().set(statusKey, "DONE", Duration.ofHours(2));
            return AiGenerateResponse.builder()
                    .status("COMPLETED")
                    .content(cachedResponse)
                    .build();
        }

        // 2. Cache miss: Call Gemini direct rescue
        log.info("Redis cache miss for key: {}. Rescuing with Gemini...", cacheKey);
        return geminiService.generate(request);
    }

    // ─── Prefetch status ─────────────────────────────────────────────────────

    public Map<String, Object> getPrefetchStatus(String slug, Language language) {
        List<String> taskKeys = buildAllTaskKeys(slug, language);
        int total = taskKeys.size();
        int done = 0, failed = 0, pending = 0;

        for (String taskKey : taskKeys) {
            String status = (String) redisTemplate.opsForValue().get(STATUS_PREFIX + taskKey);
            if ("DONE".equals(status))     done++;
            else if ("FAILED".equals(status)) failed++;
            else                              pending++;
        }

        return Map.of(
                "total",   total,
                "done",    done,
                "failed",  failed,
                "pending", pending,
                "ready",   (done == total)
        );
    }

    // ─── Prefetch all ────────────────────────────────────────────────────────

    public void prefetchAll(PrefetchRequest req) {
        aiOrchestratorService.prefetchAll(
                req.getProblemTitle(),
                req.getProblemSlug(),
                req.getProblemDescription(),
                req.getLanguage()
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private List<String> buildAllTaskKeys(String slug, Language language) {
        List<String> keys = new ArrayList<>();
        keys.add(buildCacheKey(slug, language, Approach.BRUTEFORCE, ContentType.EXPLAIN));
        for (Approach approach : Approach.values()) {
            for (ContentType ct : List.of(
                    ContentType.HINT_1, ContentType.HINT_2,
                    ContentType.HINT_3, ContentType.HINT_4,
                    ContentType.SOLUTION)) {
                keys.add(buildCacheKey(slug, language, approach, ct));
            }
        }
        return keys;
    }

    private String buildCacheKey(String slug, Language language, Approach approach, ContentType contentType) {
        return String.format("%s_%s_%s_%s", slug, language.name(), approach.name(), contentType.name()).toLowerCase();
    }
}
