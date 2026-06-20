package com.leetcodementor.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevisionQueueResponse {

    private List<RevisionItem> day3;
    private List<RevisionItem> day7;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevisionItem {
        private UUID id;
        private String problemSlug;
        private String difficulty;
        private Integer priority;
        private Boolean completed;
    }
}
