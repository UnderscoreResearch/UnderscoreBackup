package com.underscoreresearch.backup.file.implementation;

import com.google.common.collect.Sets;
import com.underscoreresearch.backup.file.FileConsumer;
import com.underscoreresearch.backup.file.FileScanner;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.*;
import com.underscoreresearch.backup.utils.StatusLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

@RequiredArgsConstructor
@Slf4j
public class FileScannerImpl implements FileScanner, StatusLogger {
    private TreeMap<String, BackupActivePath> pendingPaths;
    private ReentrantLock lock = new ReentrantLock();
    private Condition pendingDirectoriesUpdated = lock.newCondition();
    private final MetadataRepository repository;
    private final FileConsumer consumer;
    private final FileSystemAccess filesystem;
    private final AtomicInteger outstandingFiles = new AtomicInteger();
    private final AtomicLong completedFiles = new AtomicLong();
    private final AtomicLong completedSize = new AtomicLong();
    private boolean shutdown;

    @Override
    public boolean startScanning(BackupSet backupSet) throws IOException {
        lock.lock();
        shutdown = false;

        pendingPaths = stripExcludedPendingPaths(backupSet, repository.getActivePaths(backupSet.getId()));

        if (pendingPaths.size() > 0) {
            debug(() -> log.debug("Resuming paths from {}", String.join("; ",
                    pendingPaths.keySet())));
        }

        if (!addPendingPath(backupSet, backupSet.getNormalizedRoot())) {
            lock.unlock();
            return !shutdown;
        }

        processPath(backupSet, backupSet.getNormalizedRoot());

        consumer.flushAssignments();

        debug(() -> log.debug("File scanner shutting down"));
        while (pendingPaths.size() > 0 && !shutdown) {
            try {
                debug(() -> log.debug("Waiting for active paths: " + String.join(";",
                        pendingPaths.keySet())));
                pendingDirectoriesUpdated.await();
            } catch (InterruptedException e) {
                log.error("Failed to wait for completion", e);
            }
        }

        if (!shutdown) {
            TreeMap<String, BackupActivePath> remainingPaths = repository.getActivePaths(backupSet.getId());
            if (remainingPaths.size() > 0) {
                log.error("Completed with following active paths: " + String.join("; ",
                        remainingPaths.keySet()));
            }
        }
        lock.unlock();

        return !shutdown;
    }

    private TreeMap<String, BackupActivePath> stripExcludedPendingPaths(BackupSet backupSet,
                                                                        TreeMap<String, BackupActivePath> activePaths) {
        if (activePaths != null) {
            Iterator<Map.Entry<String, BackupActivePath>> iterator = activePaths.entrySet().iterator();
            while (iterator.hasNext()) {
                String path = iterator.next().getKey();
                if (!backupSet.includeDirectory(path)) {
                    try {
                        repository.popActivePath(backupSet.getId(), path);
                    } catch (IOException e) {
                        log.error("Failed to remove invalid active path: " + path);
                    }
                    iterator.remove();
                }
            }
        }
        return activePaths;
    }

    @Override
    public void logStatus() {
        if (outstandingFiles.get() > 0) {
            debug(() -> {
                log.debug("{} files completed with a total size of {}", completedFiles.get(),
                        readableSize(completedSize.get()));

                synchronized (pendingPaths) {
                    List<String> paths = calculateStartingPaths(pendingPaths);
                    log.debug("{} outstanding backup files and {} outstanding paths (Oldest {})", outstandingFiles,
                            pendingPaths.size(), paths.get(paths.size() - 1));
                }
            });
        }
    }

    private List<String> calculateStartingPaths(TreeMap<String, BackupActivePath> pendingPaths) {
        List<String> startingPaths = new ArrayList<>();
        String lastPath = null;
        for (String path : pendingPaths.keySet()) {
            if (lastPath == null || path.startsWith(lastPath)) {
                startingPaths.add(path);
            } else {
                break;
            }
            lastPath = path;
        }
        return startingPaths;
    }

