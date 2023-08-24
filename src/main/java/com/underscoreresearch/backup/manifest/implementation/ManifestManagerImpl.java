package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.utils.LogUtil.readableEta;
import static com.underscoreresearch.backup.utils.LogUtil.readableNumber;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.file.implementation.BackupStatsLogger;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.utils.StatusLine;
import com.underscoreresearch.backup.utils.StatusLogger;

@Slf4j
public class ManifestManagerImpl extends OptimizingManifestManager implements StatusLogger {
    public ManifestManagerImpl(BackupConfiguration configuration,
                               String manifestLocation,
                               RateLimitController rateLimitController,
                               ServiceManager serviceManager,
                               String installationIdentity,
                               String source,
                               boolean forceIdentity,
                               EncryptionKey publicKey,
                               BackupStatsLogger statsLogger,
                               AdditionalManifestManager additionalManifestManager,
                               UploadScheduler uploadScheduler) {
        super(configuration,
                manifestLocation,
                rateLimitController,
                serviceManager,
                installationIdentity,
                source,
                forceIdentity,
                publicKey,
                statsLogger,
                additionalManifestManager,
                uploadScheduler);
    }

    public List<StatusLine> status() {
        List<StatusLine> ret = new ArrayList<>();
        if (!Strings.isNullOrEmpty(getSource())) {
            ret.add(new StatusLine(getClass(), "SOURCE", "Browsing source", null, InstanceFactory.getAdditionalSourceName()));
        }
        synchronized (getOperationLock()) {
            if (getOperation() != null) {
                String code = getOperation().toUpperCase().replace(" ", "_");
                if (getProcessedFiles() != null && getTotalFiles() != null) {
                    ret.add(new StatusLine(getClass(), code + "_PROCESSED_FILES", getOperation() + " processed files",
                            getProcessedFiles().get(), getTotalFiles().get(),
                            readableNumber(getProcessedFiles().get()) + " / " + readableNumber(getTotalFiles().get())));
                }
                if (getProcessedOperations() != null) {
                    if (getTotalOperations() != null) {
                        if (getOperationDuration() != null) {
                            ret.add(new StatusLine(getClass(), code + "_PROCESSED_STEPS", getOperation(),
                                    getProcessedOperations().get(), getTotalOperations().get(),
                                    readableNumber(getProcessedOperations().get()) + " / " + readableNumber(getTotalOperations().get()) + " steps"
                                            + readableEta(getProcessedOperations().get(), getTotalOperations().get(),
                                            getOperationDuration().elapsed())));
                        } else {
                            ret.add(new StatusLine(getClass(), code + "_PROCESSED_STEPS", getOperation() + " processed steps",
                                    getProcessedOperations().get(), getTotalOperations().get(),
                                    readableNumber(getProcessedOperations().get()) + " / " + readableNumber(getTotalOperations().get())));
                        }
                    } else {
                        ret.add(new StatusLine(getClass(), code + "_PROCESSED_STEPS", getOperation() + " processed steps",
                                getProcessedOperations().get()));
                    }
                }

                if (getProcessedOperations() != null && getOperationDuration() != null) {
                    int elapsedMilliseconds = (int) getOperationDuration().elapsed(TimeUnit.MILLISECONDS);
                    if (elapsedMilliseconds > 0) {
                        long throughput = 1000 * getProcessedOperations().get() / elapsedMilliseconds;
                        ret.add(new StatusLine(getClass(), code + "_THROUGHPUT", getOperation() + " throughput",
                                throughput, readableNumber(throughput) + " steps/s"));
                    }
                }
            }
        }
        return ret;
    }

    @Override
    public void shutdown() throws IOException {
        super.shutdown();

        getUploadScheduler().shutdown();
    }
}
