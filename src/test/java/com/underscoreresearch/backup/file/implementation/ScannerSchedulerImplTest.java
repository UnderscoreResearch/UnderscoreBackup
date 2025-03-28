package com.underscoreresearch.backup.file.implementation;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.underscoreresearch.backup.cli.helpers.RepositoryTrimmer;
import com.underscoreresearch.backup.file.ContinuousBackup;
import com.underscoreresearch.backup.file.FileChangeWatcher;
import com.underscoreresearch.backup.file.FileScanner;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.model.BackupSetRoot;
import com.underscoreresearch.backup.utils.StateLogger;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.text.ParseException;

import static org.hamcrest.MatcherAssert.assertThat;

@Slf4j
class ScannerSchedulerImplTest {
    private BackupConfiguration configuration;
    private FileScanner scanner;
    private BackupSet set1;
    private BackupSet set2;
    private RepositoryTrimmer trimmer;
    private MetadataRepository repository;

    @BeforeEach
    public void setup() throws ParseException {
        scanner = Mockito.spy(new FileScannerTest());
        trimmer = Mockito.mock(RepositoryTrimmer.class);
        set1 = BackupSet.builder().id("set1").schedule("* * * * *")
                .roots(Lists.newArrayList(BackupSetRoot.builder().path("/prio").build())).build();
        set2 = BackupSet.builder().id("set2").schedule("*/2 * * * *")
                .roots(Lists.newArrayList(BackupSetRoot.builder().path("/slower").build())).build();
        repository = Mockito.mock(MetadataRepository.class);
        configuration = BackupConfiguration.builder().sets(Lists.newArrayList(
                set1,
                set2
        )).build();
    }

    //
    // Because real CRON tab expressions don't support second precision this test doesn't work anymore.
    //
    //@Test
    public void test() throws IOException {
        ScannerSchedulerImpl scannerScheduler = new ScannerSchedulerImpl(configuration, repository, trimmer, scanner,
                Mockito.mock(StateLogger.class), Mockito.mock(FileChangeWatcher.class), Mockito.mock(ContinuousBackup.class),
                null, false);
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(2500);
                scannerScheduler.shutdown();
            } catch (InterruptedException e) {
            }
        });
        thread.setDaemon(true);
        thread.start();

        scannerScheduler.start();
        Mockito.verify(scanner, Mockito.times(2)).startScanning(set1);
        ArgumentCaptor<BackupDirectory> rootDir = ArgumentCaptor.forClass(BackupDirectory.class);
        Mockito.verify(repository).addDirectory(rootDir.capture());

        assertThat(rootDir.getValue().getPath(), Is.is(""));
        assertThat(rootDir.getValue().getFiles(), Is.is(Sets.newTreeSet(Lists.newArrayList("/prio/", "/slower/"))));

        Mockito.verify(scanner).startScanning(set2);
        Mockito.verify(scanner, Mockito.times(2)).shutdown();
    }

    private static class FileScannerTest implements FileScanner {
        private Object lock = new Object();
        private boolean result;

        @Override
        public boolean startScanning(BackupSet backupSet) throws IOException {
            synchronized (lock) {
                log.info("Started scanning {}", backupSet.getId());
                result = true;
                try {
                    lock.wait(1500);
                } catch (InterruptedException e) {
                }
            }
            log.info("Completed scanning {}: {}", backupSet.getId(), result);
            return result;
        }

        public void logStatus() {
        }

        @Override
        public void shutdown() {
            synchronized (lock) {
                result = false;
                lock.notify();
            }
        }
    }
}