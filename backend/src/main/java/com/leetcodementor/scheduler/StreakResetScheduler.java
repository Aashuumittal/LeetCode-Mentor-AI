package com.leetcodementor.scheduler;

import com.leetcodementor.entity.UserStats;
import com.leetcodementor.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StreakResetScheduler {

    private final UserStatsRepository userStatsRepository;

    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void resetStreaks() {
        log.info("Starting streak reset job...");
        LocalDate yesterday = LocalDate.now().minusDays(1);

        List<UserStats> statsToReset = userStatsRepository.findUsersForStreakReset(yesterday);
        
        for (UserStats stats : statsToReset) {
            if (stats.getCurrentStreak() > 0) {
                log.info("Resetting streak for user: {} (was: {})", stats.getUser().getEmail(), stats.getCurrentStreak());
                stats.setCurrentStreak(0);
                userStatsRepository.save(stats);
            }
        }
        
        log.info("Streak reset job completed. Processed {} profiles.", statsToReset.size());
    }
}
