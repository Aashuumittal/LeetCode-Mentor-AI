package com.leetcodementor.controller;

import com.leetcodementor.dto.request.AiGenerateRequest;
import com.leetcodementor.dto.request.PrefetchRequest;
import com.leetcodementor.dto.response.AiGenerateResponse;
import com.leetcodementor.dto.response.ApiResponse;
import com.leetcodementor.enums.Language;
import com.leetcodementor.service.AiService;
import com.leetcodementor.service.GeminiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final GeminiService geminiService;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<AiGenerateResponse>> generate(@Valid @RequestBody AiGenerateRequest request) {
        AiGenerateResponse response = aiService.generate(request);
        return ResponseEntity.ok(ApiResponse.success(response, "AI suggestion retrieved successfully"));
    }

    @PostMapping("/gemini/generate")
    public ResponseEntity<ApiResponse<AiGenerateResponse>> generateGemini(@Valid @RequestBody AiGenerateRequest request) {
        AiGenerateResponse response = geminiService.generate(request);
        return ResponseEntity.ok(ApiResponse.success(response, "AI suggestion retrieved successfully via Gemini 2.5"));
    }

    /** Fire-and-forget: starts prefetch in background, returns immediately */
    @PostMapping("/prefetch")
    public ResponseEntity<ApiResponse<String>> prefetch(@Valid @RequestBody PrefetchRequest request) {
        aiService.prefetchAll(request);
        return ResponseEntity.ok(ApiResponse.success("Prefetch started", "Prefetching all content in background"));
    }

    /** Poll this to check how many of the 16 tasks are DONE / PENDING / FAILED */
    @GetMapping("/prefetch-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> prefetchStatus(
            @RequestParam String slug,
            @RequestParam(defaultValue = "JAVA") Language language
    ) {
        Map<String, Object> status = aiService.getPrefetchStatus(slug, language);
        return ResponseEntity.ok(ApiResponse.success(status, "Prefetch status retrieved"));
    }
}
