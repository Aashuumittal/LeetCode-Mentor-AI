package com.leetcodementor.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingRevisionResponse {
    private boolean day3HasPending;
    private boolean day7HasPending;
}
