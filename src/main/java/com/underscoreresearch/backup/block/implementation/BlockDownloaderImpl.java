package com.underscoreresearch.backup.block.implementation;

import static com.underscoreresearch.backup.utils.LogUtil.getThroughputStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.errorcorrection.ErrorCorrector;
import com.underscoreresearch.backup.errorcorrection.ErrorCorrectorFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.io.implementation.SchedulerImpl;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.utils.ManualStatusLogger;
import com.underscoreresearch.backup.utils.StateLogger;
import com.underscoreresearch.backup.utils.StatusLine;

@Slf4j
public class BlockDownloaderImpl extends SchedulerImpl implements BlockDownloader, ManualStatusLogger {
    private final BackupConfiguration configuration;
    private final RateLimitController rateLimitController;
    private final EncryptionIdentity encryptionIdentity;

    private final AtomicLong totalSize = new AtomicLong();
    private final AtomicLong totalCount = new AtomicLong();
    private final AtomicLong blockCount = new AtomicLong();

    public BlockDownloaderImpl(BackupConfiguration configuration,
                               RateLimitController rateLimitController,
                               MetadataRepository metadataRepository,
                               EncryptionIdentity encryptionIdentity,
                               int maximumConcurrency) {
        super(maximumConcurrency);

        StateLogger.addLogger(this);

        this.configuration = configuration;
        this.rateLimitController = rateLimitController;
        this.encryptionIdentity = encryptionIdentity;
    }

    @Override
    public byte[] downloadBlock(BackupBlock block, String password) throws IOException {

        for (int storageIndex = 0; storageIndex < block.getStorage().size(); storageIndex++) {
            BackupBlockStorage storage = block.getStorage().get(storageIndex);
            try {
                return EncryptorFactory.decodeBlock(storage, downloadEncryptedBlockStorage(block, storage, null),
                        encryptionIdentity.getPrivateKeys(password));
            } catch (Exception exc) {
                if (storageIndex == block.getStorage().size() - 1 || InstanceFactory.isShutdown()) {
                    throw new IOException("Failed to download block \"" + block.getHash() + "\" was unreadable", exc);
                }
            }
        }
        throw new IOException(String.format("No storage available for block \"%s\"", block.getHash()));
    }

    @Override
    public byte[] downloadEncryptedBlockStorage(BackupBlock block, BackupBlockStorage storage, Set<String> availableParts) throws IOException {
        byte[][] blockParts = new byte[storage.getParts().size()][];

        BackupDestination destination = configuration.getDestinations().get(storage.getDestination());
        IOProvider provider = IOProviderFactory.getProvider(destination);
        AtomicInteger completedParts = new AtomicInteger(0);
        ErrorCorrector corrector = ErrorCorrectorFactory.getCorrector(storage.getEc());
        int neededParts = corrector.getMinimumSufficientParts(storage);
        byte[] errorCorrected = null;
        final Set<Integer> pendingParts = new HashSet<>();

        List<Integer> remainingParts = new ArrayList<>();

        for (int i = 0; i < storage.getParts().size(); i++) {
            if (InstanceFactory.isShutdown()) {
                throw new IOException("Shutting down");
            }

            if (pendingParts.size() < neededParts && (availableParts == null || availableParts.contains(storage.getParts().get(i)))) {
                downloadPart(storage, blockParts, destination, provider, completedParts, pendingParts, i);
            } else {
                remainingParts.add(i);
            }
        }

        while (true) {
            synchronized (pendingParts) {
                while (!pendingParts.isEmpty()) {
                    if (InstanceFactory.isShutdown())
                        throw new IOException("Shutting down");
                    try {
                        pendingParts.wait(1000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            if (completedParts.get() >= neededParts) {
                try {
                    errorCorrected = corrector.decodeErrorCorrection(storage, Lists.newArrayList(blockParts));
                    break;
                } catch (Exception e) {
                    if (remainingParts.isEmpty()) {
                        throw new IOException("Failed to error correct \"" + block.getHash() + "\"", e);
                    }
                }
            }

            if (remainingParts.isEmpty() || InstanceFactory.isShutdown())
                break;

            Integer part = remainingParts.removeLast();
            downloadPart(storage, blockParts, destination, provider, completedParts, pendingParts, part);
        }

        if (errorCorrected == null) {
            throw new IOException("Failed to fetch enough data to restore block \"" + block.getHash() + "\"");
        }
        blockCount.incrementAndGet();
        return errorCorrected;
    }

    private void downloadPart(BackupBlockStorage storage,
                              byte[][] blockParts,
                              BackupDestination destination,
                              IOProvider provider,
                              AtomicInteger completedParts,
                              Set<Integer> pendingParts,
                              int partIndex) {
        Consumer<byte[]> consumer = data -> {
            synchronized (pendingParts) {
                if (data != null) {
                    blockParts[partIndex] = data;
                    totalCount.incrementAndGet();
                    totalSize.addAndGet(data.length);
                    rateLimitController.acquireDownloadPermits(destination, data.length);
                    completedParts.incrementAndGet();
                }
                pendingParts.remove(partIndex);
                pendingParts.notifyAll();
            }
        };

        synchronized (pendingParts) {
            pendingParts.add(partIndex);
        }
        schedule(() -> {
            try {
                consumer.accept(provider.download(storage.getParts().get(partIndex)));
            } catch (Throwable exc) {
                log.warn("Failed to download \"" + storage.getParts().get(partIndex) + "\" from \"" + storage.getDestination() + "\"", exc);
                consumer.accept(null);
            }
        });
    }

    @Override
    public void resetStatus() {
        super.resetDuration();
        blockCount.set(0);
        totalCount.set(0);
        totalSize.set(0);
    }

    @Override
    public List<StatusLine> status() {
        List<StatusLine> ret = getThroughputStatus(getClass(), "Downloaded", "objects", totalCount.get(),
                totalSize.get(), getDuration());
        if (blockCount.get() > 0) {
            ret.add(new StatusLine(getClass(), "DOWNLOADED_BLOCKS", "Downloaded blocks", blockCount.get()));
        }
        return ret;
    }
}
