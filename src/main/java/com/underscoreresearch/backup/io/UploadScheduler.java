package com.underscoreresearch.backup.io;

import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupUploadCompletion;

public interface UploadScheduler {
    void scheduleUpload(BackupDestination destination, String key, int index, byte[] data,
                        BackupUploadCompletion completionPromise);

    void shutdown();
}
