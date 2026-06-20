package com.leetcodementor.repository;

import com.leetcodementor.entity.User;
import com.leetcodementor.entity.UserStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserStatsRepository extends JpaRepository<UserStats, UUID> {
    Optional<UserStats> findByUser(User user);
    void deleteByUser(User user);

    @Query("SELECT us FROM UserStats us WHERE us.lastSolvedDate IS NULL OR us.lastSolvedDate < :date")
    List<UserStats> findUsersForStreakReset(@Param("date") LocalDate date);
}
