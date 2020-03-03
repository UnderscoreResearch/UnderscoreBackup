package com.underscoreresearch.backup.block;

import com.underscoreresearch.backup.model.BackupCompletion;
import com.underscoreresearch.backup.model.BackupData;
import com.underscoreresearch.backup.model.BackupSet;

public interface FileBlockUploader {
    void uploadBlock(BackupSet set,
                     BackupData unencryptedData,
                     String blockHash,
                     String format,
                     BackupCompletion completionFuture);
}
