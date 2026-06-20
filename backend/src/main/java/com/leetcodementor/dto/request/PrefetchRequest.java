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
public class PrefetchRequest {

    @NotBlank(message = "Problem slug is required")
    private String problemSlug;

    @NotBlank(message = "Problem title is required")
    private String problemTitle;

    @NotBlank(message = "Problem description is required")
    private String problemDescription;

    @NotNull(message = "Language is required")
    private Language language;
}
