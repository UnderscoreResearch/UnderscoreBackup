package com.underscoreresearch.backup.io;

import com.google.common.base.Stopwatch;
import com.underscoreresearch.backup.model.BackupDestination;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static com.underscoreresearch.backup.utils.log.LogUtil.debug;

/**
 * Utility for limiting the number of concurrent connections to a destination.
 * Provides methods for acquiring and releasing connection permits.
 */
@Slf4j
public class ConnectionLimiter {
    private final int maximumConnections;
    private final Object lock = new Object();
    private final Stopwatch stopwatch = Stopwatch.createStarted();
    private int currentConnections = 0;

    /**
     * Constructor with explicit maximum connections.
     *
     * @param maximumConnections The maximum number of concurrent connections
     */
    public ConnectionLimiter(int maximumConnections) {
        this.maximumConnections = maximumConnections;
    }

    /**
     * Constructor that extracts maximum connections from a destination.
     *
     * @param destination The backup destination
     */
    public ConnectionLimiter(BackupDestination destination) {
        maximumConnections = destination.getMaxConnections() != null ? destination.getMaxConnections() : 0;
    }

    /**
     * Try to acquire a connection permit without blocking.
     * Returns true if a permit was acquired, false if the maximum number of connections is reached.
     *
     * @return True if a connection permit was acquired, false otherwise
     */
    public boolean tryAcquire() {
        synchronized (lock) {
            if (maximumConnections > 0) {
                if (currentConnections >= maximumConnections) {
                    return false;
                }
            }
            addAcquiredConnection();
        }
        return true;
    }

    /**
     * Adds an acquired connection to the current count and logs if necessary.
     */
    private void addAcquiredConnection() {
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

    /**
     * Acquire a connection permit.
     * This method will block if the maximum number of connections is reached.
     */
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
            addAcquiredConnection();
        }
    }

    /**
     * Release a connection permit.
     * This should be called when a connection is no longer needed.
     */
    public void release() {
        synchronized (lock) {
            currentConnections--;
            lock.notifyAll();
        }
    }

    /**
     * Execute a callable with a connection permit.
     * The permit is automatically acquired before the call and released after.
     *
     * @param callable The callable to execute
     * @param <T> The return type of the callable
     * @return The result of the callable
     * @throws Exception If the callable throws an exception
     */
    public <T> T call(Callable<T> callable) throws Exception {
        acquire();
        try {
            return callable.call();
        } finally {
            release();
        }
    }
}
