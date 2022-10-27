package com.underscoreresearch.backup.io.implementation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.google.common.base.Stopwatch;
import com.underscoreresearch.backup.block.FileDownloader;
import com.underscoreresearch.backup.io.DownloadScheduler;
import com.underscoreresearch.backup.io.IOPlugin;
import com.underscoreresearch.backup.model.BackupFile;

public class DownloadSchedulerImplTest {
    @BeforeEach
    public void setup() {
    }

    @Test
    public void testConcurrency() throws IOException {
        DelayedFiledownloader downloader = Mockito.spy(new DelayedFiledownloader());
        DownloadScheduler scheduler = new DownloadSchedulerImpl(10, downloader);

        Stopwatch stopwatch = Stopwatch.createStarted();

        for (int i = 0; i < 100; i++) {
            int val = i;
            scheduler.scheduleDownload(BackupFile.builder().path(i + "").length((long) i).build(), val + "", "pwd");
        }

        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS), Matchers.greaterThan(900L));
        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS), Matchers.lessThan(2000L));
        scheduler.waitForCompletion();

        Mockito.verify(downloader, Mockito.times(100)).downloadFile(any(), any(), any());
    }

    @Test
    public void testShutdown() throws IOException {
        DelayedFiledownloader downloader = Mockito.spy(new DelayedFiledownloader());
        DownloadScheduler scheduler = new DownloadSchedulerImpl(10, downloader);

        new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                int val = i;
                scheduler.scheduleDownload(new BackupFile(), val + "", "pwd");
            }
        }).start();

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        scheduler.shutdown();

        Mockito.verify(downloader, Mockito.atMost(10)).downloadFile(any(), any(), any());
    }

    @IOPlugin("DELAY")
    public static class DelayedFiledownloader implements FileDownloader {
        @Override
        public void downloadFile(BackupFile source, String destination, String passphrase) throws IOException {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void shutdown() {

        }
    }
}