package com.underscoreresearch.backup.io;

import com.google.common.util.concurrent.RateLimiter;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupLimits;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for rate limiting uploads and downloads.
 * Enforces bandwidth limits for both overall operations and per-destination.
 */
public class RateLimitController {
    private final Map<BackupDestination, RateLimiter> destinationUploadLimit = new HashMap<>();
    private final Map<BackupDestination, RateLimiter> destinationDownloadLimit = new HashMap<>();
    private RateLimiter uploadLimit;
    private RateLimiter downloadLimit;

    /**
     * Constructor for RateLimitController.
     *
     * @param overallLimits The overall bandwidth limits
     */
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

    /**
     * Acquire permits for downloading data.
     * This method will block until the rate limits allow the download.
     *
     * @param destination The destination to download from
     * @param size The size of data to download in bytes
     */
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

    /**
     * Acquire permits for uploading data.
     * This method will block until the rate limits allow the upload.
     *
     * @param destination The destination to upload to
     * @param size The size of data to upload in bytes
     */
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
