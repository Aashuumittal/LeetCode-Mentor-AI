package com.leetcodementor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetcodementor.dto.request.CompanyQuestionImportRequest;
import com.leetcodementor.dto.response.CompanyQuestionResponse;
import com.leetcodementor.entity.CompanyQuestion;
import com.leetcodementor.enums.Company;
import com.leetcodementor.enums.Difficulty;
import com.leetcodementor.repository.CompanyQuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyQuestionService {

    private final CompanyQuestionRepository companyQuestionRepository;
    private final GroqService groqService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String COMPANY_FREQ_CACHE_PREFIX = "company-freq:";

    @Transactional(readOnly = true)
    public List<CompanyQuestionResponse> getCompanyQuestions(Company company, Difficulty difficulty, String topic) {
        List<CompanyQuestion> questions = companyQuestionRepository.findByFilters(company, difficulty, topic);
        return questions.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void importCompanyQuestions(List<CompanyQuestionImportRequest> requests) {
        List<CompanyQuestion> entities = requests.stream()
                .map(req -> CompanyQuestion.builder()
                        .company(req.getCompany())
                        .questionTitle(req.getQuestionTitle())
                        .difficulty(req.getDifficulty())
                        .topic(req.getTopic())
                        .frequency(req.getFrequency())
                        .leetcodeUrl(req.getLeetcodeUrl())
                        .build())
                .collect(Collectors.toList());

        companyQuestionRepository.saveAll(entities);
    }

    @Transactional(readOnly = true)
    public List<CompanyQuestionResponse> getCompanyFrequenciesForProblem(String title, String slug) {
        List<CompanyQuestion> questions = companyQuestionRepository.findByQuestionTitleOrSlug(title, slug);
        return questions.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * DB-first -> Redis cache -> Groq AI fallback.
     * 1. If the DB has real curated rows for this problem, return them.
     * 2. Check Redis for a previously generated AI result (TTL = 30 days).
     * 3. Call Groq, parse + validate the JSON, cache it, then return.
     */
    public List<CompanyQuestionResponse> getAiCompanyFrequenciesForProblem(
            String title, String slug, String description) {

        // 1. DB first — curated data always wins
        List<CompanyQuestion> dbResults = companyQuestionRepository.findByQuestionTitleOrSlug(title, slug);
        if (!dbResults.isEmpty()) {
            log.info("Returning DB company frequencies for problem: {}", title);
            return dbResults.stream().map(this::mapToResponse).collect(Collectors.toList());
        }

        // 2. Redis cache
        String cacheKey = COMPANY_FREQ_CACHE_PREFIX + slug;
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("Returning cached AI company frequencies for problem: {}", slug);
                List<Map<String, Object>> cachedList = objectMapper.convertValue(
                        cached, new TypeReference<List<Map<String, Object>>>() {});
                return mapAiResponseToCompanyQuestionResponses(cachedList, title);
            }
        } catch (Exception e) {
            log.warn("Redis read failed for company freq cache key {}: {}", cacheKey, e.getMessage());
        }

        // 3. Groq AI
        try {
            log.info("Calling Groq for company frequencies: {}", title);
            String rawJson = groqService.getCompanyFrequencies(title, slug, description);

            // Strip any accidental markdown fences
            String cleaned = rawJson.trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            List<Map<String, Object>> parsed = objectMapper.readValue(
                    cleaned, new TypeReference<List<Map<String, Object>>>() {});

            // Validate: keep only entries whose company string matches our enum
            Set<String> validCompanies = Arrays.stream(Company.values())
                    .map(Enum::name)
                    .collect(Collectors.toSet());

            List<Map<String, Object>> validated = parsed.stream()
                    .filter(m -> m.containsKey("company") && m.containsKey("frequency"))
                    .filter(m -> validCompanies.contains(
                            String.valueOf(m.get("company")).toUpperCase()))
                    .collect(Collectors.toList());

            if (!validated.isEmpty()) {
                redisTemplate.opsForValue().set(cacheKey, validated, Duration.ofDays(30));
            }

            return mapAiResponseToCompanyQuestionResponses(validated, title);

        } catch (Exception e) {
            log.error("Groq company frequency call failed for {}: {}", title, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<CompanyQuestionResponse> mapAiResponseToCompanyQuestionResponses(
            List<Map<String, Object>> items, String title) {
        return items.stream().map(item -> {
            String companyStr = String.valueOf(item.get("company")).toUpperCase();
            int freq = item.get("frequency") instanceof Number
                    ? ((Number) item.get("frequency")).intValue()
                    : Integer.parseInt(String.valueOf(item.get("frequency")));
            return CompanyQuestionResponse.builder()
                    .company(Company.valueOf(companyStr))
                    .questionTitle(title)
                    .frequency(freq)
                    .build();
        }).collect(Collectors.toList());
    }

    private CompanyQuestionResponse mapToResponse(CompanyQuestion cq) {
        return CompanyQuestionResponse.builder()
                .id(cq.getId())
                .company(cq.getCompany())
                .questionTitle(cq.getQuestionTitle())
                .difficulty(cq.getDifficulty())
                .topic(cq.getTopic())
                .frequency(cq.getFrequency())
                .leetcodeUrl(cq.getLeetcodeUrl())
                .build();
    }
}