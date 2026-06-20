package com.leetcodementor.entity;

import com.leetcodementor.enums.RevisionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "revision_queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevisionQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "problem_slug", nullable = false)
    private String problemSlug;

    @Column(nullable = false)
    private String difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "revision_type", nullable = false)
    private RevisionType revisionType;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(nullable = false)
    private Boolean completed;

    @Column(nullable = false)
    private Integer priority;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
