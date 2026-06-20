package com.leetcodementor.scheduler;

import com.leetcodementor.entity.RevisionQueue;
import com.leetcodementor.repository.RevisionQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RevisionMissScheduler {

    private final RevisionQueueRepository revisionQueueRepository;
    private final CacheManager cacheManager;

    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void rescheduleMissedRevisions() {
        log.info("Starting revision miss rescheduling job...");
        LocalDate today = LocalDate.now();

        List<RevisionQueue> missedRevisions = revisionQueueRepository.findByCompletedAndScheduledDateBefore(false, today);

        for (RevisionQueue revision : missedRevisions) {
            log.info("Rescheduling missed revision {} for user {} from {} to {}", 
                    revision.getProblemSlug(), revision.getUser().getEmail(), revision.getScheduledDate(), today);
            revision.setScheduledDate(today);
            revisionQueueRepository.save(revision);
        }

        // Invalidate all revisions caches
        Cache revisionsCache = cacheManager.getCache("revisions");
        if (revisionsCache != null) {
            revisionsCache.clear();
            log.info("Evicted all entries in 'revisions' cache.");
        }

        log.info("Revision miss rescheduling job completed. Rescheduled {} items.", missedRevisions.size());
    }
}
