package com.underscoreresearch.backup.io.implementation;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.utils.LogUtil.getThroughputStatus;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupUploadCompletion;
import com.underscoreresearch.backup.utils.ManualStatusLogger;
import com.underscoreresearch.backup.utils.StateLogger;
import com.underscoreresearch.backup.utils.StatusLine;

@Slf4j
public class UploadSchedulerImpl extends SchedulerImpl implements ManualStatusLogger, UploadScheduler {
    public static final String PREFIX = "blocks" + PATH_SEPARATOR;
    private static UploadSchedulerImpl instance;
    private final RateLimitController rateLimitController;
    private final AtomicLong totalSize = new AtomicLong();
    private final AtomicLong totalCount = new AtomicLong();

    public UploadSchedulerImpl(int maximumConcurrency, RateLimitController rateLimitController) {
        super(maximumConcurrency);
        this.rateLimitController = rateLimitController;
        StateLogger.addLogger(this);

        if (instance != null)
            instance.shutdown();
        instance = this;
    }

    public static String splitHash(String hash) {
        if (hash.length() > 4) {
            return hash.substring(0, 2) + PATH_SEPARATOR + hash.substring(2, 4) + PATH_SEPARATOR + hash.substring(4);
        }

        return hash;
    }

    @Override
    public void scheduleUpload(BackupDestination destination, String hash, int index, byte[] data, BackupUploadCompletion completionPromise) {
        String suggestedKey = PREFIX + splitHash(hash) + PATH_SEPARATOR + index;

        scheduleUpload(destination, suggestedKey, data, completionPromise);
    }

    @Override
    public void scheduleUpload(BackupDestination destination, String suggestedPath, byte[] data,
                               BackupUploadCompletion completionPromise) {
        Runnable runnable = () -> {
            try {
                IOProvider provider = IOProviderFactory.getProvider(destination);
                rateLimitController.acquireUploadPermits(destination, data.length);
                completionPromise.completed(provider.upload(suggestedPath, data));
                totalSize.addAndGet(data.length);
                totalCount.incrementAndGet();
            } catch (Throwable exc) {
                log.error("Upload failed for " + suggestedPath, exc);
                completionPromise.completed(null);
            }
        };

        schedule(runnable);
    }

    @Override
    public void resetStatus() {
        totalCount.set(0);
        totalSize.set(0);
        resetDuration();
    }

    @Override
    public List<StatusLine> status() {
        return getThroughputStatus(getClass(), "Uploaded", "objects", totalCount.get(), totalSize.get(), getDuration());
    }
}
