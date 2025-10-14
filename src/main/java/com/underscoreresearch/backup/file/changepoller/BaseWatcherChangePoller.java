package com.underscoreresearch.backup.file.changepoller;

import lombok.AccessLevel;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

/**
 * Base implementation of FileChangePoller that uses a WatchService.
 * This abstract class provides common functionality for implementations
 * that use the Java WatchService API to monitor file system changes.
 */
public abstract class BaseWatcherChangePoller implements FileChangePoller {
    @Getter(AccessLevel.PROTECTED)
    private final WatchService watchService;
    private final AtomicBoolean overflow = new AtomicBoolean(false);

    /**
     * Constructor for BaseWatcherChangePoller.
     *
     * @param service The WatchService to use for monitoring file system changes
     * @throws IOException If there's an error initializing the WatchService
     */
    public BaseWatcherChangePoller(WatchService service) throws IOException {
        this.watchService = service;
    }

    /**
     * Registers paths with the WatchService.
     * This method should be implemented by subclasses to specify the paths to monitor.
     *
     * @param paths List of paths to register with the WatchService
     * @throws IOException If there's an error registering the paths
     */
    @Override
    public abstract void registerPaths(List<Path> paths) throws IOException;

    /**
     * Fetches paths that have changed since the last call.
     * This implementation uses the WatchService to detect changes.
     *
     * @return List of changed paths as strings
     * @throws OverflowException If there are too many changes to report
     */
    @Override
    public List<String> fetchPaths() throws OverflowException {
        if (overflow.get()) {
            overflow.set(false);
            throw new OverflowException();
        }

        WatchKey key;
        try {
            key = watchService.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ArrayList<>();
        } catch (ClosedWatchServiceException e) {
            return new ArrayList<>();
        }
        List<String> paths = new ArrayList<>();
        if (key != null) {
            Path watchedPath = getEventPath(key.watchable());
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind().equals(OVERFLOW)) {
                    overflow.set(true);
                    break;
                }
                Path path = watchedPath.resolve((Path) event.context());
                paths.add(path.toAbsolutePath().toString());
            }
            key.reset();
        }
        return paths;
    }

    /**
     * Gets the path associated with a watchable object.
     *
     * @param watchable The watchable object
     * @return The path associated with the watchable
     */
    protected abstract Path getEventPath(Watchable watchable);

    /**
     * Closes the WatchService and releases resources.
     *
     * @throws IOException If there's an error closing the WatchService
     */
    @Override
    public void close() throws IOException {
        watchService.close();
    }
}
