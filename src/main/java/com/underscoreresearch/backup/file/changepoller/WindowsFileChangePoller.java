package com.underscoreresearch.backup.file.changepoller;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.Watchable;
import java.util.List;

import com.sun.nio.file.ExtendedWatchEventModifier;

public class WindowsFileChangePoller extends BaseWatcherChangePoller {
    public WindowsFileChangePoller() throws IOException {
        super(FileSystems.getDefault().newWatchService());
    }

    @Override
    public void registerPaths(List<Path> paths) throws IOException {
        for (Path path : paths) {
            path.register(getWatchService(), new WatchEvent.Kind[]{
                            ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW
                    },
                    ExtendedWatchEventModifier.FILE_TREE);
        }
    }

    @Override
    protected Path getEventPath(Watchable watchable) {
        return (Path) watchable;
    }
}
