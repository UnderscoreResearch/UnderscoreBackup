package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.utils.LogUtil.readableEta;
import static com.underscoreresearch.backup.utils.LogUtil.readableNumber;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.MetadataRepositoryStorage;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockAdditional;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupPendingSet;
import com.underscoreresearch.backup.model.BackupUpdatedFile;
import com.underscoreresearch.backup.utils.ManualStatusLogger;
import com.underscoreresearch.backup.utils.StateLogger;
import com.underscoreresearch.backup.utils.StatusLine;

@Slf4j
public class RepositoryUpgrader implements ManualStatusLogger {
    private final MetadataRepositoryStorage storage;
    private final MetadataRepositoryStorage updatedStorage;

    private final AtomicLong totalSteps = new AtomicLong(0);
    private final AtomicLong currentStep = new AtomicLong(0);
    private final Stopwatch stopwatch = Stopwatch.createUnstarted();

    public RepositoryUpgrader(MetadataRepositoryStorage storage, MetadataRepositoryStorage upgradedStorage) {
        this.storage = storage;
        this.updatedStorage = upgradedStorage;
    }

    public void upgrade() throws IOException {
        updatedStorage.clear();
        updatedStorage.open(false);

        try (CloseableLock ignored = storage.exclusiveLock()) {
            try (CloseableLock ignored2 = updatedStorage.exclusiveLock()) {

                stopwatch.start();

                StateLogger.addLogger(this);

                totalSteps.set(storage.getBlockCount() + storage.getFileCount() + storage.getAdditionalBlockCount()
                        + storage.getDirectoryCount() + storage.getPartCount() + storage.getUpdatedFileCount());

                log.info("Started metadata upgrade");
                {
                    Set<BackupPendingSet> pendingSets = storage.getPendingSets();
                    totalSteps.addAndGet(pendingSets.size());

                    pendingSets.forEach(set -> {
                        try {
                            currentStep.incrementAndGet();
                            updatedStorage.addPendingSets(set);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }


                try (CloseableStream<BackupBlock> blocks = storage.allBlocks()) {
                    blocks.stream().forEach(block -> {
                        try {
                            currentStep.incrementAndGet();
                            updatedStorage.addBlock(block);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }

                try (CloseableStream<BackupBlockAdditional> blocks = storage.allAdditionalBlocks()) {
                    blocks.stream().forEach(block -> {
                        try {
                            currentStep.incrementAndGet();
                            updatedStorage.addAdditionalBlock(block);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }

                try (CloseableStream<BackupFile> files = storage.allFiles(true)) {
                    files.stream().forEach(file -> {
                        try {
                            currentStep.incrementAndGet();
                            updatedStorage.addFile(file);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }

                try (CloseableStream<BackupFilePart> parts = storage.allFileParts()) {
                    parts.stream().forEach(part -> {
                        try {
                            currentStep.incrementAndGet();
                            updatedStorage.addFilePart(part);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }

                try (CloseableStream<BackupDirectory> dirs = storage.allDirectories(true)) {
                    dirs.stream().forEach(dir -> {
                        try {
                            currentStep.incrementAndGet();
                            updatedStorage.addDirectory(dir);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }

                try (CloseableStream<BackupUpdatedFile> files = storage.getUpdatedFiles()) {
                    files.stream().forEach(file -> {
                        try {
                            currentStep.incrementAndGet();
                            updatedStorage.addUpdatedFile(file, -1);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }
        } catch (RuntimeException e) {
            throw new IOException("Failed to upgrade", e);
        }

        stopwatch.stop();
        log.info("Successfully completed metadata upgrade");

        StateLogger.removeLogger(this);
    }

    @Override
    public void resetStatus() {
        currentStep.set(0);
        totalSteps.set(0);
        stopwatch.reset();
    }

    @Override
    public List<StatusLine> status() {
        int elapsedMilliseconds = (int) stopwatch.elapsed(TimeUnit.MILLISECONDS);
        if (elapsedMilliseconds > 0) {
            long throughput = 1000 * currentStep.get() / elapsedMilliseconds;
            return Lists.newArrayList(
                    new StatusLine(getClass(), "UPGRADE_PROCESSED_STEPS", "Repository upgrade",
                            currentStep.get(), totalSteps.get(),
                            readableNumber(currentStep.get()) + " / " + readableNumber(totalSteps.get()) + " steps"
                                    + readableEta(currentStep.get(), totalSteps.get(),
                                    stopwatch.elapsed())),
                    new StatusLine(getClass(), "UPGRADE_THROUGHPUT", "Repository upgrade throughput",
                            throughput, readableNumber(throughput) + " steps/s")
            );
        }
        return new ArrayList<>();
    }
}
