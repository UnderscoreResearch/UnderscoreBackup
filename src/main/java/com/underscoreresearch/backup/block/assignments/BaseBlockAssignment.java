package com.underscoreresearch.backup.block.assignments;

import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.underscoreresearch.backup.block.FileBlockAssignment;
import com.underscoreresearch.backup.model.BackupBlockCompletion;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupPartialFile;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.utils.StatusLine;
import com.underscoreresearch.backup.utils.StatusLogger;

public abstract class BaseBlockAssignment implements FileBlockAssignment, StatusLogger {

    private List<BackupPartialFile> backupPartialFiles = new ArrayList<>();

    @Override
    public void resetStatus() {
        synchronized (backupPartialFiles) {
            backupPartialFiles.clear();
        }
    }

    @Override
    public List<StatusLine> status() {
        synchronized (backupPartialFiles) {
            return backupPartialFiles.stream().map(entry -> {
                long completed = 0;
                if (entry.getParts() != null && entry.getParts().size() > 0) {
                    completed = entry.getParts().get(entry.getParts().size() - 1).getPosition();
                }
                return new StatusLine(getClass(),
                        "UPLOADED_ACTIVE_" + entry.getFile().getPath(),
                        "Currently uploading " + entry.getFile().getPath(),
                        completed,
                        entry.getFile().getLength(),
                        readableSize(completed) + " / "
                                + readableSize(entry.getFile().getLength()));
            }).collect(Collectors.toList());
        }
    }

    @Override
    public boolean assignBlocks(BackupSet set, BackupFile file, BackupBlockCompletion completionFuture) {
        BackupPartialFile backupPartialFile = new BackupPartialFile(file);
        synchronized (backupPartialFiles) {
            backupPartialFiles.add(backupPartialFile);
        }

        try {
            return internalAssignBlocks(set, backupPartialFile, completionFuture);
        } finally {
            synchronized (backupPartialFiles) {
                backupPartialFiles.remove(backupPartialFile);
            }
        }
    }

    abstract public void flushAssignments();

    abstract protected boolean internalAssignBlocks(BackupSet set, BackupPartialFile file,
                                                    BackupBlockCompletion completionFuture);
}
