package com.underscoreresearch.backup.block.assignments;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.block.FileBlockExtractor;
import com.underscoreresearch.backup.block.FileBlockUploader;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static com.underscoreresearch.backup.utils.log.LogUtil.readableSize;

/**
 * Abstract base class for handling small file block assignments.
 * Provides functionality for combining multiple small files into blocks for efficient storage.
 */
@RequiredArgsConstructor
@Slf4j
public abstract class SmallFileBlockAssignment extends BaseBlockAssignment implements FileBlockExtractor {
    private static final int MAX_FILES_PER_BLOCK = 1024;
    private final FileBlockUploader uploader;
    @Getter(AccessLevel.PROTECTED)
    private final BlockDownloader blockDownloader;
    @Getter(AccessLevel.PROTECTED)
    private final MetadataRepository repository;
    private final FileSystemAccess access;
    private final EncryptionIdentity encryptionIdentity;
    private final int maximumFileSize;
    @Getter(AccessLevel.PROTECTED)
    private final int targetSize;
    private final Map<BackupSet, PendingFile> pendingFiles = new HashMap<>();
    private final LoadingCache<KeyFetch, CachedData> cache = CacheBuilder
            .newBuilder()
            .maximumSize(2)
            .build(new CacheLoader<>() {
                @NotNull
                @Override
                public CachedData load(@NotNull KeyFetch key) throws Exception {
                    return createCacheData(key.getBlockHash(), key.getPassword());
                }
            });

    /**
     * Assign blocks to a file by reading its contents and adding it to a pending block.
     * 
     * @param set The backup set containing the file
     * @param backupPartialFile The partial file to assign blocks to
     * @param completionFuture Callback for when block assignment is complete
     * @return true if the assignment was successful, false otherwise
     */
    @Override
    protected boolean internalAssignBlocks(BackupSet set, BackupPartialFile backupPartialFile,
                                           BackupBlockCompletion completionFuture) {
        BackupFile file = backupPartialFile.getFile();

        if (file.getLength() > maximumFileSize) {
            return false;
        }

        try {
            byte[] buffer = new byte[(int) (long) file.getLength()];
            try {
                int length = access.readData(file.getPath(), buffer, 0, (int) (long) file.getLength());
                if (length != file.getLength()) {
                    log.warn("Only read {} when expected {} for \"{}\"",
                            readableSize(length),
                            readableSize(file.getLength()),
                            PathNormalizer.physicalPath(file.getPath()));
                    completionFuture.completed(null);
                    return true;
                }
            } catch (IOException exc) {
                log.warn("Failed to read file \"{}\": \u200E{}\u200E", PathNormalizer.physicalPath(file.getPath()), exc.getMessage());
                completionFuture.completed(null);
                return true;
            }
            internalAssignBlock(set, buffer, completionFuture);
        } catch (Exception e) {
            log.error("Failed to create block for \"" + PathNormalizer.physicalPath(file.getPath()) + "\"", e);
            completionFuture.completed(null);
        }

        return true;
    }

    /**
     * Add file data to a pending block, creating a new one if necessary.
     * 
     * @param set The backup set
     * @param data The file data
     * @param completionFuture Callback for when block assignment is complete
     * @throws IOException If there's an error assigning the block
     */
    private synchronized void internalAssignBlock(BackupSet set, byte[] data, BackupBlockCompletion completionFuture)
            throws IOException {
        PendingFile pendingFile = pendingFiles.computeIfAbsent(set, t -> createPendingFile());
        if (pendingFile.estimateSize() + data.length >= targetSize
                || pendingFile.getFileCount() >= MAX_FILES_PER_BLOCK) {
            uploadPending(set, pendingFile);
            pendingFile = createPendingFile();
            pendingFiles.put(set, pendingFile);
        }
        pendingFile.addData(data, set, completionFuture);
    }

    /**
     * Create a new pending file for the specific implementation.
     * 
     * @return A new PendingFile instance
     */
    protected abstract PendingFile createPendingFile();

    /**
     * Upload a pending file to storage.
     * 
     * @param set The backup set
     * @param pendingFile The pending file to upload
     */
    private void uploadPending(BackupSet set, PendingFile pendingFile) {
        try {
            uploader.uploadBlock(set, new BackupData(pendingFile.data()), pendingFile.hash(), getFormat(),
                    pendingFile::complete);
        } catch (IOException e) {
            log.error("Failed to upload block", e);
            pendingFile.complete(false);
        }
        pendingFiles.remove(set);
    }

    /**
     * Get the format identifier for this block assignment.
     * 
     * @return The format identifier string
     */
    protected abstract String getFormat();

    /**
     * Flush any pending assignments by uploading all pending files.
     */
    @Override
    public synchronized void flushAssignments() {
        for (Map.Entry<BackupSet, PendingFile> entry : pendingFiles.entrySet()) {
            if (entry.getValue().currentIndex > 0) {
                uploadPending(entry.getKey(), entry.getValue());
            }
        }
        pendingFiles.clear();
    }

    /**
     * Create a cached data object for the specific implementation.
     * 
     * @param key The block hash key
     * @param password The password for decryption
     * @return A new CachedData instance
     */
    protected abstract CachedData createCacheData(String key, String password);

    /**
     * Extract a file part from a block.
     * 
     * @param file The file part to extract
     * @param block The block containing the file part
     * @param password The password for decryption
     * @return The extracted file part data
     * @throws IOException If there's an error extracting the file part
     */
    @Override
    public byte[] extractPart(BackupFilePart file, BackupBlock block, String password) throws IOException {
        try {
            CachedData data = cache.get(new KeyFetch(file.getBlockHash(), password));
            return data.get(file.getBlockIndex(), file.getPartHash());
        } catch (ExecutionException e) {
            throw new IOException("Failed to process contents of block \"" + file.getBlockHash() + "\"", e);
        }
    }

