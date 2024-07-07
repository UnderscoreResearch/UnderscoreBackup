package com.underscoreresearch.backup.cli.helpers;

import static com.underscoreresearch.backup.utils.LogUtil.lastProcessedPath;
import static com.underscoreresearch.backup.utils.LogUtil.readableEta;
import static com.underscoreresearch.backup.utils.LogUtil.readableNumber;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.cli.ui.UIHandler;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.errorcorrection.ErrorCorrectorFactory;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupLocation;
import com.underscoreresearch.backup.utils.ManualStatusLogger;
import com.underscoreresearch.backup.utils.ProcessingStoppedException;
import com.underscoreresearch.backup.utils.StateLogger;
import com.underscoreresearch.backup.utils.StatusLine;

@Slf4j
public class BlockValidator implements ManualStatusLogger {
    public static String VALIDATE_BLOCKS_TASK = "Upgrading metadata repository";
    private final MetadataRepository repository;
    private final BackupConfiguration configuration;
    private final ManifestManager manifestManager;
    private final BlockRefresher blockRefresher;
    private final int maxBlockSize;
    private final Stopwatch stopwatch = Stopwatch.createUnstarted();
    private final AtomicLong processedSteps = new AtomicLong();
    private final AtomicLong totalSteps = new AtomicLong();
    private Duration lastHeartbeat;
    private BackupFile lastProcessed;


    public BlockValidator(MetadataRepository repository, BackupConfiguration configuration,
                          ManifestManager manifestManager, BlockRefresher blockRefresher, int maxBlockSize) {
        StateLogger.addLogger(this);

        this.repository = repository;
        this.configuration = configuration;
        this.manifestManager = manifestManager;
        this.blockRefresher = blockRefresher;
        this.maxBlockSize = maxBlockSize;
    }

    public void validateBlocks() throws IOException {
        stopwatch.start();
        lastHeartbeat = Duration.ZERO;

        log.info("Validating all blocks of files");
        manifestManager.setDisabledFlushing(true);
        try (Closeable ignored = UIHandler.registerTask(VALIDATE_BLOCKS_TASK, true)) {
            try (CloseableStream<BackupFile> files = repository.allFiles(false)) {
                totalSteps.set(repository.getFileCount());
                try {
                    files.stream().forEach((file) -> {
                        if (InstanceFactory.isShutdown())
                            throw new ProcessingStoppedException();

                        processedSteps.incrementAndGet();
                        if (lastHeartbeat.toMinutes() != stopwatch.elapsed().toMinutes()) {
                            lastHeartbeat = stopwatch.elapsed();
                            log.info("Processing path \"{}\"", PathNormalizer.physicalPath(file.getPath()));
                        }
                        lastProcessed = file;

                        if (file.getLocations() != null) {
                            AtomicLong maximumSize = new AtomicLong();
                            List<BackupLocation> validCollections = file.getLocations().stream().filter(location -> {
                                for (BackupFilePart part : location.getParts()) {
                                    try {
                                        if (!validateHash(repository, part.getBlockHash(), maximumSize, maxBlockSize)) {
                                            return false;
                                        }
                                    } catch (IOException e) {
                                        log.error("Failed to read block \"" + part.getBlockHash() + "\"", e);
                                        return false;
                                    }
                                }
                                if (maximumSize.get() < file.getLength()) {
                                    log.error("Not enough blocks to contain entire file size (\u200E{}\u200E < \u200E{}\u200E)",
                                            readableSize(maximumSize.get()), readableSize(file.getLength()));
                                    return false;
                                }
                                return true;
                            }).collect(Collectors.toList());

                            try {
                                if (validCollections.size() != file.getLocations().size()) {
                                    if (validCollections.isEmpty()) {
                                        log.error("Storage for \"{}\" does no longer exist",
                                                PathNormalizer.physicalPath(file.getPath()));
                                        repository.deleteFile(file);
                                    } else {
                                        log.warn("At least one location for \"{}\" no longer exists",
                                                PathNormalizer.physicalPath(file.getPath()));
                                        file.setLocations(validCollections);
                                        repository.addFile(file);
                                    }
                                }
                            } catch (IOException e) {
                                log.error("Failed to delete missing file \"{}\"", PathNormalizer.physicalPath(file.getPath()));
                            }
                        }
                    });

                    blockRefresher.waitForCompletion();

                    if (blockRefresher.getRefreshedBlocks() > 0) {
                        log.info("Completed block validation and refreshed {} blocks",
                                readableNumber(blockRefresher.getRefreshedBlocks()));
                    } else {
                        log.info("Completed block validation");
                    }
                } catch (ProcessingStoppedException exc) {
                    manifestManager.setDisabledFlushing(false);
                    blockRefresher.waitForCompletion();
                    log.info("Cancelled processing");
                }
            } catch (Throwable e) {
                manifestManager.setDisabledFlushing(false);
            }
        }
    }

