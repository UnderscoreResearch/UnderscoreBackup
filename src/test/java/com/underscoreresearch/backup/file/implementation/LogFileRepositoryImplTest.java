package com.underscoreresearch.backup.file.implementation;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogFileRepositoryImplTest {
    private Path tempFile;
    private LogFileRepositoryImpl logFileRepository;

    @BeforeEach
    void setUp() throws IOException {
        tempFile = Files.createTempFile("logFileRepositoryTest", ".log");
        logFileRepository = new LogFileRepositoryImpl(tempFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        logFileRepository.close();
        Files.deleteIfExists(tempFile);
    }

    @Test
    void addFileWritesToFile() throws IOException {
        String file = "testFile";
        logFileRepository.addFile(file);

        List<String> files = logFileRepository.getAllFiles();
        assertEquals(1, files.size());
        assertEquals(file, files.get(0));
    }

    @Test
    void addFileThrowsIOExceptionOnWriteFailure() throws IOException {
        logFileRepository.close();
        String file = "testFile";

        assertThrows(IOException.class, () -> logFileRepository.addFile(file));
    }

    @Test
    void resetFilesWritesAllFiles() throws IOException {
        List<String> files = List.of("file1", "file2");
        logFileRepository.resetFiles(files);

        List<String> allFiles = logFileRepository.getAllFiles();
        assertEquals(files.size(), allFiles.size());
        assertTrue(allFiles.containsAll(files));
    }

    @Test
    void getAllFilesReadsAllFiles() throws IOException {
        List<String> files = List.of("file1", "file2");
        logFileRepository.resetFiles(files);

        List<String> allFiles = logFileRepository.getAllFiles();
        assertEquals(files.size(), allFiles.size());
        assertTrue(allFiles.containsAll(files));
    }

    @Test
    void getRandomFileReturnsRandomFile() throws IOException {
        logFileRepository.addFile("otherFile");
        logFileRepository.addFile("otherFile2");
        List<String> files = List.of("file1", "file2", "file3");
        logFileRepository.resetFiles(files);

        String randomFile = logFileRepository.getRandomFile();
        assertNotNull(randomFile);
        assertTrue(files.contains(randomFile));
    }

    @Test
    void trimLogFiles_noChange() {
        List<String> files = Lists.newArrayList("file1-i.gz", "file2.gz", "file3-c.gz");

        files = LogFileRepositoryImpl.trimLogFiles(files);

        assertEquals(3, files.size());
        assertTrue(files.contains("file1-i.gz"));
        assertTrue(files.contains("file2.gz"));
        assertTrue(files.contains("file3-c.gz"));
    }

    @Test
    void trimLogFiles_ic() {
        List<String> files = Lists.newArrayList("file1-i.gz", "file2-ic.gz", "file3.gz");

        files = LogFileRepositoryImpl.trimLogFiles(files);

        assertEquals(2, files.size());
        assertTrue(files.contains("file2-ic.gz"));
        assertTrue(files.contains("file3.gz"));
    }

    @Test
    void trimLogFiles_noComplete() {
        List<String> files = Lists.newArrayList("file1.gz", "file2-i.gz", "file3.gz");

        files = LogFileRepositoryImpl.trimLogFiles(files);

        assertEquals(3, files.size());
        assertTrue(files.contains("file1.gz"));
        assertTrue(files.contains("file2-i.gz"));
        assertTrue(files.contains("file3.gz"));
    }

    @Test
    void trimLogFiles_removeOld() {
        List<String> files = Lists.newArrayList("file1.gz", "file2-i.gz", "file3-c.gz");

        files = LogFileRepositoryImpl.trimLogFiles(files);

        assertEquals(2, files.size());
        assertTrue(files.contains("file2-i.gz"));
        assertTrue(files.contains("file3-c.gz"));
    }

    @Test
    void trimLogFiles_removeOldExtraStart() {
        List<String> files = Lists.newArrayList("file1.gz", "file2-i.gz", "file3-c.gz", "file4-i.gz");

        files = LogFileRepositoryImpl.trimLogFiles(files);

        assertEquals(3, files.size());
        assertTrue(files.contains("file2-i.gz"));
        assertTrue(files.contains("file3-c.gz"));
        assertTrue(files.contains("file4-i.gz"));
    }
}
