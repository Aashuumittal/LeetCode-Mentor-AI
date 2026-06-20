package com.leetcodementor.dto.response;

import com.leetcodementor.enums.Approach;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressResponse {
    private UUID id;
    private String problemSlug;
    private Approach approach;
    private Integer hintsUnlocked;
    private Boolean solutionViewed;
    private Boolean questionExplained;
    private Boolean isSolved;
}
