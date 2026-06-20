package com.leetcodementor.service;

import com.leetcodementor.dto.request.LoginRequest;
import com.leetcodementor.dto.request.RegisterRequest;
import com.leetcodementor.dto.response.AuthResponse;
import com.leetcodementor.entity.RefreshToken;
import com.leetcodementor.entity.User;
import com.leetcodementor.entity.UserStats;
import com.leetcodementor.enums.Language;
import com.leetcodementor.exception.BadRequestException;
import com.leetcodementor.exception.UnauthorizedException;
import com.leetcodementor.repository.RefreshTokenRepository;
import com.leetcodementor.repository.UserRepository;
import com.leetcodementor.repository.UserStatsRepository;
import com.leetcodementor.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserStatsRepository userStatsRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private User user;
    private UserStats userStats;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .name("Test User")
                .email("test@example.com")
                .password("hashed_password")
                .preferredLanguage(Language.JAVA)
                .build();

        userStats = UserStats.builder()
                .user(user)
                .currentStreak(0)
                .totalSolved(0)
                .totalHintsUsed(0)
                .build();
    }

    @Test
    void register_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Test User")
                .email("test@example.com")
                .password("password123")
                .preferredLanguage(Language.JAVA)
                .build();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtUtil.generateToken(any(UserDetails.class))).thenReturn("access_token");
        when(userStatsRepository.findByUser(any(User.class))).thenReturn(Optional.of(userStats));

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("access_token", response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals(user.getEmail(), response.getUser().getEmail());
        verify(userRepository).save(any(User.class));
        verify(userStatsRepository).save(any(UserStats.class));
    }

    @Test
    void register_EmailAlreadyExists_ThrowsBadRequestException() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Test User")
                .email("test@example.com")
                .password("password123")
                .preferredLanguage(Language.JAVA)
                .build();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThrows(BadRequestException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_Success() {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(true);
        when(jwtUtil.generateToken(any(UserDetails.class))).thenReturn("access_token");
        when(userStatsRepository.findByUser(any(User.class))).thenReturn(Optional.of(userStats));

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("access_token", response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_InvalidPassword_ThrowsUnauthorizedException() {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("wrong_password")
                .build();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.login(request));
    }

    @Test
    void refresh_Success() {
        String tokenStr = "valid_refresh_token";
        RefreshToken token = RefreshToken.builder()
                .token(tokenStr)
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(refreshTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(token));
        when(jwtUtil.generateToken(any(UserDetails.class))).thenReturn("new_access_token");
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(userStats));

        AuthResponse response = authService.refresh(tokenStr);

        assertNotNull(response);
        assertEquals("new_access_token", response.getAccessToken());
        assertEquals(tokenStr, response.getRefreshToken());
    }

    @Test
    void refresh_ExpiredToken_ThrowsUnauthorizedException() {
        String tokenStr = "expired_refresh_token";
        RefreshToken token = RefreshToken.builder()
                .token(tokenStr)
                .user(user)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        when(refreshTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(token));

        assertThrows(UnauthorizedException.class, () -> authService.refresh(tokenStr));
        verify(refreshTokenRepository).delete(token);
    }
}
