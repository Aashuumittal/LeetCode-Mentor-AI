package com.leetcodementor.controller;

import com.leetcodementor.dto.request.AiGenerateRequest;
import com.leetcodementor.dto.response.AiGenerateResponse;
import com.leetcodementor.dto.response.ApiResponse;
import com.leetcodementor.service.AiService;
import com.leetcodementor.service.GeminiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
