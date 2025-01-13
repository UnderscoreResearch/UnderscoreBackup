package com.underscoreresearch.backup.io;

import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupUploadCompletion;

public interface UploadScheduler {
    void scheduleUpload(BackupDestination destination, String key, int index, int disambiguator, byte[] data,
                        BackupUploadCompletion completionPromise);

    void shutdown();

    String suggestedKey(String hash, int index, int disambiguator);

    void scheduleUpload(BackupDestination destination, String suggestedPath, byte[] data,
                        BackupUploadCompletion completionPromise);

    void waitForCompletion();
}
