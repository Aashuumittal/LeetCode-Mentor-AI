package com.leetcodementor.integration;

import com.leetcodementor.dto.request.LoginRequest;
import com.leetcodementor.dto.request.RegisterRequest;
import com.leetcodementor.dto.response.ApiResponse;
import com.leetcodementor.dto.response.AuthResponse;
import com.leetcodementor.enums.Language;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void registerAndLogin_IntegrationFlow() {
        String registerUrl = "http://localhost:" + port + "/api/auth/register";
        String loginUrl = "http://localhost:" + port + "/api/auth/login";

        RegisterRequest regReq = RegisterRequest.builder()
                .name("Integration User")
                .email("integration@example.com")
                .password("securePassword123")
                .preferredLanguage(Language.PYTHON)
                .build();

        // 1. Register User
        ResponseEntity<ApiResponse<AuthResponse>> regRes = restTemplate.exchange(
                registerUrl,
                HttpMethod.POST,
                new HttpEntity<>(regReq),
                new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {}
        );

        assertEquals(HttpStatus.OK, regRes.getStatusCode());
        assertNotNull(regRes.getBody());
        assertTrue(regRes.getBody().isSuccess());
        AuthResponse authData = regRes.getBody().getData();
        assertNotNull(authData.getAccessToken());
        assertNotNull(authData.getRefreshToken());
        assertEquals("Integration User", authData.getUser().getName());

        // 2. Login User
        LoginRequest loginReq = LoginRequest.builder()
                .email("integration@example.com")
                .password("securePassword123")
                .build();

        ResponseEntity<ApiResponse<AuthResponse>> loginRes = restTemplate.exchange(
                loginUrl,
                HttpMethod.POST,
                new HttpEntity<>(loginReq),
                new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {}
        );

        assertEquals(HttpStatus.OK, loginRes.getStatusCode());
        assertNotNull(loginRes.getBody());
        assertTrue(loginRes.getBody().isSuccess());
        AuthResponse loginData = loginRes.getBody().getData();
        assertNotNull(loginData.getAccessToken());
        assertEquals("integration@example.com", loginData.getUser().getEmail());
    }
}
