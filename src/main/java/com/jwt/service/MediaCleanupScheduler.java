package com.jwt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class MediaCleanupScheduler {
    private final MediaLifecycleService mediaLifecycleService;

    @Value("${app.media.orphan-retention-hours:24}")
    private long retentionHours;

    @Scheduled(fixedDelayString = "${app.media.cleanup-interval-ms:3600000}")
    public void cleanup() {
        MediaLifecycleService.CleanupResult result = mediaLifecycleService.cleanup(
                LocalDateTime.now().minusHours(Math.max(24, retentionHours))
        );
        if (result.deletedMedia() > 0 || result.deletedDrafts() > 0) {
            log.info("Media cleanup completed. media={}, drafts={}", result.deletedMedia(), result.deletedDrafts());
        }
    }
}
