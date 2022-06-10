package com.underscoreresearch.backup.cli.helpers;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.block.assignments.SmallFileBlockAssignment;
import com.underscoreresearch.backup.block.implementation.BlockDownloaderImpl;
import com.underscoreresearch.backup.errorcorrection.ErrorCorrectorFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.io.implementation.SchedulerImpl;
import com.underscoreresearch.backup.model.*;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.underscoreresearch.backup.utils.LogUtil.*;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

@Slf4j
public class BlockRefresher extends SchedulerImpl {
    private final BlockDownloader blockDownloader;
    private final UploadScheduler uploadScheduler;
    private final BackupConfiguration configuration;
    private final MetadataRepository repository;
    private final ConcurrentLinkedQueue<BackupBlock> pendingBlockUpdates = new ConcurrentLinkedQueue<>();
    private final AtomicLong refreshedBlocks = new AtomicLong();
    private DB processedBlockDb;
    private HTreeMap<String, Boolean> processedBlockMap;

    public BlockRefresher(int maximumConcurrency,
                          BlockDownloader blockDownloader,
                          UploadScheduler uploadScheduler,
                          BackupConfiguration configuration,
                          MetadataRepository repository) {
        super(maximumConcurrency);
        this.blockDownloader = blockDownloader;
        this.uploadScheduler = uploadScheduler;
        this.configuration = configuration;
        this.repository = repository;
    }

    public synchronized void refreshStorage(BackupBlock block, List<BackupBlockStorage> storages) throws IOException {
        if (processedBlockMap == null) {
            File tempFile = File.createTempFile("block", ".db");

            tempFile.delete();

            processedBlockDb = DBMaker
                    .fileDB(tempFile)
                    .fileMmapEnableIfSupported()
                    .fileDeleteAfterClose()
                    .make();
            processedBlockMap = processedBlockDb.hashMap("USED_BLOCKS", Serializer.STRING,
                    Serializer.BOOLEAN).createOrOpen();
            refreshedBlocks.set(0);
        }

        if (!processedBlockMap.containsKey(block.getHash())) {
            processedBlockMap.put(block.getHash(), true);
            refreshedBlocks.getAndIncrement();

            schedule(() -> {
                for (BackupBlockStorage storage : storages) {
                    try {
                        BackupDestination destination = configuration.getDestinations().get(storage.getDestination());
                        List<byte[]> partData = ErrorCorrectorFactory.encodeBlocks(destination.getErrorCorrection(), storage,
                                blockDownloader.downloadEncryptedBlockStorage(block, storage));

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
                        if (partList.stream().anyMatch(t -> t == null)) {
                            log.error("Failed to refresh storage for block {}", block.getHash());
                        } else {
                            debug(() -> log.debug("Refreshed storage for block {}", block.getHash()));
                            storage.setEc(destination.getErrorCorrection());
                            storage.setCreated(Instant.now().toEpochMilli());
                            storage.setParts(partList);
                        }
                        pendingBlockUpdates.add(block);
                    } catch (Exception e) {
                        log.error("Failed to refresh data for block {}", block.getHash());
                    }
                }
            });

            postPending();
        }
    }

    public long getRefreshedBlocks() {
        return refreshedBlocks.get();
    }

    private void postPending() {
        while (pendingBlockUpdates.size() > 0) {
            BackupBlock updateBlock = pendingBlockUpdates.poll();
            try {
                repository.addBlock(updateBlock);
                debug(() -> log.debug("Updated block {} with refreshed locations", updateBlock.getHash()));
            } catch (IOException e) {
                log.error("Failed to save update to block {}", updateBlock.getHash());
            }
        }
    }

    @Override
    public void waitForCompletion() {
        super.waitForCompletion();

        postPending();

        if (processedBlockDb != null) {
            processedBlockMap.close();
            processedBlockDb = null;
        }
        if (processedBlockDb != null) {
            processedBlockDb.close();
            processedBlockMap = null;
        }
    }
}
