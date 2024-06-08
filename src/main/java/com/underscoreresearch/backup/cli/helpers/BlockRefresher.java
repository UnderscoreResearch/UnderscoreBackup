package com.underscoreresearch.backup.cli.helpers;

import static com.underscoreresearch.backup.utils.LogUtil.debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.errorcorrection.ErrorCorrectorFactory;
import com.underscoreresearch.backup.file.CloseableMap;
import com.underscoreresearch.backup.file.MapSerializer;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.io.implementation.SchedulerImpl;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;

@Slf4j
public class BlockRefresher extends SchedulerImpl {
    private final BlockDownloader blockDownloader;
    private final UploadScheduler uploadScheduler;
    private final BackupConfiguration configuration;
    private final MetadataRepository repository;
    private final ConcurrentLinkedQueue<BackupBlock> pendingBlockUpdates = new ConcurrentLinkedQueue<>();
    private final AtomicLong refreshedBlocks = new AtomicLong();
    private final AtomicLong uploadedSize = new AtomicLong();
    private final long maximumRefreshed;
    private final ManifestManager manifestManager;
    private final EncryptionIdentity encryptionIdentity;

    private Set<String> activatedShares;
    private CloseableMap<String, Boolean> processedBlockMap;

    public BlockRefresher(int maximumConcurrency,
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

        maximumRefreshed = configuration.getProperty("maximumRefreshedBytes", Long.MAX_VALUE);
    }

    public synchronized boolean refreshStorage(BackupBlock block, List<BackupBlockStorage> storages) throws IOException {
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

        if (uploadedSize.get() > maximumRefreshed) {
            debug(() -> log.debug("Skipped updating \"{}\" with refreshed locations", block.getHash()));
            return false;
        }

        if (!processedBlockMap.containsKey(block.getHash())) {
            processedBlockMap.put(block.getHash(), true);
            refreshedBlocks.getAndIncrement();

            schedule(() -> {
                for (BackupBlockStorage storage : storages) {
                    try {
                        BackupDestination destination = configuration.getDestinations().get(storage.getDestination());

                        if (configuration.getShares() != null) {
                            for (String key : configuration.getShares().keySet())
                                if (activatedShares.contains(key)) {
                                    IdentityKeys keys = encryptionIdentity.getIdentityKeyForHash(key);
                                    storage.getAdditionalStorageProperties().put(keys, new HashMap<>());
                                }
                        }
                        List<byte[]> partData = ErrorCorrectorFactory.encodeBlocks(destination.getErrorCorrection(),
                                storage, blockDownloader.downloadEncryptedBlockStorage(block, storage));
                        partData.forEach(part -> uploadedSize.addAndGet(part.length));

                        String[] parts = new String[partData.size()];
                        AtomicInteger completed = new AtomicInteger();
                        for (int i = 0; i < partData.size(); i++) {
                            int currentIndex = i;
                            uploadScheduler.scheduleUpload(destination,
                                    block.getHash(), currentIndex, partData.get(currentIndex), key -> {
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
                            storage.setEc(destination.getErrorCorrection());
                            storage.setCreated(Instant.now().toEpochMilli());
                            storage.setParts(partList);
                        }
                        pendingBlockUpdates.add(block);
                    } catch (Exception e) {
                        log.error("Failed to refresh data for block \"{}\"", block.getHash());
                    }
                }
            });

            postPending();
        }

        return true;
    }

    public long getRefreshedBlocks() {
        return refreshedBlocks.get();
    }

    public long getRefreshedUploadSize() {
        return uploadedSize.get();
    }

    private void postPending() {
        while (pendingBlockUpdates.size() > 0) {
            BackupBlock updateBlock = pendingBlockUpdates.poll();
            try {
                repository.addBlock(updateBlock);
                debug(() -> log.debug("Updated block \"{}\" with refreshed locations", updateBlock.getHash()));
            } catch (IOException e) {
                log.error("Failed to save update to block \"{}\"", updateBlock.getHash());
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
}
