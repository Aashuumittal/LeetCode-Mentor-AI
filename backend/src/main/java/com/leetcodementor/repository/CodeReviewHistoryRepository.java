package com.leetcodementor.repository;

import com.leetcodementor.entity.CodeReviewHistory;
import com.leetcodementor.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CodeReviewHistoryRepository extends JpaRepository<CodeReviewHistory, UUID> {
    List<CodeReviewHistory> findByUserOrderByCreatedAtDesc(User user);
    void deleteByUser(User user);
}
