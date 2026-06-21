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
public class OpenRouterModel3Provider implements AiProvider {

    private final WebClient openRouterWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.model3:deepseek/deepseek-r1}")
    private String modelName;

    @Override
    public String getProviderName() {
        return "OpenRouter";
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public String callBlocking(String title, String slug, String description,
                               Approach approach, ContentType contentType, Language language) {
        log.info("Calling OpenRouter Model 3 ({}) for slug: {}...", modelName, slug);

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
            String response = openRouterWebClient.post()
                    .uri("")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60)) // OpenRouter DeepSeek R1 can take longer to think
                    .block();

            JsonNode root = objectMapper.readTree(response);
            return root.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText();
        } catch (Exception e) {
            log.error("Error calling OpenRouter Model 3 API: {}", e.getMessage());
            throw new RuntimeException("OpenRouter Model 3 API error: " + e.getMessage(), e);
        }
    }
}
