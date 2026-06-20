package com.leetcodementor.repository;

import com.leetcodementor.entity.CompanyQuestion;
import com.leetcodementor.enums.Company;
import com.leetcodementor.enums.Difficulty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompanyQuestionRepository extends JpaRepository<CompanyQuestion, UUID> {

    @Query("SELECT cq FROM CompanyQuestion cq WHERE " +
           "(:company IS NULL OR cq.company = :company) AND " +
           "(:difficulty IS NULL OR cq.difficulty = :difficulty) AND " +
           "(:topic IS NULL OR LOWER(cq.topic) LIKE LOWER(CONCAT('%', :topic, '%'))) " +
           "ORDER BY cq.frequency DESC")
    List<CompanyQuestion> findByFilters(
            @Param("company") Company company,
            @Param("difficulty") Difficulty difficulty,
            @Param("topic") String topic
    );

    @Query("SELECT cq FROM CompanyQuestion cq WHERE LOWER(cq.questionTitle) = LOWER(:title) OR LOWER(cq.leetcodeUrl) LIKE LOWER(CONCAT('%', :slug, '%'))")
    List<CompanyQuestion> findByQuestionTitleOrSlug(
            @Param("title") String title,
            @Param("slug") String slug
    );
}
