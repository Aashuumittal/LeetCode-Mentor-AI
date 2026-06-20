package com.leetcodementor.integration;

import com.leetcodementor.dto.request.RegisterRequest;
import com.leetcodementor.dto.request.SolveRequest;
import com.leetcodementor.dto.request.UpdateProgressRequest;
import com.leetcodementor.dto.response.ApiResponse;
import com.leetcodementor.dto.response.AuthResponse;
import com.leetcodementor.dto.response.ProgressResponse;
import com.leetcodementor.dto.response.SolveResponse;
import com.leetcodementor.enums.Approach;
import com.leetcodementor.enums.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class ProgressIntegrationTest {

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

    private String jwtToken;

    @BeforeEach
    void setUp() {
        // Register a user to authenticate subsequent requests
        String registerUrl = "http://localhost:" + port + "/api/auth/register";
        RegisterRequest regReq = RegisterRequest.builder()
                .name("Progress User")
                .email("progress@example.com")
                .password("testPassword123")
                .preferredLanguage(Language.JAVA)
                .build();

        ResponseEntity<ApiResponse<AuthResponse>> regRes = restTemplate.exchange(
                registerUrl,
                HttpMethod.POST,
                new HttpEntity<>(regReq),
                new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {}
        );

        if (regRes.getStatusCode() == HttpStatus.OK && regRes.getBody() != null) {
            jwtToken = regRes.getBody().getData().getAccessToken();
        }
    }

    @Test
    void progressTracking_IntegrationFlow() {
        assertNotNull(jwtToken, "Should have received JWT token during setup");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String updateUrl = "http://localhost:" + port + "/api/progress/update";
        String solveUrl = "http://localhost:" + port + "/api/progress/solve";
        String getUrl = "http://localhost:" + port + "/api/progress/two-sum";

        // 1. Update progress for bruteforce approach
        UpdateProgressRequest updateReq = UpdateProgressRequest.builder()
                .problemSlug("two-sum")
                .approach(Approach.BRUTEFORCE)
                .hintsUnlocked(1)
                .solutionViewed(false)
                .questionExplained(true)
                .build();

        ResponseEntity<ApiResponse<ProgressResponse>> updateRes = restTemplate.exchange(
                updateUrl,
                HttpMethod.POST,
                new HttpEntity<>(updateReq, headers),
                new ParameterizedTypeReference<ApiResponse<ProgressResponse>>() {}
        );

        assertEquals(HttpStatus.OK, updateRes.getStatusCode());
        assertNotNull(updateRes.getBody());
        ProgressResponse progressData = updateRes.getBody().getData();
        assertEquals(Approach.BRUTEFORCE, progressData.getApproach());
        assertEquals(1, progressData.getHintsUnlocked());

        // 2. Fetch progress and verify it returns in list
        ResponseEntity<ApiResponse<List<ProgressResponse>>> getRes = restTemplate.exchange(
                getUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<List<ProgressResponse>>>() {}
        );

        assertEquals(HttpStatus.OK, getRes.getStatusCode());
        assertNotNull(getRes.getBody());
        List<ProgressResponse> progressList = getRes.getBody().getData();
        assertFalse(progressList.isEmpty());
        assertEquals("two-sum", progressList.get(0).getProblemSlug());

        // 3. Mark problem as solved
        SolveRequest solveReq = SolveRequest.builder()
                .problemSlug("two-sum")
                .difficulty("EASY")
                .language(Language.JAVA)
                .approach(Approach.OPTIMAL)
                .hintsUsed(1)
                .solutionViewed(false)
                .build();

        ResponseEntity<ApiResponse<SolveResponse>> solveRes = restTemplate.exchange(
                solveUrl,
                HttpMethod.POST,
                new HttpEntity<>(solveReq, headers),
                new ParameterizedTypeReference<ApiResponse<SolveResponse>>() {}
        );

        assertEquals(HttpStatus.OK, solveRes.getStatusCode());
        assertNotNull(solveRes.getBody());
        SolveResponse solveData = solveRes.getBody().getData();
        assertTrue(solveData.getCurrentStreak() > 0);
    }
}
