package com.leetcodementor.entity;

import com.leetcodementor.enums.Company;
import com.leetcodementor.enums.Difficulty;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "company_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Company company;

    @Column(name = "question_title", nullable = false)
    private String questionTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private Integer frequency;

    @Column(name = "leetcode_url", nullable = false)
    private String leetcodeUrl;
}
