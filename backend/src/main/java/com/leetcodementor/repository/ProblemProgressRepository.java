package com.leetcodementor.repository;

import com.leetcodementor.entity.ProblemProgress;
import com.leetcodementor.entity.User;
import com.leetcodementor.enums.Approach;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProblemProgressRepository extends JpaRepository<ProblemProgress, UUID> {
    List<ProblemProgress> findByUserAndProblemSlug(User user, String problemSlug);
    Optional<ProblemProgress> findByUserAndProblemSlugAndApproach(User user, String problemSlug, Approach approach);
    void deleteByUser(User user);
}
