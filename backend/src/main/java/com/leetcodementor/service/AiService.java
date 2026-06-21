package com.leetcodementor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetcodementor.dto.request.AiGenerateRequest;
import com.leetcodementor.dto.request.PrefetchRequest;
import com.leetcodementor.enums.Approach;
import com.leetcodementor.enums.ContentType;
import com.leetcodementor.enums.Language;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final GroqService groqService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 2000; // base backoff unit between retries
    private static final String STATUS_PREFIX = "prefetch-status:";

    // Only these 6 are prefetched eagerly when a user opens a problem.
    // Everything else (the remaining 10 hint/solution combinations) is
    // fetched lazily on first request and cached at that point — see
    // generate() below, which now also writes the DONE status key.
    private static final List<TaskSpec> PRIORITY_TASKS = List.of(
            new TaskSpec(Approach.BRUTEFORCE, ContentType.EXPLAIN),   // Explanation (approach-agnostic, stored under BRUTEFORCE)
            new TaskSpec(Approach.BRUTEFORCE, ContentType.HINT_1),
            new TaskSpec(Approach.BRUTEFORCE, ContentType.HINT_2),
            new TaskSpec(Approach.OPTIMIZED, ContentType.HINT_1),
            new TaskSpec(Approach.OPTIMAL, ContentType.HINT_1),
            new TaskSpec(Approach.OPTIMAL, ContentType.HINT_2)
    );

    // A handful of concurrent slots is enough for 6 priority tasks and stays
    // well clear of Groq's per-minute rate limit (previously 16-at-once
    // caused widespread 429s; this also fixes the retry filter below so any
    // 429 that does happen gets retried with backoff instead of silently
    // failing the task).
    private final Executor prefetchExecutor = Executors.newFixedThreadPool(3);

    private record TaskSpec(Approach approach, ContentType contentType) {}

    // ─── Generate (streaming, Redis-first) ───────────────────────────────────

    public Flux<ServerSentEvent<String>> generate(AiGenerateRequest request) {
        String cacheKey = buildCacheKey(
                request.getProblemSlug(),
                request.getLanguage(),
                request.getApproach(),
                request.getContentType()
        );
        String statusKey = STATUS_PREFIX + cacheKey;

        String cachedResponse = (String) redisTemplate.opsForValue().get(cacheKey);
        if (cachedResponse != null) {
            log.info("Redis cache hit for key: {}", cacheKey);
            redisTemplate.opsForValue().set(statusKey, "DONE", Duration.ofHours(2));
            String[] tokens = cachedResponse.split("(?<=\\s)|(?=\\s)");
            return Flux.fromArray(tokens)
                    .delayElements(Duration.ofMillis(5))
                    .map(token -> ServerSentEvent.<String>builder()
                            .data(encodeToken(token))
                            .build())
                    .concatWith(Flux.just(ServerSentEvent.<String>builder()
                            .event("done")
                            .data(encodeToken("[DONE]"))
                            .build()));
        }

        log.info("Redis cache miss for key: {}. Streaming from Groq...", cacheKey);
        Flux<ServerSentEvent<String>> streamResult = buildStreamForRequest(request);
        StringBuilder aggregator = new StringBuilder();

        return streamResult
                .doOnNext(sse -> {
                    if (sse.data() != null && sse.event() == null) {
                        String decoded = decodeToken(sse.data());
                        if (!"[DONE]".equals(decoded)) aggregator.append(decoded);
                    }
                })
                .doOnComplete(() -> {
                    String fullText = aggregator.toString();
                    if (!fullText.isEmpty()) {
                        redisTemplate.opsForValue().set(cacheKey, fullText, Duration.ofDays(30));
                        // Lazily-fetched content is now cached too, so reflect that
                        // in the prefetch status map (this item may not have been
                        // part of the original priority batch).
                        redisTemplate.opsForValue().set(statusKey, "DONE", Duration.ofHours(2));
                        log.info("Cached AI content in Redis for key: {}", cacheKey);
                    }
                });
    }

    // ─── Prefetch status ─────────────────────────────────────────────────────

    /**
     * Returns status of all 16 prefetch tasks for a given slug.
     * Each task value is: PENDING | DONE | FAILED
     */
    public Map<String, Object> getPrefetchStatus(String slug, Language language) {
        List<String> taskKeys = buildAllTaskKeys(slug, language);
        int total = taskKeys.size();
        int done = 0, failed = 0, pending = 0;

        for (String taskKey : taskKeys) {
            String status = (String) redisTemplate.opsForValue().get(STATUS_PREFIX + taskKey);
            if ("DONE".equals(status))        done++;
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

    // ─── Prefetch priority set only (fire-and-forget, with retries) ──────────
    // Previously this fired all 16 hint/solution/explain combinations at once,
    // which routinely tripped Groq's rate limit (429s). Now it only prefetches
    // the 6 items a user is statistically most likely to need immediately;
    // everything else is fetched lazily and cached on first real request
    // (see generate() above).
    public void prefetchAll(PrefetchRequest req) {
        String slug        = req.getProblemSlug();
        String title       = req.getProblemTitle();
        String description = req.getProblemDescription();
        Language language  = req.getLanguage();

        // Mark only the priority tasks PENDING upfront so the status endpoint
        // reflects them immediately. Non-priority tasks are left alone — they
        // simply read as PENDING by default until the user requests them.
        for (TaskSpec task : PRIORITY_TASKS) {
            String cacheKey = buildCacheKey(slug, language, task.approach(), task.contentType());
            String statusKey = STATUS_PREFIX + cacheKey;
            String existing = (String) redisTemplate.opsForValue().get(statusKey);
            if (!"DONE".equals(existing)) {
                redisTemplate.opsForValue().set(statusKey, "PENDING", Duration.ofHours(2));
            }
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (TaskSpec task : PRIORITY_TASKS) {
            futures.add(CompletableFuture.runAsync(() ->
                    prefetchWithRetry(slug, title, description, task.approach(), task.contentType(), language),
                    prefetchExecutor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> log.info("Priority prefetch complete for: {}", slug))
                .exceptionally(ex -> {
                    log.warn("Priority prefetch batch error for {}: {}", slug, ex.getMessage());
                    return null;
                });

        log.info("Priority prefetch started for: {} ({} tasks)", slug, futures.size());
    }

    // ─── Retry logic ─────────────────────────────────────────────────────────

    private void prefetchWithRetry(String slug, String title, String description,
                                   Approach approach, ContentType contentType, Language language) {
        String cacheKey  = buildCacheKey(slug, language, approach, contentType);
        String statusKey = STATUS_PREFIX + cacheKey;

        // Skip if content already in Redis (e.g. from a previous visit)
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
            log.debug("Prefetch skip (already cached): {}", cacheKey);
            redisTemplate.opsForValue().set(statusKey, "DONE", Duration.ofHours(2));
            return;
        }

        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            attempt++;
            try {
                log.info("Prefetch attempt {}/{} for: {}", attempt, MAX_RETRIES, cacheKey);
                String result = callGroqBlocking(title, slug, description, approach, contentType, language);

                if (result != null && !result.isBlank()) {
                    redisTemplate.opsForValue().set(cacheKey, result, Duration.ofDays(30));
                    redisTemplate.opsForValue().set(statusKey, "DONE", Duration.ofHours(2));
                    log.info("Prefetch DONE (attempt {}): {}", attempt, cacheKey);
                    return;
                } else {
                    log.warn("Prefetch attempt {} returned empty result for: {}", attempt, cacheKey);
                }
            } catch (Exception e) {
                boolean isRateLimited = isTooManyRequests(e);
                log.warn("Prefetch attempt {}/{} failed for {} ({}): {}",
                        attempt, MAX_RETRIES, cacheKey, isRateLimited ? "429" : "error", e.getMessage());
            }

            if (attempt < MAX_RETRIES) {
                long delay = RETRY_DELAY_MS * attempt; // 2s, 4s, 6s, 8s
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // All retries exhausted
        redisTemplate.opsForValue().set(statusKey, "FAILED", Duration.ofHours(2));
        log.error("Prefetch FAILED after {} retries for: {}", MAX_RETRIES, cacheKey);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private List<String> buildAllTaskKeys(String slug, Language language) {
        List<String> keys = new ArrayList<>();
        // EXPLAIN is approach-agnostic — stored under BRUTEFORCE key
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

    private String callGroqBlocking(String title, String slug, String description,
                                    Approach approach, ContentType contentType, Language language) {
        return switch (contentType) {
            case EXPLAIN   -> groqService.blockingExplainQuestion(title, slug, description);
            case HINT_1    -> groqService.blockingGenerateHint(title, slug, description, approach, 1);
            case HINT_2    -> groqService.blockingGenerateHint(title, slug, description, approach, 2);
            case HINT_3    -> groqService.blockingGenerateHint(title, slug, description, approach, 3);
            case HINT_4    -> groqService.blockingGenerateHint(title, slug, description, approach, 4);
            case SOLUTION  -> switch (approach) {
                case BRUTEFORCE -> groqService.blockingBruteForce(title, slug, description, language);
                case OPTIMIZED  -> groqService.blockingOptimizedApproach(title, slug, description, language);
                case OPTIMAL    -> groqService.blockingOptimalApproach(title, slug, description, language);
            };
        };
    }

    private Flux<ServerSentEvent<String>> buildStreamForRequest(AiGenerateRequest request) {
        return switch (request.getContentType()) {
            case EXPLAIN -> groqService.explainQuestion(
                    request.getProblemTitle(), request.getProblemSlug(), request.getProblemDescription());
            case HINT_1  -> groqService.generateHint(
                    request.getProblemTitle(), request.getProblemSlug(), request.getProblemDescription(), request.getApproach(), 1);
            case HINT_2  -> groqService.generateHint(
                    request.getProblemTitle(), request.getProblemSlug(), request.getProblemDescription(), request.getApproach(), 2);
            case HINT_3  -> groqService.generateHint(
                    request.getProblemTitle(), request.getProblemSlug(), request.getProblemDescription(), request.getApproach(), 3);
            case HINT_4  -> groqService.generateHint(
                    request.getProblemTitle(), request.getProblemSlug(), request.getProblemDescription(), request.getApproach(), 4);
            case SOLUTION -> switch (request.getApproach()) {
                case BRUTEFORCE -> groqService.bruteForce(
                        request.getProblemTitle(), request.getProblemSlug(), request.getProblemDescription(), request.getLanguage());
                case OPTIMIZED  -> groqService.optimizedApproach(
                        request.getProblemTitle(), request.getProblemSlug(), request.getProblemDescription(), request.getLanguage());
                case OPTIMAL    -> groqService.optimalApproach(
                        request.getProblemTitle(), request.getProblemSlug(), request.getProblemDescription(), request.getLanguage());
            };
        };
    }

    private String buildCacheKey(String slug, Language language, Approach approach, ContentType contentType) {
        return String.format("%s_%s_%s_%s", slug, language.name(), approach.name(), contentType.name()).toLowerCase();
    }

    private String encodeToken(String token) {
        try { return objectMapper.writeValueAsString(token); }
        catch (Exception e) { return "\"\""; }
    }

    private String decodeToken(String wireValue) {
        try { return objectMapper.readValue(wireValue, String.class); }
        catch (Exception e) { return wireValue; }
    }

    private boolean isTooManyRequests(Throwable t) {
        return t instanceof org.springframework.web.reactive.function.client.WebClientResponseException wcre
                && wcre.getStatusCode().value() == 429;
    }
}
