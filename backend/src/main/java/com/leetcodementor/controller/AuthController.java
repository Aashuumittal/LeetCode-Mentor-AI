package com.leetcodementor.controller;

import com.leetcodementor.dto.request.LoginRequest;
import com.leetcodementor.dto.request.RegisterRequest;
import com.leetcodementor.dto.response.ApiResponse;
import com.leetcodementor.dto.response.AuthResponse;
import com.leetcodementor.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.register(request);
        setRefreshTokenCookie(response, authResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(authResponse, "Registration successful"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.login(request);
        setRefreshTokenCookie(response, authResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(authResponse, "Login successful"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = null;

        // Try getting token from body first
        if (body != null && body.containsKey("refreshToken")) {
            refreshToken = body.get("refreshToken");
        }

        // Fallback to cookie
        if (refreshToken == null && request.getCookies() != null) {
            refreshToken = Arrays.stream(request.getCookies())
                    .filter(c -> "refreshToken".equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        if (refreshToken == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Refresh token is required"));
        }

        AuthResponse authResponse = authService.refresh(refreshToken);
        setRefreshTokenCookie(response, authResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(authResponse, "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = null;

        if (body != null && body.containsKey("refreshToken")) {
            refreshToken = body.get("refreshToken");
        }

        if (refreshToken == null && request.getCookies() != null) {
            refreshToken = Arrays.stream(request.getCookies())
                    .filter(c -> "refreshToken".equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        if (refreshToken != null) {
            authService.logout(refreshToken);
        }

        // Clear cookie
        Cookie cookie = new Cookie("refreshToken", null);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return ResponseEntity.ok(ApiResponse.success(null, "Logout successful"));
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("refreshToken", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // Set to true in production with HTTPS
        cookie.setPath("/");
        cookie.setMaxAge(30 * 24 * 60 * 60); // 30 days
        response.addCookie(cookie);
    }
}
