package com.underscoreresearch.backup.file.changepoller;

import io.methvin.watchservice.MacOSXListeningWatchService;
import io.methvin.watchservice.WatchablePath;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Watchable;
import java.util.List;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

/**
 * Implementation of FileChangePoller for macOS systems.
 * This class uses the MacOSXListeningWatchService to monitor file system changes
 * on macOS, which provides better performance than the default Java WatchService.
 */
public class OsxChangePoller extends BaseWatcherChangePoller {
    /**
     * Constructor for OsxChangePoller.
     * Initializes the poller with a MacOSXListeningWatchService.
     *
     * @throws IOException If there's an error initializing the watch service
     */
    public OsxChangePoller() throws IOException {
        super(new MacOSXListeningWatchService());
    }

    /**
     * Registers paths to be monitored for changes.
     * This implementation registers each path with the MacOSXListeningWatchService
     * to monitor for create, delete, modify, and overflow events.
     *
     * @param paths List of paths to monitor
     * @throws IOException If there's an error registering the paths
     */
    @Override
    public void registerPaths(List<Path> paths) throws IOException {
        for (Path path : paths) {
            new WatchablePath(path).register(getWatchService(), ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW);
        }
    }

    /**
     * Gets the path associated with a watchable object.
     * For MacOSXListeningWatchService, the watchable is a WatchablePath.
     *
     * @param watchable The watchable object
     * @return The path associated with the watchable
     */
    @Override
    protected Path getEventPath(Watchable watchable) {
        return ((WatchablePath) watchable).getFile();
    }
}
