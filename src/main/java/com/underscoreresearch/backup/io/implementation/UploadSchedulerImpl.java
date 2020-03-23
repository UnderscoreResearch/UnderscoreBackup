package com.underscoreresearch.backup.io.implementation;

import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupUploadCompletion;
import com.underscoreresearch.backup.utils.StatusLogger;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

@Slf4j
public class UploadSchedulerImpl extends SchedulerImpl implements StatusLogger, UploadScheduler {
    public static final String PREFIX = "blocks" + PATH_SEPARATOR;
    private final RateLimitController rateLimitController;
    private AtomicLong totalSize = new AtomicLong();
    private AtomicLong totalCount = new AtomicLong();

    public UploadSchedulerImpl(int maximumConcurrency, RateLimitController rateLimitController) {
        super(maximumConcurrency);
        this.rateLimitController = rateLimitController;
    }

    @Override
    public void scheduleUpload(BackupDestination destination, String hash, int index, byte[] data, BackupUploadCompletion completionPromise) {
        String suggestedKey = PREFIX + index + PATH_SEPARATOR + splitHash(hash);

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    IOProvider provider = IOProviderFactory.getProvider(destination);
                    rateLimitController.acquireUploadPermits(destination, data.length);
                    completionPromise.completed(provider.upload(suggestedKey, data));
                    totalSize.addAndGet(data.length);
                    totalCount.incrementAndGet();
                } catch (Throwable exc) {
                    log.error("Upload failed for " + suggestedKey, exc);
                    completionPromise.completed(null);
                }
            }
        };

        schedule(runnable);
    }

    @Override
    public void logStatus() {
        if (totalCount.get() > 0) {
            debug(() -> log.debug("Uploaded {} objects of total size {}", totalCount.get(), readableSize(totalSize.get())));
        }
    }

    public static String splitHash(String hash) {
        if (hash.length() > 4) {
            return hash.substring(0, 2) + PATH_SEPARATOR + hash.substring(2, 4) + PATH_SEPARATOR + hash.substring(4);
        }

        return hash;
    }
}
