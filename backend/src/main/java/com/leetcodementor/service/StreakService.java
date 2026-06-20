package com.leetcodementor.service;

import com.leetcodementor.entity.User;
import com.leetcodementor.entity.UserStats;
import com.leetcodementor.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreakService {

    private final UserStatsRepository userStatsRepository;

    @Transactional
    public int updateStreak(User user) {
        UserStats stats = userStatsRepository.findByUser(user)
                .orElseGet(() -> UserStats.builder()
                        .user(user)
                        .currentStreak(0)
                        .totalSolved(0)
                        .totalHintsUsed(0)
                        .build());

        LocalDate today = LocalDate.now();
        LocalDate lastSolved = stats.getLastSolvedDate();

        if (lastSolved == null) {
            // First time solving
            stats.setCurrentStreak(1);
        } else if (lastSolved.equals(today)) {
            // Already solved today, keep current streak unchanged
            log.info("User {} already solved a problem today. Streak remains: {}", user.getEmail(), stats.getCurrentStreak());
        } else if (lastSolved.equals(today.minusDays(1))) {
            // Solved yesterday, increment streak
            stats.setCurrentStreak(stats.getCurrentStreak() + 1);
        } else {
            // Streak broken, reset to 1
            stats.setCurrentStreak(1);
        }

        stats.setLastSolvedDate(today);
        stats.setTotalSolved(stats.getTotalSolved() + 1);
        userStatsRepository.save(stats);

        return stats.getCurrentStreak();
    }
}
