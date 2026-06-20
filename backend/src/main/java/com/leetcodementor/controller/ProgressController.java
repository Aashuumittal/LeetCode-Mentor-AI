package com.leetcodementor.controller;

import com.leetcodementor.dto.request.SolveRequest;
import com.leetcodementor.dto.request.UpdateProgressRequest;
import com.leetcodementor.dto.response.ApiResponse;
import com.leetcodementor.dto.response.ProgressResponse;
import com.leetcodementor.dto.response.SolveResponse;
import com.leetcodementor.entity.User;
import com.leetcodementor.exception.UnauthorizedException;
import com.leetcodementor.repository.UserRepository;
import com.leetcodementor.service.ProgressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;
    private final UserRepository userRepository;

    @GetMapping("/{problemSlug}")
    public ResponseEntity<ApiResponse<List<ProgressResponse>>> getProgress(
            @PathVariable String problemSlug,
            Principal principal
    ) {
        User user = getAuthenticatedUser(principal);
        List<ProgressResponse> response = progressService.getProgress(user, problemSlug);
        return ResponseEntity.ok(ApiResponse.success(response, "Progress retrieved successfully"));
    }

    @PostMapping("/update")
    public ResponseEntity<ApiResponse<ProgressResponse>> updateProgress(
            @Valid @RequestBody UpdateProgressRequest request,
            Principal principal
    ) {
        User user = getAuthenticatedUser(principal);
        ProgressResponse response = progressService.updateProgress(user, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Progress updated successfully"));
    }

    @PostMapping("/solve")
    public ResponseEntity<ApiResponse<SolveResponse>> solveProblem(
            @Valid @RequestBody SolveRequest request,
            Principal principal
    ) {
        User user = getAuthenticatedUser(principal);
        SolveResponse response = progressService.solveProblem(user, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Problem solved and logged successfully"));
    }

    private User getAuthenticatedUser(Principal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
    }
}
