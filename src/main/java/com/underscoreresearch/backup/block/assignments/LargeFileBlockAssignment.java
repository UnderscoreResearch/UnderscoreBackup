package com.underscoreresearch.backup.block.assignments;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.block.FileBlockExtractor;
import com.underscoreresearch.backup.block.FileBlockUploader;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockCompletion;
import com.underscoreresearch.backup.model.BackupCompletion;
import com.underscoreresearch.backup.model.BackupData;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupLocation;
import com.underscoreresearch.backup.model.BackupPartialFile;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.machinestate.MachineState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.utils.log.LogUtil.readableSize;

/**
 * Abstract base class for handling large file block assignments.
 * Provides functionality for breaking large files into blocks and managing their upload.
 */
@RequiredArgsConstructor
@Slf4j
public abstract class LargeFileBlockAssignment extends BaseBlockAssignment implements FileBlockExtractor {
    private static final long GB = 1024 * 1024 * 1024;
    private final FileBlockUploader uploader;
    private final BlockDownloader blockDownloader;
    private final FileSystemAccess access;
    private final MetadataRepository metadataRepository;
    private final MachineState machineState;
    private final EncryptionIdentity encryptionIdentity;
    private final int maximumBlockSize;

    /**
     * Assign blocks to a file by breaking it into chunks and uploading each chunk.
     * Supports resuming partial uploads.
     * 
     * @param set The backup set containing the file
     * @param backupPartialFile The partial file to assign blocks to
     * @param completionFuture Callback for when block assignment is complete
     * @return true if the assignment was successful, false otherwise
     */
    @Override
    protected boolean internalAssignBlocks(BackupSet set, BackupPartialFile backupPartialFile,
                                           BackupBlockCompletion completionFuture) {
        Set<BackupCompletion> partialCompletions = new HashSet<>();
        AtomicBoolean success = new AtomicBoolean(true);

        long start = 0;
        try {
            BackupPartialFile existingPartialFile = metadataRepository.getPartialFile(backupPartialFile);
            if (existingPartialFile != null && existingPartialFile.getParts() != null) {
                for (BackupPartialFile.PartialCompletedPath part : existingPartialFile.getParts()) {
                    if (metadataRepository.block(part.getPart().getBlockHash()) == null) {
                        break;
                    }
                    backupPartialFile.addPart(metadataRepository, part);
                    start = part.getPosition();
                }

                if (start > 0) {
                    log.info("Resuming a backup \"{}\" from {}",
                            PathNormalizer.physicalPath(backupPartialFile.getFile().getPath()), readableSize(start));
                }
            }
        } catch (IOException e) {
            log.warn("Couldn't read partial file", e);
        }

        AtomicReference<BackupPartialFile> locationRef = new AtomicReference<>(backupPartialFile);
        AtomicBoolean readyToComplete = new AtomicBoolean();

        BackupFile file = backupPartialFile.getFile();

        while (start < file.getLength()) {
            if (InstanceFactory.isShutdown()) {
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
                    log.warn("Failed to read file \"{}\": \u200E{}\u200E", PathNormalizer.physicalPath(file.getPath()),
                            exc.getMessage());
                    completionFuture.completed(null);
                    return true;
                }

                final Hash hashCalc = new Hash();
                encryptionIdentity.addBlockHashSalt(hashCalc);
                hashCalc.addBytes(getClass().getName().getBytes(StandardCharsets.UTF_8));
                hashCalc.addBytes(buffer);
                final String hash = hashCalc.getHash();

                BackupFilePart part = BackupFilePart.builder()
                        .blockHash(hash)
                        .offset(start > 0 ? start : null)
                        .build();

                if (length != size) {
                    log.warn("Only read {} when expected {} for \"{}\"", readableSize(length), readableSize(size),
                            PathNormalizer.physicalPath(file.getPath()));
                    locationRef.set(null);
                    completionFuture.completed(null);
                    return true;
                } else {
                    // TODO: If at the end we end up with a small piece that should go in a small block.

                    BackupCompletion partialCompletion = new BackupCompletion() {
                        @Override
                        public void completed(boolean partialSuccess) {
                            synchronized (partialCompletions) {
                                partialCompletions.remove(this);
                                if (!partialSuccess) {
                                    success.set(false);
                                }
                                if (readyToComplete.get()) {
                                    completeIfDone(partialCompletions, backupPartialFile, locationRef, completionFuture,
                                            success);
                                }
                            }
                        }

                    };

                    synchronized (partialCompletions) {
                        partialCompletions.add(partialCompletion);
                    }

                    BackupData data;
                    {
                        byte[] finalBuffer = buffer;
                        data = new BackupData(() -> {
                            try {
                                return processBuffer(finalBuffer);
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to compress file", e);
                            }
                        });
                    }
                    buffer = null;

                    uploader.uploadBlock(set, data, hash, getFormat(), partialCompletion);
                }

                backupPartialFile.addPart(metadataRepository, new BackupPartialFile.PartialCompletedPath(end, part));

                if (end < file.getLength()) {
                    // We keep a checkpoint every 10th of a GB.
                    if (start * 10 / GB != end * 10 / GB) {
                        metadataRepository.savePartialFile(backupPartialFile);
                    }
                    if (start / GB != end / GB) {
                        log.info("Processed {} / {} for \"{}\"", readableSize(end), readableSize(file.getLength()),
                                PathNormalizer.physicalPath(file.getPath()));
                        machineState.waitForRunCheck();
                    }
                }
            } catch (Exception e) {
                log.error("Failed to create block for \"{}\"", PathNormalizer.physicalPath(file.getPath()), e);
                locationRef.set(null);
                completionFuture.completed(null);
                return true;
            }
            start = end;
        }

