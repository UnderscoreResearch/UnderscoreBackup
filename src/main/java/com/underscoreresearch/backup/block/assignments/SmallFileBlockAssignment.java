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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

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
                @Override
                public CachedData load(KeyFetch key) throws Exception {
                    return createCacheData(key.getBlockHash(), key.getPassword());
                }
            });

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

    protected abstract PendingFile createPendingFile();

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

    protected abstract String getFormat();

    @Override
    public synchronized void flushAssignments() {
        for (Map.Entry<BackupSet, PendingFile> entry : pendingFiles.entrySet()) {
            if (entry.getValue().currentIndex > 0) {
                uploadPending(entry.getKey(), entry.getValue());
            }
        }
        pendingFiles.clear();
    }

    protected abstract CachedData createCacheData(String key, String password);

    @Override
    public byte[] extractPart(BackupFilePart file, BackupBlock block, String password) throws IOException {
        try {
            CachedData data = cache.get(new KeyFetch(file.getBlockHash(), password));
            return data.get(file.getBlockIndex(), file.getPartHash());
        } catch (ExecutionException e) {
            throw new IOException("Failed to process contents of block \"" + file.getBlockHash() + "\"", e);
        }
    }

    @Override
    public long blockSize(BackupFilePart file, byte[] blockData) throws IOException {
        throw new NotImplementedException();
    }

    @AllArgsConstructor
    private static class KeyFetch {
        @Getter
        private String blockHash;
        @Getter
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

    @Data
    protected abstract static class CachedData {
        public abstract byte[] get(int index, String partHash) throws IOException;
    }

    protected abstract class PendingFile {
        private final Hash hash = new Hash();
        private final Map<String, List<BackupFilePart>> pendingParts = new HashMap<>();
        private final List<BackupCompletion> completions = new ArrayList<>();
        private int currentIndex;

        public PendingFile() {
            encryptionIdentity.addBlockHashSalt(hash);
            hash.addBytes(SmallFileBlockAssignment.this.getClass().getName().getBytes(StandardCharsets.UTF_8));
        }

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
                if (locations.size() > 0) {
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

        protected abstract void addPartData(int index, byte[] data, String partHash) throws IOException;

        public abstract int estimateSize();

        public abstract byte[] data() throws IOException;

        public synchronized String hash() {
            return hash.getHash();
        }

        public synchronized void complete(boolean success) {
            for (BackupCompletion completion : completions)
                completion.completed(success);
        }

        public int getFileCount() {
            return currentIndex;
        }
    }
}
