package com.leetcodementor.dto.response;

import com.leetcodementor.enums.Language;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private String name;
    private String email;
    private Language preferredLanguage;
    private Integer currentStreak;
    private LocalDate lastSolvedDate;
    private Integer totalSolved;
    private Integer totalHintsUsed;
}
