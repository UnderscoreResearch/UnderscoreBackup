package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.TreeSet;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.model.BackupFile;

class FileSystemAccessImplTest {
    private FileSystemAccessImpl access = new FileSystemAccessImpl();
    private File tempDir;
    private byte[] data;

    @BeforeEach
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory("test").toFile();
        data = new byte[128];
        for (int i = 0; i < data.length; i++)
            data[i] = (byte) i;
    }

    @Test
    public void testFile() throws IOException {
        String normalizedRoot = PathNormalizer.normalizePath(tempDir.getPath());

        access.writeData(normalizedRoot + PATH_SEPARATOR + "f1", data, 0, data.length);

        byte[] read = new byte[data.length];
        assertThat(access.readData(normalizedRoot + PATH_SEPARATOR + "f1", read, 0, data.length), Is.is(data.length));
        for (int i = 0; i < read.length; i++)
            assertThat(read[i], Is.is((byte) i));

        access.writeData(normalizedRoot + PATH_SEPARATOR + "f1", data, data.length / 4, data.length / 2);

        read = new byte[data.length];
        assertThat(access.readData(normalizedRoot + PATH_SEPARATOR + "f1", read, data.length / 2, data.length / 2),
                Is.is(data.length / 2));

        for (int i = 0; i < read.length / 4; i++)
            assertThat("Position " + i, read[i], Is.is((byte) (i + read.length / 4)));
        for (int i = read.length / 4; i < read.length / 4; i++)
            assertThat("Position " + i, read[i], Is.is((byte) (i - read.length / 4)));
        for (int i = read.length / 2; i < read.length; i++)
            assertThat("Position " + i, read[i], Is.is((byte) 0));

        access.truncate(normalizedRoot + PATH_SEPARATOR + "f1", data.length / 2);
        BackupFile expectedFile = BackupFile.builder().path(normalizedRoot + PATH_SEPARATOR + "f1")
                .lastChanged(new File(tempDir, "f1").lastModified()).length((long) data.length / 2).build();
        assertThat(expectedFile.isDirectory(), Is.is(false));

        access.truncate(normalizedRoot + PATH_SEPARATOR + "f1", data.length / 2);
        assertThat(new File(tempDir, "f1").length(), Is.is((long) data.length / 2));

        TreeSet set = new TreeSet();
        set.add(expectedFile);
        assertThat(access.directoryFiles(normalizedRoot), Is.is(set));
    }

    @Test
    public void testDirectory() throws IOException {
        String normalizedRoot = PathNormalizer.normalizePath(tempDir.getPath());

        access.writeData(normalizedRoot + PATH_SEPARATOR + "d1" + PATH_SEPARATOR
                + "d2" + PATH_SEPARATOR
                + "f1", data, 0, data.length);

        BackupFile expectedFile = BackupFile.builder().path(normalizedRoot + PATH_SEPARATOR + "d1" + PATH_SEPARATOR)
                .build();
        assertThat(expectedFile.isDirectory(), Is.is(true));

        TreeSet set = new TreeSet();
        set.add(expectedFile);
        assertThat(access.directoryFiles(normalizedRoot), Is.is(set));
    }

    @Test
    public void testSpecialFiles() throws IOException {
        if (new File("/dev").isDirectory()) {
            assertFalse(access.directoryFiles(PATH_SEPARATOR + "dev" + PATH_SEPARATOR).stream()
                    .anyMatch(t -> t.getPath().equals("/dev/null")));
        }
    }

    @AfterEach()
    public void teardown() {
        deleteDir(tempDir);
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
}