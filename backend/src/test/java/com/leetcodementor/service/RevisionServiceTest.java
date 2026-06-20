package com.leetcodementor.service;

import com.leetcodementor.dto.response.PendingRevisionResponse;
import com.leetcodementor.dto.response.RevisionQueueResponse;
import com.leetcodementor.entity.RevisionHistory;
import com.leetcodementor.entity.RevisionQueue;
import com.leetcodementor.entity.User;
import com.leetcodementor.enums.RevisionType;
import com.leetcodementor.repository.RevisionHistoryRepository;
import com.leetcodementor.repository.RevisionQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RevisionServiceTest {

    @Mock
    private RevisionQueueRepository revisionQueueRepository;

    @Mock
    private RevisionHistoryRepository revisionHistoryRepository;

    @InjectMocks
    private RevisionService revisionService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .build();
    }

    @Test
    void getRevisionQueue_ReturnsQueuesCorrectly() {
        LocalDate today = LocalDate.now();

        // 3 items: 1 Day 3 due today, 1 Day 7 due yesterday, 1 Day 3 due tomorrow (should be filtered out)
        RevisionQueue item1 = RevisionQueue.builder()
                .id(UUID.randomUUID())
                .user(user)
                .problemSlug("two-sum")
                .difficulty("EASY")
                .revisionType(RevisionType.DAY_3)
                .scheduledDate(today)
                .completed(false)
                .priority(50)
                .build();

        RevisionQueue item2 = RevisionQueue.builder()
                .id(UUID.randomUUID())
                .user(user)
                .problemSlug("add-two-numbers")
                .difficulty("MEDIUM")
                .revisionType(RevisionType.DAY_7)
                .scheduledDate(today.minusDays(1))
                .completed(false)
                .priority(80)
                .build();

        RevisionQueue item3 = RevisionQueue.builder()
                .id(UUID.randomUUID())
                .user(user)
                .problemSlug("longest-substring")
                .difficulty("MEDIUM")
                .revisionType(RevisionType.DAY_3)
                .scheduledDate(today.plusDays(1)) // future item
                .completed(false)
                .priority(30)
                .build();

        when(revisionQueueRepository.findByUserAndCompletedOrderByPriorityDesc(user, false))
                .thenReturn(List.of(item2, item1, item3)); // sorted by priority desc in mock

        RevisionQueueResponse response = revisionService.getRevisionQueue(user);

        assertNotNull(response);
        assertEquals(1, response.getDay3().size());
        assertEquals("two-sum", response.getDay3().get(0).getProblemSlug());

        assertEquals(1, response.getDay7().size());
        assertEquals("add-two-numbers", response.getDay7().get(0).getProblemSlug());
    }

    @Test
    void completeRevision_Success() {
        UUID queueId = UUID.randomUUID();
        RevisionQueue revision = RevisionQueue.builder()
                .id(queueId)
                .user(user)
                .problemSlug("two-sum")
                .revisionType(RevisionType.DAY_3)
                .completed(false)
                .build();

        when(revisionQueueRepository.findById(queueId)).thenReturn(Optional.of(revision));

        revisionService.completeRevision(user, queueId);

        assertTrue(revision.getCompleted());
        verify(revisionQueueRepository).save(revision);
        verify(revisionHistoryRepository).save(any(RevisionHistory.class));
    }

    @Test
    void getPendingRevisions_ReturnsTrueIfPending() {
        LocalDate today = LocalDate.now();
        when(revisionQueueRepository.existsByUserAndCompletedAndRevisionTypeAndScheduledDateLessThanEqual(
                user, false, RevisionType.DAY_3, today)).thenReturn(true);
        when(revisionQueueRepository.existsByUserAndCompletedAndRevisionTypeAndScheduledDateLessThanEqual(
                user, false, RevisionType.DAY_7, today)).thenReturn(false);

        PendingRevisionResponse response = revisionService.getPendingRevisions(user);

        assertNotNull(response);
        assertTrue(response.isDay3HasPending());
        assertFalse(response.isDay7HasPending());
    }
}
