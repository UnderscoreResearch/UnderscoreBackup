package com.underscoreresearch.backup.block.assignments;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.block.FileBlockAssignment;
import com.underscoreresearch.backup.block.FileBlockExtractor;
import com.underscoreresearch.backup.block.FileBlockUploader;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
@Slf4j
public abstract class LargeFileBlockAssignment implements FileBlockAssignment, FileBlockExtractor {
    private static final long GB = 1024 * 1024 * 1024;
    private final FileBlockUploader uploader;
    private final FileSystemAccess access;
    private final int maximumBlockSize;

    @Override
    public boolean assignBlocks(BackupSet set, BackupFile file, BackupBlockCompletion completionFuture) {
        Set<BackupCompletion> partialCompletions = new HashSet<>();
        AtomicBoolean success = new AtomicBoolean(true);
        List<BackupFilePart> parts = new ArrayList<>();
        BackupLocation location = BackupLocation.builder()
                .creation(Instant.now().toEpochMilli())
                .parts(parts)
                .build();
        BackupLocation[] locationRef = new BackupLocation[]{location};
        AtomicBoolean readyToComplete = new AtomicBoolean();

        long start = 0;
        while (start < file.getLength()) {
            if (InstanceFactory.shutdown()) {
                return true;
            }
            long end = start + maximumBlockSize;
            if (end > file.getLength())
                end = file.getLength();

            try {
                int size = (int) (end - start);
                byte[] buffer = new byte[size];
                int length;
                try {
                    length = access.readData(file.getPath(), buffer, start, size);
                } catch (IOException exc) {
                    log.warn("Failed to read file {}: {}", file.getPath(), exc.getMessage());
                    completionFuture.completed(null);
                    return true;
                }
                if (length != size) {
                    log.warn("Only read {} when expected {} for {}", length, size, file.getPath());
                    locationRef[0] = null;
                    completionFuture.completed(null);
                    return true;
                } else {
                    // TODO: If at the end we end up with a small piece that should go in a small block.

                    final String hash = Hash.hash(buffer);

                    parts.add(BackupFilePart.builder()
                            .blockHash(hash)
                            .build());

                    BackupCompletion partialCompletion = new BackupCompletion() {
                        @Override
                        public void completed(boolean partialSuccess) {
                            synchronized (partialCompletions) {
                                partialCompletions.remove(this);
                                if (!partialSuccess) {
                                    success.set(false);
                                }
                                if (partialCompletions.size() == 0) {
                                    if (readyToComplete.get()) {
                                        if (locationRef[0] != null) {
                                            completionFuture.completed(success.get()
                                                    ? Lists.newArrayList(locationRef[0])
                                                    : null);
                                        }
                                    }
                                }
                            }
                        }
                    };

                    synchronized (partialCompletions) {
                        partialCompletions.add(partialCompletion);
                    }

                    BackupData data = new BackupData(processBuffer(buffer));
                    buffer = null;

                    uploader.uploadBlock(set, data, hash, getFormat(), partialCompletion);
                }

                if (start / GB != end / GB && start < file.getLength()) {
                    long ts = end;
                    log.info("Scheduled {}G/{}G for {}", ts / GB, file.getLength() / GB, file.getPath());
                }
            } catch (Exception e) {
                log.error("Failed to create block for " + file.getPath(), e);
                locationRef[0] = null;
                completionFuture.completed(null);
                return true;
            }
            start = end;
        }

        synchronized (partialCompletions) {
            readyToComplete.set(true);
            if (partialCompletions.size() == 0) {
                completionFuture.completed(success.get() ? Lists.newArrayList(locationRef[0]) : null);
            }
        }
        return true;
    }

    @Override
    public void flushAssignments() {
    }

    protected abstract byte[] processBuffer(byte[] buffer) throws IOException;

    protected abstract String getFormat();

    @Override
    public abstract byte[] extractPart(BackupFilePart file, byte[] blockData) throws IOException;

    @Override
    public boolean shouldCache() {
        return false;
    }
}
