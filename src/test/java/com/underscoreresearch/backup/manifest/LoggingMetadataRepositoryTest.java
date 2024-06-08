package com.underscoreresearch.backup.manifest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.implementation.LoggingMetadataRepository;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.manifest.model.PushActivePath;
import com.underscoreresearch.backup.model.BackupActiveFile;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupPendingSet;

class LoggingMetadataRepositoryTest {
    private MetadataRepository repository;
    private LoggingMetadataRepository loggingMetadataRepository;
    private ManifestManager manifestManager;
    private BackupFile backupFile;
    private BackupBlock backupBlock;
    private BackupFilePart backupFilePart;

    @BeforeEach
    public void setup() {
        repository = Mockito.mock(MetadataRepository.class);
        manifestManager = Mockito.mock(ManifestManager.class);
        loggingMetadataRepository = new LoggingMetadataRepository(repository, manifestManager, null, null, 500, false);
        backupFile = new BackupFile();
        backupBlock = new BackupBlock();
        backupFilePart = new BackupFilePart();
    }

    @Test
    void addFile() throws IOException {
        loggingMetadataRepository.addFile(backupFile);
        Mockito.verify(repository).addFile(backupFile);
        Mockito.verify(manifestManager).addLogEntry(anyString(), anyString());
    }

    @Test
    void file() throws IOException {
        loggingMetadataRepository.file("path");
        Mockito.verify(repository).file("path");
        Mockito.verify(manifestManager, Mockito.never()).addLogEntry(anyString(), anyString());
    }

    @Test
    void fileTimestamp() throws IOException {
        loggingMetadataRepository.file("path", 1L);
        Mockito.verify(repository).file("path", 1L);
        Mockito.verify(manifestManager, Mockito.never()).addLogEntry(anyString(), anyString());
    }

    @Test
    void directoryTimestamp() throws IOException {
        loggingMetadataRepository.directory("path", 1L, true);
        Mockito.verify(repository).directory("path", 1L, true);
        Mockito.verify(manifestManager, Mockito.never()).addLogEntry(anyString(), anyString());
    }

    @Test
    void deleteFile() throws IOException {
        loggingMetadataRepository.deleteFile(backupFile);
        Mockito.verify(repository).deleteFile(backupFile);
        Mockito.verify(manifestManager).addLogEntry(anyString(), anyString());
    }

    @Test
    void existingFilePart() throws IOException {
        loggingMetadataRepository.existingFilePart("hash");
        Mockito.verify(repository).existingFilePart("hash");
        Mockito.verify(manifestManager, Mockito.never()).addLogEntry(anyString(), anyString());
    }

    @Test
    void deleteFilePart() throws IOException {
        loggingMetadataRepository.deleteFilePart(backupFilePart);
        Mockito.verify(repository).deleteFilePart(backupFilePart);
        Mockito.verify(manifestManager).addLogEntry(anyString(), anyString());
    }

    @Test
    void addBlock() throws IOException {
        loggingMetadataRepository.addBlock(backupBlock);
        Mockito.verify(repository).addBlock(backupBlock);
        Mockito.verify(manifestManager).addLogEntry(anyString(), anyString());
    }

    @Test
    void block() throws IOException {
        loggingMetadataRepository.block("hash");
        Mockito.verify(repository).block("hash");
        Mockito.verify(manifestManager, Mockito.never()).addLogEntry(anyString(), anyString());
    }

    @Test
    void deleteBlock() throws IOException {
        loggingMetadataRepository.deleteBlock(backupBlock);
        Mockito.verify(repository).deleteBlock(backupBlock);
        Mockito.verify(manifestManager).addLogEntry(anyString(), anyString());
    }

    @Test
    void addDirectory() throws IOException {
        long timestamp = Instant.now().toEpochMilli();
        loggingMetadataRepository.addDirectory(new BackupDirectory("path", timestamp, null, Sets.newTreeSet(Lists.newArrayList("a")), null));
        Mockito.verify(repository).addDirectory(new BackupDirectory("path", timestamp, null, Sets.newTreeSet(Lists.newArrayList("a")), null));
        Mockito.verify(manifestManager).addLogEntry(anyString(), anyString());
    }

    @Test
    void deleteDirectory() throws IOException {
        long timestamp = Instant.now().toEpochMilli();
        loggingMetadataRepository.deleteDirectory("path", timestamp);
        Mockito.verify(repository).deleteDirectory("path", timestamp);
        Mockito.verify(manifestManager).addLogEntry(anyString(), anyString());
    }

    @Test
    void pushActivePath() throws IOException, InterruptedException {
        loggingMetadataRepository.pushActivePath("s1", "path", new BackupActivePath());
        Mockito.verify(repository, Mockito.never()).pushActivePath("s1", "path", new BackupActivePath());
        Mockito.verify(manifestManager, Mockito.never()).addLogEntry(anyString(), anyString());
        for (int i = 0; i < 6; i++) {
            Thread.sleep(100);
            loggingMetadataRepository.pushActivePath("s1", "path", new BackupActivePath("path",
                    Sets.newHashSet(BackupActiveFile.builder().path(i + "").build())));
            Mockito.verify(repository, Mockito.never()).pushActivePath("s1", "path", new BackupActivePath());
            Mockito.verify(manifestManager, Mockito.never()).addLogEntry(anyString(), anyString());
        }
        loggingMetadataRepository.pushActivePath("s1", "path2", new BackupActivePath());
        Mockito.verify(repository, Mockito.never()).pushActivePath("s1", "path2", new BackupActivePath());

        BackupActivePath path = new BackupActivePath("path", Sets.newHashSet());
        loggingMetadataRepository.pushActivePath("s1", "path", path);
        Mockito.verify(manifestManager, Mockito.never()).addLogEntry(anyString(), anyString());
        Thread.sleep(2000);
        Mockito.verify(repository).pushActivePath("s1", "path", new BackupActivePath());
        Mockito.verify(manifestManager, Mockito.times(2)).addLogEntry(anyString(), anyString());
        Mockito.verify(manifestManager).addLogEntry(anyString(),
                eq(new ObjectMapper().writeValueAsString(new PushActivePath("s1", "path", path))));
    }

    @Test
    void popActivePath() throws IOException {
        loggingMetadataRepository.popActivePath("s1", "path");
        Mockito.verify(repository).popActivePath("s1", "path");
        Mockito.verify(manifestManager).addLogEntry(anyString(), anyString());
    }

    @Test
    void getActivePaths() throws IOException {
        loggingMetadataRepository.getActivePaths(null);
        Mockito.verify(repository).getActivePaths(null);
        Mockito.verify(manifestManager, Mockito.never()).addLogEntry(anyString(), anyString());
    }

    @Test
    public void addPendingSet() throws IOException {
        loggingMetadataRepository.addPendingSets(new BackupPendingSet());
        Mockito.verify(repository).addPendingSets(new BackupPendingSet());
    }

    @Test
    public void getPendingSets() throws IOException {
        Mockito.when(repository.getPendingSets()).thenReturn(new HashSet<>());
        assertThat(loggingMetadataRepository.getPendingSets(), Is.is(new HashSet<>()));
        Mockito.verify(repository).getPendingSets();
    }

    @Test
    public void deletePendingSet() throws IOException {
        loggingMetadataRepository.deletePendingSets("1");
        Mockito.verify(repository).deletePendingSets("1");
    }
}
