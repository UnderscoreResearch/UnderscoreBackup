package com.underscoreresearch.backup.io.implementation;

import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.getThroughputStatus;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Stopwatch;
import com.underscoreresearch.backup.block.FileDownloader;
import com.underscoreresearch.backup.io.DownloadScheduler;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.utils.StatusLine;
import com.underscoreresearch.backup.utils.StatusLogger;

@Slf4j
public class DownloadSchedulerImpl extends SchedulerImpl implements StatusLogger, DownloadScheduler {
    private final FileDownloader fileDownloader;
    private AtomicLong totalSize = new AtomicLong();
    private AtomicLong totalCount = new AtomicLong();
    private Stopwatch duration;

    public DownloadSchedulerImpl(int maximumConcurrency,
                                 FileDownloader fileDownloader) {
        super(maximumConcurrency);
        this.fileDownloader = fileDownloader;
    }

    @Override
    public void scheduleDownload(BackupFile file, String destination) {
        if (duration == null)
            duration = Stopwatch.createStarted();

        schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    log.info("Restoring " + file.getPath() + " to " + destination);
                    fileDownloader.downloadFile(file, destination);
                    debug(() -> log.debug("Restored " + file.getPath()));
                    totalSize.addAndGet(file.getLength());
                    totalCount.incrementAndGet();
                } catch (IOException e) {
                    log.error("Failed to download file", e);
                }
            }
        });
    }

    @Override
    public void resetStatus() {
        totalCount.set(0);
        totalSize.set(0);
        duration = null;
    }

    @Override
    public List<StatusLine> status() {
        return getThroughputStatus(getClass(), "Restored", "files", totalCount.get(), totalSize.get(), duration);
    }
}
