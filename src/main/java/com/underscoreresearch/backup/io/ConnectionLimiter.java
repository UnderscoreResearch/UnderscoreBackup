package com.underscoreresearch.backup.io;

import com.underscoreresearch.backup.model.BackupDestination;

import java.util.concurrent.Callable;

public class ConnectionLimiter {
    private final int maximumConnections;
    private final Object lock = new Object();
    private int currentConnections = 0;

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
