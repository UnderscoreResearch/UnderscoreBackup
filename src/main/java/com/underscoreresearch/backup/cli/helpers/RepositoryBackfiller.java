package com.underscoreresearch.backup.cli.helpers;

import static com.underscoreresearch.backup.configuration.RestoreModule.DOWNLOAD_THREADS;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableNumber;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.block.BlockFormatFactory;
import com.underscoreresearch.backup.block.FileBlockExtractor;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableMap;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.MapSerializer;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.io.implementation.SchedulerImpl;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupLocation;

@Slf4j
public class RepositoryBackfiller {
    private final MetadataRepository repository;
    private final ManifestManager manifestManager;
    private final BlockDownloader blockDownloader;
    private final EncryptionIdentity encryptionKey;
    private final int maximumConcurrency;

    @Inject
    public RepositoryBackfiller(final MetadataRepository repository,
                                final ManifestManager manifestManager,
                                final BlockDownloader blockDownloader,
                                final EncryptionIdentity encryptionKey,
                                @Named(DOWNLOAD_THREADS) final int maximumConcurrency) {
        this.repository = repository;
        this.manifestManager = manifestManager;
        this.blockDownloader = blockDownloader;
        this.maximumConcurrency = maximumConcurrency;
        this.encryptionKey = encryptionKey;
    }

    public void executeBackfill(String password) throws IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        AtomicLong lastDuration = new AtomicLong(0);
        long blockCount = repository.getBlockCount();
        long fileCount = repository.getFileCount();

