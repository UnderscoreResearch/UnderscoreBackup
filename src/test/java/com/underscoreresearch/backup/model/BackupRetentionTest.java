package com.underscoreresearch.backup.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupRetentionTest {
    private BackupRetention retention;
    private BackupFile testFile = new BackupFile();
    private BackupFile previousFile = new BackupFile();

    @BeforeEach
    public void setup() {
        TreeSet<BackupRetentionAdditional> older = new TreeSet<>();
        older.add(new BackupRetentionAdditional(new BackupTimespan(2L, BackupTimeUnit.MONTHS),
                new BackupTimespan(1L, BackupTimeUnit.WEEKS)));
        older.add(new BackupRetentionAdditional(new BackupTimespan(2L, BackupTimeUnit.WEEKS),
                new BackupTimespan(2L, BackupTimeUnit.DAYS)));
        retention = BackupRetention.builder()
                .defaultFrequency(new BackupTimespan(5L, BackupTimeUnit.MINUTES))
                .retainDeleted(new BackupTimespan(10L, BackupTimeUnit.MINUTES))
                .older(older)
                .build();
    }

    @Test
    public void testDeletedOnly() {
        testFile.setDeleted(new BackupTimespan(9L, BackupTimeUnit.MINUTES).toInstant().toEpochMilli());
        assertTrue(retention.keepFile(testFile, null, true));
        testFile.setDeleted(new BackupTimespan(11L, BackupTimeUnit.MINUTES).toInstant().toEpochMilli());
        assertFalse(retention.keepFile(testFile, null, true));
    }

    @Test
    public void testDeleteImmediately() {
        retention.setRetainDeleted(new BackupTimespan());
        assertFalse(retention.keepFile(testFile, null, true));
        assertTrue(retention.keepFile(testFile, null, false));
    }

    @Test
    public void testImmediately() {
        retention.setDefaultFrequency(new BackupTimespan());
        retention.setOlder(null);
        assertTrue(retention.keepFile(testFile, testFile, false));
    }

    @Test
    public void testForever() {
        retention.setDefaultFrequency(BackupTimespan.builder().unit(BackupTimeUnit.FOREVER).build());
        retention.setOlder(null);
        assertFalse(retention.keepFile(testFile, testFile, false));
    }

    @Test
    void testDefaultFrequency() {
        previousFile.setAdded(new BackupTimespan(5L, BackupTimeUnit.MINUTES).toInstant().toEpochMilli());
        testFile.setAdded(new BackupTimespan(9L, BackupTimeUnit.MINUTES).toInstant().toEpochMilli());
        assertFalse(retention.keepFile(testFile, previousFile, false));
        testFile.setAdded(new BackupTimespan(11L, BackupTimeUnit.MINUTES).toInstant().toEpochMilli());
        assertTrue(retention.keepFile(testFile, previousFile, false));
    }

    @Test
    void testEndDefaultFrequency() {
        previousFile.setAdded(new BackupTimespan(12, BackupTimeUnit.DAYS).toInstant().toEpochMilli());
        testFile.setAdded(new BackupTimespan(13, BackupTimeUnit.DAYS).toInstant().toEpochMilli());
        assertTrue(retention.keepFile(testFile, previousFile, false));
    }

    @Test
    void testSecondOlderFrequency() {
        previousFile.setAdded(new BackupTimespan(15, BackupTimeUnit.DAYS).toInstant().toEpochMilli());
        testFile.setAdded(new BackupTimespan(16, BackupTimeUnit.DAYS).toInstant().toEpochMilli());
        assertFalse(retention.keepFile(testFile, previousFile, false));
        testFile.setAdded(new BackupTimespan(18, BackupTimeUnit.DAYS).toInstant().toEpochMilli());
        assertTrue(retention.keepFile(testFile, previousFile, false));
    }

    @Test
    void testOldestFrequency() {
        previousFile.setAdded(new BackupTimespan(62, BackupTimeUnit.DAYS).toInstant().toEpochMilli());
        testFile.setAdded(new BackupTimespan(65, BackupTimeUnit.DAYS).toInstant().toEpochMilli());
        assertFalse(retention.keepFile(testFile, previousFile, false));
        testFile.setAdded(new BackupTimespan(80, BackupTimeUnit.DAYS).toInstant().toEpochMilli());
        assertTrue(retention.keepFile(testFile, previousFile, false));
    }

    @Test
    void testFirst() {
        testFile.setAdded(new BackupTimespan(2, BackupTimeUnit.SECONDS).toInstant().toEpochMilli());
        assertTrue(retention.keepFile(testFile, null, false));
        testFile.setAdded(new BackupTimespan(2, BackupTimeUnit.MINUTES).toInstant().toEpochMilli());
        assertTrue(retention.keepFile(testFile, null, false));
        testFile.setAdded(new BackupTimespan(2, BackupTimeUnit.HOURS).toInstant().toEpochMilli());
        assertTrue(retention.keepFile(testFile, null, false));
        testFile.setAdded(new BackupTimespan(2, BackupTimeUnit.WEEKS).toInstant().toEpochMilli());
        assertTrue(retention.keepFile(testFile, null, false));
        testFile.setAdded(new BackupTimespan(2, BackupTimeUnit.MONTHS).toInstant().toEpochMilli());
        assertTrue(retention.keepFile(testFile, null, false));
        testFile.setAdded(new BackupTimespan(2, BackupTimeUnit.YEARS).toInstant().toEpochMilli());
        assertTrue(retention.keepFile(testFile, null, false));
    }
}