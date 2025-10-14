package com.underscoreresearch.backup.file;

import java.io.Closeable;

/**
 * Abstract class representing a closeable lock mechanism.
 * Provides a way to acquire and release locks with automatic resource management
 * through the Closeable interface.
 */
public abstract class CloseableLock implements Closeable {
    /**
     * Closes this lock, releasing any system resources associated with it.
     */
    @Override
    public abstract void close();

    /**
     * Checks if the lock has been requested.
     *
     * @return true if the lock has been requested, false otherwise
     */
    public abstract boolean requested();
}
