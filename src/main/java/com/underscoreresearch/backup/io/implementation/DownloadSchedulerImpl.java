package com.underscoreresearch.backup.io.implementation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Stopwatch;
import com.underscoreresearch.backup.block.FileDownloader;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.CloseableMap;
import com.underscoreresearch.backup.file.CloseableSortedMap;
import com.underscoreresearch.backup.file.MapSerializer;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.io.DownloadScheduler;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.utils.log.ManualStatusLogger;
import com.underscoreresearch.backup.utils.log.StateLogger;
import com.underscoreresearch.backup.utils.log.StatusLine;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static com.underscoreresearch.backup.utils.log.LogUtil.debug;
import static com.underscoreresearch.backup.utils.log.LogUtil.getThroughputStatus;
import static com.underscoreresearch.backup.utils.log.LogUtil.lastProcessedPath;
import static com.underscoreresearch.backup.utils.log.LogUtil.readableDuration;
import static com.underscoreresearch.backup.utils.log.LogUtil.readableSize;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Implementation of DownloadScheduler that manages asynchronous downloads.
 * Provides status tracking and callbacks for download operations.
 */
@Slf4j
public class DownloadSchedulerImpl extends SchedulerImpl implements ManualStatusLogger, DownloadScheduler {
    private static final ObjectReader SCHEDULED_DOWNLOAD_READER = MAPPER.readerFor(ScheduledDownload.class);
    private static final ObjectWriter SCHEDULED_DOWNLOAD_WRITER = MAPPER.writerFor(ScheduledDownload.class);

    private final FileDownloader fileDownloader;
    private final AtomicLong totalSize = new AtomicLong();
    private final AtomicLong totalCount = new AtomicLong();
    private final AtomicLong failedCount = new AtomicLong();
    private final AtomicLong pendingOutstanding = new AtomicLong();
    private final AtomicLong index = new AtomicLong(0);
    private final MetadataRepository repository;
    private final List<Consumer<String>> completionCallbacks = new ArrayList<>();
    private BackupFile lastProcessed;
    private Stopwatch duration;
    private CloseableSortedMap<ScheduledDownloadKey, ScheduledDownload> fileMap;
    private String pendingPassword;

    /**
     * Constructor for DownloadSchedulerImpl.
     *
     * @param maximumConcurrency The maximum number of concurrent downloads
     * @param repository The metadata repository for temporary storage
     * @param fileDownloader The file downloader to use for downloading files
     */
    public DownloadSchedulerImpl(int maximumConcurrency,
                                 MetadataRepository repository,
                                 FileDownloader fileDownloader) {
        super(maximumConcurrency);
        this.fileDownloader = fileDownloader;
        this.repository = repository;

        StateLogger.addLogger(this);
    }

    /**
     * Add a callback to be notified when downloads complete.
     *
     * @param callback The callback to add
     */
    public void addCompletionCallback(Consumer<String> callback) {
        synchronized (completionCallbacks) {
            completionCallbacks.add(callback);
        }
    }

    /**
     * Remove a previously added completion callback.
     *
     * @param callback The callback to remove
     */
    public void removeCompletionCallback(Consumer<String> callback) {
        synchronized (completionCallbacks) {
            completionCallbacks.remove(callback);
        }
    }

    /**
     * Notify all registered callbacks that a download has completed.
     *
     * @param path The path of the completed download
     */
    private void notifyCompleted(String path) {
        synchronized (completionCallbacks) {
            for (Consumer<String> callback : completionCallbacks) {
                callback.accept(path);
            }
        }
    }

    /**
     * Schedule a download of a backup file.
     *
     * @param file The file to download
     * @param destination The destination path to save the file
     * @param password The password for decryption, if required
     */
    @Override
    public void scheduleDownload(BackupFile file, String destination, String password) {
        if (duration == null)
            duration = Stopwatch.createStarted();

        if (file.getLocations() != null && !file.getLocations().isEmpty()
                && file.getLocations().get(0).getParts().size() == 1) {
            initializeMap();

            pendingOutstanding.incrementAndGet();
            fileMap.put(new ScheduledDownloadKey(file.getLocations().get(0).getParts().get(0).getBlockHash(), index.incrementAndGet()),
                    new ScheduledDownload(file, destination));
            pendingPassword = password;
        } else {
            internalSchedule(file, destination, password);
        }
    }

