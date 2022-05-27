package com.underscoreresearch.backup.block.assignments;

import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.NotImplementedException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.block.BlockFormatPlugin;
import com.underscoreresearch.backup.block.FileBlockExtractor;
import com.underscoreresearch.backup.block.FileBlockUploader;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockCompletion;
import com.underscoreresearch.backup.model.BackupCompletion;
import com.underscoreresearch.backup.model.BackupData;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupLocation;
import com.underscoreresearch.backup.model.BackupPartialFile;
import com.underscoreresearch.backup.model.BackupSet;

@RequiredArgsConstructor
@Slf4j
@BlockFormatPlugin("ZIP")
public class SmallFileBlockAssignment extends BaseBlockAssignment implements FileBlockExtractor {
    private static final int MAX_FILES_PER_BLOCK = 1024;
    private final FileBlockUploader uploader;
    private final BlockDownloader blockDownloader;
    private final MetadataRepository repository;
    private final FileSystemAccess access;
    private final int maximumFileSize;
    private final int targetSize;
    private Map<BackupSet, PendingFile> pendingFiles = new HashMap<>();

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
                    log.warn("Only read {} when expected {} for {}",
                            readableSize(length),
                            readableSize(file.getLength()),
                            file.getPath());
                    completionFuture.completed(null);
                    return true;
                }
            } catch (IOException exc) {
                log.warn("Failed to read file {}: {}", file.getPath(), exc.getMessage());
                completionFuture.completed(null);
                return true;
            }
            internalAssignBlock(set, buffer, completionFuture);
        } catch (Exception e) {
            log.error("Failed to create block for " + file.getPath(), e);
            completionFuture.completed(null);
        }

        return true;
    }

    private synchronized void internalAssignBlock(BackupSet set, byte[] data, BackupBlockCompletion completionFuture)
            throws IOException {
        PendingFile pendingFile = pendingFiles.computeIfAbsent(set, t -> new PendingFile());
        if (pendingFile.estimateSize() + data.length >= targetSize
                || pendingFile.getFileCount() >= MAX_FILES_PER_BLOCK) {
            uploadPending(set, pendingFile);
            pendingFile = new PendingFile();
            pendingFiles.put(set, pendingFile);
        }
        pendingFile.addData(data, set, completionFuture);
    }

    private void uploadPending(BackupSet set, PendingFile pendingFile) {
        try {
            uploader.uploadBlock(set, new BackupData(pendingFile.data()), pendingFile.hash(), "ZIP", (success) -> {
                pendingFile.complete(success);
            });
        } catch (IOException e) {
            log.error("Failed to upload block", e);
            pendingFile.complete(false);
        }
        pendingFiles.remove(set);
    }

    @Override
    public synchronized void flushAssignments() {
        for (Map.Entry<BackupSet, PendingFile> entry : pendingFiles.entrySet()) {
            if (entry.getValue().currentIndex > 0) {
                uploadPending(entry.getKey(), entry.getValue());
            }
        }
        pendingFiles.clear();
    }

    @Data
    @AllArgsConstructor
    private static class CacheEntry {
        private boolean compressed;
        private byte[] data;

        public byte[] get() throws IOException {
            if (compressed) {
                try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
                    try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                        return IOUtils.readAllBytes(gzipInputStream);
                    }
                }
            }
            return data;
        }
    }

    @Data
    private class CachedData {
        private static final long MINIMUM_COMPRESSED_SIZE = 8192;
        private static final long MINIMUM_COMPRESSED_RATIO = 2;
        private Map<String, CacheEntry> blockEntries;

        private CachedData(String hash) {
            try {
                blockEntries = new HashMap<>();
                try (ByteArrayInputStream inputStream = new ByteArrayInputStream(blockDownloader.downloadBlock(hash))) {
                    try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
                        ZipEntry ze;
                        while ((ze = zipInputStream.getNextEntry()) != null) {
                            byte[] data = IOUtils.readAllBytes(zipInputStream);
                            if (ze.getSize() > MINIMUM_COMPRESSED_SIZE
                                    && ze.getSize() / ze.getCompressedSize() > MINIMUM_COMPRESSED_RATIO) {
                                try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                                    try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
                                        gzipOutputStream.write(data);
                                    }
                                    blockEntries.put(ze.getName(), new CacheEntry(true, byteArrayOutputStream.toByteArray()));
                                }
                            } else {
                                blockEntries.put(ze.getName(), new CacheEntry(false, data));
                            }
                        }
                    }
                }
            } catch (IOException exc) {
                throw new RuntimeException(exc);
            }
        }

        public byte[] get(String key) throws IOException {
            CacheEntry entry = blockEntries.get(key);
            if (entry != null) {
                return entry.get();
            }
            return null;
        }
    }

    private LoadingCache<String, CachedData> cache = CacheBuilder
            .newBuilder()
            .maximumSize(2)
            .build(new CacheLoader<String, CachedData>() {
                @Override
                public CachedData load(String key) throws Exception {
                    return new CachedData(key);
                }
            });

    @Override
    public byte[] extractPart(BackupFilePart file, BackupBlock block) throws IOException {
        try {
            CachedData data = cache.get(block.getHash());
            return data.get(file.getBlockIndex().toString());
        } catch (ExecutionException e) {
            throw new IOException("Failed to process contents of block " + file.getBlockHash(), e);
        }
    }

    @Override
    public long blockSize(BackupFilePart file, byte[] blockData) throws IOException {
        throw new NotImplementedException();
    }

    private class PendingFile {
        private ByteArrayOutputStream output = new ByteArrayOutputStream(targetSize);
        private ZipOutputStream zipOutputStream = new ZipOutputStream(output);
        private int currentIndex;
        private Hash hash = new Hash();
        private Map<String, List<BackupFilePart>> pendingParts = new HashMap<>();
        private List<BackupCompletion> completions = new ArrayList<>();

        public synchronized void addData(byte[] data, BackupSet set, BackupBlockCompletion completion) throws IOException {
            String partHash = Hash.hash(data);

            List<BackupFilePart> existingParts = repository.existingFilePart(partHash);
            if (existingParts != null && existingParts.size() > 0) {
                List<BackupLocation> locations = new ArrayList<>();
                for (BackupFilePart part : existingParts) {
                    BackupBlock block = repository.block(part.getBlockHash());
                    boolean skip = false;
                    if (block != null) {
                        for (String destination : set.getDestinations()) {
                            if (!block.getStorage().stream()
                                    .anyMatch(storage -> storage.getDestination().equals(destination))) {
                                skip = true;
                                break;
                            }
                        }
                    } else {
                        log.warn("Block " + part.getBlockHash() + " did not exist");
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
                hash.addBytes(data);

                ZipEntry entry = new ZipEntry(currentIndex + "");
                zipOutputStream.putNextEntry(entry);
                zipOutputStream.write(data, 0, data.length);
                zipOutputStream.closeEntry();
                zipOutputStream.flush();

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

        public synchronized int estimateSize() {
            return output.size();
        }

        public synchronized byte[] data() throws IOException {
            zipOutputStream.close();
            zipOutputStream = null;
            byte[] data = output.toByteArray();
            output.close();
            output = null;
            return data;
        }

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
