package com.underscoreresearch.backup.io.implementation;

import static com.underscoreresearch.backup.file.implementation.LockingMetadataRepositoryTest.LARGE_PATH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.block.FileDownloader;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.implementation.LockingMetadataRepository;
import com.underscoreresearch.backup.io.DownloadScheduler;
import com.underscoreresearch.backup.io.IOPlugin;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupLocation;

public class DownloadSchedulerImplTest {
    private File tempDir;
    private MetadataRepository repository;

    @BeforeEach
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory("test").toFile();
        repository = new LockingMetadataRepository(tempDir.getPath(), false);
        repository.open(false);
    }

    @AfterEach
    public void tearDown() throws IOException {
        repository.close();
        IOUtils.deleteContents(tempDir);
        assertTrue(tempDir.delete());
    }

    @Test
    public void testConcurrencyShort() throws IOException {
        testConcurrency("");
    }

    @Test
    public void testConcurrencyLarge() throws IOException {
        testConcurrency(LARGE_PATH);
    }

    private void testConcurrency(String prefix) throws IOException {
        DelayedFiledownloader downloader = Mockito.spy(new DelayedFiledownloader());
        DownloadScheduler scheduler = new DownloadSchedulerImpl(10, repository, downloader);

        Stopwatch stopwatch = Stopwatch.createStarted();

        for (int i = 0; i < 100; i++) {
            scheduler.scheduleDownload(BackupFile.builder().path(prefix + i).length((long) i).build(), i + "", "pwd");
        }

        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS), Matchers.greaterThan(900L));
        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS), Matchers.lessThan(2000L));
        scheduler.waitForCompletion();

        Mockito.verify(downloader, Mockito.times(100)).downloadFile(any(), any(), any());
    }

    @Test
    public void testShutdown() throws IOException {
        DelayedFiledownloader downloader = Mockito.spy(new DelayedFiledownloader());
        DownloadScheduler scheduler = new DownloadSchedulerImpl(10, repository, downloader);

        Thread thread = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                scheduler.scheduleDownload(BackupFile.builder().path("" + i).length((long) i).build(),
                        "" + i, "pwd");
            }
        });
        thread.setDaemon(true);
        thread.start();

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            fail();
        }

        scheduler.shutdown();

        Mockito.verify(downloader, Mockito.atMost(10)).downloadFile(any(), any(), any());
    }

    @Test
    public void testWaitForCompletionShort() throws IOException {
        testWaitForCompletion("");
    }

    @Test
    public void testWaitForCompletionLong() throws IOException {
        testWaitForCompletion(LARGE_PATH);
    }

    private void testWaitForCompletion(String prefix) throws IOException {
        DelayedFiledownloader downloader = Mockito.spy(new DelayedFiledownloader());
        DownloadScheduler scheduler = new DownloadSchedulerImpl(10, repository, downloader);

        for (int i = 0; i < 100; i++) {
            scheduler.scheduleDownload(BackupFile.builder()
                    .path(prefix + i).length((long) i)
                    .locations(Lists.newArrayList(BackupLocation.builder()
                            .parts(Lists.newArrayList(BackupFilePart.builder().blockHash("hash").build()))
                            .build()))
                    .build(), "dest", "pwd");
        }

        scheduler.waitForCompletion();
        Mockito.verify(downloader, Mockito.times(100)).downloadFile(any(), any(), any());
    }

    @IOPlugin("DELAY")
    public static class DelayedFiledownloader implements FileDownloader {
        @Override
        public void downloadFile(BackupFile source, String destination, String password) throws IOException {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                fail();
            }
        }

        @Override
        public void shutdown() {

        }
    }
}