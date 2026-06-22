package com.leetcodementor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetcodementor.dto.request.CodeReviewRequest;
import com.leetcodementor.dto.response.CodeReviewResponse;
import com.leetcodementor.entity.CodeReviewHistory;
import com.leetcodementor.entity.User;
import com.leetcodementor.exception.BadRequestException;
import com.leetcodementor.repository.CodeReviewHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeReviewService {

    private final GeminiService geminiService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CodeReviewHistoryRepository codeReviewHistoryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public CodeReviewResponse reviewCode(User user, CodeReviewRequest request) {
        log.info("Starting code review for user: {} (language: {})", user.getEmail(), request.getLanguage());

        String slug = request.getProblemSlug() != null ? request.getProblemSlug() : "unknown";
        String hash = DigestUtils.md5DigestAsHex(request.getCode().getBytes(StandardCharsets.UTF_8));
        String cacheKey = String.format("%s_%s_%s", slug, request.getLanguage().name(), hash).toLowerCase();

        // 1. Check Redis cache first
        String cachedJson = (String) redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            log.info("Redis cache hit for code review key: {}", cacheKey);
            try {
                return objectMapper.readValue(cachedJson, CodeReviewResponse.class);
            } catch (Exception e) {
                log.warn("Failed to parse cached JSON for code review key: {}. Regenerating...", cacheKey, e);
            }
        }

        // 2. Redis miss: Call Gemini ONLY
        log.info("Redis cache miss for code review key: {}. Calling Gemini...", cacheKey);
        String rawResponse = geminiService.codeReview(request.getCode(), request.getLanguage(), request.getProblemSlug());

        String cleanedJson = cleanJsonOutput(rawResponse);

        try {
            // Verify and map to response object
            CodeReviewResponse responseDto = objectMapper.readValue(cleanedJson, CodeReviewResponse.class);

            // Save review to Redis cache
            redisTemplate.opsForValue().set(cacheKey, cleanedJson, Duration.ofDays(30));

            // Save review to database history
            CodeReviewHistory history = CodeReviewHistory.builder()
                    .user(user)
                    .problemSlug(request.getProblemSlug())
                    .codeSnippet(request.getCode())
                    .reviewResult(cleanedJson)
                    .build();
            codeReviewHistoryRepository.save(history);

            return responseDto;
        } catch (Exception e) {
            log.error("Failed to parse Gemini code review response as JSON. Cleaned input was: {}", cleanedJson, e);
            throw new BadRequestException("Unable to generate answer right now. Please try again.");
        }
    }

    private String cleanJsonOutput(String raw) {
        if (raw == null) return "{}";
        String clean = raw.trim();
        if (clean.startsWith("```json")) {
            clean = clean.substring(7).trim();
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3).trim();
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length() - 3).trim();
        }
        return clean;
    }
}
