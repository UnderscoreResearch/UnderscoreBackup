package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.underscoreresearch.backup.file.FileConsumer;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.manifest.LoggingMetadataRepository;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupCompletion;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilter;
import com.underscoreresearch.backup.model.BackupFilterType;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.utils.StateLogger;

class FileScannerImplTest {
    private FileSystemAccessImpl access;
    private BackupSet set;
    private File tempDir;
    private MetadataRepository repository;
    private FileConsumer consumer;
    private String stopFile;
    private FileScannerImpl scanner;
    private List<String> backedUp;
    private boolean delayedBackup;

    private class Consumer implements FileConsumer {

        @Override
        public void backupFile(BackupSet backupSet, BackupFile file, BackupCompletion completionPromise) {
            if (file.getPath().equals(stopFile)) {
                scanner.shutdown();
            }

            assertThat(backupSet, Is.is(set));

            if (delayedBackup) {
                new Thread(() -> {
                    try {
                        Thread.sleep((int) (Math.random() * 500));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    backupFileSubmit(file, completionPromise);
                }).start();
            } else
                backupFileSubmit(file, completionPromise);
        }

        @Override
        public void flushAssignments() {

        }
    }

    @BeforeEach
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory("test").toFile();
        repository = new LoggingMetadataRepository(new MapdbMetadataRepository(tempDir.getPath()), Mockito.mock(ManifestManager.class));
        repository.open(false);

        access = new FileSystemAccessImpl();
        String root = Paths.get(".").toAbsolutePath().toString();
        set = BackupSet.builder()
                .root(PathNormalizer.normalizePath(root))
                .id("s1")
                .exclusions(Lists.newArrayList(
                        "\\.iml$",
                        "~$",
                        ".bak"))
                .filters(Lists.newArrayList(
                        BackupFilter.builder().paths(Lists.newArrayList(".git", ".idea", "target")).type(BackupFilterType.EXCLUDE).build()
                ))
                .build();
        backedUp = new ArrayList<>();

        consumer = new Consumer();
        delayedBackup = false;

        scanner = new FileScannerImpl(repository, consumer, access, Mockito.mock(StateLogger.class));
    }

    private void backupFileSubmit(BackupFile file, BackupCompletion completionPromise) {
        synchronized (backedUp) {
            backedUp.add(file.getPath());
        }
        completionPromise.completed(true);
    }

    @Test
    public void basic() throws IOException {
        delayedBackup = true;
        scanner.startScanning(set);
    }

    @Test
    public void emptySet() throws IOException {
        set.setRoot(set.getNormalizedRoot() + PATH_SEPARATOR + "path_not_used");
        scanner.startScanning(set);
    }

    @Test
    public void interrupted() throws IOException {
        scanner.startScanning(set);
        List<String> answer = backedUp;

        backedUp = new ArrayList<>();
        stopFile = answer.get(answer.size() / 3);
        scanner.startScanning(set);
        stopFile = answer.get(2 * answer.size() / 3);
        scanner.startScanning(set);
        stopFile = null;
        scanner.startScanning(set);

        Set<String> processedFiles = backedUp.stream().collect(Collectors.toSet());

        assertThat(processedFiles.size(), Is.is(backedUp.size()));

        scanner.startScanning(set);

        assertThat(processedFiles, Is.is(Sets.newHashSet(answer)));
    }

    @AfterEach
    public void teardown() throws IOException {
        repository.close();
        String[] entries = tempDir.list();
        for (String s : entries) {
            File currentFile = new File(tempDir.getPath(), s);
            currentFile.delete();
        }
        tempDir.delete();
    }
}