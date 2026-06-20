package com.leetcodementor.controller;

import com.leetcodementor.dto.request.CodeReviewRequest;
import com.leetcodementor.dto.response.ApiResponse;
import com.leetcodementor.dto.response.CodeReviewResponse;
import com.leetcodementor.entity.User;
import com.leetcodementor.exception.UnauthorizedException;
import com.leetcodementor.repository.UserRepository;
import com.leetcodementor.service.CodeReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class CodeReviewController {

    private final CodeReviewService codeReviewService;
    private final UserRepository userRepository;

    @PostMapping("/code")
    public ResponseEntity<ApiResponse<CodeReviewResponse>> reviewCode(
            @Valid @RequestBody CodeReviewRequest request,
            Principal principal
    ) {
        User user = getAuthenticatedUser(principal);
        CodeReviewResponse response = codeReviewService.reviewCode(user, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Code review completed successfully"));
    }

    private User getAuthenticatedUser(Principal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
    }
}
