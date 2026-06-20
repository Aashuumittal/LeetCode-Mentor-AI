package com.leetcodementor.repository;

import com.leetcodementor.entity.RevisionQueue;
import com.leetcodementor.entity.User;
import com.leetcodementor.enums.RevisionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface RevisionQueueRepository extends JpaRepository<RevisionQueue, UUID> {
    List<RevisionQueue> findByUserAndCompletedOrderByPriorityDesc(User user, boolean completed);
    List<RevisionQueue> findByUserAndCompletedAndRevisionTypeOrderByPriorityDesc(User user, boolean completed, RevisionType revisionType);
    boolean existsByUserAndCompletedAndRevisionTypeAndScheduledDateLessThanEqual(User user, boolean completed, RevisionType revisionType, LocalDate date);
    List<RevisionQueue> findByCompletedAndScheduledDateBefore(boolean completed, LocalDate date);
    void deleteByUser(User user);
}
