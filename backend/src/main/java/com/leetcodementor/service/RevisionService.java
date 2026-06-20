package com.leetcodementor.service;

import com.leetcodementor.dto.response.PendingRevisionResponse;
import com.leetcodementor.dto.response.RevisionQueueResponse;
import com.leetcodementor.entity.RevisionHistory;
import com.leetcodementor.entity.RevisionQueue;
import com.leetcodementor.entity.User;
import com.leetcodementor.enums.RevisionType;
import com.leetcodementor.exception.ResourceNotFoundException;
import com.leetcodementor.repository.RevisionHistoryRepository;
import com.leetcodementor.repository.RevisionQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RevisionService {

    private final RevisionQueueRepository revisionQueueRepository;
    private final RevisionHistoryRepository revisionHistoryRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = "revisions", key = "'revision:' + #user.id + ':' + T(java.time.LocalDate).now().toString()")
    public RevisionQueueResponse getRevisionQueue(User user) {
        LocalDate today = LocalDate.now();

        List<RevisionQueue> allPending = revisionQueueRepository.findByUserAndCompletedOrderByPriorityDesc(user, false);

        // Filter items due on or before today
        List<RevisionQueueResponse.RevisionItem> day3Items = allPending.stream()
                .filter(r -> r.getRevisionType() == RevisionType.DAY_3 && !r.getScheduledDate().isAfter(today))
                .map(this::mapToItem)
                .collect(Collectors.toList());

        List<RevisionQueueResponse.RevisionItem> day7Items = allPending.stream()
                .filter(r -> r.getRevisionType() == RevisionType.DAY_7 && !r.getScheduledDate().isAfter(today))
                .map(this::mapToItem)
                .collect(Collectors.toList());

        return RevisionQueueResponse.builder()
                .day3(day3Items)
                .day7(day7Items)
                .build();
    }

    @Transactional
    @CacheEvict(value = {"revisions", "pending-revisions"}, key = "'revision:' + #user.id + ':*'", allEntries = true)
    public void completeRevision(User user, UUID revisionQueueId) {
        RevisionQueue revision = revisionQueueRepository.findById(revisionQueueId)
                .orElseThrow(() -> new ResourceNotFoundException("Revision item not found: " + revisionQueueId));

        if (!revision.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Revision item does not belong to the user");
        }

        if (revision.getCompleted()) {
            return; // Already completed
        }

        revision.setCompleted(true);
        revisionQueueRepository.save(revision);

        RevisionHistory history = RevisionHistory.builder()
                .user(user)
                .problemSlug(revision.getProblemSlug())
                .revisionType(revision.getRevisionType())
                .build();
        revisionHistoryRepository.save(history);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "pending-revisions", key = "'pending-revision:' + #user.id")
    public PendingRevisionResponse getPendingRevisions(User user) {
        LocalDate today = LocalDate.now();

        boolean day3HasPending = revisionQueueRepository
                .existsByUserAndCompletedAndRevisionTypeAndScheduledDateLessThanEqual(user, false, RevisionType.DAY_3, today);

        boolean day7HasPending = revisionQueueRepository
                .existsByUserAndCompletedAndRevisionTypeAndScheduledDateLessThanEqual(user, false, RevisionType.DAY_7, today);

        return PendingRevisionResponse.builder()
                .day3HasPending(day3HasPending)
                .day7HasPending(day7HasPending)
                .build();
    }

    private RevisionQueueResponse.RevisionItem mapToItem(RevisionQueue rq) {
        return RevisionQueueResponse.RevisionItem.builder()
                .id(rq.getId())
                .problemSlug(rq.getProblemSlug())
                .difficulty(rq.getDifficulty())
                .priority(rq.getPriority())
                .completed(rq.getCompleted())
                .build();
    }
}
