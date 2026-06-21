package com.leetcodementor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetcodementor.enums.Approach;
import com.leetcodementor.enums.ContentType;
import com.leetcodementor.enums.Language;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroqModel1Provider implements AiProvider {

    private final WebClient groqWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.model1:llama-3.3-70b-versatile}")
    private String modelName;

    @Override
    public String getProviderName() {
        return "Groq";
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public String callBlocking(String title, String slug, String description,
                               Approach approach, ContentType contentType, Language language) {
        log.info("Calling Groq Model 1 ({}) for slug: {}...", modelName, slug);

        String systemPrompt = AiPromptBuilder.getSystemPrompt(contentType);
        String userPrompt = AiPromptBuilder.getUserPrompt(title, slug, description, approach, contentType, language);

        Map<String, Object> requestBody = Map.of(
                "model", modelName,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "stream", false
        );

        try {
            String response = groqWebClient.post()
                    .uri("")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            return root.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText();
        } catch (Exception e) {
            log.error("Error calling Groq Model 1 API: {}", e.getMessage());
            throw new RuntimeException("Groq Model 1 API error: " + e.getMessage(), e);
        }
    }
}
