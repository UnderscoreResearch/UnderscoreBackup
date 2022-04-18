package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.utils.LogUtil.debug;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.block.FileBlockAssignment;
import com.underscoreresearch.backup.file.FileConsumer;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.model.BackupCompletion;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupSet;

@RequiredArgsConstructor
@Slf4j
public class FileConsumerImpl implements FileConsumer {
    private final MetadataRepository repository;
    private final List<FileBlockAssignment> assignments;

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
                        log.warn("Failed backing up {}", file.getPath());
                        completionPromise.completed(false);
                    }
                })) {
                    return;
                }
            }
            log.error("No block assigned would touch file {}", file.getPath());
            completionPromise.completed(false);
        }
    }

    @Override
    public void flushAssignments() {
        for (FileBlockAssignment assignment : assignments) {
            assignment.flushAssignments();
        }
    }

    private void saveFile(BackupFile file, BackupCompletion completionPromise) {
        try {
            debug(() -> log.debug("Completed file {}", file.getPath()));
            file.setAdded(Instant.now().toEpochMilli());
            repository.addFile(file);
            completionPromise.completed(true);
        } catch (IOException e) {
            log.error("Failed to add file " + file.getPath(), e);
            completionPromise.completed(false);
        }
    }
}