    private BackupActiveStatus processPath(BackupSet set, String currentPath) throws IOException {
        BackupActivePath pendingFiles = pendingPaths.get(currentPath);

        boolean anyIncluded = false;

        lock.unlock();
        Set<BackupFile> directoryFiles = filesystem.directoryFiles(currentPath);
        lock.lock();

        for (BackupFile file : directoryFiles) {
            if (shutdown) {
                return BackupActiveStatus.INCOMPLETE;
            }

            if (pendingFiles.unprocessedFile(file.getPath())) {
                if (file.isDirectory()) {
                    if (set.includeDirectory(file.getPath())) {
                        BackupActiveStatus status;
                        if (addPendingPath(set, file.getPath())) {
                            status = processPath(set, file.getPath());
                        } else {
                            status = BackupActiveStatus.EXCLUDED;
                        }
                        if (status == BackupActiveStatus.INCLUDED || status == BackupActiveStatus.INCOMPLETE) {
                            anyIncluded = true;
                        }
                        pendingFiles.getFile(file).setStatus(status);
                    } else {
                        pendingFiles.getFile(file).setStatus(BackupActiveStatus.EXCLUDED);
                    }
                    updateActivePath(set, currentPath);
                } else {
                    if (set.includeFile(file.getPath())) {
                        BackupFile existingFile = repository.lastFile(file.getPath());

                        anyIncluded = true;

                        if (existingFile == null || !existingFile.getLastChanged().equals(file.getLastChanged())) {
                            lock.unlock();
                            log.info("Backing up {} ({})", file.getPath(), readableSize(file.getLength()));
                            outstandingFiles.incrementAndGet();
                            consumer.backupFile(set, file, (success) -> {
                                if (!success) {
                                    if (!IOUtils.hasInternet()) {
                                        log.error("Lost internet connection, shutting down set {} backup", set.getId());
                                        shutdown();
                                    }
                                }
                                outstandingFiles.decrementAndGet();
                                completedFiles.incrementAndGet();
                                completedSize.addAndGet(file.getLength());
                                lock.lock();
                                pendingFiles.getFile(file).setStatus(success ?
                                        BackupActiveStatus.INCLUDED :
                                        BackupActiveStatus.EXCLUDED);
                                updateActivePath(set, currentPath);
                                lock.unlock();
                            });
                            lock.lock();
                        } else {
                            pendingFiles.getFile(file).setStatus(BackupActiveStatus.INCLUDED);
                        }
                    } else {
                        pendingFiles.getFile(file).setStatus(BackupActiveStatus.EXCLUDED);
                    }
                }
            }
        }

        updateActivePath(set, currentPath);

        if (anyIncluded) {
            return pendingFiles.completed() ? BackupActiveStatus.INCLUDED : BackupActiveStatus.INCOMPLETE;
        } else {
            return BackupActiveStatus.EXCLUDED;
        }
    }

    public void shutdown() {
        lock.lock();
        shutdown = true;
        pendingDirectoriesUpdated.signalAll();
        lock.unlock();
    }

    private boolean addPendingPath(BackupSet set, String path) {
        if (!path.endsWith(PATH_SEPARATOR)) {
            path = path + PATH_SEPARATOR;
        }
        if (pendingPaths.containsKey(path))
            return true;

        lock.unlock();

        Set<BackupActiveFile> files = filesystem.directoryFiles(path).stream()
                .map(file -> new BackupActiveFile(BackupActivePath.stripPath(file.getPath())))
                .collect(Collectors.toSet());

        lock.lock();
        if (files.size() == 0)
            return false;

        BackupActivePath activePath = new BackupActivePath(path, files);
        pendingPaths.put(path, activePath);

        final String debugPath = path;
        debug(() -> log.debug("Started processing {}", debugPath));
        try {
            repository.pushActivePath(set.getId(), path, activePath);
        } catch (IOException e) {
            log.error("Failed to add active path " + path, e);
        }
        return true;
    }

    private void updateActivePath(BackupSet set, String currentPath) {
        BackupActivePath pending = pendingPaths.get(currentPath);
        if (pending != null) {
            if (pending.completed()) {
                try {
                    repository.popActivePath(set.getId(), currentPath);
                    Set<String> includedPaths = pending.includedPaths();
                    repository.addDirectory(new BackupDirectory(currentPath,
                            Instant.now().toEpochMilli(),
                            Sets.newTreeSet(includedPaths)));
                } catch (IOException e) {
                    log.error("Failed to record completing " + currentPath, e);
                }
                pendingPaths.remove(currentPath);
                debug(() -> log.debug("Completed processing {}", currentPath));
                String parent = BackupActivePath.findParent(currentPath);
                if (parent != null) {
                    BackupActivePath parentActive = pendingPaths.get(parent);
                    if (parentActive != null) {
                        if (parentActive.unprocessedFile(currentPath)) {
                            parentActive.getFile(currentPath).setStatus(pending.includedPaths().size() > 0
                                    ? BackupActiveStatus.INCLUDED : BackupActiveStatus.EXCLUDED);
                            updateActivePath(set, parent);
                        }
                    }
                }
                pendingDirectoriesUpdated.signalAll();
            } else {
                try {
                    repository.pushActivePath(set.getId(), currentPath, pending);
                } catch (IOException e) {
                    log.error("Failed to record updating " + currentPath, e);
                }
            }
        }
    }
}