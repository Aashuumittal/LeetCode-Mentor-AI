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
import com.leetcodementor.enums.Language;
import com.leetcodementor.repository.ProblemProgressRepository;
import com.leetcodementor.repository.RevisionQueueRepository;
import com.leetcodementor.repository.UserStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgressServiceTest {

    @Mock
    private ProblemProgressRepository problemProgressRepository;

    @Mock
    private RevisionQueueRepository revisionQueueRepository;

    @Mock
    private UserStatsRepository userStatsRepository;

    @Mock
    private StreakService streakService;

    @InjectMocks
    private ProgressService progressService;

    private User user;
    private UserStats userStats;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .build();

        userStats = UserStats.builder()
                .user(user)
                .currentStreak(5)
                .totalSolved(10)
                .totalHintsUsed(2)
                .build();
    }

    @Test
    void getProgress_ReturnsList() {
        ProblemProgress progress = ProblemProgress.builder()
                .problemSlug("two-sum")
                .approach(Approach.BRUTEFORCE)
                .hintsUnlocked(1)
                .solutionViewed(false)
                .questionExplained(true)
                .isSolved(true)
                .build();

        when(problemProgressRepository.findByUserAndProblemSlug(user, "two-sum"))
                .thenReturn(List.of(progress));

        List<ProgressResponse> result = progressService.getProgress(user, "two-sum");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(Approach.BRUTEFORCE, result.get(0).getApproach());
    }

    @Test
    void updateProgress_Success() {
        UpdateProgressRequest request = UpdateProgressRequest.builder()
                .problemSlug("two-sum")
                .approach(Approach.OPTIMAL)
                .hintsUnlocked(2)
                .solutionViewed(true)
                .questionExplained(true)
                .build();

        ProblemProgress mockProgress = ProblemProgress.builder()
                .user(user)
                .problemSlug("two-sum")
                .approach(Approach.OPTIMAL)
                .hintsUnlocked(0)
                .solutionViewed(false)
                .questionExplained(false)
                .isSolved(false)
                .build();

        when(problemProgressRepository.findByUserAndProblemSlugAndApproach(user, "two-sum", Approach.OPTIMAL))
                .thenReturn(Optional.of(mockProgress));
        when(problemProgressRepository.save(any(ProblemProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProgressResponse response = progressService.updateProgress(user, request);

        assertNotNull(response);
        assertEquals(2, response.getHintsUnlocked());
        assertTrue(response.getSolutionViewed());
        assertTrue(response.getQuestionExplained());
    }

    @Test
    void solveProblem_Success() {
        SolveRequest request = SolveRequest.builder()
                .problemSlug("two-sum")
                .difficulty("EASY")
                .language(Language.JAVA)
                .approach(Approach.OPTIMIZED)
                .hintsUsed(2)
                .solutionViewed(false)
                .build();

        ProblemProgress mockProgress = ProblemProgress.builder()
                .user(user)
                .problemSlug("two-sum")
                .approach(Approach.OPTIMIZED)
                .hintsUnlocked(0)
                .solutionViewed(false)
                .isSolved(false)
                .build();

        when(problemProgressRepository.findByUserAndProblemSlugAndApproach(user, "two-sum", Approach.OPTIMIZED))
                .thenReturn(Optional.of(mockProgress));
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(userStats));
        when(streakService.updateStreak(user)).thenReturn(6);

        SolveResponse response = progressService.solveProblem(user, request);

        assertNotNull(response);
        assertEquals(6, response.getCurrentStreak());

        // Verify total hints was updated
        assertEquals(4, userStats.getTotalHintsUsed()); // 2 original + 2 used

        // Capture scheduled revisions and verify priority computation
        ArgumentCaptor<RevisionQueue> queueCaptor = ArgumentCaptor.forClass(RevisionQueue.class);
        verify(revisionQueueRepository, times(2)).save(queueCaptor.capture());

        List<RevisionQueue> scheduledRevisions = queueCaptor.getAllValues();
        assertEquals(2, scheduledRevisions.size());
        
        // Priority for 2 hints used and solution not viewed is 40
        assertEquals(40, scheduledRevisions.get(0).getPriority());
        assertEquals(40, scheduledRevisions.get(1).getPriority());
    }
}
