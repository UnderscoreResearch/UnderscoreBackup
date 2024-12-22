package com.underscoreresearch.backup.file.implementation;

import com.underscoreresearch.backup.file.CloseableLock;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MapdbMetadataRepositoryTest extends LockingMetadataRepositoryTest {
    @Override
    protected LockingMetadataRepository createRepository(File tempDir) {
        return new LockingMetadataRepository(tempDir.getPath(), false, LockingMetadataRepository.MAPDB_STORAGE);
    }

    @Test
    public void testLocks() {
        try (ExecutorService threads = Executors.newFixedThreadPool(10)) {
            AtomicInteger count = new AtomicInteger(0);

            for (int i = 0; i < 100; i++) {
                threads.submit(() -> {
                    try (CloseableLock ignored = repository.acquireLock()) {
                        assertThat(count.incrementAndGet(), Is.is(1));
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        assertThat(count.decrementAndGet(), Is.is(0));
                    }
                });
            }
        }
    }

    @Test
    public void testLockRequested() throws InterruptedException {
        try (CloseableLock lock = repository.acquireLock()) {
            assertFalse(lock.requested());
            new Thread(() -> {
                try (CloseableLock lock2 = repository.acquireLock()) {
                }
            }).start();

            Thread.sleep(10);
            assertTrue(lock.requested());
        }
    }
}
