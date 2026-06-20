package com.leetcodementor.repository;

import com.leetcodementor.entity.RevisionHistory;
import com.leetcodementor.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RevisionHistoryRepository extends JpaRepository<RevisionHistory, UUID> {
    void deleteByUser(User user);
}
