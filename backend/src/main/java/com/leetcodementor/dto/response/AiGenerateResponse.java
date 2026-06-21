package com.leetcodementor.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiGenerateResponse {
    private String status; // SUCCESS, PENDING, FAILED
    private String content;
}
