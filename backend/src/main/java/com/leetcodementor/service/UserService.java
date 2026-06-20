package com.leetcodementor.service;

import com.leetcodementor.dto.response.UserResponse;
import com.leetcodementor.entity.User;
import com.leetcodementor.entity.UserStats;
import com.leetcodementor.exception.ResourceNotFoundException;
import com.leetcodementor.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;
    private final ProblemProgressRepository problemProgressRepository;
    private final RevisionQueueRepository revisionQueueRepository;
    private final RevisionHistoryRepository revisionHistoryRepository;
    private final CodeReviewHistoryRepository codeReviewHistoryRepository;

    @Transactional(readOnly = true)
    public UserResponse getUserProfileAndStats(User user) {
        UserStats stats = userStatsRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Stats not found for user: " + user.getEmail()));

        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .preferredLanguage(user.getPreferredLanguage())
                .currentStreak(stats.getCurrentStreak())
                .lastSolvedDate(stats.getLastSolvedDate())
                .totalSolved(stats.getTotalSolved())
                .totalHintsUsed(stats.getTotalHintsUsed())
                .build();
    }

    @Transactional
    public void resetAllProgress(User user) {
        // Clear all progress data for this user
        problemProgressRepository.deleteByUser(user);
        revisionQueueRepository.deleteByUser(user);
        revisionHistoryRepository.deleteByUser(user);
        codeReviewHistoryRepository.deleteByUser(user);

        // Reset stats
        UserStats stats = userStatsRepository.findByUser(user)
                .orElseGet(() -> UserStats.builder().user(user).build());

        stats.setCurrentStreak(0);
        stats.setLastSolvedDate(null);
        stats.setTotalSolved(0);
        stats.setTotalHintsUsed(0);
        userStatsRepository.save(stats);
    }
}
