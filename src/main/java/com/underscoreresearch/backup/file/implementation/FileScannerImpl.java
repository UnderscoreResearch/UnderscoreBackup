package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.getThroughputStatus;
import static com.underscoreresearch.backup.utils.LogUtil.readableDuration;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.underscoreresearch.backup.file.FileConsumer;
import com.underscoreresearch.backup.file.FileScanner;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupActiveFile;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupActiveStatus;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupLocation;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.model.BackupSetDestinations;
import com.underscoreresearch.backup.model.BackupSetRoot;
import com.underscoreresearch.backup.utils.StatusLine;
import com.underscoreresearch.backup.utils.StatusLogger;
import com.underscoreresearch.backup.utils.state.MachineState;

@RequiredArgsConstructor
@Slf4j
public class FileScannerImpl implements FileScanner, StatusLogger {

    private final MetadataRepository repository;
    private final FileConsumer consumer;
    private final FileSystemAccess filesystem;
    private final MachineState machineState;
    private final boolean debug;
    private final String manifestLocation;
    private final AtomicInteger outstandingFiles = new AtomicInteger();
    private final AtomicLong completedFiles = new AtomicLong();
    private final AtomicLong completedSize = new AtomicLong();
    private TreeMap<String, BackupActivePath> pendingPaths;
    private ReentrantLock lock = new ReentrantLock();
    private Condition pendingDirectoriesUpdated = lock.newCondition();
    private boolean shutdown;
    private Stopwatch duration;

    private Duration lastPath;

    @Override
    public boolean startScanning(BackupSet backupSet) throws IOException {
        lock.lock();
        shutdown = false;

        if (duration == null) {
            duration = Stopwatch.createStarted();
        }
        lastPath = Duration.ZERO;

        TreeMap<String, BackupActivePath> originalActivePaths = repository.getActivePaths(backupSet.getId());
        pendingPaths = stripExcludedPendingPaths(backupSet, originalActivePaths);

        boolean needStorageValidation = BackupSetDestinations.needStorageValidation(
                manifestLocation, backupSet, originalActivePaths.size() == 0);
        if (needStorageValidation)
            log.info("Enabled storage validation for set {}", backupSet.getId());

        if (pendingPaths.size() > 0) {
            debug(() -> log.debug("Resuming paths from {}", String.join("; ",
                    pendingPaths.keySet().stream().map(t -> PathNormalizer.physicalPath(t))
                            .collect(Collectors.toList()))));
        }

        if (!registerBackupRoots(backupSet)) {
            lock.unlock();
            return !shutdown;
        }

        pendingPaths.values().forEach(t -> t.setUnprocessed(true));

        for (BackupSetRoot root : backupSet.getRoots()) {
            if (!shutdown && pendingPaths.containsKey(root.getNormalizedPath()))
                processPath(backupSet, root.getNormalizedPath(), needStorageValidation);
        }

        consumer.flushAssignments();

        debug(() -> log.debug("File scanner shutting down"));
        while (processedPendingPaths().size() > 0 && !shutdown) {
            try {
                debug(() -> log.debug("Waiting for active paths: " + formatPathList(processedPendingPaths()
                        .keySet())));
                pendingDirectoriesUpdated.await();
            } catch (InterruptedException e) {
                log.error("Failed to wait for completion", e);
            }
        }

        if (!shutdown) {
            while (pendingPaths.size() > 0) {
                String path = pendingPaths.lastKey();
                log.info("Closing remaining active path: " + path);
                updateActivePath(backupSet, path, true);
            }

            TreeMap<String, BackupActivePath> remainingPaths = repository.getActivePaths(backupSet.getId());
            if (remainingPaths.size() > 0) {
                log.error("Completed with following active paths: " + formatPathList(remainingPaths.keySet()));
                for (Map.Entry<String, BackupActivePath> entry : remainingPaths.entrySet()) {
                    repository.popActivePath(backupSet.getId(), entry.getKey());
                }
            }
            BackupSetDestinations.completedStorageValidation(manifestLocation, backupSet);
        }
        lock.unlock();

        return !shutdown;
    }

    private String formatPathList(Collection<String> keySet) {
        return String.join(", ",
                keySet.stream().map(t -> PathNormalizer.physicalPath(t)).collect(Collectors.toList()));
    }

