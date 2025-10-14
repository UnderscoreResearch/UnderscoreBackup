package com.underscoreresearch.backup.file.implementation;

import com.underscoreresearch.backup.file.ContinuousBackup;
import com.underscoreresearch.backup.file.FileChangeWatcher;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.file.changepoller.FileChangePoller;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.model.BackupSetRoot;
import com.underscoreresearch.backup.model.BackupUpdatedFile;
import com.underscoreresearch.backup.machinestate.MachineState;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
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

/**
 * Implementation of FileChangeWatcher that monitors file system changes.
 * Uses a FileChangePoller to detect file changes and submits them to a ContinuousBackup for processing.
 */
@Slf4j
public class FileChangeWatcherImpl implements FileChangeWatcher {
    private static final int THREAD_POOL_SIZE = 5;
    private static final int QUEUE_SIZE = 100;
    private final List<BackupSet> sets;
    private final Map<String, Long> whenBySet;
    private final MetadataRepository repository;
    private final ContinuousBackup continuousBackup;
    private final Path manifestDirectory;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final MachineState machineState;
    private FileChangePoller poller;
    private Thread thread;
    private ExecutorService executorService;
    private BlockingQueue<Runnable> executionQueue;

    /**
     * Constructor for FileChangeWatcherImpl.
     *
     * @param configuration The backup configuration containing backup sets
     * @param repository The metadata repository to use for file operations
     * @param continuousBackup The continuous backup to handle file changes
     * @param manifestDirectory The directory where the manifest is stored
     * @param machineState The machine state to monitor system status
     */
    public FileChangeWatcherImpl(BackupConfiguration configuration, MetadataRepository repository,
                                 ContinuousBackup continuousBackup, String manifestDirectory,
                                 MachineState machineState) {
        this.repository = repository;
        this.manifestDirectory = FileSystems.getDefault().getPath(manifestDirectory);
        this.continuousBackup = continuousBackup;
        this.machineState = machineState;

        whenBySet = new HashMap<>();
        sets = getContinuousSets(configuration, whenBySet);
    }

    /**
     * Gets the list of backup sets that have continuous backup enabled.
     *
     * @param configuration The backup configuration
     * @param whenBySet Optional map to store the frequency of each backup set
     * @return List of backup sets with continuous backup enabled
     */
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

    /**
     * Starts the file change watcher.
     * Creates a poller and registers paths to watch.
     *
     * @throws IOException if an I/O error occurs
     * @throws IllegalStateException if the watcher is already started
     */
    @Override
    public void start() throws IOException {
        if (sets.isEmpty())
            return;
        lock.lock();
        try {
            if (poller != null)
                throw new IllegalStateException("Already started");
            poller = machineState.createPoller();

            List<Path> paths = new ArrayList<>();
            for (BackupSet set : sets) {
                for (BackupSetRoot root : set.getRoots()) {
                    paths.add(FileSystems.getDefault().getPath(root.getPath()));
                }
            }
            poller.registerPaths(paths);

            if (thread == null) {
                thread = new Thread(new PollingThread(), "FileChangeWatcher");
                thread.setDaemon(true);
                executionQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
                executorService = new ThreadPoolExecutor(THREAD_POOL_SIZE, THREAD_POOL_SIZE,
                        0, TimeUnit.MILLISECONDS,
                        executionQueue,
                        r -> {
                            Thread executorThread = new Thread(r, "FileChangeWatcherConsumer");
                            executorThread.setDaemon(true);
                            return executorThread;
                        });

                thread.start();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Stops the file change watcher.
     * Closes the poller and shuts down the executor service.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void stop() throws IOException {
        lock.lock();
        try {
            if (poller == null)
                return;
            poller.close();
            poller = null;

            while (thread != null) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting for file change watcher to stop", e);
                }
            }

            executorService.shutdownNow();
            executorService = null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if the file change watcher is active.
     *
     * @return true if the watcher is active, false otherwise
     */
    @Override
    public boolean active() {
        return !sets.isEmpty() && poller != null;
    }

    /**
     * Thread that polls for file changes and submits them for processing.
     */
    private class PollingThread implements Runnable {
        private final AtomicBoolean overflowing = new AtomicBoolean();

        /**
         * Main run method for the polling thread.
         * Continuously polls for file changes and submits them to the executor service.
         */
        @Override
        public void run() {
            lock.lock();
            try {
                while (poller != null) {
                    List<String> paths;
                    try {
                        FileChangePoller currentPoller = poller;
                        lock.unlock();
                        try {
                            paths = currentPoller.fetchPaths();
                        } finally {
                            lock.lock();
                        }
                    } catch (FileChangePoller.OverflowException e) {
                        if (!overflowing.get()) {
                            log.warn("Overflow detected, some files may not be backed up");
                            overflowing.set(true);
                        }
                        continue;
                    } catch (IOException e) {
                        log.warn("Error while watching for file changes", e);
                        return;
                    }
                    while (executionQueue.remainingCapacity() < THREAD_POOL_SIZE) {
                        try {
                            condition.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.warn("Error while waiting for execution queue to free up", e);
                        }
                    }

                    executorService.submit(() -> processPaths(paths));
                }
            } finally {
                try {
                    if (poller != null) {
                        poller.close();
                    }
                } catch (IOException e) {
                    log.error("Failed to close file change monitor", e);
                }
                thread = null;
                condition.signalAll();
                lock.unlock();
            }
        }

        /**
         * Processes a list of changed paths.
         * Adds updated files to the repository and signals the continuous backup.
         *
         * @param paths List of paths that have changed
         */
        private void processPaths(List<String> paths) {
            boolean anyChanged = false;
            for (String pathStr : paths) {
                Path path = Path.of(pathStr);
                if (!path.startsWith(manifestDirectory)) {
                    String filePath = PathNormalizer.normalizePath(pathStr);
                    for (BackupSet set : sets) {
                        if (set.includeFile(filePath)) {
                            File file = path.toFile();
                            try {
                                long when;
                                try {
                                    when = file.exists() ? Files.getLastModifiedTime(file.toPath()).toMillis() : 0;
                                } catch (NoSuchFileException e) {
                                    // It's a race condition and I have seen it in the logs.
                                    when = 0;
                                }
                                if (repository.addUpdatedFile(new BackupUpdatedFile(filePath, when),
                                        whenBySet.get(set.getId()))) {
                                    anyChanged = true;
                                }
                            } catch (IOException e) {
                                log.warn("Failed processing file change for \"{}\"", path, e);
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
