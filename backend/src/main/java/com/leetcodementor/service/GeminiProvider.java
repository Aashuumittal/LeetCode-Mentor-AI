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
public class GeminiProvider implements AiProvider {

    private final WebClient geminiWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.model:gemini-2.5-flash}")
    private String modelName;

    @Override
    public String getProviderName() {
        return "Gemini";
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public String callBlocking(String title, String slug, String description,
                               Approach approach, ContentType contentType, Language language) {
        log.info("Calling Gemini Model ({}) for slug: {}...", modelName, slug);

        String systemPrompt = AiPromptBuilder.getSystemPrompt(contentType);
        String userPrompt = AiPromptBuilder.getUserPrompt(title, slug, description, approach, contentType, language);

        // Map request to Google Gemini API request body format:
        // {
        //   "contents": [
        //     {
        //       "parts": [
        //         { "text": "userPrompt" }
        //       ]
        //     }
        //   ],
        //   "systemInstruction": {
        //     "parts": [
        //       { "text": "systemPrompt" }
        //     ]
        //   }
        // }
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", userPrompt)))
                ),
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemPrompt))
                )
        );

        String uri = String.format("/v1beta/models/%s:generateContent?key=%s", modelName, apiKey);

        try {
            String response = geminiWebClient.post()
                    .uri(uri)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText();

            if (content == null || content.isBlank()) {
                throw new RuntimeException("Gemini returned an empty response.");
            }

            return content;
        } catch (Exception e) {
            log.error("Error calling Gemini API: {}", e.getMessage());
            throw new RuntimeException("Gemini API error: " + e.getMessage(), e);
        }
    }
}