        synchronized (partialCompletions) {
            readyToComplete.set(true);
            completeIfDone(partialCompletions, backupPartialFile, locationRef, completionFuture, success);
        }
        return true;
    }

    /**
     * Complete the block assignment if all partial uploads are done.
     * 
     * @param partialCompletions Set of partial completions to check
     * @param backupPartialFile The partial file being processed
     * @param locationRef Reference to the location being built
     * @param completionFuture Callback for when block assignment is complete
     * @param success Whether the operation was successful so far
     */
    private void completeIfDone(Set<BackupCompletion> partialCompletions,
                                BackupPartialFile backupPartialFile, AtomicReference<BackupPartialFile> locationRef,
                                BackupBlockCompletion completionFuture, AtomicBoolean success) {
        if (partialCompletions.isEmpty()) {
            try {
                metadataRepository.deletePartialFile(backupPartialFile);
            } catch (IOException e) {
                log.error("Failed to remove partial file data for \"{}\"",
                        PathNormalizer.physicalPath(backupPartialFile.getFile().getPath()));
            }
            if (locationRef.get() != null) {
                completionFuture.completed(success.get()
                        ? Lists.newArrayList(createLocation(locationRef))
                        : null);
            }
        }
    }

    /**
     * Create a backup location from a partial file reference.
     * 
     * @param locationRef Reference to the partial file
     * @return A new BackupLocation containing the file parts
     */
    private BackupLocation createLocation(AtomicReference<BackupPartialFile> locationRef) {
        BackupPartialFile file = locationRef.get();
        if (file != null) {
            return BackupLocation.builder().creation(Instant.now().toEpochMilli())
                    .parts(file.getParts().stream().map(BackupPartialFile.PartialCompletedPath::getPart)
                            .collect(Collectors.toList())).build();
        } else {
            throw new RuntimeException("Expected an existing location reference");
        }
    }

    /**
     * Flush any pending assignments.
     * No-op in this implementation.
     */
    @Override
    public void flushAssignments() {
    }

    /**
     * Process a buffer before storage, typically by compressing it.
     * 
     * @param buffer The buffer to process
     * @return The processed buffer
     * @throws IOException If there's an error processing the buffer
     */
    protected abstract byte[] processBuffer(byte[] buffer) throws IOException;

    /**
     * Get the format identifier for this block assignment.
     * 
     * @return The format identifier string
     */
    protected abstract String getFormat();

    /**
     * Extract a file part from a block by downloading and processing it.
     * 
     * @param file The file part to extract
     * @param block The block containing the file part
     * @param password The password for decryption
     * @return The extracted file part data
     * @throws IOException If there's an error extracting the file part
     */
    @Override
    public byte[] extractPart(BackupFilePart file, BackupBlock block, String password) throws IOException {
        return extractPart(file, blockDownloader.downloadBlock(block, password));
    }

    /**
     * Extract a file part from block data.
     * 
     * @param file The file part to extract
     * @param blockData The block data
     * @return The extracted file part data
     * @throws IOException If there's an error extracting the file part
     */
    protected abstract byte[] extractPart(BackupFilePart file, byte[] blockData) throws IOException;

    /**
     * Calculate the size of a block for a specific file part.
     * 
     * @param file The file part
     * @param blockData The block data
     * @return The size of the block in bytes
     * @throws IOException If there's an error calculating the block size
     */
    @Override
    public long blockSize(BackupFilePart file, byte[] blockData) throws IOException {
        return extractPart(file, blockData).length;
    }
}