    private boolean registerBackupRoots(BackupSet backupSet) {
        boolean anyFound = false;
        for (BackupSetRoot root : backupSet.getRoots()) {
            if (addPendingPath(backupSet, root.getNormalizedPath())) {
                anyFound = true;
            }
        }
        return anyFound;
    }

    private Map<String, BackupActivePath> processedPendingPaths() {
        return pendingPaths.entrySet().stream().filter(t -> !t.getValue().isUnprocessed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
    public void resetStatus() {
        completedSize.set(0);
        completedFiles.set(0);
        outstandingFiles.set(0);
        duration = null;
    }

    @Override
    public List<StatusLine> status() {
        List<StatusLine> ret = getThroughputStatus(getClass(), "Finished", "files",
                completedFiles.get(), completedSize.get(),
                duration != null ? duration.elapsed() : Duration.ZERO);

        if (duration != null) {
            ret.add(new StatusLine(getClass(), "BACKUP_DURATION", "Total duration", duration.elapsed().toMillis(),
                    readableDuration(duration.elapsed())));
        }

        if (outstandingFiles.get() > 0 && debug) {
            ret.add(new StatusLine(getClass(), "OUTSTANDING_FILES", "Outstanding backup files",
                    (long) outstandingFiles.get()));
            ret.add(new StatusLine(getClass(), "OUTSTANDING_PATHS", "Outstanding backup paths",
                    (long) pendingPaths.size()));
        }
        return ret;
    }

    private BackupActiveStatus processPath(BackupSet set, String currentPath, boolean needStorageValidation) throws IOException {
        BackupActivePath pendingFiles = pendingPaths.get(currentPath);
        pendingFiles.getFiles().forEach(file -> {
            if (BackupActiveStatus.INCOMPLETE.equals(file.getStatus()))
                file.setStatus(null);
        });
        pendingFiles.setUnprocessed(false);

        boolean anyIncluded = false;

        lock.unlock();

        if (duration != null && (lastPath == null || lastPath.toMinutes() != duration.elapsed().toMinutes())) {
            lastPath = duration.elapsed();
            log.info("Started processing {}", PathNormalizer.physicalPath(currentPath));
        }

        Set<BackupFile> directoryFiles = filesystem.directoryFiles(currentPath);
        lock.lock();

        for (BackupFile file : directoryFiles) {
            if (shutdown) {
                return BackupActiveStatus.INCOMPLETE;
            }

            lock.unlock();
            try {
                machineState.waitForPower();
            } finally {
                lock.lock();
            }

            if (pendingFiles.unprocessedFile(file.getPath())) {
                if (file.isDirectory()) {
                    if (set.includeDirectory(file.getPath())) {
                        BackupActiveStatus status;
                        if (addPendingPath(set, file.getPath())) {
                            status = processPath(set, file.getPath(), needStorageValidation);
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
                    updateActivePath(set, currentPath, false);
                } else {
                    if (set.includeFile(file.getPath())) {
                        BackupFile existingFile;
                        try {
                            existingFile = repository.lastFile(file.getPath());
                        } catch (IOException e) {
                            log.error("Failed to read metadata about file for {}. Backing up again to be sure. Consider doing rebuild-repository.", file.getPath(), e);
                            existingFile = null;
                        }

                        anyIncluded = true;

                        if (existingFile == null
                                || !existingFile.getLastChanged().equals(file.getLastChanged())
                                || !existingFile.getLength().equals(file.getLength())
                                || (needStorageValidation && invalidStorage(existingFile, set))) {
                            lock.unlock();
                            log.info("Backing up {} ({})", file.getPath(), readableSize(file.getLength()));
                            outstandingFiles.incrementAndGet();
                            pendingFiles.getFile(file).setStatus(BackupActiveStatus.INCOMPLETE);

                            consumer.backupFile(set, file, (success) -> {
                                outstandingFiles.decrementAndGet();
                                if (!success) {
                                    if (!IOUtils.hasInternet()) {
                                        log.warn("Lost internet connection, shutting down set {} backup", set.getId());
                                        shutdown();
                                        return;
                                    }
                                }
                                completedFiles.incrementAndGet();
                                completedSize.addAndGet(file.getLength());
                                lock.lock();
                                pendingFiles.getFile(file).setStatus(success ?
                                        BackupActiveStatus.INCLUDED :
                                        BackupActiveStatus.EXCLUDED);
                                updateActivePath(set, currentPath, false);
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

        for (BackupActiveFile file : pendingFiles.getFiles()) {
            if (file.getStatus() == null) {
                file.setStatus(BackupActiveStatus.EXCLUDED);
                debug(() -> log.debug("File " + file.getPath() + " missing"));
            }
        }

        updateActivePath(set, currentPath, false);

        if (anyIncluded) {
            return pendingFiles.completed() ? BackupActiveStatus.INCLUDED : BackupActiveStatus.INCOMPLETE;
        } else {
            return BackupActiveStatus.EXCLUDED;
        }
    }

    private boolean invalidStorage(BackupFile existingFile, BackupSet set) {
        if (existingFile.getLength() != 0) {
            for (BackupLocation location : existingFile.getLocations()) {
                for (BackupFilePart part : location.getParts()) {
                    try {
                        List<BackupBlock> blocks;
                        if (BackupBlock.isSuperBlock(part.getBlockHash())) {
                            blocks = BackupBlock.expandBlock(part.getBlockHash(), repository);
                        } else {
                            BackupBlock block = repository.block(part.getBlockHash());
                            if (block == null) {
                                return true;
                            }
                            blocks = Lists.newArrayList(block);
                        }

                        for (BackupBlock block : blocks) {
                            HashSet<String> destinations = Sets.newHashSet(set.getDestinations());

                            for (BackupBlockStorage storage : block.getStorage()) {
                                destinations.remove(storage.getDestination());
                            }

                            if (destinations.size() > 0) {
                                log.warn("Missing destinations for existing {}", existingFile.getPath());
                                return true;
                            }
                        }
                    } catch (IOException e) {
                        log.error("Failed to get block for existing file {}", existingFile.getPath(), e);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void shutdown() {
        lock.lock();
        shutdown = true;
        pendingDirectoriesUpdated.signalAll();
        lock.unlock();
    }

    private boolean addPendingPath(BackupSet set, String path) {
        if (pendingPaths.containsKey(path))
            return true;

        lock.unlock();

        BackupActivePath activePath;
        if (path.endsWith(PATH_SEPARATOR)) {
            Set<BackupActiveFile> files = filesystem.directoryFiles(path).stream()
                    .map(file -> new BackupActiveFile(BackupActivePath.stripPath(file.getPath())))
                    .collect(Collectors.toSet());
            activePath = new BackupActivePath(path, files);
        } else {
            activePath = new BackupActivePath("", Sets.newHashSet(new BackupActiveFile(path)));
        }

        lock.lock();

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

    private void updateActivePath(BackupSet set, String currentPath, boolean forceClose) {
        BackupActivePath pending = pendingPaths.get(currentPath);
        if (pending != null) {
            if (pending.completed() || forceClose) {
                try {
                    repository.popActivePath(set.getId(), currentPath);
                    Set<String> includedPaths = pending.includedPaths();
                    if (currentPath.endsWith(PATH_SEPARATOR)) {
                        if (includedPaths.size() > 0 || repository.lastDirectory(currentPath) != null) {
                            repository.addDirectory(new BackupDirectory(currentPath,
                                    Instant.now().toEpochMilli(),
                                    Sets.newTreeSet(includedPaths)));
                        }
                    }
                } catch (IOException e) {
                    log.error("Failed to record completing " + currentPath, e);
                }
                pendingPaths.remove(currentPath);
                debug(() -> log.debug("Completed processing {}", PathNormalizer.physicalPath(currentPath)));
                String parent = BackupActivePath.findParent(currentPath);
                if (parent != null) {
                    BackupActivePath parentActive = pendingPaths.get(parent);
                    if (parentActive != null) {
                        if (parentActive.unprocessedFile(currentPath)) {
                            parentActive.getFile(currentPath).setStatus(pending.includedPaths().size() > 0
                                    ? BackupActiveStatus.INCLUDED : BackupActiveStatus.EXCLUDED);
                            updateActivePath(set, parent, false);
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