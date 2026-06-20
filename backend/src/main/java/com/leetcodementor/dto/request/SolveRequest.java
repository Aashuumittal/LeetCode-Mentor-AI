package com.leetcodementor.dto.request;

import com.leetcodementor.enums.Approach;
import com.leetcodementor.enums.Language;
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
public class SolveRequest {

    @NotBlank(message = "Problem slug is required")
    private String problemSlug;

    @NotBlank(message = "Difficulty is required")
    private String difficulty;

    @NotNull(message = "Language is required")
    private Language language;

    @NotNull(message = "Approach is required")
    private Approach approach;

    @NotNull(message = "hintsUsed is required")
    @Min(value = 0, message = "hintsUsed must be at least 0")
    private Integer hintsUsed;

    @NotNull(message = "solutionViewed is required")
    private Boolean solutionViewed;
}
