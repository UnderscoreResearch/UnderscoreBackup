package com.underscoreresearch.backup.file.changepoller;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Watchable;
import java.util.List;

import io.methvin.watcher.hashing.FileHasher;
import io.methvin.watchservice.MacOSXListeningWatchService;
import io.methvin.watchservice.WatchablePath;

public class OsxChangePoller extends BaseWatcherChangePoller {
    private static final MacOSXListeningWatchService.Config WATCH_SERVICE_CONFIG = new MacOSXListeningWatchService.Config() {
        @Override
        public FileHasher fileHasher() {
            return null;
        }
    };

    public OsxChangePoller() throws IOException {
        super(new MacOSXListeningWatchService(WATCH_SERVICE_CONFIG));
    }

    @Override
    public void registerPaths(List<Path> paths) throws IOException {
        for (Path path : paths) {
            new WatchablePath(path).register(getWatchService(), ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW);
        }
    }

    @Override
    protected Path getEventPath(Watchable watchable) {
        return ((WatchablePath) watchable).getFile();
    }
}
