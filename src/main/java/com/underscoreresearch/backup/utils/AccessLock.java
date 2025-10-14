package com.underscoreresearch.backup.utils;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.FileLockInterruptionException;
import java.nio.channels.OverlappingFileLockException;

/**
 * Provides file-based locking mechanism to ensure exclusive or shared access to resources.
 * This class handles acquiring, managing, and releasing file locks for coordinating access
 * between multiple processes or threads.
 */
@Slf4j
public class AccessLock implements Closeable {
    @Getter
    private final String filename;
    private RandomAccessFile file;
    private FileChannel channel;
    private FileLock lock;

    /**
     * Creates a new AccessLock for the specified file.
     *
     * @param filename The path to the file to use for locking
     */
    public AccessLock(String filename) {
        this.filename = filename;
    }

    /**
     * Gets the file channel associated with the currently held lock.
     *
     * @return The file channel
     * @throws IOException If the lock is not currently held
     */
    public synchronized FileChannel getLockedChannel() throws IOException {
        if (lock != null) {
            return lock.channel();
        } else {
            throw new IOException("Tried getting channel of unheld lock");
        }
    }

    /**
     * Attempts to acquire a lock on the file without blocking.
     *
     * @param exclusive Whether to acquire an exclusive (write) lock or a shared (read) lock
     * @return True if the lock was acquired, false otherwise
     * @throws IOException If there was an error accessing the file
     */
    public synchronized boolean tryLock(boolean exclusive) throws IOException {
        ensureOpenFile();
        if (lock == null) {
            while (true) {
                try {
                    lock = channel.tryLock(0, Long.MAX_VALUE, !exclusive);
                    if (lock == null || lock.isValid()) {
                        return lock != null;
                    }
                    release();
                } catch (ClosedChannelException e) {
                    ensureOpenFile();
                } catch (OverlappingFileLockException e) {
                    log.error("Overlapping file lock", e);
                    return false;
                } catch (FileLockInterruptionException ignored) {
                }
            }
        }
        return true;
    }

    /**
     * Ensures the file is open and ready for locking operations.
     * If the file channel is closed or null, reopens the file.
     *
     * @throws IOException If there was an error opening the file
     */
    private void ensureOpenFile() throws IOException {
        if (channel == null || !channel.isOpen()) {
            close();
            file = new RandomAccessFile(filename, "rw");
            channel = file.getChannel();
        }
    }

    /**
     * Acquires a lock on the file, blocking until the lock is available.
     *
     * @param exclusive Whether to acquire an exclusive (write) lock or a shared (read) lock
     * @throws IOException If there was an error accessing the file
     */
    public synchronized void lock(boolean exclusive) throws IOException {
        ensureOpenFile();
        if (lock == null) {
            while (true) {
                try {
                    Thread.interrupted();
                    do {
                        lock = channel.lock(0, Long.MAX_VALUE, !exclusive);
                    } while (lock == null || !lock.isValid());
                    break;
                } catch (ClosedChannelException e) {
                    ensureOpenFile();
                } catch (FileLockInterruptionException ignored) {
                }
            }
        }
    }

    /**
     * Releases the currently held lock, if any.
     *
     * @throws IOException If there was an error releasing the lock
     */
    public synchronized void release() throws IOException {
        if (lock != null) {
            if (lock.channel().isOpen()) {
                lock.close();
            }
            lock = null;
        }
    }

    /**
     * Closes this access lock, releasing any held lock and closing the file.
     *
     * @throws IOException If there was an error closing the file
     */
    @Override
    public synchronized void close() throws IOException {
        release();
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        if (file != null) {
            file.close();
        }
    }
}
