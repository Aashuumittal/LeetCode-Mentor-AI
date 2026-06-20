package com.leetcodementor.service;

import com.leetcodementor.dto.request.LoginRequest;
import com.leetcodementor.dto.request.RegisterRequest;
import com.leetcodementor.dto.response.AuthResponse;
import com.leetcodementor.dto.response.UserResponse;
import com.leetcodementor.entity.RefreshToken;
import com.leetcodementor.entity.User;
import com.leetcodementor.entity.UserStats;
import com.leetcodementor.exception.BadRequestException;
import com.leetcodementor.exception.UnauthorizedException;
import com.leetcodementor.repository.RefreshTokenRepository;
import com.leetcodementor.repository.UserRepository;
import com.leetcodementor.repository.UserStatsRepository;
import com.leetcodementor.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserStatsRepository userStatsRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already in use");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .preferredLanguage(request.getPreferredLanguage())
                .build();

        User savedUser = userRepository.save(user);

        UserStats stats = UserStats.builder()
                .user(savedUser)
                .currentStreak(0)
                .totalSolved(0)
                .totalHintsUsed(0)
                .build();
        userStatsRepository.save(stats);

        return generateAuthResponse(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenStr) {
        RefreshToken token = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(token);
            throw new UnauthorizedException("Refresh token expired. Please login again.");
        }

        User user = token.getUser();
        org.springframework.security.core.userdetails.UserDetails userDetails = 
                new org.springframework.security.core.userdetails.User(user.getEmail(), user.getPassword(), java.util.Collections.emptyList());
        
        String newAccessToken = jwtUtil.generateToken(userDetails);

        UserStats stats = userStatsRepository.findByUser(user)
                .orElse(null);

        UserResponse userResponse = mapToUserResponse(user, stats);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshTokenStr)
                .user(userResponse)
                .build();
    }

    @Transactional
    public void logout(String refreshTokenStr) {
        refreshTokenRepository.deleteByToken(refreshTokenStr);
    }

    private AuthResponse generateAuthResponse(User user) {
        org.springframework.security.core.userdetails.UserDetails userDetails = 
                new org.springframework.security.core.userdetails.User(user.getEmail(), user.getPassword(), java.util.Collections.emptyList());
        
        String accessToken = jwtUtil.generateToken(userDetails);
        
        // Generate new Refresh Token
        String tokenStr = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenStr)
                .user(user)
                .expiresAt(LocalDateTime.now().plusWeeks(4)) // ~ 28-30 days
                .build();
        
        refreshTokenRepository.deleteByUser(user); // clear old tokens
        refreshTokenRepository.save(refreshToken);

        UserStats stats = userStatsRepository.findByUser(user)
                .orElse(null);

        UserResponse userResponse = mapToUserResponse(user, stats);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(tokenStr)
                .user(userResponse)
                .build();
    }

    private UserResponse mapToUserResponse(User user, UserStats stats) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .preferredLanguage(user.getPreferredLanguage())
                .currentStreak(stats != null ? stats.getCurrentStreak() : 0)
                .lastSolvedDate(stats != null ? stats.getLastSolvedDate() : null)
                .totalSolved(stats != null ? stats.getTotalSolved() : 0)
                .totalHintsUsed(stats != null ? stats.getTotalHintsUsed() : 0)
                .build();
    }
}
