package com.leetcodementor.entity;

import com.leetcodementor.enums.Approach;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "problem_progress",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "problem_slug", "approach"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "problem_slug", nullable = false)
    private String problemSlug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Approach approach;

    @Column(name = "hints_unlocked", nullable = false)
    private Integer hintsUnlocked;

    @Column(name = "solution_viewed", nullable = false)
    private Boolean solutionViewed;

    @Column(name = "question_explained", nullable = false)
    private Boolean questionExplained;

    @Column(name = "is_solved", nullable = false)
    private Boolean isSolved;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
