package com.underscoreresearch.backup.block;

import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockUploadCompletion;
import com.underscoreresearch.backup.model.BackupCompletion;
import com.underscoreresearch.backup.model.BackupData;
import com.underscoreresearch.backup.model.BackupSet;

import java.util.Set;

public interface FileBlockUploader {
    void uploadBlock(BackupSet set,
                     BackupData unencryptedData,
                     String blockHash,
                     String format,
                     BackupCompletion completionFuture);

    void uploadBlock(Set<String> requiredDestinations,
                     BackupBlock existingBlock,
                     BackupData unencryptedData,
                     String blockHash,
                     String format,
                     BackupBlockUploadCompletion completionFuture);
}
