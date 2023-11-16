package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.io.IOUtils.INTERNET_WAIT;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.lastProcessedPath;
import static com.underscoreresearch.backup.utils.LogUtil.readableNumber;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.extern.slf4j.Slf4j;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.ContinuousBackup;
import com.underscoreresearch.backup.file.FileConsumer;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupPartialFile;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.model.BackupUpdatedFile;
import com.underscoreresearch.backup.utils.ManualStatusLogger;
import com.underscoreresearch.backup.utils.StateLogger;
import com.underscoreresearch.backup.utils.StatusLine;
import com.underscoreresearch.backup.utils.state.MachineState;

@Slf4j
public class ContinuousBackupImpl implements ContinuousBackup, ManualStatusLogger {
    private static final int MAX_PENDING_FILES = 100;
    private static final long FLUSH_TIME_MS = Duration.ofMinutes(1).toMillis();
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final MetadataRepository repository;
    private final FileConsumer fileConsumer;
    private final MachineState machineState;
    private final List<BackupSet> sets;
    private final AtomicLong processedFiles = new AtomicLong(0);
    private final AtomicLong processedSize = new AtomicLong(0);
    private final HashSet<String> pendingFiles = new HashSet<>();
    private BackupFile lastProcessed;
    private boolean shutdown;
    private boolean retry;
    private Thread thread;
    private boolean pause;

    public ContinuousBackupImpl(MetadataRepository repository, FileConsumer fileConsumer,
                                MachineState machineState, BackupConfiguration configuration) {
        this.repository = repository;
        this.fileConsumer = fileConsumer;
        this.machineState = machineState;
        this.sets = FileChangeWatcherImpl.getContinuousSets(configuration, null);
    }

    @Override
    public void resetStatus() {
        processedFiles.set(0);
        processedSize.set(0);
        lastProcessed = null;
    }

    @Override
    public List<StatusLine> status() {
        if (thread != null) {
            synchronized (pendingFiles) {
                List<StatusLine> ret = Lists.newArrayList(
                        new StatusLine(getClass(), "CONTINUOUS_BACKUP_FILES", "Continuous files processed",
                                processedFiles.get(), readableNumber(processedFiles.get())),
                        new StatusLine(getClass(), "CONTINUOUS_OUTSTANDING_FILES", "Continuous outstanding files",
                                (long) pendingFiles.size(), readableNumber(pendingFiles.size())),
                        new StatusLine(getClass(), "CONTINUOUS_BACKUP_SIZE", "Continuous files size processed",
                                processedSize.get(), readableSize(processedSize.get()))
                );
                lastProcessedPath(getClass(), ret, lastProcessed, "PROCESSED_PATH");
                return ret;
            }
        }
        return new ArrayList<>();
    }

    public void start() {
        if (sets.isEmpty())
            return;

        lock.lock();
        if (thread == null) {
            shutdown = retry = pause = false;
            pendingFiles.clear();
            thread = new Thread(new ScanThread(), "ContinuousBackup");
            thread.setDaemon(true);
            thread.start();
        }
        lock.unlock();
    }

    public void shutdown() {
        lock.lock();
        shutdown = true;
        condition.signalAll();
        while (thread != null) {
            try {
                condition.await();
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for shutdown", e);
            }
        }
        fileConsumer.flushAssignments();
        pendingFiles.clear();
        lock.unlock();
    }

