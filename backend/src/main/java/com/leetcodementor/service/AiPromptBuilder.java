package com.leetcodementor.service;

import com.leetcodementor.enums.Approach;
import com.leetcodementor.enums.ContentType;
import com.leetcodementor.enums.Language;

public class AiPromptBuilder {

    public static String getSystemPrompt(ContentType contentType) {
        if (contentType == ContentType.EXPLAIN) {
            return "You are LeetCode Mentor AI, a world-class Data Structures and Algorithms (DSA) coach. " +
                    "Help the user learn DSA through structured guidance. Do NOT write or output any code implementations.";
        } else if (contentType == ContentType.SOLUTION) {
            return "You are LeetCode Mentor AI, a world-class Data Structures and Algorithms (DSA) coach.";
        } else { // Hints
            return "You are LeetCode Mentor AI, a world-class Data Structures and Algorithms (DSA) coach. " +
                    "Help the user learn DSA through guidance rather than giving code answers immediately.";
        }
    }

    public static String getUserPrompt(String title, String slug, String description,
                                      Approach approach, ContentType contentType, Language language) {
        return switch (contentType) {
            case EXPLAIN -> String.format(
                    "Explain the following LeetCode problem under 150 words total. " +
                            "You MUST NOT discuss any approaches (bruteforce, optimized, optimal, etc.), time/space complexity analysis, or long essays. " +
                            "You MUST structure your response with the following markdown headers exactly (in this order):\n" +
                            "## Problem Summary\n" +
                            "[2-3 lines maximum explaining the problem clearly]\n\n" +
                            "## Example\n" +
                            "[Show one simple example from the problem, explaining it step by step in max 5 lines]\n\n" +
                            "## Key Observations\n" +
                            "[Exactly 3 short bullet points only]\n\n" +
                            "## Companies Asked\n" +
                            "Google\n" +
                            "Amazon\n" +
                            "Microsoft\n" +
                            "[List these three companies exactly, with their estimated frequency or occurrences, but keep it extremely brief]\n\n" +
                            "## Interview Priority\n" +
                            "[High, Medium, or Low only]\n\n" +
                            "Title: %s (Slug: %s)\n" +
                            "Description:\n%s\n\n" +
                            "Total word count MUST be under 150 words.",
                    title, slug, description
            );
            case HINT_1 -> buildHintPrompt(title, description, approach, 1);
            case HINT_2 -> buildHintPrompt(title, description, approach, 2);
            case HINT_3 -> buildHintPrompt(title, description, approach, 3);
            case HINT_4 -> buildHintPrompt(title, description, approach, 4);
            case SOLUTION -> String.format(
                    "Provide the Solution for the %s approach in %s for the following problem:\n" +
                            "Title: %s (Slug: %s)\n" +
                            "Description:\n%s\n\n" +
                            "You MUST return clean markdown. You MUST structure your response with the following headers exactly (in this order) and nothing else:\n" +
                            "## Idea\n" +
                            "[A very short and concise explanation of the idea]\n\n" +
                            "## %s Code\n" +
                            "[Complete and clean implementation in a markdown code block]\n\n" +
                            "## Time Complexity\n" +
                            "[Provide the time complexity class like O(N) with a 1-line justification]\n\n" +
                            "## Space Complexity\n" +
                            "[Provide the space complexity class like O(1) with a 1-line justification]\n\n" +
                            "Do NOT include any essays, long discussions, or other headers.",
                    approach.name(), language.name(), title, slug, description, language.name()
            );
        };
    }

    private static String buildHintPrompt(String title, String description, Approach approach, int hintLevel) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Provide Hint %d for the %s approach of the following problem:\n", hintLevel, approach.name()))
                .append("Title: ").append(title).append("\n")
                .append("Description:\n").append(description).append("\n\n");

        switch (hintLevel) {
            case 1 -> sb.append("Guidelines:\n")
                    .append("1. Provide MAXIMUM 2 lines of text.\n")
                    .append("2. Give a high-level conceptual direction only. DO NOT discuss specific formulas, algorithms, data structures, or code.");
            case 2 -> sb.append("Guidelines:\n")
                    .append("1. Provide MAXIMUM 3 lines of text.\n")
                    .append("2. Reveal a small, important observation. DO NOT reveal the algorithm or full approach.");
            case 3 -> sb.append("Guidelines:\n")
                    .append("1. Provide MAXIMUM 5 lines of text.\n")
                    .append("2. Reveal the main implementation idea or core logic. DO NOT write code.");
            case 4 -> sb.append("Guidelines:\n")
                    .append("1. Provide MAXIMUM 8 lines of text.\n")
                    .append("2. Reveal the full algorithm/logic steps and highlight important edge cases (e.g. overflow, boundary checks). DO NOT write code.");
            default -> throw new IllegalArgumentException("Invalid hint level: " + hintLevel);
        }
        return sb.toString();
    }
}
