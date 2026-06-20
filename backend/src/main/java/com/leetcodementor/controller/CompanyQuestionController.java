package com.leetcodementor.controller;

import com.leetcodementor.dto.request.CompanyQuestionImportRequest;
import com.leetcodementor.dto.response.ApiResponse;
import com.leetcodementor.dto.response.CompanyQuestionResponse;
import com.leetcodementor.enums.Company;
import com.leetcodementor.enums.Difficulty;
import com.leetcodementor.service.CompanyQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/company-questions")
@RequiredArgsConstructor
public class CompanyQuestionController {

    private final CompanyQuestionService companyQuestionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CompanyQuestionResponse>>> getCompanyQuestions(
            @RequestParam(required = false) Company company,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) String topic
    ) {
        List<CompanyQuestionResponse> response = companyQuestionService.getCompanyQuestions(company, difficulty, topic);
        return ResponseEntity.ok(ApiResponse.success(response, "Company questions retrieved successfully"));
    }

    @GetMapping("/problem")
    public ResponseEntity<ApiResponse<List<CompanyQuestionResponse>>> getFrequenciesForProblem(
            @RequestParam String title,
            @RequestParam String slug,
            @RequestParam(required = false) String description
    ) {
        List<CompanyQuestionResponse> response =
                companyQuestionService.getAiCompanyFrequenciesForProblem(title, slug, description);
        return ResponseEntity.ok(ApiResponse.success(response, "Company frequencies retrieved successfully"));
    }

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<Void>> importCompanyQuestions(
            @Valid @RequestBody List<CompanyQuestionImportRequest> request
    ) {
        companyQuestionService.importCompanyQuestions(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Company questions imported successfully"));
    }
}