package com.underscoreresearch.backup.utils;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.FileLockInterruptionException;

import lombok.Getter;

public class AccessLock implements Closeable {
    @Getter
    private String filename;
    private RandomAccessFile file;
    private FileChannel channel;
    private FileLock lock;

    public synchronized FileChannel getLockedChannel() throws IOException {
        if (lock != null) {
            return lock.channel();
        } else {
            throw new IOException("Tried getting channel of unheld lock");
        }
    }

    public AccessLock(String filename) {
        this.filename = filename;
    }

    public synchronized boolean tryLock(boolean exclusive) throws IOException {
        ensureOpenFile();
        if (lock == null) {
            while (true) {
                try {
                    Thread.interrupted();
                    lock = channel.tryLock(0, Long.MAX_VALUE, !exclusive);
                    if (lock == null || lock.isValid()) {
                        return lock != null;
                    }
                    release();
                } catch (ClosedChannelException e) {
                    ensureOpenFile();
                } catch (FileLockInterruptionException e) {
                }
            }
        }
        return true;
    }

    private void ensureOpenFile() throws IOException {
        if (channel == null || !channel.isOpen()) {
            close();
            file = new RandomAccessFile(filename, "rw");
            channel = file.getChannel();
        }
    }

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
                } catch (FileLockInterruptionException e) {
                }
            }
        }
    }

    public synchronized void release() throws IOException {
        if (lock != null) {
            if (lock.channel().isOpen()) {
                lock.close();
            }
            lock = null;
        }
    }

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
