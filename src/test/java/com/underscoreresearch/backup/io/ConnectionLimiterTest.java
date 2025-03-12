package com.underscoreresearch.backup.io;

import com.google.common.base.Stopwatch;
import com.underscoreresearch.backup.model.BackupDestination;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

class ConnectionLimiterTest {
    private static ExecutorService executor = Executors.newFixedThreadPool(10);
    private ConnectionLimiter limiter;

    @BeforeEach
    void setup() {
        BackupDestination destination = new BackupDestination();
        destination.setMaxConnections(5);
        limiter = new ConnectionLimiter(destination);
    }

    @Test
    void acquire() {
        List<Future<?>> futures = new ArrayList<>();

        Stopwatch stopwatch = Stopwatch.createStarted();
        AtomicInteger concurrent = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            Future<?> future = executor.submit(() -> {
                limiter.acquire();
                if (concurrent.incrementAndGet() > 5) {
                    throw new RuntimeException("Too many connections");
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                concurrent.decrementAndGet();
                limiter.release();
            });
            futures.add(future);
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        if (stopwatch.elapsed().toMillis() < 200) {
            throw new RuntimeException("Completed too fast");
        }

        if (stopwatch.elapsed().toMillis() > 500) {
            throw new RuntimeException("Completed too slow");
        }
    }

    @Test
    void call() {
        List<Future<?>> futures = new ArrayList<>();

        Stopwatch stopwatch = Stopwatch.createStarted();
        AtomicInteger concurrent = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            Future<?> future = executor.submit(() -> {
                try {
                    limiter.call(() -> {
                        if (concurrent.incrementAndGet() > 5) {
                            throw new RuntimeException("Too many connections");
                        }
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        concurrent.decrementAndGet();
                        return null;
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        if (stopwatch.elapsed().toMillis() < 200) {
            throw new RuntimeException("Completed too fast");
        }

        if (stopwatch.elapsed().toMillis() > 500) {
            throw new RuntimeException("Completed too slow");
        }
    }

}