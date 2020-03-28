package com.underscoreresearch.backup.file.implementation;

import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.text.ParseException;

import lombok.extern.slf4j.Slf4j;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.underscoreresearch.backup.file.FileScanner;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.RepositoryTrimmer;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupSet;

@Slf4j
class ScannerSchedulerImplTest {
    private BackupConfiguration configuration;
    private FileScanner scanner;
    private BackupSet set1;
    private BackupSet set2;
    private RepositoryTrimmer trimmer;
    private MetadataRepository repository;

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

    @BeforeEach
    public void setup() throws ParseException {
        scanner = Mockito.spy(new FileScannerTest());
        trimmer = Mockito.mock(RepositoryTrimmer.class);
        set1 = BackupSet.builder().id("set1").schedule("* * * * * ?").root("/prio").build();
        set2 = BackupSet.builder().id("set2").schedule("*/2 * * * * ?").root("/slower").build();
        repository = Mockito.mock(MetadataRepository.class);
        configuration = BackupConfiguration.builder().sets(Lists.newArrayList(
                set1,
                set2
        )).build();
    }

    @Test
    public void test() throws IOException {
        ScannerSchedulerImpl scannerScheduler = new ScannerSchedulerImpl(configuration, repository, trimmer, scanner);
        new Thread(() -> {
            try {
                Thread.sleep(2500);
                scannerScheduler.shutdown();
            } catch (InterruptedException e) {
            }
        }).start();
        scannerScheduler.start();
        Mockito.verify(scanner, Mockito.times(2)).startScanning(set1);
        ArgumentCaptor<BackupDirectory> rootDir = ArgumentCaptor.forClass(BackupDirectory.class);
        Mockito.verify(repository).addDirectory(rootDir.capture());

        assertThat(rootDir.getValue().getPath(), Is.is(""));
        assertThat(rootDir.getValue().getFiles(), Is.is(Sets.newTreeSet(Lists.newArrayList("/prio/", "/slower/"))));

        Mockito.verify(scanner).startScanning(set2);
        Mockito.verify(scanner, Mockito.times(2)).shutdown();
    }
}