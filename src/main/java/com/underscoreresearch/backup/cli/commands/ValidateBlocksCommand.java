package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.configuration.RestoreModule.DOWNLOAD_THREADS;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;

import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.implementation.SchedulerImpl;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;

@CommandPlugin(value = "validate-blocks", description = "Validate that all used blocks for files exists",
        needPrivateKey = false, needConfiguration = true, readonlyRepository = false)
@Slf4j
public class ValidateBlocksCommand extends SimpleCommand {
    public static class BackfillDownloader extends SchedulerImpl {
        private final BlockDownloader blockDownloader;
        private final MetadataRepository repository;
        @Getter
        private AtomicLong completed = new AtomicLong();
        @Getter
        private AtomicLong processed = new AtomicLong();

        private ConcurrentLinkedQueue<BackupBlock> pendingUpdates = new ConcurrentLinkedQueue<>();

        @Inject
        public BackfillDownloader(@Named(DOWNLOAD_THREADS) int maximumConcurrency, BlockDownloader blockDownloader,
                                  MetadataRepository repository) {
            super(maximumConcurrency);

            this.blockDownloader = blockDownloader;
            this.repository = repository;
        }

        public void backfillStorage(BackupBlock block) {
            if (block.getStorage().stream().anyMatch(storage -> !EncryptorFactory.getEncryptor(storage.getEncryption()).validStorage(storage))) {
                schedule(() -> {
                    for (BackupBlockStorage storage : block.getStorage()) {
                        try {
                            Encryptor encryptor = EncryptorFactory.getEncryptor(storage.getEncryption());
                            if (!encryptor.validStorage(storage)) {
                                byte[] data = blockDownloader.downloadEncryptedBlockStorage(block, storage);
                                encryptor.backfillEncryption(storage, data);
                            }
                        } catch (IOException e) {
                            log.error("Failed to download backfill block {}", block.getHash());
                        }
                    }
                    pendingUpdates.add(block);
                });
            }
            postPending();
            processed.incrementAndGet();
        }

        private void postPending() {
            while (pendingUpdates.size() > 0) {
                BackupBlock updateBlock = pendingUpdates.poll();
                try {
                    repository.addBlock(updateBlock);
                } catch (IOException e) {
                    log.error("Failed to save update to block {}", updateBlock.getHash());
                }
                completed.incrementAndGet();
            }
        }

        @Override
        public void waitForCompletion() {
            super.waitForCompletion();
            postPending();
        }
    }

    public void executeCommand() throws Exception {
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);

        InstanceFactory.getInstance(BlockValidator.class)
                .validateBlocks();

        Stopwatch stopwatch = Stopwatch.createStarted();
        AtomicLong lastDuration = new AtomicLong(0);
        if (InstanceFactory.getInstance(CommandLine.class).hasOption(CommandLineModule.BACKFILL_ENCRYPTION)) {
            log.info("Backfilling encryption to block storage");
            BackfillDownloader backfillDownloader = InstanceFactory.getInstance(BackfillDownloader.class);
            try (CloseableLock ignore = repository.acquireLock()) {
                repository.allBlocks().forEach(block -> {
                    backfillDownloader.backfillStorage(block);
                    long currentMinute = stopwatch.elapsed(TimeUnit.MINUTES);
                    if (currentMinute != lastDuration.get()) {
                        log.info("Processed {} blocks, fixed {} blocks so far", backfillDownloader.getProcessed().get(),
                                backfillDownloader.getCompleted().get());
                        lastDuration.set(currentMinute);
                    }
                });
                backfillDownloader.waitForCompletion();
            }
            log.info("Updated {} blocks", backfillDownloader.getCompleted().get());
        }

        repository.flushLogging();
        manifestManager.shutdown();
        repository.close();
    }

}
