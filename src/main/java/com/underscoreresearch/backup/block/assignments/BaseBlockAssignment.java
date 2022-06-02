package com.underscoreresearch.backup.block.assignments;

import static com.underscoreresearch.backup.utils.LogUtil.readableEta;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;

import com.underscoreresearch.backup.block.FileBlockAssignment;
import com.underscoreresearch.backup.model.BackupBlockCompletion;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupPartialFile;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.utils.StatusLine;
import com.underscoreresearch.backup.utils.StatusLogger;

public abstract class BaseBlockAssignment implements FileBlockAssignment, StatusLogger {
    @Getter
    private static class Progress {
        private BackupPartialFile partialFile;
        private Instant started;
        private long initialCompleted;

        public Progress(BackupPartialFile partialFile) {
            this.partialFile = partialFile;
            this.started = Instant.now();
            if (partialFile.getParts() != null && partialFile.getParts().size() > 0) {
                initialCompleted = partialFile.getParts().get(partialFile.getParts().size() - 1).getPosition();
            }
        }
    }

    private List<Progress> backupPartialFiles = new ArrayList<>();

    @Override
    public void resetStatus() {
        synchronized (backupPartialFiles) {
            backupPartialFiles.clear();
        }
    }

    @Override
    public List<StatusLine> status() {
        synchronized (backupPartialFiles) {
            return backupPartialFiles.stream().map(progress -> {
                long completed = 0;
                BackupPartialFile partial = progress.getPartialFile();
                if (partial.getParts() != null && partial.getParts().size() > 0) {
                    completed = partial.getParts().get(partial.getParts().size() - 1).getPosition();
                }

                Duration duration = Duration.ofMillis(Instant.now().toEpochMilli() - progress.getStarted().toEpochMilli());
                if (duration.toSeconds() > 5) {
                    return new StatusLine(getClass(),
                            "UPLOADED_ACTIVE_" + partial.getFile().getPath(),
                            "Currently uploading " + partial.getFile().getPath(),
                            completed,
                            partial.getFile().getLength(),
                            readableSize(completed) + " / "
                                    + readableSize(partial.getFile().getLength())
                                    + readableEta(completed - progress.initialCompleted,
                                    partial.getFile().getLength() - progress.initialCompleted, duration));
                } else {
                    return new StatusLine(getClass(),
                            "UPLOADED_ACTIVE_" + partial.getFile().getPath(),
                            "Currently uploading " + partial.getFile().getPath(),
                            completed,
                            partial.getFile().getLength(),
                            readableSize(completed) + " / "
                                    + readableSize(partial.getFile().getLength()));
                }
            }).collect(Collectors.toList());
        }
    }

    @Override
    public boolean assignBlocks(BackupSet set, BackupFile file, BackupBlockCompletion completionFuture) {
        BackupPartialFile backupPartialFile = new BackupPartialFile(file);
        Progress progress = new Progress(backupPartialFile);
        synchronized (backupPartialFiles) {
            backupPartialFiles.add(progress);
        }

        try {
            return internalAssignBlocks(set, backupPartialFile, completionFuture);
        } finally {
            synchronized (backupPartialFiles) {
                backupPartialFiles.remove(progress);
            }
        }
    }

    abstract public void flushAssignments();

    abstract protected boolean internalAssignBlocks(BackupSet set, BackupPartialFile file,
                                                    BackupBlockCompletion completionFuture);
}
