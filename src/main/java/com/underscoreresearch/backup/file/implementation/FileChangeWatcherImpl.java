package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import com.sun.nio.file.ExtendedWatchEventModifier;
import com.underscoreresearch.backup.file.ContinuousBackup;
import com.underscoreresearch.backup.file.FileChangeWatcher;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.model.BackupSetRoot;
import com.underscoreresearch.backup.model.BackupUpdatedFile;

@Slf4j
public class FileChangeWatcherImpl implements FileChangeWatcher {
    private final List<BackupSet> sets;
    private final Map<String, Long> whenBySet;
    private final MetadataRepository repository;
    private final ContinuousBackup continuousBackup;
    private final Path manifestDirectory;
    private WatchService watchService;



    private Lock lock = new ReentrantLock();
    private Thread thread;

    public FileChangeWatcherImpl(BackupConfiguration configuration, MetadataRepository repository,
                                 ContinuousBackup continuousBackup, String manifestDirectory) {
        this.repository = repository;
        this.manifestDirectory = FileSystems.getDefault().getPath(manifestDirectory);
        this.continuousBackup = continuousBackup;

        whenBySet = new HashMap<>();
        sets = getContinuousSets(configuration, whenBySet);
    }

    public static List<BackupSet> getContinuousSets(BackupConfiguration configuration, Map<String, Long> whenBySet) {
        return configuration.getSets().stream().filter(set ->
        {
            Duration duration = set.getRetention() != null && set.getRetention().getDefaultFrequency() != null ?
                    set.getRetention().getDefaultFrequency().toDuration() :
                    null;
            if (duration == null) {
                return false;
            }
            if (whenBySet != null)
                whenBySet.put(set.getId(), set.getRetention().getDefaultFrequency().toDuration().toMillis());

          return set.getContinuous() != null && set.getContinuous();
        }).collect(Collectors.toList());
    }

    private class PollingThread implements Runnable {
        @Override
        public void run() {
            boolean overflowing = false;
            lock.lock();
            try {
                while (watchService != null) {
                    boolean anyChanged = false;
                    try {
                        WatchService currentWatchService = watchService;
                        lock.unlock();
                        try {
                            WatchKey key = currentWatchService.take();
                            if (key != null) {
                                Path watchedPath = (Path) key.watchable();
                                for (WatchEvent<?> event : key.pollEvents()) {
                                    if (event.kind().equals(OVERFLOW)) {
                                        if (!overflowing) {
                                            log.warn("Overflow detected, some files may not be backed up");
                                            overflowing = true;
                                        }
                                        continue;
                                    }
                                    Path path = watchedPath.resolve((Path) event.context());
                                    if (!path.startsWith(manifestDirectory)) {
                                        String filePath = PathNormalizer.normalizePath(path.toString());
                                        for (BackupSet set : sets) {
                                            if (set.includeFile(filePath)) {
                                                File file = path.toFile();
                                                final long when = file.exists() ? file.lastModified() : 0;
                                                if (repository.addUpdatedFile(new BackupUpdatedFile(filePath, when),
                                                        whenBySet.get(set.getId()))) {
                                                    anyChanged = true;
                                                    overflowing = false;
                                                }
                                            }
                                        }
                                    }
                                }
                                key.reset();
                            }
                        } finally {
                            lock.lock();
                        }
                    } catch (ClosedWatchServiceException e) {
                        // Ignored, happens when closing the watch service is closed.
                    } catch (InterruptedException | IOException e) {
                        log.warn("Error while watching for file changes", e);
                    }
                    if (anyChanged) {
                        continuousBackup.signalChanged();
                    }
                }
            } finally {
                thread = null;
                lock.unlock();
            }
        }
    }

    @Override
    public void start() throws IOException {
        if (sets.isEmpty())
            return;
        lock.lock();
        try {
            if (watchService != null)
                throw new IllegalStateException("Already started");
            watchService = FileSystems.getDefault().newWatchService();

            for (BackupSet set : sets) {
                for (BackupSetRoot root : set.getRoots()) {
                    Path path = FileSystems.getDefault().getPath(root.getPath());
                    path.register(watchService, new WatchEvent.Kind[]{
                                    ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW
                            },
                            ExtendedWatchEventModifier.FILE_TREE);
                }
            }

            if (thread == null) {
                thread = new Thread(new PollingThread(), "FileChangeWatcher");
                thread.start();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void stop() throws IOException {
        lock.lock();
        try {
            if (watchService == null)
                return;
            watchService.close();
            watchService = null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean active() {
        return !sets.isEmpty();
    }
}
