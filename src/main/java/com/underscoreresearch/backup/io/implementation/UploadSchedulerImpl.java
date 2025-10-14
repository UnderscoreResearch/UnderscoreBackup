package com.underscoreresearch.backup.io.implementation;

import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.IOProviderUtil;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupUploadCompletion;
import com.underscoreresearch.backup.service.SubscriptionLackingException;
import com.underscoreresearch.backup.utils.log.ManualStatusLogger;
import com.underscoreresearch.backup.utils.ProcessingStoppedException;
import com.underscoreresearch.backup.utils.log.StateLogger;
import com.underscoreresearch.backup.utils.log.StatusLine;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.utils.log.LogUtil.getThroughputStatus;

/**
 * Implementation of UploadScheduler that manages asynchronous uploads.
 * Provides rate limiting and status tracking for uploads.
 */
@Slf4j
public class UploadSchedulerImpl extends SchedulerImpl implements ManualStatusLogger, UploadScheduler {
    public static final String PREFIX = "blocks" + PATH_SEPARATOR;
    private static UploadSchedulerImpl instance;
    private final RateLimitController rateLimitController;
    private final AtomicLong totalSize = new AtomicLong();
    private final AtomicLong totalCount = new AtomicLong();

    /**
     * Constructor for UploadSchedulerImpl.
     *
     * @param maximumConcurrency The maximum number of concurrent uploads
     * @param rateLimitController The controller for rate limiting uploads
     */
    public UploadSchedulerImpl(int maximumConcurrency, RateLimitController rateLimitController) {
        super(maximumConcurrency);
        this.rateLimitController = rateLimitController;
        StateLogger.addLogger(this);

        if (instance != null)
            instance.shutdown();
        instance = this;
    }

    /**
     * Split a hash into a path structure for storage.
     * Creates a directory structure based on the first few characters of the hash.
     *
     * @param hash The hash to split
     * @return The path structure for the hash
     */
    public static String splitHash(String hash) {
        if (hash.length() > 4) {
            return hash.substring(0, 2) + PATH_SEPARATOR + hash.substring(2, 4) + PATH_SEPARATOR + hash.substring(4);
        }

        return hash;
    }

    /**
     * Schedule an upload with specific parameters.
     *
     * @param destination The destination to upload to
     * @param hash The hash of the data
     * @param index The index for the upload
     * @param disambiguator The disambiguator for the upload
     * @param data The data to upload
     * @param completionPromise The promise to complete when the upload finishes
     */
    @Override
    public void scheduleUpload(BackupDestination destination, String hash, int index, int disambiguator, byte[] data, BackupUploadCompletion completionPromise) {
        String suggestedKey = suggestedKey(hash, index, disambiguator);

        scheduleUpload(destination, suggestedKey, data, completionPromise);
    }

    /**
     * Generate a suggested key based on parameters.
     *
     * @param hash The hash of the data
     * @param index The index for the key
     * @param disambiguator The disambiguator for the key
     * @return The suggested key
     */
    @Override
    public String suggestedKey(String hash, int index, int disambiguator) {
        if (disambiguator == 0) {
            return PREFIX + index + PATH_SEPARATOR + splitHash(hash);
        } else {
            return PREFIX + index + PATH_SEPARATOR + disambiguator + "-" + splitHash(hash);
        }
    }

    /**
     * Schedule an upload with a suggested path.
     *
     * @param destination The destination to upload to
     * @param suggestedPath The suggested path for the upload
     * @param data The data to upload
     * @param completionPromise The promise to complete when the upload finishes
     */
    @Override
    public void scheduleUpload(BackupDestination destination, String suggestedPath, byte[] data,
                               BackupUploadCompletion completionPromise) {
        Runnable runnable = () -> {
            try {
                IOProvider provider = IOProviderFactory.getProvider(destination);
                rateLimitController.acquireUploadPermits(destination, data.length);
                completionPromise.completed(IOProviderUtil.upload(provider, suggestedPath, data));
                totalSize.addAndGet(data.length);
                totalCount.incrementAndGet();
            } catch (ProcessingStoppedException exc) {
                log.warn("Upload cancelled for \"" + suggestedPath + "\" because of shutdown");
                completionPromise.completed(null);
            } catch (SubscriptionLackingException exc) {
                log.error(exc.getMessage() + " Upload failed for \"" + suggestedPath + "\" failed.");
                completionPromise.completed(null);
            } catch (Throwable exc) {
                log.error("Upload failed for \"" + suggestedPath + "\"", exc);
                completionPromise.completed(null);
            }
        };

        if (!schedule(runnable)) {
            completionPromise.completed(null);
        }
    }

    /**
     * Reset the status counters.
     */
    @Override
    public void resetStatus() {
        totalCount.set(0);
        totalSize.set(0);
        resetDuration();
    }

    /**
     * Get the current status of uploads.
     *
     * @return List of status lines
     */
    @Override
    public List<StatusLine> status() {
        return getThroughputStatus(getClass(), "Uploaded", "objects", totalCount.get(), totalSize.get(), getDuration());
    }
}
