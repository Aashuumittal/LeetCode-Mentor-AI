package com.leetcodementor.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NewDayScheduler {

    private final CacheManager cacheManager;

    @Scheduled(cron = "0 0 6 * * *")
    public void resetPendingNotificationCache() {
        log.info("Starting new day reset job...");

        Cache pendingRevisionsCache = cacheManager.getCache("pending-revisions");
        if (pendingRevisionsCache != null) {
            pendingRevisionsCache.clear();
            log.info("Evicted all entries in 'pending-revisions' cache.");
        }

        log.info("New day reset job completed.");
    }
}
