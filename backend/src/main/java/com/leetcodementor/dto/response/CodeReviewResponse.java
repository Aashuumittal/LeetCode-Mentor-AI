package com.leetcodementor.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeReviewResponse {

    private List<SyntaxIssue> syntaxIssues;
    private List<LogicIssue> logicIssues;
    private List<Optimization> optimizations;
    private BetterApproach betterApproach;
    private Complexity timeComplexity;
    private Complexity spaceComplexity;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyntaxIssue {
        private int line;
        private String issue;
        private String fix;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogicIssue {
        private String description;
        private String suggestion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Optimization {
        private String description;
        private String improvedCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BetterApproach {
        private String description;
        private String example;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Complexity {
        private String current;
        private String optimized;
        private String explanation;
    }
}
