package com.leetcodementor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetcodementor.entity.AiRequestMetadata;
import com.leetcodementor.enums.Approach;
import com.leetcodementor.enums.ContentType;
import com.leetcodementor.enums.Language;
import com.leetcodementor.repository.AiRequestMetadataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class AiOrchestratorService {

    private final List<AiProvider> providers = new ArrayList<>();
    private final RedisTemplate<String, Object> redisTemplate;
    private final AiRequestMetadataRepository aiRequestMetadataRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newFixedThreadPool(16);

    private static final String STATUS_PREFIX = "prefetch-status:";

    @Autowired
    public AiOrchestratorService(GroqModel1Provider model1Provider,
                                 GroqModel2Provider model2Provider,
                                 OpenRouterModel3Provider model3Provider,
                                 RedisTemplate<String, Object> redisTemplate,
                                 AiRequestMetadataRepository aiRequestMetadataRepository) {
        this.providers.add(model1Provider);
        this.providers.add(model2Provider);
        this.providers.add(model3Provider);
        this.redisTemplate = redisTemplate;
        this.aiRequestMetadataRepository = aiRequestMetadataRepository;
    }

    private record TaskSpec(Approach approach, ContentType contentType) {}

    public void prefetchAll(String title, String slug, String description, Language language) {
        log.info("Starting orchestrated prefetch for slug: {}", slug);
        CompletableFuture.runAsync(() -> runOrchestration(title, slug, description, language), executorService);
    }

    private void runOrchestration(String title, String slug, String description, Language language) {
        List<TaskSpec> allTasks = buildAllTasks();

        // 1. Mark status for all tasks
        for (TaskSpec task : allTasks) {
            String cacheKey = buildCacheKey(slug, language, task.approach(), task.contentType());
            String statusKey = STATUS_PREFIX + cacheKey;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
                redisTemplate.opsForValue().set(statusKey, "DONE", Duration.ofHours(2));
            } else {
                redisTemplate.opsForValue().set(statusKey, "PENDING", Duration.ofHours(2));
            }
        }

        // STEP 1: Provider 1 (Groq Model 1)
        List<TaskSpec> step1Tasks = getMissingTasks(slug, language, allTasks);
        if (!step1Tasks.isEmpty()) {
            executeStep(providers.get(0), step1Tasks, title, slug, description, language);
        }

        // STEP 2: Provider 2 (Groq Model 2)
        List<TaskSpec> step2Tasks = getMissingTasks(slug, language, allTasks);
        if (!step2Tasks.isEmpty()) {
            executeStep(providers.get(1), step2Tasks, title, slug, description, language);
        }

        // STEP 3: Provider 3 (OpenRouter Model 3)
        List<TaskSpec> step3Tasks = getMissingTasks(slug, language, allTasks);
        if (!step3Tasks.isEmpty()) {
            executeStepAndMarkFailed(providers.get(2), step3Tasks, title, slug, description, language);
        }

        log.info("Prefetch orchestration complete for: {}", slug);
    }

    private void executeStep(AiProvider provider, List<TaskSpec> tasks, String title, String slug, String description, Language language) {
        log.info("Executing Step with provider {} for {} tasks on slug: {}", provider.getProviderName(), tasks.size(), slug);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (TaskSpec task : tasks) {
            futures.add(CompletableFuture.runAsync(() -> {
                String cacheKey = buildCacheKey(slug, language, task.approach(), task.contentType());
                try {
                    String result = callWithBackoff(provider, title, slug, description, task.approach(), task.contentType(), language, cacheKey);
                    if (result != null && !result.isBlank()) {
                        cacheAndSaveMetadata(cacheKey, result, provider);
                    }
                } catch (Exception e) {
                    log.error("Step execution failed for provider {} on key {}: {}", provider.getProviderName(), cacheKey, e.getMessage());
                }
            }, executorService));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private void executeStepAndMarkFailed(AiProvider provider, List<TaskSpec> tasks, String title, String slug, String description, Language language) {
        log.info("Executing Fallback Step with provider {} for {} tasks on slug: {}", provider.getProviderName(), tasks.size(), slug);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (TaskSpec task : tasks) {
            futures.add(CompletableFuture.runAsync(() -> {
                String cacheKey = buildCacheKey(slug, language, task.approach(), task.contentType());
                String statusKey = STATUS_PREFIX + cacheKey;
                try {
                    String result = callWithBackoff(provider, title, slug, description, task.approach(), task.contentType(), language, cacheKey);
                    if (result != null && !result.isBlank()) {
                        cacheAndSaveMetadata(cacheKey, result, provider);
                    } else {
                        redisTemplate.opsForValue().set(statusKey, "FAILED", Duration.ofHours(2));
                    }
                } catch (Exception e) {
                    log.error("Final step execution failed for provider {} on key {}: {}", provider.getProviderName(), cacheKey, e.getMessage());
                    redisTemplate.opsForValue().set(statusKey, "FAILED", Duration.ofHours(2));
                }
            }, executorService));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private String callWithBackoff(AiProvider provider, String title, String slug, String description,
                                   Approach approach, ContentType contentType, Language language, String cacheKey) throws Exception {
        int attempt = 0;
        while (true) {
            try {
                String result = provider.callBlocking(title, slug, description, approach, contentType, language);
                if (result != null && !result.isBlank()) {
                    return result;
                }
                throw new RuntimeException("Empty response from AI provider");
            } catch (Exception e) {
                attempt++;
                if (attempt > 1) {
                    throw e; // Propage error to fall back to next provider
                }
                long delayMs = (1L << attempt) * 1000; // 2s, 4s, 8s
                log.warn("Attempt {} failed for provider {} on key {}. Retrying in {}s...",
                        attempt, provider.getProviderName(), cacheKey, delayMs / 1000);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Sleep interrupted during backoff", ie);
                }
            }
        }
    }

    private void cacheAndSaveMetadata(String cacheKey, String content, AiProvider provider) {
        String statusKey = STATUS_PREFIX + cacheKey;

        // 1. Cache response in Redis
        redisTemplate.opsForValue().set(cacheKey, content, Duration.ofDays(30));
        redisTemplate.opsForValue().set(statusKey, "DONE", Duration.ofHours(2));

        // 2. Save metadata in Redis
        try {
            Map<String, String> meta = Map.of(
                    "provider", provider.getProviderName(),
                    "model", provider.getModelName(),
                    "cacheKey", cacheKey,
                    "createdAt", java.time.Instant.now().toString()
            );
            redisTemplate.opsForValue().set("metadata:" + cacheKey, objectMapper.writeValueAsString(meta), Duration.ofDays(30));
        } catch (Exception e) {
            log.error("Failed to save metadata to Redis for key: {}", cacheKey, e);
        }

        // 3. Save metadata in PostgreSQL database
        try {
            AiRequestMetadata metadata = aiRequestMetadataRepository.findByCacheKey(cacheKey)
                    .orElseGet(() -> AiRequestMetadata.builder().cacheKey(cacheKey).build());
            metadata.setProvider(provider.getProviderName());
            metadata.setModel(provider.getModelName());
            metadata.setCreatedAt(LocalDateTime.now());
            aiRequestMetadataRepository.save(metadata);
            log.info("Saved AI request metadata in DB for key: {}", cacheKey);
        } catch (Exception e) {
            log.error("Failed to save metadata to PostgreSQL for key: {}", cacheKey, e);
        }
    }

    private List<TaskSpec> getMissingTasks(String slug, Language language, List<TaskSpec> allTasks) {
        List<TaskSpec> missing = new ArrayList<>();
        for (TaskSpec task : allTasks) {
            String cacheKey = buildCacheKey(slug, language, task.approach(), task.contentType());
            if (Boolean.FALSE.equals(redisTemplate.hasKey(cacheKey))) {
                missing.add(task);
            }
        }
        return missing;
    }

    private List<TaskSpec> buildAllTasks() {
        List<TaskSpec> specs = new ArrayList<>();
        specs.add(new TaskSpec(Approach.BRUTEFORCE, ContentType.EXPLAIN));
        for (Approach approach : Approach.values()) {
            for (ContentType ct : List.of(
                    ContentType.HINT_1, ContentType.HINT_2,
                    ContentType.HINT_3, ContentType.HINT_4,
                    ContentType.SOLUTION)) {
                specs.add(new TaskSpec(approach, ct));
            }
        }
        return specs;
    }

    private String buildCacheKey(String slug, Language language, Approach approach, ContentType contentType) {
        return String.format("%s_%s_%s_%s", slug, language.name(), approach.name(), contentType.name()).toLowerCase();
    }
}
