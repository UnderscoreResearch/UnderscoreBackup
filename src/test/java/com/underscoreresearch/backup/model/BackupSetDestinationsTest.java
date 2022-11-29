package com.underscoreresearch.backup.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

class BackupSetDestinationsTest {
    private File manifestFile;
    private String manifestLocation;
    private BackupSet backupSet;

    @BeforeEach
    public void setup() throws IOException {
        manifestFile = Files.createTempDirectory("manifest").toFile();
        manifestLocation = manifestFile.getAbsolutePath();
        backupSet = BackupSet.builder().id("set").build();
    }

    @AfterEach
    public void teardown() throws IOException {
        deleteDir(manifestFile);
    }

    @Test
    public void testBasic() throws IOException {
        backupSet.setDestinations(Lists.newArrayList("d0"));
        assertTrue(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, true));
        assertTrue(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, false));
        BackupSetDestinations.completedStorageValidation(manifestLocation, backupSet);
        assertFalse(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, true));
        assertFalse(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, false));
    }

    @Test
    public void testNonInitial() throws IOException {
        backupSet.setDestinations(Lists.newArrayList("d0"));
        assertTrue(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, false));
        assertTrue(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, false));
        BackupSetDestinations.completedStorageValidation(manifestLocation, backupSet);
        assertTrue(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, true));
        assertTrue(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, false));
        BackupSetDestinations.completedStorageValidation(manifestLocation, backupSet);
        assertFalse(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, true));
    }

    @Test
    public void testRemoving() throws IOException {
        backupSet.setDestinations(Lists.newArrayList("d0", "d1", "d2"));
        assertTrue(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, true));
        BackupSetDestinations.completedStorageValidation(manifestLocation, backupSet);
        assertFalse(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, true));
        backupSet.setDestinations(Lists.newArrayList("d0", "d2"));
        assertFalse(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, false));
        BackupSetDestinations.completedStorageValidation(manifestLocation, backupSet);
        backupSet.setDestinations(Lists.newArrayList("d0"));
        assertFalse(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, true));
    }

    @Test
    public void testAddRemove() throws IOException {
        backupSet.setDestinations(Lists.newArrayList("d0", "d1"));
        assertTrue(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, true));
        BackupSetDestinations.completedStorageValidation(manifestLocation, backupSet);
        backupSet.setDestinations(Lists.newArrayList("d0"));
        assertFalse(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, true));
        backupSet.setDestinations(Lists.newArrayList("d0", "d1"));
        assertTrue(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, false));
        BackupSetDestinations.completedStorageValidation(manifestLocation, backupSet);
        assertTrue(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, true));
        BackupSetDestinations.completedStorageValidation(manifestLocation, backupSet);
        assertFalse(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, true));
    }

    @Test
    public void testAdd() throws IOException {
        backupSet.setDestinations(Lists.newArrayList("d0"));
        assertTrue(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, true));
        BackupSetDestinations.completedStorageValidation(manifestLocation, backupSet);
        backupSet.setDestinations(Lists.newArrayList("d0", "d1"));
        assertTrue(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, true));
        BackupSetDestinations.completedStorageValidation(manifestLocation, backupSet);
        assertFalse(BackupSetDestinations.needStorageValidation(manifestLocation, backupSet, true));
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