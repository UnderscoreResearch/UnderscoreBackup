package com.underscoreresearch.backup.block.implementation;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.underscoreresearch.backup.block.FileBlockUploader;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.errorcorrection.ErrorCorrectorFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupCompletion;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupData;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.model.BackupUploadCompletion;
import com.underscoreresearch.backup.utils.ManualStatusLogger;
import com.underscoreresearch.backup.utils.StateLogger;
import com.underscoreresearch.backup.utils.StatusLine;

@Slf4j
public class FileBlockUploaderImpl implements FileBlockUploader, ManualStatusLogger {
    private final BackupConfiguration configuration;
    private final MetadataRepository repository;
    private final UploadScheduler uploadScheduler;
    private final AtomicLong totalBlocks = new AtomicLong();
    private final ManifestManager manifestManager;
    private final EncryptionKey key;
    private final Set<String> usedDestinations;
    private Set<String> activatedShares;

    public FileBlockUploaderImpl(BackupConfiguration configuration, MetadataRepository repository,
                                 UploadScheduler uploadScheduler, ManifestManager manifestManager,
                                 EncryptionKey key) {
        StateLogger.addLogger(this);

        this.configuration = configuration;
        this.repository = repository;
        this.uploadScheduler = uploadScheduler;
        this.manifestManager = manifestManager;

        usedDestinations = new HashSet<>();
        for (BackupSet set : configuration.getSets()) {
            usedDestinations.addAll(set.getDestinations());
        }
        this.key = key;
    }

    @Override
    public void uploadBlock(BackupSet set,
                            BackupData unencryptedData,
                            String blockHash,
                            String format,
                            BackupCompletion completionFuture) {
        if (activatedShares == null) {
            synchronized (this) {
                if (activatedShares == null) {
                    activatedShares = manifestManager.getActivatedShares().keySet();
                }
            }
        }

        BackupBlock block = BackupBlock.builder()
                .format(format)
                .hash(blockHash)
                .created(Instant.now().toEpochMilli())
                .storage(new ArrayList<>())
                .build();

        Set<String> neededDestinations = Sets.newHashSet(set.getDestinations());
        try {
            try {
                BackupBlock existingBlock = repository.block(blockHash);
                if (existingBlock != null) {
                    boolean usable = true;
                    for (String destinationName : neededDestinations) {
                        if (!existingBlock.getStorage().stream().anyMatch(t -> t.getDestination().equals(destinationName))) {
                            usable = false;
                        }
                    }
                    if (!usable) {
                        if (existingBlock.getFormat() == format) {
                            existingBlock.getStorage().stream().forEach(t -> {
                                neededDestinations.remove(t.getDestination());
                                block.getStorage().add(t);
                            });
                        } else {
                            existingBlock.getStorage().stream().forEach(t -> neededDestinations.add(t.getDestination()));
                        }

                        // Only keep destinations that are used by any sets in the configuration.
                        neededDestinations.retainAll(usedDestinations);
                    } else {
                        completionFuture.completed(true);
                        return;
                    }
                }
            } catch (IOException e) {
                log.warn("Failed to fetch block definition " + blockHash, e);
            }

            Set<BackupUploadCompletion> completions = new HashSet<>();
            AtomicBoolean canComplete = new AtomicBoolean();
            int destinationsLeft = neededDestinations.size();
            for (String destinationName : neededDestinations) {
                destinationsLeft--;
                BackupDestination destination = configuration.getDestinations().get(destinationName);

                BackupBlockStorage storage = BackupBlockStorage.builder().destination(destinationName).build();
                block.getStorage().add(storage);

                List<byte[]> parts;
                {
                    if (configuration.getShares() != null) {
                        for (String key : configuration.getShares().keySet())
                            if (activatedShares.contains(key))
                                storage.getAdditionalStorageProperties().put(EncryptionKey.createWithPublicKey(key),
                                        new HashMap<>());
                    }
                    byte[] encrypted = EncryptorFactory.encryptBlock(destination.getEncryption(),
                            storage, unencryptedData.getData(), key);
                    if (destinationsLeft <= 0)
                        unencryptedData.clear();

                    parts = ErrorCorrectorFactory.encodeBlocks(destination.getErrorCorrection(), storage, encrypted);
                }
                storage.setParts(new ArrayList<>(parts.size()));

                for (int i = 0; i < parts.size(); i++) {
                    storage.getParts().add(null);
                    int currentIndex = i;
                    BackupUploadCompletion completion = new BackupUploadCompletion() {
                        @Override
                        public void completed(String key) {
                            storage.getParts().set(currentIndex, key);
                            synchronized (completions) {
                                completions.remove(this);

                                if (canComplete.get() && completions.size() == 0) {
                                    try {
                                        if (storage.getParts().stream().anyMatch(t -> t == null)) {
                                            completionFuture.completed(false);
                                        } else {
                                            totalBlocks.incrementAndGet();
                                            repository.addBlock(block);
                                            completionFuture.completed(true);
                                        }
                                    } catch (IOException e) {
                                        log.error("Failed to save block " + blockHash, e);
                                        completionFuture.completed(false);
                                    }
                                }
                            }
                        }
                    };

                    synchronized (completions) {
                        completions.add(completion);
                    }

                    uploadScheduler.scheduleUpload(destination, blockHash, i, parts.get(i), completion);
                }
            }

            synchronized (completions) {
                canComplete.set(true);
                if (completions.size() == 0) {
                    totalBlocks.incrementAndGet();
                    repository.addBlock(block);
                    completionFuture.completed(true);
                }
            }
        } catch (Throwable e) {
            log.error("Failed to save block " + blockHash, e);
            completionFuture.completed(false);
        }
    }

    @Override
    public void resetStatus() {
        totalBlocks.set(0);
    }

    @Override
    public List<StatusLine> status() {
        if (totalBlocks.get() > 0) {
            return Lists.newArrayList(new StatusLine(getClass(), "UPLOADED_BLOCKS", "Uploaded blocks", totalBlocks.get()));
        }
        return new ArrayList<>();
    }
}