        manifestManager.setDisabledFlushing(true);
        try {
            try (CloseableMap<String, Long> knownSizes = repository.temporaryMap(new MapSerializer<>() {
                @Override
                public byte[] encodeKey(String s) {
                    return s.getBytes(StandardCharsets.UTF_8);
                }

                @Override
                public byte[] encodeValue(Long val) {
                    ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
                    buffer.putLong(val);
                    return buffer.array();
                }

                @Override
                public Long decodeValue(byte[] data) {
                    return ByteBuffer.wrap(data).getLong();
                }

                @Override
                public String decodeKey(byte[] data) {
                    return new String(data, StandardCharsets.UTF_8);
                }
            })) {
                BackfillDownloader backfillDownloader = new BackfillDownloader(knownSizes);

                try (CloseableLock ignore = repository.acquireLock()) {
                    log.info("Collecting all known block sizes");
                    try (CloseableStream<BackupFile> files = repository.allFiles(true)) {
                        files.stream().forEach(backfillDownloader::addInferredBlockSizes);
                    }
                    log.info("Backfilling file block offsets");
                    try (CloseableStream<BackupFile> files = repository.allFiles(true)) {
                        files.stream().forEach(file -> {
                            backfillDownloader.backfillFilePartOffsets(file, password);
                            long currentMinute = stopwatch.elapsed(TimeUnit.MINUTES);
                            if (currentMinute != lastDuration.get()) {
                                log.info("Processed {} / {} files, updated {} files so far (Last file \"{}\")",
                                        readableNumber(backfillDownloader.getProcessed().get()),
                                        readableNumber(fileCount),
                                        readableNumber(backfillDownloader.getCompletedFiles().get()),
                                        PathNormalizer.physicalPath(file.getPath()));
                                lastDuration.set(currentMinute);
                            }
                        });
                    }

                    log.info("Backfilling encryption to block storage");
                    backfillDownloader.getProcessed().set(0L);

                    try (CloseableStream<BackupBlock> blocks = repository.allBlocks()) {
                        blocks.stream().forEach(block -> {
                            backfillDownloader.backfillStorage(block);
                            long currentMinute = stopwatch.elapsed(TimeUnit.MINUTES);
                            if (currentMinute != lastDuration.get()) {
                                log.info("Processed {} / {} blocks, fixed {} blocks so far",
                                        readableNumber(backfillDownloader.getProcessed().get()),
                                        readableNumber(blockCount),
                                        readableNumber(backfillDownloader.getCompletedBlocks().get()));
                                lastDuration.set(currentMinute);
                            }
                        });
                    }

                    backfillDownloader.waitForCompletion();
                }
                log.info("Updated {} blocks", backfillDownloader.getCompletedBlocks().get());
                log.info("Updated {} files", backfillDownloader.getCompletedFiles().get());
            }
        } finally {
            manifestManager.setDisabledFlushing(false);
        }
    }

    public class BackfillDownloader extends SchedulerImpl {
        private final CloseableMap<String, Long> blockSizes;
        @Getter
        private final AtomicLong completedBlocks = new AtomicLong();
        @Getter
        private final AtomicLong processed = new AtomicLong();
        @Getter
        private final AtomicLong completedFiles = new AtomicLong();

        private final ConcurrentLinkedQueue<BackupBlock> pendingBlockUpdates = new ConcurrentLinkedQueue<>();
        private final ArrayList<BackupFile> pendingFileUpdates = new ArrayList<>();

        @Inject
        public BackfillDownloader(CloseableMap<String, Long> blockSizes) {
            super(maximumConcurrency);

            this.blockSizes = blockSizes;
        }

        public void backfillStorage(BackupBlock block) {
            processed.incrementAndGet();
            if (block.getStorage().stream().anyMatch(storage -> !EncryptorFactory.getEncryptor(storage.getEncryption()).validStorage(storage))) {
                schedule(() -> {
                    for (BackupBlockStorage storage : block.getStorage()) {
                        try {
                            Encryptor encryptor = EncryptorFactory.getEncryptor(storage.getEncryption());
                            if (!encryptor.validStorage(storage)) {
                                byte[] data = blockDownloader.downloadEncryptedBlockStorage(block, storage, null);
                                encryptor.backfillEncryption(storage, data);
                            }
                        } catch (IOException e) {
                            log.error("Failed to download backfill block \"{}\"", block.getHash());
                        }
                    }
                    pendingBlockUpdates.add(block);
                });
                postPending();
            }
        }

        public void backfillFilePartOffsets(BackupFile file, String password) {
            processed.incrementAndGet();
            if (file.getLocations() != null) {
                for (BackupLocation location : file.getLocations()) {
                    if (location.getParts().size() > 1) {
                        for (int i = 1; i < location.getParts().size(); i++) {
                            // This code relies on the lsat block never being a super block
                            if ((location.getParts().get(i).getOffset() == null)
                                    || incompleteSuperblock(location.getParts().get(i - 1).getBlockHash())) {
                                processOffsetBackfill(file, password);
                                postPending();
                                return;
                            }
                        }
                    }
                }
            }
        }

        private boolean incompleteSuperblock(String blockHash) {
            if (BackupBlock.isSuperBlock(blockHash)) {
                try {
                    BackupBlock block = repository.block(blockHash);
                    if (block.getOffsets() == null || block.getOffsets().size() != block.getHashes().size()) {
                        return true;
                    }
                } catch (IOException e) {
                    log.error("Failed to fetch backup block");
                    return true;
                }
            }
            return false;
        }

        private void processOffsetBackfill(BackupFile file, String password) {
            for (BackupLocation location : file.getLocations()) {
                if (location.getParts().size() > 1) {
                    for (int i = 0; i < location.getParts().size() - 1; i++) {
                        if (pendingBlockUpdates.size() > 100) {
                            postPending();
                        }
                        if (location.getParts().get(i + 1).getOffset() == null || incompleteSuperblock(location.getParts().get(i).getBlockHash())) {
                            BackupFilePart part = location.getParts().get(i);
                            try {
                                List<BackupBlock> blocks = BackupBlock.expandBlock(part.getBlockHash(), repository);
                                for (BackupBlock block : blocks) {
                                    if (blockSizes.get(block.getHash()) == null) {
                                        schedule(() -> downloadPartBlock(part, block, password));
                                    }
                                }
                            } catch (IOException e) {
                                log.error("Failed to fetch block \"{}\"", part.getBlockHash(), e);
                            }
                        }
                    }
                }
            }
            synchronized (pendingFileUpdates) {
                pendingFileUpdates.add(file);
            }
        }

        private void downloadPartBlock(BackupFilePart part, BackupBlock block, String password) {
            boolean updateBlock = false;
            for (BackupBlockStorage storage : block.getStorage()) {
                try {
                    Encryptor encryptor = EncryptorFactory.getEncryptor(storage.getEncryption());
                    byte[] data = blockDownloader.downloadEncryptedBlockStorage(block, storage, null);
                    if (!encryptor.validStorage(storage)) {
                        encryptor.backfillEncryption(storage, data);
                        updateBlock = true;
                    }

                    FileBlockExtractor extractor = BlockFormatFactory.getExtractor(block.getFormat());
                    try {
                        blockSizes.put(block.getHash(), extractor.blockSize(part,
                                encryptor.decodeBlock(storage, data, encryptionKey.getPrivateKeys(password))));
                    } catch (GeneralSecurityException e) {
                        log.error("Failed to decode block {}", block.getHash(), e);
                    }

                    break;
                } catch (IOException e) {
                    log.error("Failed to download backfill block \"{}\"", block.getHash());
                }
            }
            if (updateBlock) {
                pendingBlockUpdates.add(block);
            }
        }

        private void postPending() {
            while (!pendingBlockUpdates.isEmpty()) {
                BackupBlock updateBlock = pendingBlockUpdates.poll();
                try {
                    repository.addBlock(updateBlock);
                    debug(() -> log.debug("Updated block \"{}\" with encryption metadata", updateBlock.getHash()));
                } catch (IOException e) {
                    log.error("Failed to save update to block \"{}\"", updateBlock.getHash());
                }
                completedBlocks.incrementAndGet();
            }

            synchronized (pendingFileUpdates) {
                for (int i = 0; i < pendingFileUpdates.size(); ) {
                    BackupFile file = pendingFileUpdates.get(i);
                    boolean valid = true;
                    for (BackupLocation location : file.getLocations()) {
                        if (!processLocation(location)) {
                            valid = false;
                            break;
                        }
                    }
                    if (valid) {
                        pendingFileUpdates.remove(i);
                        try {
                            repository.addFile(file);
                            completedFiles.incrementAndGet();
                            debug(() -> log.debug("Updated file \"{}\" with block offsets",
                                    PathNormalizer.physicalPath(file.getPath())));
                        } catch (IOException e) {
                            log.error("Failed to save update to file \"{}\"", PathNormalizer.physicalPath(file.getPath()));
                        }
                    } else {
                        i++;
                    }
                }
            }
        }

        private boolean processLocation(BackupLocation location) {
            long offset = 0;
            for (int i = 1; i < location.getParts().size(); i++) {
                String blockHash = location.getParts().get(i - 1).getBlockHash();
                if (BackupBlock.isSuperBlock(blockHash)) {
                    try {
                        BackupBlock block = repository.block(blockHash);
                        if (block.getOffsets() == null || block.getOffsets().size() != block.getHashes().size()) {
                            List<Long> offsets = new ArrayList<>();
                            for (String hash : block.getHashes()) {
                                offsets.add(offset);
                                Long size = blockSizes.get(hash);
                                if (size == null) {
                                    return false;
                                }
                                offset += size;
                            }
                            block.setOffsets(offsets);
                            repository.addBlock(block);
                            debug(() -> log.debug("Updated superblock \"{}\" with offsets", block.getHash()));
                        }
                    } catch (IOException e) {
                        log.error("Failed to fetch block \"{}\"", blockHash);
                    }
                } else {
                    Long size = blockSizes.get(blockHash);
                    if (size == null) {
                        return false;
                    }
                    offset += size;
                }
                location.getParts().get(i).setOffset(offset);
            }
            return true;
        }

        @Override
        public void waitForCompletion() {
            super.waitForCompletion();
            postPending();
        }

        public void addInferredBlockSizes(BackupFile file) {
            if (file.getLocations() != null) {
                for (BackupLocation location : file.getLocations()) {
                    if (location.getParts().size() > 1) {
                        List<String> blockHashes = new ArrayList<>();
                        List<Long> offsets = new ArrayList<>();
                        for (BackupFilePart part : location.getParts()) {
                            if (BackupBlock.isSuperBlock(part.getBlockHash())) {
                                try {
                                    BackupBlock block = repository.block(part.getBlockHash());
                                    if (block == null || block.getOffsets() == null
                                            || block.getHashes().size() != block.getOffsets().size()) {
                                        offsets.add(null);
                                        break;
                                    }
                                    for (int i = 0; i < block.getHashes().size(); i++) {
                                        blockHashes.add(block.getHashes().get(i));
                                        offsets.add(block.getOffsets().get(i));
                                    }
                                } catch (IOException e) {
                                    offsets.add(null);
                                    break;
                                }
                            } else {
                                blockHashes.add(part.getBlockHash());
                                offsets.add(part.getOffset());
                            }
                        }
                        offsets.add(file.getLength());

                        long offset = 0;
                        for (int i = 0; i < blockHashes.size(); i++) {
                            Long nextOffset = offsets.get(i + 1);
                            if (nextOffset == null) {
                                break;
                            }
                            blockSizes.put(blockHashes.get(i), nextOffset - offset);
                            offset = nextOffset;
                        }
                    }
                }
            }
        }
    }
}