    private boolean validateHash(MetadataRepository repository, String blockHash, AtomicLong maximumSize,
                                 int maxBlockSize) throws IOException {
        BackupBlock block = repository.block(blockHash);
        if (block == null) {
            log.error("Block hash \"{}\" does not exist", blockHash);
            return false;
        }
        if (block.isSuperBlock()) {
            if (block.getHashes() == null) {
                log.error("Super block \"{}\" is missing hashes", block.getHash());
                return false;
            } else {
                for (String hash : block.getHashes()) {
                    validateHash(repository, hash, maximumSize, maxBlockSize);
                }
            }
        } else {
            if (maximumSize != null) {
                maximumSize.addAndGet(maxBlockSize);
            }
            return validateBlockStorage(block);
        }
        return true;
    }

    private boolean validateBlockStorage(BackupBlock block) {
        boolean anyChange = false;
        List<BackupBlockStorage> needsRefresh = null;
        for (int i = 0; i < block.getStorage().size(); ) {
            BackupBlockStorage storage = block.getStorage().get(i);
            try {
                EncryptorFactory.getEncryptor(storage.getEncryption());
            } catch (IllegalArgumentException exc) {
                log.warn("Found invalid encryption \"{}\" for block \"{}\"", storage.getEncryption(), block.getHash());
                anyChange = true;
                block.getStorage().remove(i);
                continue;
            }
            try {
                ErrorCorrectorFactory.getCorrector(storage.getEc());
            } catch (IllegalArgumentException exc) {
                log.warn("Found invalid error correction \"{}\" for block \"{}\"", storage.getEc(), block.getHash());
                anyChange = true;
                block.getStorage().remove(i);
                continue;
            }
            if (storage.getParts().stream().anyMatch(Objects::isNull)) {
                log.warn("Block hash \"{}\" has missing parts", block.getHash());
                anyChange = true;
                block.getStorage().remove(i);
                continue;
            }

            BackupDestination destination = configuration.getDestinations().get(storage.getDestination());
            if (destination == null) {
                log.error("Block \"{}\" referencing missing destination \"{}\"", block.getHash(), storage.getDestination());
            } else if (destination.getMaxRetention() != null) {
                long created;
                if (storage.getCreated() != null)
                    created = storage.getCreated();
                else
                    created = block.getCreated();

                if (destination.getMaxRetention().toEpochMilli() > created) {
                    if (needsRefresh == null) {
                        needsRefresh = new ArrayList<>();
                    }
                    needsRefresh.add(storage);
                }
            }
            i++;
        }
        if (anyChange || needsRefresh != null) {
            if (needsRefresh != null && needsRefresh.size() > 0) {
                try {
                    if (!blockRefresher.refreshStorage(block, needsRefresh)) {
                        if (anyChange) {
                            repository.addBlock(block);
                        }
                    }
                } catch (IOException e) {
                    log.error("Failed to refresh block \"{}\"", block.getHash(), e);
                }
            } else {
                try {
                    if (block.getStorage().size() > 0) {
                        repository.addBlock(block);
                        return true;
                    } else {
                        repository.deleteBlock(block);
                        return false;
                    }
                } catch (IOException e) {
                    log.error("Could not save updated block \"{}\"", block.getHash(), e);
                }
            }
        }
        return block.getStorage().size() > 0;
    }

    @Override
    public void resetStatus() {
        stopwatch.reset();
        processedSteps.set(0L);
        lastProcessed = null;
    }

    @Override
    public List<StatusLine> status() {
        if (stopwatch.isRunning()) {
            long elapsedMilliseconds = stopwatch.elapsed().toMillis();
            if (elapsedMilliseconds > 0) {
                long throughput = 1000 * processedSteps.get() / elapsedMilliseconds;
                List<StatusLine> ret = Lists.newArrayList(
                        new StatusLine(getClass(), "VALIDATE_THROUGHPUT", "Validating blocks throughput",
                                throughput, readableNumber(throughput) + " steps/s"),
                        new StatusLine(getClass(), "VALIDATE_STEPS", "Validating blocks",
                                processedSteps.get(), totalSteps.get(),
                                readableNumber(processedSteps.get()) + " / "
                                        + readableNumber(totalSteps.get()) + " steps"
                                        + readableEta(processedSteps.get(), totalSteps.get(),
                                        Duration.ofMillis(elapsedMilliseconds))));
                if (blockRefresher.getRefreshedBlocks() > 0) {
                    ret.add(new StatusLine(getClass(), "VALIDATE_REFRESH", "Refreshed storage blocks",
                            blockRefresher.getRefreshedBlocks(), readableNumber(blockRefresher.getRefreshedBlocks())));
                    ret.add(new StatusLine(getClass(), "VALIDATE_REFRESH_SIZE", "Refreshed storage uploaded",
                            blockRefresher.getRefreshedUploadSize(), readableSize(blockRefresher.getRefreshedUploadSize())));
                }
                lastProcessedPath(getClass(), ret, lastProcessed, "VALIDATE_PROCESSED_PATH");
                return ret;
            }
        }
        return new ArrayList<>();
    }
}
