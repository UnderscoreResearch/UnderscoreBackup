package com.underscoreresearch.backup.block.assignments;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.block.BlockFormatPlugin;
import com.underscoreresearch.backup.block.FileBlockAssignment;
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
import com.underscoreresearch.backup.model.BackupSet;

@RequiredArgsConstructor
@Slf4j
@BlockFormatPlugin("ZIP")
public class SmallFileBlockAssignment implements FileBlockAssignment, FileBlockExtractor {
    private final FileBlockUploader uploader;
    private final MetadataRepository repository;
    private final FileSystemAccess access;
    private final int maximumFileSize;
    private final int targetSize;
    private Map<BackupSet, PendingFile> pendingFiles = new HashMap<>();

    @Override
    public boolean assignBlocks(BackupSet set, BackupFile file, BackupBlockCompletion completionFuture) {
        if (file.getLength() > maximumFileSize) {
            return false;
        }

        try {
            byte[] buffer = new byte[(int) (long) file.getLength()];
            try {
                int length = access.readData(file.getPath(), buffer, 0, (int) (long) file.getLength());
                if (length != file.getLength()) {
                    log.warn("Only read {} when expected {} for {}", length, file.getLength(), file.getPath());
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

    public boolean assignBlock(BackupSet set, byte[] data, BackupBlockCompletion completionFuture) throws IOException {
        if (data.length > maximumFileSize) {
            return false;
        }
        internalAssignBlock(set, data, completionFuture);

        return true;
    }

    private synchronized void internalAssignBlock(BackupSet set, byte[] data, BackupBlockCompletion completionFuture)
            throws IOException {
        PendingFile pendingFile = pendingFiles.computeIfAbsent(set, t -> new PendingFile());
        if (pendingFile.estimateSize() + data.length >= targetSize) {
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
    private static class CachedData {
        private String hash;
        private Map<String, byte[]> unpackged = new TreeMap<>();

        private CachedData(String hash) {
            this.hash = hash;
        }

        public Map<String, byte[]> unpackData(byte[] blockData) throws IOException {
            synchronized (unpackged) {
                if (unpackged.size() == 0) {
                    try (ByteArrayInputStream inputStream = new ByteArrayInputStream(blockData)) {
                        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
                            ZipEntry ze;
                            while ((ze = zipInputStream.getNextEntry()) != null) {
                                unpackged.put(ze.getName(), IOUtils.readAllBytes(zipInputStream));
                            }
                        }
                    }
                }
            }
            return unpackged;
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
    public byte[] extractPart(BackupFilePart file, byte[] blockData) throws IOException {
        try {
            CachedData data = cache.get(file.getBlockHash());
            return data.unpackData(blockData).get(file.getBlockIndex().toString());
        } catch (ExecutionException e) {
            throw new IOException("Failed to process contents of block " + file.getBlockHash(), e);
        }
    }

    @Override
    public boolean shouldCache() {
        return true;
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
                        for (String destinations : set.getDestinations()) {
                            if (!block.getStorage().stream()
                                    .anyMatch(storage -> storage.getDestination().equals(destinations))) {
                                skip = true;
                                break;
                            }
                        }
                    } else {
                        log.error("Block " + part.getBlockHash() + " did not exist");
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
    }
}