    /**
     * Calculate the size of a block for a specific file part.
     * Not implemented for small file blocks.
     * 
     * @param file The file part
     * @param blockData The block data
     * @return The size of the block in bytes
     * @throws IOException If there's an error calculating the block size
     */
    @Override
    public long blockSize(BackupFilePart file, byte[] blockData) throws IOException {
        throw new NotImplementedException();
    }

    /**
     * Inner class for key fetching in the cache.
     */
    @Getter
    @AllArgsConstructor
    private static class KeyFetch {
        private String blockHash;
        private String password;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            KeyFetch keyFetch = (KeyFetch) o;
            return Objects.equals(blockHash, keyFetch.blockHash);
        }

        @Override
        public int hashCode() {
            return Objects.hash(blockHash);
        }
    }

    /**
     * Abstract class for cached block data.
     */
    @Data
    protected abstract static class CachedData {
        /**
         * Get a specific part from the cached block data.
         * 
         * @param index The index of the part
         * @param partHash The hash of the part
         * @return The part data
         * @throws IOException If there's an error getting the part
         */
        public abstract byte[] get(int index, String partHash) throws IOException;
    }

    /**
     * Abstract class for pending file data.
     */
    protected abstract class PendingFile {
        private final Hash hash = new Hash();
        private final Map<String, List<BackupFilePart>> pendingParts = new HashMap<>();
        private final List<BackupCompletion> completions = new ArrayList<>();
        private int currentIndex;

        /**
         * Constructor that initializes the hash with salt.
         */
        public PendingFile() {
            encryptionIdentity.addBlockHashSalt(hash);
            hash.addBytes(SmallFileBlockAssignment.this.getClass().getName().getBytes(StandardCharsets.UTF_8));
        }

        /**
         * Add data to the pending file.
         * 
         * @param data The data to add
         * @param set The backup set
         * @param completion Callback for when the addition is complete
         * @throws IOException If there's an error adding the data
         */
        public synchronized void addData(byte[] data, BackupSet set, BackupBlockCompletion completion) throws IOException {
            String partHash;
            {
                Hash partHasher = new Hash();
                encryptionIdentity.addBlockHashSalt(partHasher);
                partHasher.addBytes(data);
                partHash = partHasher.getHash();
            }

            List<BackupFilePart> existingParts = repository.existingFilePart(partHash);
            if (existingParts != null && !existingParts.isEmpty()) {
                List<BackupLocation> locations = new ArrayList<>();
                for (BackupFilePart part : existingParts) {
                    BackupBlock block = repository.block(part.getBlockHash());
                    boolean skip = false;
                    if (block != null) {
                        for (String destination : set.getDestinations()) {
                            if (block.getStorage().stream()
                                    .noneMatch(storage -> storage.getDestination().equals(destination))) {
                                skip = true;
                                break;
                            }
                        }
                    } else {
                        log.warn("Block \"" + part.getBlockHash() + "\" did not exist");
                        skip = true;
                    }
                    if (!skip) {
                        locations.add(BackupLocation.builder()
                                .creation(block.getCreated())
                                .parts(Lists.newArrayList(part))
                                .build());
                    }
                }
                if (!locations.isEmpty()) {
                    completion.completed(locations);
                    return;
                }
            }

            List<BackupFilePart> existingPending = pendingParts.get(partHash);
            List<BackupFilePart> piecePart;
            if (existingPending == null) {
                currentIndex++;
                hash.addBytes(new byte[]{(byte) data.length,
                        (byte) (data.length / 0x100),
                        (byte) (data.length / 0x10000),
                        (byte) (data.length / 0x1000000)});
                hash.addBytes(data);

                addPartData(currentIndex, data, partHash);

                piecePart = Lists.newArrayList(BackupFilePart.builder()
                        .partHash(partHash)
                        .blockIndex(currentIndex)
                        .build());

                pendingParts.put(partHash, piecePart);
            } else {
                piecePart = existingPending;
            }

            completions.add((success) -> {
                if (success) {
                    piecePart.forEach(part -> part.setBlockHash(hash.getHash()));
                    completion.completed(Lists.newArrayList(BackupLocation.builder()
                            .creation(Instant.now().toEpochMilli())
                            .parts(piecePart)
                            .build()));
                } else
                    completion.completed(null);
            });

        }

        /**
         * Add a part to the pending file.
         * 
         * @param index The index of the part
         * @param data The data to add
         * @param partHash The hash of the part
         * @throws IOException If there's an error adding the part
         */
        protected abstract void addPartData(int index, byte[] data, String partHash) throws IOException;

        /**
         * Estimate the current size of the pending file.
         * 
         * @return The estimated size in bytes
         */
        public abstract int estimateSize();

        /**
         * Get the complete data for the pending file.
         * 
         * @return The complete file data as a byte array
         * @throws IOException If there's an error finalizing the data
         */
        public abstract byte[] data() throws IOException;

        /**
         * Get the hash of the pending file.
         * 
         * @return The hash string
         */
        public synchronized String hash() {
            return hash.getHash();
        }

        /**
         * Complete the pending file operation.
         * 
         * @param success Whether the operation was successful
         */
        public synchronized void complete(boolean success) {
            for (BackupCompletion completion : completions)
                completion.completed(success);
        }

        /**
         * Get the number of files in this pending file.
         * 
         * @return The file count
         */
        public int getFileCount() {
            return currentIndex;
        }
    }
}
