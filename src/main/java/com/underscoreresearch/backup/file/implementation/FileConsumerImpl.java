package com.underscoreresearch.backup.file.implementation;

import com.underscoreresearch.backup.block.FileBlockAssignment;
import com.underscoreresearch.backup.file.FileConsumer;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.model.BackupCompletion;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static com.underscoreresearch.backup.utils.log.LogUtil.debug;

/**
 * Implementation of FileConsumer that assigns blocks to files and saves them to the repository.
 * Uses a list of FileBlockAssignment implementations to handle different types of files.
 */
@RequiredArgsConstructor
@Slf4j
public class FileConsumerImpl implements FileConsumer {
    private final MetadataRepository repository;
    private final List<FileBlockAssignment> assignments;

    /**
     * Backs up a file by assigning blocks and saving it to the repository.
     *
     * @param set The backup set to which the file belongs
     * @param file The file to back up
     * @param completionPromise Callback to notify when the backup is complete
     */
    @Override
    public void backupFile(BackupSet set, BackupFile file, BackupCompletion completionPromise) {
        if (file.getLength() == 0) {
            saveFile(file, completionPromise);
        } else {
            for (FileBlockAssignment assignment : assignments) {
                if (assignment.assignBlocks(set, file, (locations) -> {
                    if (locations != null) {
                        file.setLocations(locations);
                        saveFile(file, completionPromise);
                    } else {
                        log.warn("Failed backing up \"{}\"", PathNormalizer.physicalPath(file.getPath()));
                        completionPromise.completed(false);
                    }
                })) {
                    return;
                }
            }
            log.error("No block assignment could handle file \"{}\"", PathNormalizer.physicalPath(file.getPath()));
            completionPromise.completed(false);
        }
    }

    /**
     * Flushes all pending block assignments.
     * This ensures that any buffered data is written to the repository.
     */
    @Override
    public void flushAssignments() {
        for (FileBlockAssignment assignment : assignments) {
            assignment.flushAssignments();
        }
    }

    /**
     * Saves a file to the repository and notifies the completion promise.
     *
     * @param file The file to save
     * @param completionPromise Callback to notify when the save is complete
     */
    private void saveFile(BackupFile file, BackupCompletion completionPromise) {
        try {
            debug(() -> log.debug("Completed file \"{}\"", PathNormalizer.physicalPath(file.getPath())));
            file.setAdded(Instant.now().toEpochMilli());
            repository.addFile(file);
            completionPromise.completed(true);
        } catch (IOException e) {
            log.error("Failed to add file \"" + PathNormalizer.physicalPath(file.getPath()) + "\"", e);
            completionPromise.completed(false);
        }
    }
}