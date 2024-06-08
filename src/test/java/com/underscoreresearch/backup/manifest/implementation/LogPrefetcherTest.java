package com.underscoreresearch.backup.manifest.implementation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.google.common.base.Stopwatch;
import com.underscoreresearch.backup.encryption.encryptors.NoneEncryptor;
import com.underscoreresearch.backup.model.BackupConfiguration;

class LogPrefetcherTest {
    @Test
    public void testConcurrency() {
        List<String> files = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            files.add("file" + i);
        }
        LogPrefetcher logPrefetcher = new LogPrefetcher(files, BackupConfiguration.builder().build(), (file) -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return file.getBytes(StandardCharsets.UTF_8);
        }, new NoneEncryptor(), null);
        Stopwatch stopwatch = Stopwatch.createStarted();
        logPrefetcher.start();
        for (int i = 0; i < 100; i++) {
            try {
                assertEquals("file" + i,
                        new String(logPrefetcher.getLog("file" + i), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        assertThat(stopwatch.elapsed().toMillis(), Matchers.greaterThanOrEqualTo(2500L));
        logPrefetcher.shutdown();
    }

    @Test
    public void testFailure() {
        List<String> files = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            files.add("file" + i);
        }
        LogPrefetcher logPrefetcher = new LogPrefetcher(files, BackupConfiguration.builder().build(), (file) -> {
            if (file.equals("file10"))
                throw new IOException("Doh");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return file.getBytes(StandardCharsets.UTF_8);
        }, new NoneEncryptor(), null);
        Stopwatch stopwatch = Stopwatch.createStarted();
        logPrefetcher.start();
        assertThrows(IOException.class, () -> {
            for (int i = 0; i < 11; i++) {
                assertEquals("file" + i,
                        new String(logPrefetcher.getLog("file" + i), StandardCharsets.UTF_8));
            }
        });
        logPrefetcher.shutdown();
        assertThat(stopwatch.elapsed().toMillis(), Matchers.lessThanOrEqualTo(2500L));
    }
}