    /**
     * Initialize the temporary map for storing download information.
     */
    private synchronized void initializeMap() {
        if (fileMap == null) {
            try {
                File file = File.createTempFile("underscorebackup-restore", ".db");
                IOUtils.deleteFile(file);
                fileMap = repository.temporarySortedMap(new MapSerializer<ScheduledDownloadKey, ScheduledDownload>() {
                    @Override
                    public byte[] encodeKey(ScheduledDownloadKey scheduledDownloadKey) {
                        byte[] blockHash = scheduledDownloadKey.getBlockHash().getBytes(StandardCharsets.UTF_8);
                        ByteBuffer buffer = ByteBuffer.allocate(blockHash.length + Long.BYTES);
                        buffer.put(blockHash);
                        buffer.putLong(scheduledDownloadKey.index);
                        return buffer.array();
                    }

                    @Override
                    public byte[] encodeValue(ScheduledDownload scheduledDownload) {
                        try {
                            return SCHEDULED_DOWNLOAD_WRITER.writeValueAsBytes(scheduledDownload);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public ScheduledDownload decodeValue(byte[] data) {
                        try {
                            return SCHEDULED_DOWNLOAD_READER.readValue(data);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public ScheduledDownloadKey decodeKey(byte[] data) {
                        ByteBuffer buffer = ByteBuffer.wrap(data);
                        String block = new String(buffer.slice(0, data.length - Long.BYTES).array(),
                                StandardCharsets.UTF_8);
                        return new ScheduledDownloadKey(block, buffer.getLong(data.length - Long.BYTES));
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize restoring", e);
            }
        }
    }

    /**
     * Wait for all scheduled downloads to complete.
     * Processes any pending downloads in the temporary map.
     */
    @Override
    public void waitForCompletion() {
        if (fileMap != null) {
            fileMap.readOnlyEntryStream(true).forEach(entry -> {
                if (InstanceFactory.isShutdown()) {
                    return;
                }
                pendingOutstanding.decrementAndGet();
                ScheduledDownload download = entry.getValue();
                String destination = download.getDestination();
                internalSchedule(download.getFile(), destination, pendingPassword);
            });

            CloseableMap<ScheduledDownloadKey, ScheduledDownload> closingFileMap = fileMap;

            fileMap = null;

            try {
                closingFileMap.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            pendingPassword = null;
        }

        super.waitForCompletion();
    }

    /**
     * Internal method to schedule a download.
     *
     * @param file The file to download
     * @param destination The destination path
     * @param password The password for decryption
     */
    private void internalSchedule(BackupFile file, String destination, String password) {
        schedule(() -> {
            try {
                if (file.getLength() == null) {
                    log.warn("File \"{}\" had undefined length", PathNormalizer.physicalPath(file.getPath()));
                    file.setLength(0L);
                }
                log.info("Restoring \"{}\" to \"{}\" ({})", PathNormalizer.physicalPath(file.getPath()),
                        destination, readableSize(file.getLength()));
                fileDownloader.downloadFile(file, destination, password);
                debug(() -> log.debug("Restored \"{}\"", PathNormalizer.physicalPath(file.getPath())));
                totalSize.addAndGet(file.getLength());
                totalCount.incrementAndGet();
                lastProcessed = file;
            } catch (Exception e) {
                if (!isShutdown()) {
                    log.error("Failed to restore file \"{}\"", PathNormalizer.physicalPath(file.getPath()), e);
                    failedCount.incrementAndGet();
                }
            }
            notifyCompleted(file.getPath());
        });
    }

    /**
     * Reset the status counters.
     */
    @Override
    public void resetStatus() {
        totalCount.set(0);
        totalSize.set(0);
        failedCount.set(0);
        duration = null;
        lastProcessed = null;
    }

    /**
     * Get the current status of downloads.
     *
     * @return List of status lines
     */
    @Override
    public List<StatusLine> status() {
        List<StatusLine> ret = getThroughputStatus(getClass(), "Restored", "files", totalCount.get(), totalSize.get(),
                duration != null ? duration.elapsed() : Duration.ZERO);
        if (duration != null) {
            ret.add(new StatusLine(getClass(), "RESTORE_DURATION", "Total duration", duration.elapsed().toMillis(), readableDuration(duration.elapsed())));
            lastProcessedPath(getClass(), ret, lastProcessed, "PROCESSED_PATH");
        }
        if (failedCount.get() > 0) {
            ret.add(new StatusLine(getClass(), "RESTORED_OBJECTS_FAILED", "Failed to restore files", failedCount.get()));
        }
        if (pendingOutstanding.get() > 0) {
            long count = pendingOutstanding.get();
            ret.add(new StatusLine(getClass(), "RESTORED_OBJECTS_PENDING", "Pending files to restore", count));
        }
        return ret;
    }

    /**
     * Key class for the scheduled download map.
     */
    @Data
    @NoArgsConstructor
    public static class ScheduledDownloadKey {
        private String blockHash;
        private Long index;

        /**
         * Constructor for ScheduledDownloadKey.
         *
         * @param blockHash The hash of the block
         * @param index The index for ordering
         */
        public ScheduledDownloadKey(String blockHash, Long index) {
            this.blockHash = blockHash;
            this.index = index;
        }
    }

    /**
     * Value class for the scheduled download map.
     */
    @Data
    @NoArgsConstructor
    public static class ScheduledDownload {
        private BackupFile file;
        private String destination;

        /**
         * Constructor for ScheduledDownload.
         *
         * @param file The file to download
         * @param destination The destination path
         */
        public ScheduledDownload(BackupFile file, String destination) {
            this.file = file;
            this.destination = destination;
        }
    }
}
