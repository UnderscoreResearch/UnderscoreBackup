package com.underscoreresearch.backup.file;

import com.underscoreresearch.backup.model.BackupCompletion;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupSet;

public interface FileConsumer {
    void backupFile(BackupSet set, BackupFile file, BackupCompletion completionPromise);

    void flushAssignments();
}
