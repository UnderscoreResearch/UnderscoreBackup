package com.underscoreresearch.backup.cli.helpers;

import static com.underscoreresearch.backup.utils.LogUtil.debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.base.Stopwatch;
import com.underscoreresearch.backup.io.IOIndex;
import lombok.extern.slf4j.Slf4j;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.errorcorrection.ErrorCorrector;
import com.underscoreresearch.backup.errorcorrection.ErrorCorrectorFactory;
import com.underscoreresearch.backup.file.CloseableMap;
import com.underscoreresearch.backup.file.MapSerializer;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.io.implementation.SchedulerImpl;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;

@Slf4j
public class DestinationBlockProcessor extends SchedulerImpl {
    private final BlockDownloader blockDownloader;
    private final UploadScheduler uploadScheduler;
    private final BackupConfiguration configuration;
    private final MetadataRepository repository;
    private final ConcurrentLinkedQueue<BackupBlock> pendingBlockUpdates = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<BackupBlock> pendingBlockDeletes = new ConcurrentLinkedQueue<>();
    private final AtomicLong refreshedBlocks = new AtomicLong();
    private final AtomicLong validatedBlocks = new AtomicLong();
    private final AtomicLong missingBlocks = new AtomicLong();
    private final AtomicLong uploadedSize = new AtomicLong();
    private final AtomicLong missingFiles = new AtomicLong();
    private final AtomicLong validatedFiles = new AtomicLong();
    private final AtomicBoolean hasSkipped = new AtomicBoolean(false);
    private final long maximumRefreshed;
    private final boolean noDelete;
    private final ManifestManager manifestManager;
    private final EncryptionIdentity encryptionIdentity;

    private Set<String> activatedShares;
    private CloseableMap<String, Boolean> processedBlockMap;
    private Stopwatch lastUpdate;

    public DestinationBlockProcessor(int maximumConcurrency,
                                     boolean noDelete,
                                     BlockDownloader blockDownloader,
                                     UploadScheduler uploadScheduler,
                                     BackupConfiguration configuration,
                                     MetadataRepository repository,
                                     ManifestManager manifestManager,
                                     EncryptionIdentity encryptionIdentity) {
        super(maximumConcurrency);
        this.blockDownloader = blockDownloader;
        this.uploadScheduler = uploadScheduler;
        this.configuration = configuration;
        this.repository = repository;
        this.manifestManager = manifestManager;
        this.encryptionIdentity = encryptionIdentity;
        this.noDelete = noDelete;

        maximumRefreshed = configuration.getProperty("maximumRefreshedBytes", Long.MAX_VALUE);
    }

    private synchronized void processBlockStorage(BackupBlock block, Runnable runnable) throws IOException {
        if (activatedShares == null) {
            synchronized (this) {
                if (activatedShares == null) {
                    activatedShares = manifestManager.getActivatedShares().keySet();
                }
            }
        }

        if (processedBlockMap == null) {
            processedBlockMap = repository.temporaryMap(new MapSerializer<String, Boolean>() {
                @Override
                public byte[] encodeKey(String s) {
                    return s.getBytes(StandardCharsets.UTF_8);
                }

                @Override
                public byte[] encodeValue(Boolean aBoolean) {
                    return new byte[]{aBoolean ? (byte) 1 : 0};
                }

                @Override
                public Boolean decodeValue(byte[] data) {
                    return data[0] != 0;
                }

                @Override
                public String decodeKey(byte[] data) {
                    return new String(data, StandardCharsets.UTF_8);
                }
            });
            refreshedBlocks.set(0);
            uploadedSize.set(0);
        }

        if (!processedBlockMap.containsKey(block.getHash())) {
            processedBlockMap.put(block.getHash(), true);

            schedule(runnable);

            postPending();
        }
    }

    public boolean refreshStorage(BackupBlock block, List<BackupBlockStorage> storages) throws IOException {
        if (uploadedSize.get() > maximumRefreshed) {
            debug(() -> log.debug("Skipped refreshing block \"{}\"", block.getHash()));
            hasSkipped.set(true);
            return false;
        }

        processBlockStorage(block, () -> refreshBlockInternal(block, storages, null));

        return true;
    }

    public boolean hasSkippedOperation() throws IOException {
        return hasSkipped.get();
    }

