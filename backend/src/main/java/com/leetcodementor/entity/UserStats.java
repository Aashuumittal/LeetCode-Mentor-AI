package com.leetcodementor.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "user_stats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStats {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "current_streak", nullable = false)
    private Integer currentStreak;

    @Column(name = "last_solved_date")
    private LocalDate lastSolvedDate;

    @Column(name = "total_solved", nullable = false)
    private Integer totalSolved;

    @Column(name = "total_hints_used", nullable = false)
    private Integer totalHintsUsed;
}
