package com.leetcodementor.entity;

import com.leetcodementor.enums.RevisionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "revision_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevisionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "problem_slug", nullable = false)
    private String problemSlug;

    @CreationTimestamp
    @Column(name = "completed_at", nullable = false, updatable = false)
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revision_type", nullable = false)
    private RevisionType revisionType;
}
