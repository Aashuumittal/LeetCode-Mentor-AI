package com.leetcodementor.config;

import com.leetcodementor.entity.CompanyQuestion;
import com.leetcodementor.enums.Company;
import com.leetcodementor.enums.Difficulty;
import com.leetcodementor.repository.CompanyQuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final CompanyQuestionRepository companyQuestionRepository;

    @Override
    public void run(String... args) throws Exception {
        if (companyQuestionRepository.count() == 0) {
            log.info("Seeding initial company questions data...");
            List<CompanyQuestion> seedData = Arrays.asList(
                    // Two Sum
                    CompanyQuestion.builder()
                            .company(Company.GOOGLE)
                            .questionTitle("Two Sum")
                            .difficulty(Difficulty.EASY)
                            .topic("Arrays & Hashing")
                            .frequency(24)
                            .leetcodeUrl("https://leetcode.com/problems/two-sum")
                            .build(),
                    CompanyQuestion.builder()
                            .company(Company.AMAZON)
                            .questionTitle("Two Sum")
                            .difficulty(Difficulty.EASY)
                            .topic("Arrays & Hashing")
                            .frequency(18)
                            .leetcodeUrl("https://leetcode.com/problems/two-sum")
                            .build(),
                    CompanyQuestion.builder()
                            .company(Company.MICROSOFT)
                            .questionTitle("Two Sum")
                            .difficulty(Difficulty.EASY)
                            .topic("Arrays & Hashing")
                            .frequency(12)
                            .leetcodeUrl("https://leetcode.com/problems/two-sum")
                            .build(),

                    // Median of Two Sorted Arrays
                    CompanyQuestion.builder()
                            .company(Company.GOOGLE)
                            .questionTitle("Median of Two Sorted Arrays")
                            .difficulty(Difficulty.HARD)
                            .topic("Binary Search")
                            .frequency(18)
                            .leetcodeUrl("https://leetcode.com/problems/median-of-two-sorted-arrays")
                            .build(),
                    CompanyQuestion.builder()
                            .company(Company.AMAZON)
                            .questionTitle("Median of Two Sorted Arrays")
                            .difficulty(Difficulty.HARD)
                            .topic("Binary Search")
                            .frequency(14)
                            .leetcodeUrl("https://leetcode.com/problems/median-of-two-sorted-arrays")
                            .build(),
                    CompanyQuestion.builder()
                            .company(Company.MICROSOFT)
                            .questionTitle("Median of Two Sorted Arrays")
                            .difficulty(Difficulty.HARD)
                            .topic("Binary Search")
                            .frequency(10)
                            .leetcodeUrl("https://leetcode.com/problems/median-of-two-sorted-arrays")
                            .build()
            );
            companyQuestionRepository.saveAll(seedData);
            log.info("Successfully seeded {} company questions.", seedData.size());
        } else {
            log.info("Company questions table already contains data, skipping seed.");
        }
    }
}
