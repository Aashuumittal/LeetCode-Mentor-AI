package com.leetcodementor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetcodementor.enums.Approach;
import com.leetcodementor.enums.Language;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroqService {

    private final WebClient groqWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String MODEL_NAME = "llama-3.3-70b-versatile";

    public Flux<ServerSentEvent<String>> explainQuestion(String title, String slug, String description) {
        log.info("Sending explainQuestion request to Groq API...");

        String systemPrompt = "You are LeetCode Mentor AI, a world-class Data Structures and Algorithms (DSA) coach. " +
                "Help the user learn DSA through structured guidance. Do NOT write or output any code implementations.";

        String userPrompt = String.format(
                "Please explain the following LeetCode problem clearly:\n" +
                        "Title: %s (Slug: %s)\n" +
                        "Description:\n%s\n\n" +
                        "You MUST return clean markdown. You MUST structure your response with the following headers exactly (in this order):\n" +
                        "### Problem Summary\n" +
                        "### Intuition\n" +
                        "### Example\n" +
                        "### Approaches\n" +
                        "### Complexity\n" +
                        "### Companies Asked\n" +
                        "### Interview Priority\n\n" +
                        "Do NOT include any other section headers or code implementations.",
                title, slug, description
        );

        return streamChatCompletion(systemPrompt, userPrompt);
    }

    public Flux<ServerSentEvent<String>> bruteForce(String title, String slug, String description, Language language) {
        log.info("Sending bruteForce request to Groq API...");
        return streamSolution(title, slug, description, Approach.BRUTEFORCE, language);
    }

    public Flux<ServerSentEvent<String>> optimizedApproach(String title, String slug, String description, Language language) {
        log.info("Sending optimizedApproach request to Groq API...");
        return streamSolution(title, slug, description, Approach.OPTIMIZED, language);
    }

    public Flux<ServerSentEvent<String>> optimalApproach(String title, String slug, String description, Language language) {
        log.info("Sending optimalApproach request to Groq API...");
        return streamSolution(title, slug, description, Approach.OPTIMAL, language);
    }

    public Flux<ServerSentEvent<String>> generateHint(String title, String slug, String description, Approach approach, int hintLevel) {
        log.info("Sending generateHint request (level {}) to Groq API...", hintLevel);

        String systemPrompt = "You are LeetCode Mentor AI, a world-class Data Structures and Algorithms (DSA) coach. " +
                "Help the user learn DSA through guidance rather than giving code answers immediately.";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Provide Hint %d for the %s approach of the following problem:\n", hintLevel, approach.name()))
                .append("Title: ").append(title).append("\n")
                .append("Description:\n").append(description).append("\n\n");

        switch (hintLevel) {
            case 1 -> sb.append("Guidelines: Keep it high-level and conceptual. Give a nudge in the right direction. ")
                    .append("Do NOT talk about specific data structures or write code. Explain the basic mathematical or logical idea behind this approach.");
            case 2 -> sb.append("Guidelines: Suggest the appropriate data structure(s) (e.g., Stack, Hash Map, Two Pointers) and the algorithmic framework. ")
                    .append("Explain *why* this choice fits the problem. Do NOT write code.");
            case 3 -> sb.append("Guidelines: Explain the state transitions, loop invariants, pointer moves, or boundary conditions needed for implementation. ")
                    .append("Give a pseudocode-like explanation of the logic, but do NOT write real code.");
            case 4 -> sb.append("Guidelines: Describe how to dry-run the logic step-by-step on a small test case. ")
                    .append("List critical edge cases the user must write checks for (e.g., empty arrays, single element, negative values). Do NOT write code.");
            default -> throw new IllegalArgumentException("Invalid hint level: " + hintLevel);
        }

        return streamChatCompletion(systemPrompt, sb.toString());
    }

    /**
     * Ask Groq to estimate which of our 7 known companies frequently ask this
     * problem. Returns a raw JSON string (array of {company, frequency} objects)
     * that the caller must parse. Uses blockChatCompletion — fast, no streaming.
     */
    public String getCompanyFrequencies(String title, String slug, String description) {
        log.info("Requesting AI company frequencies for problem: {}", title);

        String systemPrompt = "You are a LeetCode interview data analyst. " +
                "You have deep knowledge of which companies ask which LeetCode problems based on publicly available interview reports. " +
                "Return ONLY a valid JSON array. No markdown, no explanation, no preamble.";

        String userPrompt = String.format(
                "For the LeetCode problem \"%s\" (slug: %s), estimate how frequently each of the following companies asks it in interviews.\n\n" +
                "Problem description (for context):\n%s\n\n" +
                "Companies to evaluate: GOOGLE, AMAZON, MICROSOFT, ADOBE, UBER, ATLASSIAN, FLIPKART\n\n" +
                "Return ONLY a JSON array for companies that ask this problem (frequency > 0). Omit companies that don't ask it.\n" +
                "Each element must have exactly these two fields:\n" +
                "  - \"company\": one of the company names above (ALL CAPS)\n" +
                "  - \"frequency\": an integer between 1 and 50 representing how many times it has been reported\n\n" +
                "Example output format (do not copy these numbers, give real estimates):\n" +
                "[{\"company\":\"GOOGLE\",\"frequency\":22},{\"company\":\"AMAZON\",\"frequency\":15}]\n\n" +
                "Return ONLY the JSON array. Nothing else.",
                title, slug, description != null ? description : "Not provided"
        );

        return blockChatCompletion(systemPrompt, userPrompt);
    }

    public String codeReview(String code, Language language, String problemSlug) {
        log.info("Sending codeReview request to Groq API...");

        String systemPrompt = "You are an automated code review bot. Your task is to review the following code snippet and return ONLY a valid, parseable JSON object matching the requested schema.";

        String userPrompt = "Code details:\n" +
                "- Programming Language: " + language.name() + "\n" +
                "- Optional Problem Context (slug): " + (problemSlug != null ? problemSlug : "Not provided") + "\n\n" +
                "Code Snippet:\n" +
                "-----------------------------------------\n" +
                code + "\n" +
                "-----------------------------------------\n\n" +
                "You MUST respond ONLY with a JSON object. Do not include markdown code block characters like ```json or any other conversational text. Ensure fields have correct types. If no issues or optimizations are found, return empty arrays.\n\n" +
                "JSON Schema:\n" +
                "{\n" +
                "  \"syntaxIssues\": [\n" +
                "    { \"line\": 12, \"issue\": \"Brief description\", \"fix\": \"How to fix it\" }\n" +
                "  ],\n" +
                "  \"logicIssues\": [\n" +
                "    { \"description\": \"Logic bug details\", \"suggestion\": \"What to change\" }\n" +
                "  ],\n" +
                "  \"optimizations\": [\n" +
                "    { \"description\": \"Optimization details\", \"improvedCode\": \"Optimized code block\" }\n" +
                "  ],\n" +
                "  \"betterApproach\": {\n" +
                "    \"description\": \"Alternative approach details (e.g. O(N) instead of O(N^2))\",\n" +
                "    \"example\": \"Code illustration or logic flow description\"\n" +
                "  },\n" +
                "  \"timeComplexity\": {\n" +
                "    \"current\": \"e.g., O(N^2)\",\n" +
                "    \"optimized\": \"e.g., O(N)\",\n" +
                "    \"explanation\": \"Brief complexity analysis\"\n" +
                "  },\n" +
                "  \"spaceComplexity\": {\n" +
                "    \"current\": \"e.g., O(1)\",\n" +
                "    \"optimized\": \"e.g., O(N)\",\n" +
                "    \"explanation\": \"Brief memory usage analysis\"\n" +
                "  }\n" +
                "}";

        return blockChatCompletion(systemPrompt, userPrompt);
    }

    private Flux<ServerSentEvent<String>> streamSolution(String title, String slug, String description, Approach approach, Language language) {
        String systemPrompt = "You are LeetCode Mentor AI, a world-class Data Structures and Algorithms (DSA) coach.";

        String userPrompt = String.format(
                "Provide the full Solution for the %s approach in %s for the following problem:\n" +
                        "Title: %s (Slug: %s)\n" +
                        "Description:\n%s\n\n" +
                        "Guidelines:\n" +
                        "1. Write clean, complete, production-ready, and well-commented code.\n" +
                        "2. Explain the code structure and line-by-line highlights briefly.\n" +
                        "3. State the Time Complexity and Space Complexity clearly with brief proofs/explanations.\n" +
                        "Format the code block using markdown syntax.",
                approach.name(), language.name(), title, slug, description
        );

        return streamChatCompletion(systemPrompt, userPrompt);
    }

    private Flux<ServerSentEvent<String>> streamChatCompletion(String systemPrompt, String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", MODEL_NAME,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "stream", true
        );

        ParameterizedTypeReference<ServerSentEvent<String>> typeRef = new ParameterizedTypeReference<>() {};

        return groqWebClient.post()
                .uri("")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(typeRef)
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(15))
                        .filter(GroqService::isRetryable)
                )
                .map(sse -> parseGroqChunk(sse.data()))
                .filter(text -> !text.isEmpty())
                .map(text -> ServerSentEvent.<String>builder()
                        .data(encodeTokenForWire(text))
                        .build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder().event("done").data(encodeTokenForWire("[DONE]")).build()))
                .doOnCancel(() -> log.info("Groq streaming request was cancelled by client."))
                .onErrorResume(e -> {
                    log.error("Error during streaming Groq content", e);
                    return Flux.just(ServerSentEvent.<String>builder().event("error").data("AI generation error: " + e.getMessage()).build());
                });
    }

    private String blockChatCompletion(String systemPrompt, String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", MODEL_NAME,
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
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(3))
                            .maxBackoff(Duration.ofSeconds(20))
                            .filter(GroqService::isRetryable)
                    )
                    .block();

            JsonNode root = objectMapper.readTree(response);
            return root.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText();
        } catch (Exception e) {
            log.error("Error calling Groq API (blocking)", e);
            throw new RuntimeException("Groq API error: " + e.getMessage(), e);
        }
    }

    /**
     * SSE "data:" field values are whitespace-fragile: a token that IS a single
     * space, or that starts/ends with one, can get trimmed by intermediate
     * writers/proxies or by a naive client-side parser. JSON-encoding the token
     * guarantees every character (including spaces) survives transport intact.
     * The frontend must JSON.parse() each data line to recover the exact text.
     */
    private String encodeTokenForWire(String token) {
        try {
            return objectMapper.writeValueAsString(token);
        } catch (Exception e) {
            // Fallback: should never happen for a plain String, but never drop data.
            return "\"\"";
        }
    }

    private String parseGroqChunk(String chunk) {
        if (chunk == null || chunk.trim().isEmpty() || "[DONE]".equals(chunk.trim())) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(chunk);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).path("delta");
                if (delta.has("content")) {
                    return delta.path("content").asText();
                }
            }
        } catch (Exception e) {
            log.debug("Skip non-JSON chunk: {}", chunk);
        }
        return "";
    }

    /**
     * Retries on transient network failures (IOException/TimeoutException) and
     * on HTTP 429 (Groq rate limit) — the original filter only covered the
     * former, so every 429 used to fail the call immediately with zero
     * in-place retry, relying entirely on the caller's own outer retry loop.
     */
    private static boolean isRetryable(Throwable t) {
        if (t instanceof java.io.IOException || t instanceof java.util.concurrent.TimeoutException) {
            return true;
        }
        if (t instanceof org.springframework.web.reactive.function.client.WebClientResponseException wcre) {
            return wcre.getStatusCode().value() == 429;
        }
        return false;
    }

    // Blocking versions for prefetch

    public String blockingExplainQuestion(String title, String slug, String description) {
        String systemPrompt = "You are LeetCode Mentor AI, a world-class DSA coach. Do NOT write code implementations.";
        String userPrompt = String.format(
                "Explain the following LeetCode problem:\nTitle: %s (Slug: %s)\nDescription:\n%s\n\n" +
                "Return clean markdown with these sections in order:\n" +
                "### Problem Summary\n### Intuition\n### Example\n### Approaches\n### Complexity\n### Companies Asked\n### Interview Priority",
                title, slug, description);
        return blockChatCompletion(systemPrompt, userPrompt);
    }

    public String blockingGenerateHint(String title, String slug, String description, Approach approach, int hintLevel) {
        String systemPrompt = "You are LeetCode Mentor AI, a world-class DSA coach.";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Provide Hint %d for the %s approach of:\nTitle: %s\nDescription:\n%s\n\n",
                hintLevel, approach.name(), title, description));
        switch (hintLevel) {
            case 1 -> sb.append("Keep it high-level and conceptual. No code, no data structures yet.");
            case 2 -> sb.append("Suggest the appropriate data structure(s) and algorithmic framework. No code.");
            case 3 -> sb.append("Explain state transitions, loop invariants, pointer moves. Pseudocode only, no real code.");
            case 4 -> sb.append("Dry-run on a small example and list critical edge cases. No code.");
            default -> throw new IllegalArgumentException("Invalid hint level: " + hintLevel);
        }
        return blockChatCompletion(systemPrompt, sb.toString());
    }

    public String blockingBruteForce(String title, String slug, String description, Language language) {
        return blockingSolution(title, slug, description, Approach.BRUTEFORCE, language);
    }

    public String blockingOptimizedApproach(String title, String slug, String description, Language language) {
        return blockingSolution(title, slug, description, Approach.OPTIMIZED, language);
    }

    public String blockingOptimalApproach(String title, String slug, String description, Language language) {
        return blockingSolution(title, slug, description, Approach.OPTIMAL, language);
    }

    private String blockingSolution(String title, String slug, String description, Approach approach, Language language) {
        String systemPrompt = "You are LeetCode Mentor AI, a world-class DSA coach.";
        String userPrompt = String.format(
                "Provide the full %s solution in %s for:\nTitle: %s (Slug: %s)\nDescription:\n%s\n\n" +
                "Write clean, complete, well-commented code. Explain the structure briefly. State Time and Space complexity.",
                approach.name(), language.name(), title, slug, description);
        return blockChatCompletion(systemPrompt, userPrompt);
    }
}
