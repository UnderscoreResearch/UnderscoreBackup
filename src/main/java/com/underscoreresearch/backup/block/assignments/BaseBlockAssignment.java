package com.underscoreresearch.backup.block.assignments;

import static com.underscoreresearch.backup.utils.LogUtil.readableEta;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.Getter;

import com.underscoreresearch.backup.block.FileBlockAssignment;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.model.BackupBlockCompletion;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupPartialFile;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.utils.ManualStatusLogger;
import com.underscoreresearch.backup.utils.StateLogger;
import com.underscoreresearch.backup.utils.StatusLine;

public abstract class BaseBlockAssignment implements FileBlockAssignment, ManualStatusLogger {
    private final List<Progress> backupPartialFiles = new ArrayList<>();

    public BaseBlockAssignment() {
        StateLogger.addLogger(this);
    }

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
                BackupPartialFile partial = progress.getPartialFile();
                if (partial.getParts() != null && !partial.getParts().isEmpty()) {
                    long completed = partial.getParts().get(partial.getParts().size() - 1).getPosition();

                    Duration duration = Duration.ofMillis(Instant.now().toEpochMilli() - progress.getStarted().toEpochMilli());
                    if (duration.toSeconds() > 5) {
                        return new StatusLine(getClass(),
                                "UPLOADED_ACTIVE_" + partial.getFile().getPath(),
                                "Uploading " + PathNormalizer.physicalPath(partial.getFile().getPath()),
                                completed,
                                partial.getFile().getLength(),
                                readableSize(completed) + " / "
                                        + readableSize(partial.getFile().getLength())
                                        + readableEta(completed - progress.initialCompleted,
                                        partial.getFile().getLength() - progress.initialCompleted, duration));
                    } else {
                        return new StatusLine(getClass(),
                                "UPLOADED_ACTIVE_" + partial.getFile().getPath(),
                                "Uploading " + PathNormalizer.physicalPath(partial.getFile().getPath()),
                                completed,
                                partial.getFile().getLength(),
                                readableSize(completed) + " / "
                                        + readableSize(partial.getFile().getLength()));
                    }
                } else {
                    return null;
                }
            }).filter(Objects::nonNull).collect(Collectors.toList());
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

    @Getter
    private static class Progress {
        private final BackupPartialFile partialFile;
        private final Instant started = Instant.now();
        private long initialCompleted;

        public Progress(BackupPartialFile partialFile) {
            this.partialFile = partialFile;
            if (partialFile.getParts() != null && !partialFile.getParts().isEmpty()) {
                initialCompleted = partialFile.getParts().get(partialFile.getParts().size() - 1).getPosition();
            }
        }
    }
}
