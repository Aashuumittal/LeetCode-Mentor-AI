package com.leetcodementor.controller;

import com.leetcodementor.dto.request.AiGenerateRequest;
import com.leetcodementor.dto.request.PrefetchRequest;
import com.leetcodementor.dto.response.ApiResponse;
import com.leetcodementor.enums.Language;
import com.leetcodementor.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> generate(@Valid @RequestBody AiGenerateRequest request) {
        return aiService.generate(request);
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
