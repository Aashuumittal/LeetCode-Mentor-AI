package com.leetcodementor.dto.request;

import com.leetcodementor.enums.Approach;
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
public class UpdateProgressRequest {

    @NotBlank(message = "Problem slug is required")
    private String problemSlug;

    @NotNull(message = "Approach is required")
    private Approach approach;

    @NotNull(message = "hintsUnlocked is required")
    private Integer hintsUnlocked;

    @NotNull(message = "solutionViewed is required")
    private Boolean solutionViewed;

    @NotNull(message = "questionExplained is required")
    private Boolean questionExplained;
}
