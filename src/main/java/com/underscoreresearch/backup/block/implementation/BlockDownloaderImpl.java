package com.underscoreresearch.backup.block.implementation;

import static com.underscoreresearch.backup.utils.LogUtil.getThroughputStatus;

import java.io.IOException;
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
import com.underscoreresearch.backup.encryption.EncryptionKey;
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
import com.underscoreresearch.backup.utils.StatusLine;
import com.underscoreresearch.backup.utils.StatusLogger;

@Slf4j
public class BlockDownloaderImpl extends SchedulerImpl implements BlockDownloader, StatusLogger {
    private final BackupConfiguration configuration;
    private final RateLimitController rateLimitController;
    private final MetadataRepository metadataRepository;
    private final EncryptionKey key;

    private AtomicLong totalSize = new AtomicLong();
    private AtomicLong totalCount = new AtomicLong();
    private AtomicLong blockCount = new AtomicLong();

    public BlockDownloaderImpl(BackupConfiguration configuration,
                               RateLimitController rateLimitController,
                               MetadataRepository metadataRepository,
                               EncryptionKey key,
                               int maximumConcurrency) {
        super(maximumConcurrency);

        this.configuration = configuration;
        this.rateLimitController = rateLimitController;
        this.metadataRepository = metadataRepository;
        this.key = key;
    }

    @Override
    public byte[] downloadBlock(String blockHash, String passphrase) throws IOException {
        BackupBlock block = metadataRepository.block(blockHash);
        if (block == null) {
            throw new IOException(String.format("Trying to get unknown block %s", blockHash));
        }

        for (int storageIndex = 0; storageIndex < block.getStorage().size(); storageIndex++) {
            BackupBlockStorage storage = block.getStorage().get(storageIndex);
            try {
                return EncryptorFactory.decodeBlock(storage, downloadEncryptedBlockStorage(block, storage), key.getPrivateKey(passphrase));
            } catch (Exception exc) {
                if (storageIndex == block.getStorage().size() - 1 || InstanceFactory.isShutdown()) {
                    throw new IOException("Failed to download block " + block.getHash() + " was unreadable", exc);
                }
            }
        }
        throw new IOException(String.format("No storage available for block %s", blockHash));
    }

    public byte[] downloadEncryptedBlockStorage(BackupBlock block, BackupBlockStorage storage) throws IOException {
        byte[][] blockParts = new byte[storage.getParts().size()][];

        BackupDestination destination = configuration.getDestinations().get(storage.getDestination());
        IOProvider provider = IOProviderFactory.getProvider(destination);
        AtomicInteger completedParts = new AtomicInteger(0);
        ErrorCorrector corrector = ErrorCorrectorFactory.getCorrector(destination.getErrorCorrection());
        int neededParts = corrector.getMinimumSufficientParts(storage);
        byte[] errorCorrected = null;
        Set<Integer> pendingParts = new HashSet<>();

        int i = 0;
        while (i < storage.getParts().size() && i < neededParts) {
            if (InstanceFactory.isShutdown()) {
                throw new IOException("Shutting down");
            }

            downloadPart(storage, blockParts, destination, provider, completedParts, pendingParts, i);

            i++;
        }

        while (true) {
            synchronized (pendingParts) {
                while (pendingParts.size() > 0) {
                    if (InstanceFactory.isShutdown())
                        throw new IOException("Shutting down");
                    try {
                        pendingParts.wait(1000);
                    } catch (InterruptedException exc) {
                    }
                }
            }

            if (completedParts.get() >= neededParts) {
                try {
                    errorCorrected = corrector.decodeErrorCorrection(storage, Lists.newArrayList(blockParts));
                    break;
                } catch (Exception e) {
                    if (i == storage.getParts().size() - 1) {
                        throw new IOException("Failed to error correct " + block.getHash(), e);
                    }
                }
            }

            if (i >= storage.getParts().size() || InstanceFactory.isShutdown())
                break;

            downloadPart(storage, blockParts, destination, provider, completedParts, pendingParts, i);
            i++;
        }

        if (errorCorrected == null) {
            throw new IOException("Failed to fetch enough data to restore it");
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
                log.warn("Failed to download " + storage.getParts().get(partIndex) + " from " + storage.getDestination(), exc);
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
