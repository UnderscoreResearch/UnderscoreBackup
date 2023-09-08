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
import com.underscoreresearch.backup.model.BackupSetRoot;
import com.underscoreresearch.backup.utils.state.MachineState;

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
    private File manifestLocation;

    @BeforeEach
    public void setup() throws IOException {
        manifestLocation = Files.createTempDirectory("manifest").toFile();
        tempDir = Files.createTempDirectory("test").toFile();
        repository = new LoggingMetadataRepository(new LockingMetadataRepository(tempDir.getPath(), false),
                Mockito.mock(ManifestManager.class), false);
        repository.open(false);

        access = new FileSystemAccessImpl();
        String root = Paths.get("src").toAbsolutePath().toString();
        set = BackupSet.builder()
                .roots(Lists.newArrayList(BackupSetRoot.builder()
                        .path(PathNormalizer.normalizePath(root))
                        .filters(Lists.newArrayList(
                                BackupFilter.builder().paths(Lists.newArrayList(".git", ".idea", "target"))
                                        .type(BackupFilterType.EXCLUDE).build()
                        ))
                        .build()))
                .id("s1")
                .destinations(Lists.newArrayList("do"))
                .exclusions(Lists.newArrayList(
                        "\\.iml$",
                        "~$",
                        ".bak"))
                .build();
        backedUp = new ArrayList<>();

        consumer = new Consumer();
        delayedBackup = false;

        scanner = new FileScannerImpl(repository, consumer, access, new MachineState(false), true,
                manifestLocation.getAbsolutePath());
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
        set.getRoots().get(0).setPath(set.getRoots().get(0).getNormalizedPath() + PATH_SEPARATOR + "path_not_used");
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
        deleteDir(tempDir);
        deleteDir(manifestLocation);
    }

    private void deleteDir(File tempDir) {
        String[] entries = tempDir.list();
        for (String s : entries) {
            File currentFile = new File(tempDir.getPath(), s);
            if (currentFile.isDirectory()) {
                deleteDir(currentFile);
            } else {
                currentFile.delete();
            }
        }
        tempDir.delete();
    }

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
}