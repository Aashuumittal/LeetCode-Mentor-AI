package com.leetcodementor.dto.response;

import com.leetcodementor.enums.Company;
import com.leetcodementor.enums.Difficulty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyQuestionResponse {
    private UUID id;
    private Company company;
    private String questionTitle;
    private Difficulty difficulty;
    private String topic;
    private Integer frequency;
    private String leetcodeUrl;
}