    public boolean validateBlockStorage(BackupBlock block, List<BackupBlockStorage> storages, boolean force)
            throws IOException {

        if (!force && uploadedSize.get() > maximumRefreshed) {
            debug(() -> log.debug("Skipped validating block \"{}\"", block.getHash()));
            hasSkipped.set(true);
            return false;
        }

        processBlockStorage(block, () -> {
            List<BackupBlockStorage> missingStorage = Lists.newArrayList();
            Map<BackupBlockStorage, Set<String>> availableStorage = new HashMap<>();
            validatedBlocks.getAndIncrement();
            for (BackupBlockStorage storage : storages) {
                BackupDestination destination = configuration.getDestinations().get(storage.getDestination());
                IOProvider provider = IOProviderFactory.getProvider(destination);
                awaitStopwatch(provider);
                int exists = 0;
                try {
                    Set<String> availableParts = new HashSet<>();
                    for (int i = 0; i < storage.getParts().size(); i++) {
                        String part = storage.getParts().get(i);
                        if (provider.exists(part)) {
                            availableParts.add(part);
                            exists++;
                        }
                    }
                    if (exists == storage.getParts().size()) {
                        availableStorage.put(storage, availableParts);
                        storage.setValidated(Instant.now().toEpochMilli());
                        debug(() -> log.debug("Validated block \"{}\"", block.getHash()));
                    } else {
                        ErrorCorrector ec = ErrorCorrectorFactory.getCorrector(storage.getEc());
                        if (ec.getMinimumSufficientParts(storage) <= exists) {
                            availableStorage.put(storage, availableParts);
                        }
                        missingStorage.add(storage);
                    }
                } catch (Exception e) {
                    log.error("Failed to check block at destination \"{}\"", block.getHash(), e);
                    return;
                }
            }
            if (!missingStorage.isEmpty()) {
                if (!availableStorage.isEmpty()) {
                    if (!refreshBlockInternal(block, missingStorage, availableStorage)) {
                        if (!InstanceFactory.isShutdown()) {
                            log.warn("Block \"{}\" has missing parts and could not be read", block.getHash());
                            pendingBlockDeletes.add(block);
                            missingBlocks.getAndIncrement();
                        }
                    }
                } else {
                    log.warn("Block \"{}\" has missing parts and cannot be restored", block.getHash());
                    pendingBlockDeletes.add(block);
                    missingBlocks.getAndIncrement();
                }
            } else {
                pendingBlockUpdates.add(block);
            }
        });

        return true;
    }

    private boolean refreshBlockInternal(BackupBlock block, List<BackupBlockStorage> needUpdates, Map<BackupBlockStorage, Set<String>> availableData) {
        byte[] data = null;
        for (BackupBlockStorage availableStorage : availableData != null ? availableData.keySet() : needUpdates) {
            try {
                if (availableData != null) {
                    data = blockDownloader.downloadEncryptedBlockStorage(block, availableStorage, availableData.get(availableStorage));
                } else {
                    data = blockDownloader.downloadEncryptedBlockStorage(block, availableStorage, null);
                }
                break;
            } catch (IOException e) {
                log.warn("Failed fetching block from destination \"{}\"", availableStorage.getDestination());
            }
        }

        if (data != null) {
            boolean any = false;
            for (BackupBlockStorage storage : needUpdates) {
                try {
                    BackupDestination destination = configuration.getDestinations().get(storage.getDestination());

                    IOProvider provider = IOProviderFactory.getProvider(destination);
                    awaitStopwatch(provider);

                    if (configuration.getShares() != null) {
                        for (String key : configuration.getShares().keySet())
                            if (activatedShares.contains(key)) {
                                IdentityKeys keys = encryptionIdentity.getIdentityKeyForHash(key);
                                storage.getAdditionalStorageProperties().put(keys, new HashMap<>());
                            }
                    }
                    List<byte[]> partData = ErrorCorrectorFactory.encodeBlocks(destination.getErrorCorrection(),
                            storage, data);
                    partData.forEach(part -> uploadedSize.addAndGet(part.length));

                    String[] parts = new String[partData.size()];
                    AtomicInteger completed = new AtomicInteger();
                    for (int i = 0; i < partData.size(); i++) {
                        int currentIndex = i;

                        // We don't want to write over existing data if we can avoid it.
                        int disambiguator = 0;
                        while(storage.getParts().contains(uploadScheduler.suggestedKey(block.getHash(), currentIndex, disambiguator)))
                            disambiguator++;

                        uploadScheduler.scheduleUpload(destination,
                                block.getHash(), currentIndex, disambiguator, partData.get(currentIndex), key -> {
                                    parts[currentIndex] = key;
                                    synchronized (completed) {
                                        completed.incrementAndGet();
                                        completed.notify();
                                    }
                                });
                    }

                    synchronized (completed) {
                        while (completed.get() < parts.length) {
                            completed.wait();
                        }
                    }
                    List<String> partList = Lists.newArrayList(parts);
                    if (partList.stream().anyMatch(Objects::isNull)) {
                        log.error("Failed to refresh storage for block \"{}\"", block.getHash());
                    } else {
                        debug(() -> log.debug("Refreshed storage for block \"{}\"", block.getHash()));

                        BackupBlockStorage updatedStorage;
                        if (noDelete) {
                            storage.setCreated(Instant.now().toEpochMilli());
                            updatedStorage = storage.toBuilder().build();;
                            block.getStorage().add(updatedStorage);
                        } else {
                            List<String> originalParts = storage.getParts();

                            updatedStorage = storage;

                            for (String part : originalParts) {
                                if (!partList.contains(part)) {
                                    provider.delete(part);
                                }
                            }
                        }
                        updatedStorage.setEc(destination.getErrorCorrection());
                        updatedStorage.setCreated(Instant.now().toEpochMilli());
                        updatedStorage.setValidated(null);
                        updatedStorage.setParts(partList);
                        any = true;
                    }
                } catch (Exception e) {
                    log.error("Failed to refresh data for block \"{}\" on destination \"{}\"",
                            block.getHash(), storage.getDestination(), e);
                }
            }
            if (any) {
                pendingBlockUpdates.add(block);
                refreshedBlocks.getAndIncrement();
            }
            return true;
        } else {
            log.error("Failed to refresh data for block \"{}\"", block.getHash());
            return false;
        }
    }

