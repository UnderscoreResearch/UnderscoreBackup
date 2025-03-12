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

public abstract class BaseWatcherChangePoller implements FileChangePoller {
    @Getter(AccessLevel.PROTECTED)
    private final WatchService watchService;
    private final AtomicBoolean overflow = new AtomicBoolean(false);

    public BaseWatcherChangePoller(WatchService service) throws IOException {
        this.watchService = service;
    }

    @Override
    public abstract void registerPaths(List<Path> paths) throws IOException;

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

    protected abstract Path getEventPath(Watchable watchable);

    @Override
    public void close() throws IOException {
        watchService.close();
    }
}
