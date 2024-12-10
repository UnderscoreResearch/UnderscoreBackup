package com.underscoreresearch.backup.io;

import com.google.common.base.Stopwatch;
import com.underscoreresearch.backup.model.BackupDestination;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ConnectionLimiter {
    private final int maximumConnections;
    private final Object lock = new Object();
    private int currentConnections = 0;
    private final Stopwatch stopwatch = Stopwatch.createStarted();

    public ConnectionLimiter(int maximumConnections) {
        this.maximumConnections = maximumConnections;
    }

    public ConnectionLimiter(BackupDestination destination) {
        maximumConnections = destination.getMaxConnections() != null ? destination.getMaxConnections() : 0;
    }

    public void acquire() {
        if (maximumConnections > 0) {
            synchronized (lock) {
                while (currentConnections >= maximumConnections) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (currentConnections > maximumConnections * 0.75 && stopwatch.elapsed(TimeUnit.MINUTES) > 1) {
                    log.info("{}/{} connections used", currentConnections, maximumConnections);
                    stopwatch.reset().start();
                }
                currentConnections++;
            }
        }
    }

    public void release() {
        if (maximumConnections > 0) {
            synchronized (lock) {
                currentConnections--;
                lock.notifyAll();
            }
        }
    }

    public <T> T call(Callable<T> callable) throws Exception {
        acquire();
        try {
            return callable.call();
        } finally {
            release();
        }
    }
}