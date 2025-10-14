package com.underscoreresearch.backup.block.assignments;

import com.underscoreresearch.backup.block.FileBlockAssignment;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.model.BackupBlockCompletion;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupPartialFile;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.utils.log.ManualStatusLogger;
import com.underscoreresearch.backup.utils.log.StateLogger;
import com.underscoreresearch.backup.utils.log.StatusLine;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.utils.log.LogUtil.readableEta;
import static com.underscoreresearch.backup.utils.log.LogUtil.readableSize;

/**
 * Base implementation of FileBlockAssignment that provides common functionality
 * for tracking progress and managing block assignments.
 */
public abstract class BaseBlockAssignment implements FileBlockAssignment, ManualStatusLogger {
    private final List<Progress> backupPartialFiles = new ArrayList<>();

    /**
     * Constructor that registers this instance with the StateLogger.
     */
    public BaseBlockAssignment() {
        StateLogger.addLogger(this);
    }

    /**
     * Reset the status tracking for this assignment.
     */
    @Override
    public void resetStatus() {
        synchronized (backupPartialFiles) {
            backupPartialFiles.clear();
        }
    }

    /**
     * Generate status lines for active file uploads.
     * 
     * @return List of status lines representing current upload progress
     */
    @Override
    public List<StatusLine> status() {
        synchronized (backupPartialFiles) {
            return backupPartialFiles.stream().map(progress -> {
                BackupPartialFile partial = progress.getPartialFile();
                if (partial.getParts() != null && !partial.getParts().isEmpty()) {
                    long completed = partial.getParts().getLast().getPosition();

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

    /**
     * Assign blocks to a backup file and track progress.
     * 
     * @param set The backup set containing the file
     * @param file The file to assign blocks to
     * @param completionFuture Callback for when block assignment is complete
     * @return true if the assignment was successful, false otherwise
     */
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

    /**
     * Abstract method to flush assignments, implemented by subclasses.
     */
    abstract public void flushAssignments();

    /**
     * Internal method for assigning blocks, implemented by subclasses.
     * 
     * @param set The backup set containing the file
     * @param file The partial file to assign blocks to
     * @param completionFuture Callback for when block assignment is complete
     * @return true if the assignment was successful, false otherwise
     */
    abstract protected boolean internalAssignBlocks(BackupSet set, BackupPartialFile file,
                                                    BackupBlockCompletion completionFuture);

    /**
     * Inner class for tracking progress of file uploads.
     */
    @Getter
    private static class Progress {
        private final BackupPartialFile partialFile;
        private final Instant started = Instant.now();
        private long initialCompleted;

        /**
         * Create a new progress tracker for a partial file.
         * 
         * @param partialFile The partial file to track
         */
        public Progress(BackupPartialFile partialFile) {
            this.partialFile = partialFile;
            if (partialFile.getParts() != null && !partialFile.getParts().isEmpty()) {
                initialCompleted = partialFile.getParts().getLast().getPosition();
            }
        }
    }
}
