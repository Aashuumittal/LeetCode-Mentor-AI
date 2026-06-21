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
            case HINT_1 -> buildHintPrompt(title, description, approach, 1);
            case HINT_2 -> buildHintPrompt(title, description, approach, 2);
            case HINT_3 -> buildHintPrompt(title, description, approach, 3);
            case HINT_4 -> buildHintPrompt(title, description, approach, 4);
            case SOLUTION -> String.format(
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
        };
    }

    private static String buildHintPrompt(String title, String description, Approach approach, int hintLevel) {
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
        return sb.toString();
    }
}
