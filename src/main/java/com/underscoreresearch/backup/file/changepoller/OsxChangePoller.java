package com.underscoreresearch.backup.file.changepoller;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Watchable;
import java.util.List;

import io.methvin.watchservice.MacOSXListeningWatchService;
import io.methvin.watchservice.WatchablePath;

public class OsxChangePoller extends BaseWatcherChangePoller {
    public OsxChangePoller() throws IOException {
        super(new MacOSXListeningWatchService());
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
