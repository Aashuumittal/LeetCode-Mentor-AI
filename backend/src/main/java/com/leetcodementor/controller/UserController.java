package com.leetcodementor.controller;

import com.leetcodementor.dto.response.ApiResponse;
import com.leetcodementor.dto.response.UserResponse;
import com.leetcodementor.entity.User;
import com.leetcodementor.exception.UnauthorizedException;
import com.leetcodementor.repository.UserRepository;
import com.leetcodementor.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(Principal principal) {
        User user = getAuthenticatedUser(principal);
        UserResponse response = userService.getUserProfileAndStats(user);
        return ResponseEntity.ok(ApiResponse.success(response, "User profile retrieved"));
    }

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Void>> resetProgress(Principal principal) {
        User user = getAuthenticatedUser(principal);
        userService.resetAllProgress(user);
        return ResponseEntity.ok(ApiResponse.success(null, "All progress has been reset successfully"));
    }

    private User getAuthenticatedUser(Principal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
    }
}
