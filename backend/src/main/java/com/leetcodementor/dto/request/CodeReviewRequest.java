package com.leetcodementor.dto.request;

import com.leetcodementor.enums.Language;
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
public class CodeReviewRequest {

    @NotBlank(message = "Code snippet is required")
    private String code;

    @NotNull(message = "Language is required")
    private Language language;

    private String problemSlug;
}
