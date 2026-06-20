package com.leetcodementor.dto.request;

import com.leetcodementor.enums.Company;
import com.leetcodementor.enums.Difficulty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyQuestionImportRequest {

    @NotNull(message = "Company is required")
    private Company company;

    @NotBlank(message = "Question title is required")
    private String questionTitle;

    @NotNull(message = "Difficulty is required")
    private Difficulty difficulty;

    @NotBlank(message = "Topic is required")
    private String topic;

    @NotNull(message = "Frequency is required")
    @Min(value = 0, message = "Frequency must be at least 0")
    private Integer frequency;

    @NotBlank(message = "LeetCode URL is required")
    private String leetcodeUrl;
}
