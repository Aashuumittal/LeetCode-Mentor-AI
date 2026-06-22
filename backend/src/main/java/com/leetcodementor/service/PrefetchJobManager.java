package com.leetcodementor.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class PrefetchJobManager {

    private final AtomicReference<PrefetchJob> currentJob = new AtomicReference<>();

    public static class PrefetchJob {
        private final String slug;
        private final List<Future<?>> futures = new ArrayList<>();
        private volatile boolean cancelled = false;

        public PrefetchJob(String slug) {
            this.slug = slug;
        }

        public synchronized void addFuture(Future<?> future) {
            if (cancelled) {
                future.cancel(true);
            } else {
                futures.add(future);
            }
        }

        public synchronized void cancel() {
            cancelled = true;
            for (Future<?> f : futures) {
                f.cancel(true);
            }
            futures.clear();
        }

        public boolean isCancelled() {
            return cancelled || Thread.currentThread().isInterrupted();
        }

        public String getSlug() {
            return slug;
        }
    }

    public PrefetchJob startNewJob(String slug) {
        PrefetchJob newJob = new PrefetchJob(slug);
        PrefetchJob oldJob = currentJob.getAndSet(newJob);
        if (oldJob != null && !oldJob.getSlug().equals(slug)) {
            log.info("Cancelling previous prefetch job for slug: {}", oldJob.getSlug());
            oldJob.cancel();
        }
        return newJob;
    }

    public String getCurrentProblemSlug() {
        PrefetchJob job = currentJob.get();
        return job != null ? job.getSlug() : null;
    }
}
