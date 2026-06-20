package com.leetcodementor.controller;

import com.leetcodementor.dto.request.RevisionCompleteRequest;
import com.leetcodementor.dto.response.ApiResponse;
import com.leetcodementor.dto.response.PendingRevisionResponse;
import com.leetcodementor.dto.response.RevisionQueueResponse;
import com.leetcodementor.entity.User;
import com.leetcodementor.exception.UnauthorizedException;
import com.leetcodementor.repository.UserRepository;
import com.leetcodementor.service.RevisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/revision")
@RequiredArgsConstructor
public class RevisionController {

    private final RevisionService revisionService;
    private final UserRepository userRepository;

    @GetMapping("/queue")
    public ResponseEntity<ApiResponse<RevisionQueueResponse>> getRevisionQueue(Principal principal) {
        User user = getAuthenticatedUser(principal);
        RevisionQueueResponse response = revisionService.getRevisionQueue(user);
        return ResponseEntity.ok(ApiResponse.success(response, "Revision queue retrieved successfully"));
    }

    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<Void>> completeRevision(
            @Valid @RequestBody RevisionCompleteRequest request,
            Principal principal
    ) {
        User user = getAuthenticatedUser(principal);
        revisionService.completeRevision(user, request.getRevisionQueueId());
        return ResponseEntity.ok(ApiResponse.success(null, "Revision marked as completed"));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<PendingRevisionResponse>> getPendingRevisions(Principal principal) {
        User user = getAuthenticatedUser(principal);
        PendingRevisionResponse response = revisionService.getPendingRevisions(user);
        return ResponseEntity.ok(ApiResponse.success(response, "Pending revisions status retrieved"));
    }

    private User getAuthenticatedUser(Principal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
    }
}
