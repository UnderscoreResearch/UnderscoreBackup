package com.underscoreresearch.backup.io.implementation;

import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.getThroughputStatus;
import static com.underscoreresearch.backup.utils.LogUtil.lastProcessedPath;
import static com.underscoreresearch.backup.utils.LogUtil.readableDuration;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_FILE_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_FILE_WRITER;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.mapdb.serializer.SerializerArrayTuple;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Stopwatch;
import com.underscoreresearch.backup.block.FileDownloader;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.io.DownloadScheduler;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.utils.StatusLine;
import com.underscoreresearch.backup.utils.StatusLogger;

@Slf4j
public class DownloadSchedulerImpl extends SchedulerImpl implements StatusLogger, DownloadScheduler {
    private final FileDownloader fileDownloader;
    private AtomicLong totalSize = new AtomicLong();
    private AtomicLong totalCount = new AtomicLong();
    private AtomicLong failedCount = new AtomicLong();
    private AtomicLong pendingOutstanding = new AtomicLong();
    private BackupFile lastProcessed;
    private Stopwatch duration;
    private DB fileDb;
    private BTreeMap<Object[], String> fileMap;
    private String pendingPassword;

    public DownloadSchedulerImpl(int maximumConcurrency,
                                 FileDownloader fileDownloader) {
        super(maximumConcurrency);
        this.fileDownloader = fileDownloader;
    }

    @Override
    public void scheduleDownload(BackupFile file, String destination, String password) {
        if (duration == null)
            duration = Stopwatch.createStarted();

        if (file.getLocations() != null && file.getLocations().size() > 0
                && file.getLocations().get(0).getParts().size() == 1) {
            initializeMap();

            try {
                pendingOutstanding.incrementAndGet();
                fileMap.put(new Object[]{file.getLocations().get(0).getParts().get(0).getBlockHash(), BACKUP_FILE_WRITER.writeValueAsString(file)},
                        destination);
                pendingPassword = password;
            } catch (JsonProcessingException e) {
                log.error("Failed to temporarily serialize backup file", e);
            }
        } else {
            internalSchedule(file, destination, password);
        }
    }

    private synchronized void initializeMap() {
        if (fileDb == null) {
            try {
                File file = File.createTempFile("underscorebackup-restore", ".db");
                file.delete();
                fileDb = DBMaker
                        .fileDB(file)
                        .fileDeleteAfterClose()
                        .fileMmapEnableIfSupported()
                        .make();
                fileMap = fileDb.treeMap("PENDING_RESTORE")
                        .keySerializer(new SerializerArrayTuple(Serializer.STRING, Serializer.STRING))
                        .valueSerializer(Serializer.STRING).createOrOpen();
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize restoring", e);
            }
        }
    }

    @Override
    public void waitForCompletion() {
        if (fileMap != null) {
            for (Map.Entry<Object[], String> entry : fileMap.entrySet()) {
                if (InstanceFactory.isShutdown()) {
                    break;
                }
                try {
                    pendingOutstanding.decrementAndGet();
                    BackupFile file = BACKUP_FILE_READER.readValue((String) entry.getKey()[1]);
                    String destination = entry.getValue();
                    internalSchedule(file, destination, pendingPassword);
                } catch (JsonProcessingException e) {
                    log.error("Failed to deserialize backup file", e);
                }
            }

            BTreeMap<Object[], String> closingFileMap = fileMap;
            DB closingDb = fileDb;

            fileMap = null;
            fileDb = null;

            closingFileMap.close();
            closingDb.close();

            pendingPassword = null;
        }

        super.waitForCompletion();
    }

    private void internalSchedule(BackupFile file, String destination, String password) {
        schedule(() -> {
            try {
                if (file.getLength() == null) {
                    log.warn("File {} had undefined length", PathNormalizer.physicalPath(file.getPath()));
                    file.setLength(0L);
                }
                log.info("Restoring {} to {} ({})", PathNormalizer.physicalPath(file.getPath()),
                        destination, readableSize(file.getLength()));
                fileDownloader.downloadFile(file, destination, password);
                debug(() -> log.debug("Restored " + PathNormalizer.physicalPath(file.getPath())));
                totalSize.addAndGet(file.getLength());
                totalCount.incrementAndGet();
                lastProcessed = file;
            } catch (Exception e) {
                if (!isShutdown()) {
                    log.error("Failed to restore file {}", PathNormalizer.physicalPath(file.getPath()), e);
                    failedCount.incrementAndGet();
                }
            }
        });
    }

    @Override
    public void resetStatus() {
        totalCount.set(0);
        totalSize.set(0);
        failedCount.set(0);
        duration = null;
        lastProcessed = null;
    }

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
}
