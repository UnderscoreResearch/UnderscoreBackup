package com.underscoreresearch.backup.file.changepoller;

import com.sun.nio.file.ExtendedWatchEventModifier;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.Watchable;
import java.util.List;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

/**
 * Implementation of FileChangePoller for Windows systems.
 * This class uses the Windows-specific ExtendedWatchEventModifier.FILE_TREE
 * to monitor file system changes recursively on Windows.
 */
public class WindowsFileChangePoller extends BaseWatcherChangePoller {
    /**
     * Constructor for WindowsFileChangePoller.
     * Initializes the poller with the default file system's WatchService.
     *
     * @throws IOException If there's an error initializing the watch service
     */
    public WindowsFileChangePoller() throws IOException {
        super(FileSystems.getDefault().newWatchService());
    }

    /**
     * Registers paths to be monitored for changes.
     * This implementation uses the Windows-specific FILE_TREE modifier to
     * monitor directories recursively.
     *
     * @param paths List of paths to monitor
     * @throws IOException If there's an error registering the paths
     */
    @Override
    public void registerPaths(List<Path> paths) throws IOException {
        for (Path path : paths) {
            path.register(getWatchService(), new WatchEvent.Kind[]{
                            ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW
                    },
                    ExtendedWatchEventModifier.FILE_TREE);
        }
    }

    /**
     * Gets the path associated with a watchable object.
     * For the default WatchService, the watchable is a Path.
     *
     * @param watchable The watchable object
     * @return The path associated with the watchable
     */
    @Override
    protected Path getEventPath(Watchable watchable) {
        return (Path) watchable;
    }
}
