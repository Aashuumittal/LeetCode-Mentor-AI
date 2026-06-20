package com.leetcodementor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetcodementor.dto.request.CodeReviewRequest;
import com.leetcodementor.dto.response.CodeReviewResponse;
import com.leetcodementor.entity.CodeReviewHistory;
import com.leetcodementor.entity.User;
import com.leetcodementor.repository.CodeReviewHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeReviewService {

    private final GroqService groqService;
    private final CodeReviewHistoryRepository codeReviewHistoryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public CodeReviewResponse reviewCode(User user, CodeReviewRequest request) {
        log.info("Starting code review for user: {} (language: {})", user.getEmail(), request.getLanguage());

        String rawResponse = groqService.codeReview(request.getCode(), request.getLanguage(), request.getProblemSlug());

        String cleanedJson = cleanJsonOutput(rawResponse);

        try {
            // Verify and map to response object
            CodeReviewResponse responseDto = objectMapper.readValue(cleanedJson, CodeReviewResponse.class);

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
            log.error("Failed to parse Groq code review response as JSON. Cleaned input was: {}", cleanedJson, e);
            throw new RuntimeException("AI Code Review output format validation failed: " + e.getMessage(), e);
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
