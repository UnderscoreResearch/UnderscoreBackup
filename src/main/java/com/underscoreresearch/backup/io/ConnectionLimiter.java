package com.underscoreresearch.backup.io;

import com.google.common.base.Stopwatch;
import com.underscoreresearch.backup.model.BackupDestination;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static com.underscoreresearch.backup.utils.LogUtil.debug;

@Slf4j
public class ConnectionLimiter {
    private final int maximumConnections;
    private final Object lock = new Object();
    private final Stopwatch stopwatch = Stopwatch.createStarted();
    private int currentConnections = 0;

    public ConnectionLimiter(int maximumConnections) {
        this.maximumConnections = maximumConnections;
    }

    public ConnectionLimiter(BackupDestination destination) {
        maximumConnections = destination.getMaxConnections() != null ? destination.getMaxConnections() : 0;
    }

    public void acquire() {
        synchronized (lock) {
            if (maximumConnections > 0) {
                while (currentConnections >= maximumConnections) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            currentConnections++;
            debug(() -> {
                if (currentConnections > maximumConnections * 0.75 && stopwatch.elapsed(TimeUnit.MINUTES) > 1) {
                    if (maximumConnections > 0) {
                        log.debug("{}/{} connections used", currentConnections, maximumConnections);
                    } else {
                        log.debug("{} connections used", currentConnections);
                    }
                    stopwatch.reset().start();
                }
            });
        }
    }

    public void release() {
        synchronized (lock) {
            currentConnections--;
            lock.notifyAll();
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
