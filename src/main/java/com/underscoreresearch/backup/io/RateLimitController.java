package com.underscoreresearch.backup.io;

import java.util.HashMap;
import java.util.Map;

import com.google.common.util.concurrent.RateLimiter;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupLimits;

public class RateLimitController {
    private final Map<BackupDestination, RateLimiter> destinationUploadLimit = new HashMap<>();
    private final Map<BackupDestination, RateLimiter> destinationDownloadLimit = new HashMap<>();
    private RateLimiter uploadLimit;
    private RateLimiter downloadLimit;

    public RateLimitController(BackupLimits overallLimits) {
        if (overallLimits != null) {
            if (overallLimits.getMaximumUploadBytesPerSecond() != null) {
                uploadLimit = RateLimiter.create(overallLimits.getMaximumUploadBytesPerSecond());
            }
            if (overallLimits.getMaximumDownloadBytesPerSecond() != null) {
                downloadLimit = RateLimiter.create(overallLimits.getMaximumDownloadBytesPerSecond());
            }
        }
    }

    public void acquireDownloadPermits(BackupDestination destination, int size) {
        if (downloadLimit != null)
            downloadLimit.acquire(size);

        if (destination.getLimits() != null && destination.getLimits().getMaximumDownloadBytesPerSecond() != null) {
            RateLimiter rateLimiter;
            synchronized (destinationDownloadLimit) {
                rateLimiter = destinationDownloadLimit.computeIfAbsent(destination,
                        (t) -> RateLimiter.create(destination.getLimits().getMaximumDownloadBytesPerSecond()));
            }
            rateLimiter.acquire(size);
        }
    }

    public void acquireUploadPermits(BackupDestination destination, int size) {
        if (uploadLimit != null)
            uploadLimit.acquire(size);

        if (destination.getLimits() != null && destination.getLimits().getMaximumUploadBytesPerSecond() != null) {
            RateLimiter rateLimiter;
            synchronized (destinationDownloadLimit) {
                rateLimiter = destinationUploadLimit.computeIfAbsent(destination,
                        (t) -> RateLimiter.create(destination.getLimits().getMaximumUploadBytesPerSecond()));
            }
            rateLimiter.acquire(size);
        }
    }
}
