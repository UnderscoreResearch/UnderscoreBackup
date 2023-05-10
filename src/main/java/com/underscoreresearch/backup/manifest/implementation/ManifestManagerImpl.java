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
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.utils.StatusLine;
import com.underscoreresearch.backup.utils.StatusLogger;

@Slf4j
public class ManifestManagerImpl extends OptimizingManifestManager implements StatusLogger {
    public ManifestManagerImpl(BackupConfiguration configuration,
                               String manifestLocation,
                               IOProvider provider,
                               Encryptor encryptor,
                               RateLimitController rateLimitController,
                               ServiceManager serviceManager,
                               String installationIdentity,
                               String source,
                               boolean forceIdentity,
                               EncryptionKey publicKey)
            throws IOException {
        super(configuration,
                manifestLocation,
                provider,
                encryptor,
                rateLimitController,
                serviceManager,
                installationIdentity,
                source,
                forceIdentity,
                publicKey);
    }


    public boolean temporal() {
        return false;
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
                            ret.add(new StatusLine(getClass(), code + "_PROCESSED_OPERATIONS", getOperation() + " processed operations",
                                    getProcessedOperations().get(), getTotalOperations().get(),
                                    readableNumber(getProcessedOperations().get()) + " / " + readableNumber(getTotalOperations().get())
                                            + readableEta(getProcessedOperations().get(), getTotalOperations().get(),
                                            getOperationDuration().elapsed())));
                        } else {
                            ret.add(new StatusLine(getClass(), code + "_PROCESSED_OPERATIONS", getOperation() + " processed operations",
                                    getProcessedOperations().get(), getTotalOperations().get(),
                                    readableNumber(getProcessedOperations().get()) + " / " + readableNumber(getTotalOperations().get())));
                        }
                    } else {
                        ret.add(new StatusLine(getClass(), code + "_PROCESSED_OPERATIONS", getOperation() + " processed operations",
                                getProcessedOperations().get()));
                    }
                }

                if (getProcessedOperations() != null && getOperationDuration() != null) {
                    int elapsedMilliseconds = (int) getOperationDuration().elapsed(TimeUnit.MILLISECONDS);
                    if (elapsedMilliseconds > 0) {
                        long throughput = 1000 * getProcessedOperations().get() / elapsedMilliseconds;
                        ret.add(new StatusLine(getClass(), code + "_THROUGHPUT", getOperation() + " throughput",
                                throughput, readableNumber(throughput) + " operations/s"));
                    }
                }
            }
        }
        return ret;
    }
}
