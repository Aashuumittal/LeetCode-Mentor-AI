package com.leetcodementor.service;

import com.leetcodementor.dto.request.SolveRequest;
import com.leetcodementor.dto.request.UpdateProgressRequest;
import com.leetcodementor.dto.response.ProgressResponse;
import com.leetcodementor.dto.response.SolveResponse;
import com.leetcodementor.entity.ProblemProgress;
import com.leetcodementor.entity.RevisionQueue;
import com.leetcodementor.entity.User;
import com.leetcodementor.entity.UserStats;
import com.leetcodementor.enums.Approach;
import com.leetcodementor.enums.RevisionType;
import com.leetcodementor.repository.ProblemProgressRepository;
import com.leetcodementor.repository.RevisionQueueRepository;
import com.leetcodementor.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgressService {

    private final ProblemProgressRepository problemProgressRepository;
    private final RevisionQueueRepository revisionQueueRepository;
    private final UserStatsRepository userStatsRepository;
    private final StreakService streakService;

    @Transactional(readOnly = true)
    public List<ProgressResponse> getProgress(User user, String problemSlug) {
        List<ProblemProgress> progressList = problemProgressRepository.findByUserAndProblemSlug(user, problemSlug);
        return progressList.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProgressResponse updateProgress(User user, UpdateProgressRequest request) {
        ProblemProgress progress = problemProgressRepository
                .findByUserAndProblemSlugAndApproach(user, request.getProblemSlug(), request.getApproach())
                .orElseGet(() -> ProblemProgress.builder()
                        .user(user)
                        .problemSlug(request.getProblemSlug())
                        .approach(request.getApproach())
                        .isSolved(false)
                        .build());

        progress.setHintsUnlocked(request.getHintsUnlocked());
        progress.setSolutionViewed(request.getSolutionViewed());
        progress.setQuestionExplained(request.getQuestionExplained());

        ProblemProgress saved = problemProgressRepository.save(progress);
        return mapToResponse(saved);
    }

    @Transactional
    @CacheEvict(value = {"revisions", "pending-revisions"}, key = "'revision:' + #user.id + ':*'", allEntries = true)
    public SolveResponse solveProblem(User user, SolveRequest request) {
        // 1. Mark problem as solved for the selected approach
        ProblemProgress progress = problemProgressRepository
                .findByUserAndProblemSlugAndApproach(user, request.getProblemSlug(), request.getApproach())
                .orElseGet(() -> ProblemProgress.builder()
                        .user(user)
                        .problemSlug(request.getProblemSlug())
                        .approach(request.getApproach())
                        .questionExplained(false)
                        .build());

        progress.setHintsUnlocked(Math.max(progress.getHintsUnlocked() == null ? 0 : progress.getHintsUnlocked(), request.getHintsUsed()));
        progress.setSolutionViewed(progress.getSolutionViewed() == null ? request.getSolutionViewed() : progress.getSolutionViewed() || request.getSolutionViewed());
        progress.setIsSolved(true);
        problemProgressRepository.save(progress);

        // 2. Update user stats hints counts
        UserStats stats = userStatsRepository.findByUser(user)
                .orElseGet(() -> UserStats.builder()
                        .user(user)
                        .currentStreak(0)
                        .totalSolved(0)
                        .totalHintsUsed(0)
                        .build());
        stats.setTotalHintsUsed(stats.getTotalHintsUsed() + request.getHintsUsed());
        userStatsRepository.save(stats);

        // 3. Update streak (and totalSolved)
        int currentStreak = streakService.updateStreak(user);

        // 4. Calculate spaced repetition priority
        int priority = calculatePriority(request.getSolutionViewed(), request.getHintsUsed());

        // 5. Schedule revisions: DAY_3 (today + 3 days) and DAY_7 (today + 7 days)
        LocalDate today = LocalDate.now();
        
        RevisionQueue day3Revision = RevisionQueue.builder()
                .user(user)
                .problemSlug(request.getProblemSlug())
                .difficulty(request.getDifficulty())
                .revisionType(RevisionType.DAY_3)
                .scheduledDate(today.plusDays(3))
                .completed(false)
                .priority(priority)
                .build();

        RevisionQueue day7Revision = RevisionQueue.builder()
                .user(user)
                .problemSlug(request.getProblemSlug())
                .difficulty(request.getDifficulty())
                .revisionType(RevisionType.DAY_7)
                .scheduledDate(today.plusDays(7))
                .completed(false)
                .priority(priority)
                .build();

        revisionQueueRepository.save(day3Revision);
        revisionQueueRepository.save(day7Revision);

        return SolveResponse.builder()
                .currentStreak(currentStreak)
                .build();
    }

    private int calculatePriority(boolean solutionViewed, int hintsUsed) {
        if (solutionViewed) {
            return 100;
        }
        return switch (hintsUsed) {
            case 4 -> 80;
            case 3 -> 60;
            case 2 -> 40;
            case 1 -> 20;
            default -> 10;
        };
    }

    private ProgressResponse mapToResponse(ProblemProgress progress) {
        return ProgressResponse.builder()
                .id(progress.getId())
                .problemSlug(progress.getProblemSlug())
                .approach(progress.getApproach())
                .hintsUnlocked(progress.getHintsUnlocked())
                .solutionViewed(progress.getSolutionViewed())
                .questionExplained(progress.getQuestionExplained())
                .isSolved(progress.getIsSolved())
                .build();
    }
}
