package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.utils.LogUtil.readableEta;
import static com.underscoreresearch.backup.utils.LogUtil.readableNumber;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.errorcorrection.ErrorCorrectorFactory;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupLocation;
import com.underscoreresearch.backup.utils.StatusLine;
import com.underscoreresearch.backup.utils.StatusLogger;

@Slf4j
@RequiredArgsConstructor
public class BlockValidator implements StatusLogger {
    private final MetadataRepository repository;
    private final BackupConfiguration configuration;
    private final ManifestManager manifestManager;
    private final int maxBlockSize;

    private Stopwatch stopwatch = Stopwatch.createUnstarted();
    private AtomicLong processedSteps = new AtomicLong();
    private AtomicLong totalSteps = new AtomicLong();
    private Duration lastHeartbeat;

    public void validateBlocks() throws IOException {
        stopwatch.start();
        lastHeartbeat = Duration.ZERO;

        log.info("Validating all blocks of files");
        manifestManager.setDisabledFlushing(true);
        try (CloseableLock ignored = repository.acquireLock()) {
            totalSteps.set(repository.getFileCount());
            repository.allFiles(false).forEach((file) -> {
                if (InstanceFactory.isShutdown())
                    throw new RuntimeException(new InterruptedException());

                processedSteps.incrementAndGet();
                if (lastHeartbeat.toMinutes() != stopwatch.elapsed().toMinutes()) {
                    lastHeartbeat = stopwatch.elapsed();
                    log.info("Processing path {}", file.getPath());
                }

                if (file.getLocations() != null) {
                    AtomicLong maximumSize = new AtomicLong();
                    List<BackupLocation> validCollections = file.getLocations().stream().filter(location -> {
                        for (BackupFilePart part : location.getParts()) {
                            try {
                                if (!validateHash(repository, part.getBlockHash(), maximumSize, maxBlockSize)) {
                                    return false;
                                }
                            } catch (IOException e) {
                                log.error("Failed to read block " + part.getBlockHash(), e);
                                return false;
                            }
                        }
                        if (maximumSize.get() < file.getLength()) {
                            log.error("Not enough blocks to contain entire file size ({} < {})",
                                    readableSize(maximumSize.get()), readableSize(file.getLength()));
                            return false;
                        }
                        return true;
                    }).collect(Collectors.toList());

                    try {
                        if (validCollections.size() != file.getLocations().size()) {
                            if (validCollections.size() == 0) {
                                log.error("Storage for {} does no longer exist", file.getPath());
                                repository.deleteFile(file);
                            } else {
                                log.warn("At least one location for {} no longer exists", file.getPath());
                                file.setLocations(validCollections);
                                repository.addFile(file);
                            }
                        }
                    } catch (IOException e) {
                        log.error("Failed to delete missing file {}", file.getPath());
                    }
                }
            });

            log.info("Completed block validation");
        } catch (RuntimeException exc) {
            manifestManager.setDisabledFlushing(false);
            if (exc.getCause() instanceof InterruptedException) {
                log.info("Cancelled processing");
            } else {
                throw exc;
            }
        }
    }

    private boolean validateHash(MetadataRepository repository, String blockHash, AtomicLong maximumSize,
                                 int maxBlockSize) throws IOException {
        BackupBlock block = repository.block(blockHash);
        if (block == null) {
            log.error("Block hash {} does not exist", blockHash);
            return false;
        }
        if (block.isSuperBlock()) {
            if (block.getHashes() == null) {
                log.error("Super block {} is missing hashes", block.getHash());
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
            if (!validateBlockStorage(block)) {
                return false;
            }
        }
        return true;
    }

    private boolean validateBlockStorage(BackupBlock block) {
        boolean anyChange = false;
        for (int i = 0; i < block.getStorage().size(); i++) {
            BackupBlockStorage storage = block.getStorage().get(i);
            try {
                EncryptorFactory.getEncryptor(storage.getEncryption());
            } catch (IllegalArgumentException exc) {
                log.warn("Found invalid encryption {} for block {}", storage.getEncryption(), block.getHash());
                anyChange = true;
                block.getStorage().remove(i);
                continue;
            }
            try {
                ErrorCorrectorFactory.getCorrector(storage.getEc());
            } catch (IllegalArgumentException exc) {
                log.warn("Found invalid error correction {} for block {}", storage.getEc(), block.getHash());
                anyChange = true;
                block.getStorage().remove(i);
                continue;
            }
            if (storage.getParts().stream().anyMatch(part -> part == null)) {
                log.warn("Block hash {} has missing parts", block.getHash());
                anyChange = true;
                block.getStorage().remove(i);
                continue;
            }
            i++;
        }
        if (anyChange) {
            try {
                if (block.getStorage().size() > 0) {
                    repository.addBlock(block);
                    return true;
                } else {
                    repository.deleteBlock(block);
                    return false;
                }
            } catch (IOException e) {
                log.error("Could not save updated block {}", block.getHash(), e);
            }
        }
        return block.getStorage().size() > 0;
    }

    @Override
    public void resetStatus() {
        stopwatch.reset();
        processedSteps.set(0L);
    }

    @Override
    public List<StatusLine> status() {
        if (stopwatch.isRunning()) {
            long elapsedMilliseconds = stopwatch.elapsed().toMillis();
            if (elapsedMilliseconds > 0) {
                long throughput = 1000 * processedSteps.get() / elapsedMilliseconds;
                return Lists.newArrayList(
                        new StatusLine(getClass(), "VALIDATE_THROUGHPUT", "Validating blocks throughput",
                                throughput, readableNumber(throughput) + " steps/s"),
                        new StatusLine(getClass(), "VALIDATE_STEPS", "Validating blocks steps completed",
                                processedSteps.get(), totalSteps.get(),
                                readableNumber(processedSteps.get()) + " / "
                                        + readableNumber(totalSteps.get()) + " steps"
                                        + readableEta(processedSteps.get(), totalSteps.get(),
                                        Duration.ofMillis(elapsedMilliseconds))));
            }
        }
        return new ArrayList<>();
    }
}
