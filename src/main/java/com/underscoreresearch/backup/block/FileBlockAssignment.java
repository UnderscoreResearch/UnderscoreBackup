package com.underscoreresearch.backup.block;

import com.underscoreresearch.backup.model.BackupBlockCompletion;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupSet;

public interface FileBlockAssignment {
    boolean assignBlocks(BackupSet set, BackupFile file, BackupBlockCompletion completionFuture);

    void flushAssignments();
}