    public long getRefreshedBlocks() {
        return refreshedBlocks.get();
    }

    public long getMissingBlocks() {
        return missingBlocks.get();
    }

    public long getValidatedBlocks() {
        return validatedBlocks.get();
    }

    public long getRefreshedUploadSize() {
        return uploadedSize.get();
    }

    public long getMissingFiles() {
        return missingFiles.get();
    }

    public long getValidatedFiles() {
        return validatedFiles.get();
    }

    private void postPending() {
        while (!pendingBlockUpdates.isEmpty()) {
            BackupBlock updateBlock = pendingBlockUpdates.poll();
            try {
                repository.addBlock(updateBlock);
                debug(() -> log.debug("Updated block \"{}\"", updateBlock.getHash()));
            } catch (IOException e) {
                log.error("Failed to save update to block \"{}\"", updateBlock.getHash(), e);
            }
        }
        while (!pendingBlockDeletes.isEmpty()) {
            BackupBlock deleteBlock = pendingBlockDeletes.poll();
            try {
                repository.deleteBlock(deleteBlock);
                debug(() -> log.debug("Delete block \"{}\" because of missing data in destination", deleteBlock.getHash()));
            } catch (IOException e) {
                log.error("Failed to delete block \"{}\"", deleteBlock.getHash(), e);
            }
        }
    }

    @Override
    public void waitForCompletion() {
        super.waitForCompletion();

        postPending();

        if (processedBlockMap != null) {
            try {
                processedBlockMap.close();
            } catch (IOException e) {
                log.error("Failed to close temporary refresh block map", e);
            }
            processedBlockMap = null;
        }
    }

    public void validateExists(IOProvider provider, String file) {
        schedule(() -> {
            try {
                awaitStopwatch(provider);
                if (!provider.exists(file)) {
                    log.error("File \"{}\" does not exist", file);
                    missingFiles.incrementAndGet();
                }
                validatedFiles.incrementAndGet();
            } catch (IOException e) {
                log.error("Failed to check file \"{}\"", file, e);
            }
        });
    }

    public void resetProgress() {
        refreshedBlocks.set(0L);
        validatedBlocks.set(0L);
        missingBlocks.set(0L);
        uploadedSize.set(0L);
        missingFiles.set(0L);
        validatedFiles.set(0L);
        hasSkipped.set(false);
    }

    private void awaitStopwatch(IOProvider provider) {
        Stopwatch newStopwatch = lastUpdate;
        if (newStopwatch != null) {
            if (provider instanceof IOIndex ioIndex) {
                if (ioIndex.hasConsistentWrites()) {
                    return;
                }
            }

            long milliseconds = newStopwatch.elapsed(TimeUnit.MILLISECONDS);
            long left = 20000 - milliseconds;
            if (left < 0) {
                log.info("Completed waiting for eventual consistency");
                lastUpdate = null;
            } else {
                try {
                    Thread.sleep(left);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void setEventualConsistencyTimer(Stopwatch stopwatch) {
        this.lastUpdate = stopwatch;
    }
}
