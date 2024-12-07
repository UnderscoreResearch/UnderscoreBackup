package com.underscoreresearch.backup.cli.helpers;

import static com.underscoreresearch.backup.utils.LogUtil.lastProcessedPath;
import static com.underscoreresearch.backup.utils.LogUtil.readableEta;
import static com.underscoreresearch.backup.utils.LogUtil.readableNumber;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import com.underscoreresearch.backup.file.implementation.BackupStatsLogger;
import com.underscoreresearch.backup.io.IOUtils;
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
    private final DestinationBlockProcessor destinationBlockProcessor;
    private final BackupStatsLogger backupStatsLogger;
    private final int maxBlockSize;
    private final String manifestLocation;
    private final Stopwatch stopwatch = Stopwatch.createUnstarted();
    private final AtomicLong processedSteps = new AtomicLong();
    private final AtomicLong totalSteps = new AtomicLong();
    private final AtomicLong totalBlocks = new AtomicLong();
    private Duration lastHeartbeat;
    private BackupFile currentlyProcessing;
    private BackupFile lastProcessed;


    public BlockValidator(MetadataRepository repository, BackupConfiguration configuration,
                          ManifestManager manifestManager, DestinationBlockProcessor destinationBlockProcessor,
                          BackupStatsLogger backupStatsLogger, int maxBlockSize, String manifestLocation) {
        StateLogger.addLogger(this);

        this.repository = repository;
        this.configuration = configuration;
        this.manifestManager = manifestManager;
        this.destinationBlockProcessor = destinationBlockProcessor;
        this.maxBlockSize = maxBlockSize;
        this.backupStatsLogger = backupStatsLogger;
        this.manifestLocation = manifestLocation;
    }

    public void validateBlocks(boolean validateDestination) throws IOException {
        stopwatch.start();
        lastHeartbeat = Duration.ZERO;

        log.info("Validating all blocks of files");
        manifestManager.setDisabledFlushing(true);
        backupStatsLogger.setDownloadRunning(true);

        String ignoreBefore = null;
        if (validateDestination) {
            File file = getCancelledCheckpointFile();
            if (file.exists()) {
                try (FileInputStream stream = new FileInputStream(file)) {
                    ignoreBefore = new String(IOUtils.readAllBytes(stream), StandardCharsets.UTF_8);
                    log.info("Resuming from path \"{}\"", PathNormalizer.physicalPath(ignoreBefore));
                } catch (IOException e) {
                    log.error("Failed to read progress file", e);
                }
            }
        }

        try (Closeable ignored = UIHandler.registerTask(VALIDATE_BLOCKS_TASK, true)) {
            totalSteps.set(repository.getFileCount());
            processedSteps.set(0L);
            destinationBlockProcessor.resetProgress();
            totalBlocks.set(validateDestination ? repository.getBlockCount() : 0L);

            validateBlocksInternal(validateDestination, ignoreBefore);

            if (!InstanceFactory.isShutdown()) {
                if (validateDestination && destinationBlockProcessor.getMissingBlocks() > 0) {
                    log.warn("Found {} missing blocks in destinations. Checking if any files are now invalid",
                            readableNumber(destinationBlockProcessor.getMissingBlocks()));
                    totalSteps.set(totalSteps.get() + repository.getFileCount());
                    validateBlocksInternal(false, null);
                } else if (destinationBlockProcessor.getRefreshedBlocks() > 0) {
                    log.info("Completed block validation and refreshed {} blocks",
                            readableNumber(destinationBlockProcessor.getRefreshedBlocks()));
                } else {
                    log.info("Completed block validation");
                }
            }
        } finally {
            backupStatsLogger.setDownloadRunning(false);
        }
    }

    private void validateBlocksInternal(boolean validateDestination, String ignoreBefore) {
        try (CloseableStream<BackupFile> files = repository.allFiles(true)) {
            try {
                files.stream().forEach((file) -> {
                    processedSteps.incrementAndGet();
                    currentlyProcessing = file;

                    if (ignoreBefore != null && file.getPath().compareTo(ignoreBefore) < 0) {
                        return;
                    }

                    if (InstanceFactory.isShutdown())
                        throw new ProcessingStoppedException();

                    if (lastHeartbeat.toMinutes() != stopwatch.elapsed().toMinutes()) {
                        lastHeartbeat = stopwatch.elapsed();
                        log.info("Processing path \"{}\"", PathNormalizer.physicalPath(file.getPath()));
                        if (validateDestination) {
                            saveCancelledCheckpoint();
                        }
                    }

                    if (file.getLocations() != null) {
                        AtomicLong maximumSize = new AtomicLong();
                        List<BackupLocation> validCollections = file.getLocations().stream().filter(location -> {
                            for (BackupFilePart part : location.getParts()) {
                                try {
                                    if (!validateHash(repository, part.getBlockHash(), maximumSize, maxBlockSize,
                                            validateDestination)) {
                                        return false;
                                    }
                                } catch (IOException e) {
                                    log.warn("Failed to read block \"" + part.getBlockHash() + "\"", e);
                                    return false;
                                }
                            }
                            if (maximumSize.get() < file.getLength()) {
                                log.warn("Not enough blocks to contain entire file size (\u200E{}\u200E < \u200E{}\u200E)",
                                        readableSize(maximumSize.get()), readableSize(file.getLength()));
                                return false;
                            }
                            return true;
                        }).collect(Collectors.toList());

                        try {
                            if (validCollections.size() != file.getLocations().size()) {
                                if (validCollections.isEmpty()) {
                                    log.error("Storage for \"{}\" does no longer exist removing from repository",
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
                    lastProcessed = file;
                });
                File file = getCancelledCheckpointFile();
                if (file.exists() && !file.delete()) {
                    log.warn("Failed to delete progress file");
                }
            } catch (ProcessingStoppedException exc) {
                if (validateDestination && lastProcessed != null) {
                    saveCancelledCheckpoint();
                }

                manifestManager.setDisabledFlushing(false);
                destinationBlockProcessor.waitForCompletion();
                log.info("Cancelled processing");
            }
        } catch (Throwable e) {
            manifestManager.setDisabledFlushing(false);
        }

        destinationBlockProcessor.waitForCompletion();
    }

    private void saveCancelledCheckpoint() {
        File file = getCancelledCheckpointFile();
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write(lastProcessed.getPath());
            log.info("Stored last processed path \"{}\"", lastProcessed.getPath());
        } catch (IOException e) {
            log.error("Failed to write progress to file", e);
        }
    }

    private File getCancelledCheckpointFile() {
        return new File(manifestLocation, "validationrecovery.txt");
    }

    private boolean validateHash(MetadataRepository repository, String blockHash, AtomicLong maximumSize,
                                 int maxBlockSize, boolean validateDestination) throws IOException {
        BackupBlock block = repository.block(blockHash);
        if (block == null) {
            log.warn("Block hash \"{}\" does not exist", blockHash);
            return false;
        }
        if (block.isSuperBlock()) {
            if (block.getHashes() == null) {
                log.warn("Super block \"{}\" is missing hashes", block.getHash());
                return false;
            } else {
                for (String hash : block.getHashes()) {
                    validateHash(repository, hash, maximumSize, maxBlockSize, validateDestination);
                }
            }
        } else {
            if (maximumSize != null) {
                maximumSize.addAndGet(maxBlockSize);
            }
            return validateBlockStorage(block, validateDestination);
        }
        return true;
    }

    private boolean validateBlockStorage(BackupBlock block, boolean validateDestination) {
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
                log.warn("Block \"{}\" referencing missing destination \"{}\"", block.getHash(), storage.getDestination());
            } else if (validateDestination) {
                try {
                    destinationBlockProcessor.validateBlockStorage(block, block.getStorage());
                } catch (IOException e) {
                    log.error("Failed to validate block storage \"{}\"", block.getHash(), e);
                }
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
            if (needsRefresh != null && !needsRefresh.isEmpty()) {
                try {
                    if (!destinationBlockProcessor.refreshStorage(block, needsRefresh)) {
                        if (anyChange) {
                            repository.addBlock(block);
                        }
                    }
                } catch (IOException e) {
                    log.error("Failed to refresh block \"{}\"", block.getHash(), e);
                }
            } else {
                try {
                    if (!block.getStorage().isEmpty()) {
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
        return !block.getStorage().isEmpty();
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
                        new StatusLine(getClass(), "VALIDATE_THROUGHPUT", "Validating files throughput",
                                throughput, readableNumber(throughput) + " files/s"),
                        new StatusLine(getClass(), "VALIDATE_STEPS", "Validating file blocks",
                                processedSteps.get(), totalSteps.get(),
                                readableNumber(processedSteps.get()) + " / "
                                        + readableNumber(totalSteps.get()) + " files"
                                        + (totalBlocks.get() > 0 ?  "" :
                                        readableEta(processedSteps.get(), totalSteps.get(), Duration.ofMillis(elapsedMilliseconds)))));
                if (destinationBlockProcessor.getRefreshedBlocks() > 0) {
                    ret.add(new StatusLine(getClass(), "VALIDATE_REFRESH", "Refreshed storage blocks",
                            destinationBlockProcessor.getRefreshedBlocks(), readableNumber(destinationBlockProcessor.getRefreshedBlocks())));
                    ret.add(new StatusLine(getClass(), "VALIDATE_REFRESH_SIZE", "Refreshed storage uploaded",
                            destinationBlockProcessor.getRefreshedUploadSize(), readableSize(destinationBlockProcessor.getRefreshedUploadSize())));
                }
                if (totalBlocks.get() > 0) {
                    ret.add(new StatusLine(getClass(), "VALIDATE_DESTINATION_BLOCKS", "Validating destination blocks",
                            destinationBlockProcessor.getValidatedBlocks(), totalBlocks.get(),
                            readableNumber(destinationBlockProcessor.getValidatedBlocks()) + " / "
                                    + readableNumber(totalBlocks.get()) + " blocks"
                                    + readableEta(destinationBlockProcessor.getValidatedBlocks(), totalBlocks.get(),
                                    Duration.ofMillis(elapsedMilliseconds))));
                    ret.add(new StatusLine(getClass(), "VALIDATE_BLOCKS_THROUGHPUT", "Validating blocks throughput",
                                    throughput, readableNumber(1000 * destinationBlockProcessor.getValidatedBlocks() /
                            elapsedMilliseconds) + " blocks/s"));
                    ret.add(new StatusLine(getClass(), "VALIDATE_MISSING_DESTINATION_BLOCKS", "Missing destination blocks",
                            destinationBlockProcessor.getMissingBlocks(), readableNumber(destinationBlockProcessor.getMissingBlocks())));
                }
                lastProcessedPath(getClass(), ret, currentlyProcessing, "VALIDATE_PROCESSED_PATH");
                return ret;
            }
        }
        return new ArrayList<>();
    }
}