    public void signalChanged() {
        lock.lock();
        retry = true;
        try {
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void processFile(BackupUpdatedFile updatedFile) throws IOException {
        BackupSet set = findSet(updatedFile.getPath());
        if (set == null) {
            repository.removeUpdatedFile(updatedFile);
            return;
        }

        File file = new File(PathNormalizer.physicalPath(updatedFile.getPath()));
        if (!file.exists()) {
            BackupFile existingFile = repository.file(file.getPath(), null);
            if (existingFile != null && existingFile.getDeleted() == null) {
                log.info("File deleted \"{}\"", PathNormalizer.physicalPath(updatedFile.getPath()));

                existingFile.setDeleted(System.currentTimeMillis());
                repository.addFile(existingFile);

                try (CloseableLock ignore = repository.acquireLock()) {
                    removeFileFromDirectory(file);
                }
            }
        } else if (file.isFile()) {
            BackupFile existingFile = repository.file(file.getPath(), null);
            if (existingFile == null
                    || (existingFile.getLastChanged() != Files.getLastModifiedTime(file.toPath()).toMillis() || existingFile.getLength() != file.length())) {
                log.info("Backing up \"{}\"", PathNormalizer.physicalPath(updatedFile.getPath()));
                uploadFile(set, file, updatedFile);
            }
        }
        repository.removeUpdatedFile(updatedFile);
    }

    private BackupSet findSet(String path) {
        if (path.endsWith(PathNormalizer.PATH_SEPARATOR)) {
            for (BackupSet set : sets) {
                if (set.includeDirectory(path)) {
                    return set;
                }
            }
        } else {
            for (BackupSet set : sets) {
                if (set.includeFile(path + PathNormalizer.PATH_SEPARATOR)) {
                    return set;
                }
            }
        }
        return null;
    }

    private void uploadFile(BackupSet set, File file, BackupUpdatedFile updatedFile) throws IOException {
        BackupFile backupFile;
        try {
            backupFile = BackupFile.builder()
                    .path(updatedFile.getPath())
                    .length(file.length())
                    .added(System.currentTimeMillis())
                    .lastChanged(Files.getLastModifiedTime(file.toPath()).toMillis())
                    .build();
        } catch (IOException e) {
            log.warn("Failed to get last modified time for file \"{}\"", file, e);
            return;
        }
        BackupPartialFile partialFile = new BackupPartialFile(backupFile);
        repository.savePartialFile(partialFile);

        synchronized (pendingFiles) {
            pendingFiles.add(updatedFile.getPath());
        }
        lastProcessed = backupFile;

        fileConsumer.backupFile(set, backupFile, success -> {
            synchronized (pendingFiles) {
                pendingFiles.remove(updatedFile.getPath());
            }
            try {
                repository.deletePartialFile(partialFile);
            } catch (IOException e) {
                log.warn("Failed to delete partial file \"{}\"", file, e);
            }
            processedFiles.incrementAndGet();
            processedSize.addAndGet(backupFile.getLength());
            if (!success) {
                while (!IOUtils.hasInternet() && !shutdown) {
                    lock.lock();
                    if (!pause) {
                        log.warn("Lost internet connection, pausing continuous backup");
                    }
                    pause = true;
                    lock.unlock();

                    try {
                        Thread.sleep(INTERNET_WAIT);
                    } catch (InterruptedException e) {
                        log.warn("Failed to wait for internet connection", e);
                    }
                }
                lock.lock();
                pause = false;
                condition.signalAll();
                lock.unlock();
            } else {
                try (CloseableLock ignore = repository.acquireLock()) {
                    addFileToDirectory(file, file.isDirectory());
                } catch (IOException e) {
                    log.error("Error adding file \"{}\" to parent directory", file, e);
                }
            }
        });
    }

    private void addFileToDirectory(File file, boolean isDirectory) throws IOException {
        File parentFile = file.getParentFile();
        String dirPath = PathNormalizer.normalizePath(parentFile.toString());
        if (!dirPath.endsWith(PathNormalizer.PATH_SEPARATOR))
            dirPath += PathNormalizer.PATH_SEPARATOR;
        BackupDirectory directory = repository.directory(dirPath, null, false);
        if (directory == null || directory.getDeleted() != null) {
            addFileToDirectory(parentFile, true);
            directory = BackupDirectory.builder()
                    .added(System.currentTimeMillis())
                    .files(new TreeSet<>())
                    .path(dirPath)
                    .build();
        }
        if (directory.getFiles().add(file.getName() + (isDirectory ? PathNormalizer.PATH_SEPARATOR : ""))) {
            repository.addDirectory(directory);
        }
    }

    private void removeFileFromDirectory(File file) throws IOException {
        File parentFile = file.getParentFile();
        String dirPath = PathNormalizer.normalizePath(parentFile.toString());
        if (!dirPath.endsWith(PathNormalizer.PATH_SEPARATOR))
            dirPath += PathNormalizer.PATH_SEPARATOR;
        BackupDirectory directory = repository.directory(dirPath, null, false);
        if (directory != null) {
            if (directory.getFiles().remove(file.getName()) || directory.getFiles().remove(file.getName() + PathNormalizer.PATH_SEPARATOR)) {
                directory.setAdded(System.currentTimeMillis());
                repository.addDirectory(directory);
                if (directory.getFiles().isEmpty()) {
                    removeFileFromDirectory(parentFile);
                }
            }
        }
    }

    public static class InterruptedScan extends RuntimeException {
    }

    private class ScanThread implements Runnable {
        public ScanThread() {
        }

        @Override
        public void run() {
            lock.lock();
            try {
                StateLogger.addLogger(ContinuousBackupImpl.this);
                long nextFlush = 0;
                while (!shutdown) {
                    retry = false;
                    AtomicLong knownWaitEpoch = new AtomicLong(0L);

                    List<BackupUpdatedFile> currentFiles = new ArrayList<>();

                    // Have to do this roundabout way because we can't hold the lock while
                    // we are submitting files to the file consumer since it might block
                    // until the file is processed.

                    try (CloseableStream<BackupUpdatedFile> files = repository.getUpdatedFiles()) {
                        files.stream().forEach(file -> {
                            if (file.getLastUpdated() > System.currentTimeMillis()) {
                                knownWaitEpoch.set(file.getLastUpdated());
                                throw new InterruptedScan();
                            }

                            synchronized (pendingFiles) {
                                if (pendingFiles.contains(file.getPath()))
                                    return;
                            }

                            currentFiles.add(file);
                            if (currentFiles.size() > MAX_PENDING_FILES) {
                                debug(() -> log.debug("Too many pending files, waiting for consumer to catch up"));
                                retry = true;
                                throw new InterruptedScan();
                            }
                        });
                    } catch (IOException e) {
                        log.error("Error scanning for updated files", e);
                    } catch (InterruptedScan ignored) {
                    }

                    try {
                        lock.unlock();

                        machineState.waitForRunCheck();

                        currentFiles.forEach(file -> {
                            try {
                                lock.lock();
                                if (!pause) {
                                    lock.unlock();
                                    processFile(file);
                                } else {
                                    lock.unlock();
                                }
                            } catch (IOException e) {
                                log.error("Error processing file \"{}\"", file.getPath(), e);
                            }
                        });
                        if (!currentFiles.isEmpty())
                            nextFlush = System.currentTimeMillis() + FLUSH_TIME_MS;

                    } finally {
                        lock.lock();
                    }

                    while (pause) {
                        try {
                            condition.await();
                        } catch (InterruptedException e) {
                            log.warn("Failed to wait", e);
                        }
                    }
                    if (retry) {
                        continue;
                    }

                    if (flushAndWait(nextFlush, knownWaitEpoch.get())) {
                        nextFlush = 0;
                    }
                }
            } finally {
                StateLogger.removeLogger(ContinuousBackupImpl.this);
                thread = null;
                condition.signalAll();
                lock.unlock();
            }
        }

        private boolean flushAndWait(long nextFlush, long next) {
            if (nextFlush == 0 || (next > 0 && nextFlush > next)) {
                waitNext(next);
                return false;
            }
            try {
                if (!condition.awaitUntil(new Date(nextFlush))) {
                    debug(() -> log.debug("Uploading pending small files"));
                    fileConsumer.flushAssignments();
                    waitNext(next);
                    return true;
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for next file", e);
            }
            return false;
        }

        private void waitNext(long next) {
            try {
                if (next > 0) {
                    if (!condition.awaitUntil(new Date(next))) {
                        throw new InterruptedException();
                    }
                } else {
                    condition.await();
                }
            } catch (InterruptedException ignored) {
            }
        }
    }
}