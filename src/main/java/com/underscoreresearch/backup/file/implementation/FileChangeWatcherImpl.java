package com.underscoreresearch.backup.file.implementation;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
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
    private static final int THREAD_POOL_SIZE = 5;
    private static final int QUEUE_SIZE = 100;
    private final List<BackupSet> sets;
    private final Map<String, Long> whenBySet;
    private final MetadataRepository repository;
    private final ContinuousBackup continuousBackup;
    private final Path manifestDirectory;
    private WatchService watchService;

    private Lock lock = new ReentrantLock();
    private Condition condition = lock.newCondition();
    private Thread thread;
    private ExecutorService executorService;
    private BlockingQueue<Runnable> executionQueue;

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
                executionQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
                executorService = new ThreadPoolExecutor(THREAD_POOL_SIZE, THREAD_POOL_SIZE,
                        0, TimeUnit.MILLISECONDS,
                        executionQueue,
                        r -> new Thread(r, "FileChangeWatcherConsumer"));

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

            while (thread != null) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting for file change watcher to stop", e);
                }
            }

            executorService.shutdownNow();
            executorService = null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean active() {
        return !sets.isEmpty();
    }

    private class PollingThread implements Runnable {
        private AtomicBoolean overflowing = new AtomicBoolean();

        @Override
        public void run() {
            lock.lock();
            try {
                while (watchService != null) {
                    List<Path> paths = new ArrayList<>();
                    try {
                        WatchService currentWatchService = watchService;
                        lock.unlock();
                        try {
                            WatchKey key = currentWatchService.take();
                            if (key != null) {
                                Path watchedPath = (Path) key.watchable();
                                for (WatchEvent<?> event : key.pollEvents()) {
                                    if (event.kind().equals(OVERFLOW)) {
                                        if (!overflowing.get()) {
                                            log.warn("Overflow detected, some files may not be backed up");
                                            overflowing.set(true);
                                        }
                                        continue;
                                    }
                                    Path path = watchedPath.resolve((Path) event.context());
                                    paths.add(path);
                                }
                                key.reset();
                            }
                        } finally {
                            lock.lock();
                        }
                    } catch (ClosedWatchServiceException e) {
                        // Ignored, happens when closing the watch service is closed.
                        paths.clear();
                    } catch (InterruptedException e) {
                        log.warn("Error while watching for file changes", e);
                    }
                    while (executionQueue.remainingCapacity() < THREAD_POOL_SIZE) {
                        try {
                            condition.await();
                        } catch (InterruptedException e) {
                            log.warn("Error while waiting for execution queue to free up", e);
                        }
                    }
                    executorService.submit(() -> processPaths(paths));
                }
            } finally {
                thread = null;
                condition.signalAll();
                lock.unlock();
            }
        }

        private void processPaths(List<Path> paths) {
            boolean anyChanged = false;
            for (Path path : paths) {
                if (!path.startsWith(manifestDirectory)) {
                    String filePath = PathNormalizer.normalizePath(path.toString());
                    for (BackupSet set : sets) {
                        if (set.includeFile(filePath)) {
                            File file = path.toFile();
                            final long when = file.exists() ? file.lastModified() : 0;
                            try {
                                if (repository.addUpdatedFile(new BackupUpdatedFile(filePath, when),
                                        whenBySet.get(set.getId()))) {
                                    anyChanged = true;
                                }
                            } catch (IOException e) {
                                log.error("Error while processing file change for {}", path, e);
                            }
                        }
                    }
                }
            }
            if (anyChanged) {
                continuousBackup.signalChanged();
                overflowing.set(false);
            }

            if (executionQueue.remainingCapacity() < THREAD_POOL_SIZE * 2) {
                lock.lock();
                condition.signalAll();
                lock.unlock();
            }
        }
    }
}
