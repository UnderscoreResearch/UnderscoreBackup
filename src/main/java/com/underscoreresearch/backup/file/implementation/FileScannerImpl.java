package com.underscoreresearch.backup.file.implementation;

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
import com.underscoreresearch.backup.utils.ManualStatusLogger;
import com.underscoreresearch.backup.utils.StateLogger;
import com.underscoreresearch.backup.utils.StatusLine;
import com.underscoreresearch.backup.utils.state.MachineState;
import lombok.extern.slf4j.Slf4j;

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

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.getThroughputStatus;
import static com.underscoreresearch.backup.utils.LogUtil.lastProcessedPath;
import static com.underscoreresearch.backup.utils.LogUtil.readableDuration;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

@Slf4j
public class FileScannerImpl implements FileScanner, ManualStatusLogger {
    private final MetadataRepository repository;
    private final FileConsumer consumer;
    private final FileSystemAccess filesystem;
    private final MachineState machineState;
    private final boolean debug;
    private final String manifestLocation;
    private final AtomicInteger outstandingFiles = new AtomicInteger();
    private final AtomicLong completedFiles = new AtomicLong();
    private final AtomicLong completedSize = new AtomicLong();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition pendingDirectoriesUpdated = lock.newCondition();
    private TreeMap<String, BackupActivePath> pendingPaths;
    private boolean shutdown;
    private Stopwatch duration;
    private BackupFile lastProcessed;
    private Duration lastPath;

    public FileScannerImpl(MetadataRepository repository, FileConsumer consumer, FileSystemAccess filesystem,
                           MachineState machineState, boolean debug, String manifestLocation) {
        this.repository = repository;
        this.consumer = consumer;
        this.filesystem = filesystem;
        this.machineState = machineState;
        this.debug = debug;
        this.manifestLocation = manifestLocation;

        StateLogger.addLogger(this);
    }

    @Override
    public boolean startScanning(BackupSet backupSet) throws IOException {
        lock.lock();

        boolean completed = false;
        try {
            if (duration == null) {
                duration = Stopwatch.createStarted();
            }
            lastPath = Duration.ZERO;

            TreeMap<String, BackupActivePath> originalActivePaths = repository.getActivePaths(backupSet.getId());
            pendingPaths = stripExcludedPendingPaths(backupSet, originalActivePaths);

            boolean needStorageValidation = BackupSetDestinations.needStorageValidation(
                    manifestLocation, backupSet, originalActivePaths.isEmpty());
            if (needStorageValidation)
                log.info("Enabled storage validation for set \"{}\"", backupSet.getId());

            if (!pendingPaths.isEmpty()) {
                debug(() -> log.debug("Resuming paths from \"{}\"", pendingPaths.keySet().stream().map(PathNormalizer::physicalPath)
                        .collect(Collectors.joining("\", \""))));
            }
            if (!registerBackupRoots(backupSet)) {
                return !shutdown;
            }

            pendingPaths.values().forEach(t -> t.setUnprocessed(true));

            for (BackupSetRoot root : backupSet.getRoots()) {
                if (!shutdown && pendingPaths.containsKey(root.getNormalizedPath())) {
                    try {
                        processPath(backupSet, root.getNormalizedPath(), needStorageValidation);
                    } catch (Throwable exc) {
                        try {
                            consumer.flushAssignments();
                            resetStatus();
                        } catch (Throwable e) {
                            log.error("Failed to reset status", e);
                        }
                        throw exc;
                    }
                }
            }
        } finally {
            lock.unlock();
        }

        consumer.flushAssignments();

        lock.lock();
        try {
            debug(() -> log.debug("File scanner shutting down"));
            while (!processedPendingPaths().isEmpty() && !shutdown) {
                try {
                    debug(() -> log.debug("Waiting for active paths: " + formatPathList(processedPendingPaths()
                            .keySet())));
                    pendingDirectoriesUpdated.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Failed to wait for completion", e);
                }
            }

            if (!shutdown) {
                while (!pendingPaths.isEmpty()) {
                    String path = pendingPaths.lastKey();
                    log.info("Closing remaining active path: \"" + path + "\"");
                    updateActivePath(backupSet, path, true);
                }

                TreeMap<String, BackupActivePath> remainingPaths = repository.getActivePaths(backupSet.getId());
                if (!remainingPaths.isEmpty()) {
                    log.error("Completed with following active paths: " + formatPathList(remainingPaths.keySet()));
                    for (Map.Entry<String, BackupActivePath> entry : remainingPaths.entrySet()) {
                        repository.popActivePath(backupSet.getId(), entry.getKey());
                    }
                }
                BackupSetDestinations.completedStorageValidation(manifestLocation, backupSet);
            }
        } finally {
            completed = !shutdown;
            shutdown = false;

            lock.unlock();
        }

        return completed;
    }

    private String formatPathList(Collection<String> keySet) {
        return "\"" + keySet.stream().map(PathNormalizer::physicalPath)
                .collect(Collectors.joining("\", \"")) + "\"";
    }

    private boolean registerBackupRoots(BackupSet backupSet) {
        boolean anyFound = false;
        for (BackupSetRoot root : backupSet.getRoots()) {
            addPendingPath(backupSet, root.getNormalizedPath());
            lastProcessed = BackupFile.builder().path(root.getNormalizedPath()).build();
            anyFound = true;
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
                        log.error("Failed to remove invalid active path: \"" + path + "\"");
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
        lastProcessed = null;
    }

    @Override
    public List<StatusLine> status() {
        List<StatusLine> ret = getThroughputStatus(getClass(), "Completed", "files",
                completedFiles.get(), completedSize.get(),
                duration != null ? duration.elapsed() : Duration.ZERO);

        if (duration != null) {
            ret.add(new StatusLine(getClass(), "BACKUP_DURATION", "Total duration", duration.elapsed().toMillis(),
                    readableDuration(duration.elapsed())));
            lastProcessedPath(getClass(), ret, lastProcessed, "PROCESSED_PATH");
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
        Set<BackupFile> directoryFiles;

        lock.unlock();
        try {
            if (duration != null && (lastPath == null || lastPath.toMinutes() != duration.elapsed().toMinutes())) {
                lastPath = duration.elapsed();
                log.info("Started processing \"{}\"", PathNormalizer.physicalPath(currentPath));
            }

            directoryFiles = filesystem.directoryFiles(currentPath);
        } finally {
            lock.lock();
        }

        for (BackupFile file : directoryFiles) {
            if (shutdown) {
                return BackupActiveStatus.INCOMPLETE;
            }

            lock.unlock();
            try {
                machineState.waitForRunCheck();
            } finally {
                lock.lock();
            }

            if (pendingFiles.unprocessedFile(file.getPath())) {
                if (file.isDirectory()) {
                    if (set.includeDirectory(file.getPath())) {
                        addPendingPath(set, file.getPath());
                        BackupActiveStatus status = processPath(set, file.getPath(), needStorageValidation);
                        lastProcessed = file;

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
                            existingFile = repository.file(file.getPath(), null);
                        } catch (IOException e) {
                            log.error("Failed to read metadata about file for \"{}\". Backing up again to be sure. Consider doing rebuild-repository.", PathNormalizer.physicalPath(file.getPath()), e);
                            existingFile = null;
                        }

                        anyIncluded = true;

                        if (existingFile == null
                                || !existingFile.getLastChanged().equals(file.getLastChanged())
                                || !existingFile.getLength().equals(file.getLength())
                                || (needStorageValidation && invalidStorage(existingFile, set))) {
                            lock.unlock();
                            try {
                                log.info("Backing up \"{}\" ({})", PathNormalizer.physicalPath(file.getPath()), readableSize(file.getLength()));
                                outstandingFiles.incrementAndGet();
                                pendingFiles.getFile(file).setStatus(BackupActiveStatus.INCOMPLETE);

                                file.setPermissions(filesystem.extractPermissions(file.getPath()));

                                lastProcessed = file;
                                consumer.backupFile(set, file, (success) -> {
                                    outstandingFiles.decrementAndGet();
                                    if (!success) {
                                        if (!IOUtils.hasInternet()) {
                                            log.warn("Lost internet connection, shutting down set \"{}\" backup", set.getId());
                                            shutdown();
                                            return;
                                        }
                                    }
                                    completedFiles.incrementAndGet();
                                    completedSize.addAndGet(file.getLength());
                                    lock.lock();
                                    try {
                                        pendingFiles.getFile(file).setStatus(success ?
                                                BackupActiveStatus.INCLUDED :
                                                BackupActiveStatus.EXCLUDED);
                                        updateActivePath(set, currentPath, false);
                                    } finally {
                                        lock.unlock();
                                    }
                                });
                            } finally {
                                lock.lock();
                            }
                        } else {
                            if (existingFile.getDeleted() != null
                                    && existingFile.getLastChanged().equals(file.getLastChanged())
                                    && existingFile.getLength().equals(file.getLength())) {
                                existingFile.setDeleted(null);
                                repository.addFile(existingFile);
                                log.info("File \"{}\" undeleted", PathNormalizer.physicalPath(file.getPath()));
                            }
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
                debug(() -> log.debug("File \"" + PathNormalizer.physicalPath(file.getPath()) + "\" missing"));
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

                            if (!destinations.isEmpty()) {
                                log.warn("Missing destinations for existing \"{}\"", PathNormalizer.physicalPath(existingFile.getPath()));
                                return true;
                            }
                        }
                    } catch (IOException e) {
                        log.error("Failed to get block for existing file \"{}\"", PathNormalizer.physicalPath(existingFile.getPath()), e);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void shutdown() {
        lock.lock();
        try {
            shutdown = true;
            pendingDirectoriesUpdated.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void addPendingPath(BackupSet set, String path) {
        if (!pendingPaths.containsKey(path)) {
            BackupActivePath activePath;

            lock.unlock();
            try {
                if (path.endsWith(PATH_SEPARATOR)) {
                    Set<BackupActiveFile> files = filesystem.directoryFiles(path).stream()
                            .map(file -> new BackupActiveFile(BackupActivePath.stripPath(file.getPath())))
                            .collect(Collectors.toSet());

                    activePath = new BackupActivePath(path, files);
                } else {
                    activePath = new BackupActivePath("", Sets.newHashSet(new BackupActiveFile(path)));
                }
            } finally {
                lock.lock();
            }

            pendingPaths.put(path, activePath);

            final String debugPath = path;
            debug(() -> log.debug("Started processing \"{}\"", PathNormalizer.physicalPath(debugPath)));
            try {
                repository.pushActivePath(set.getId(), path, activePath);
            } catch (IOException e) {
                log.error("Failed to add active path \"" + path + "\"", e);
            }
        }
    }

    private void updateActivePath(BackupSet set, String currentPath, boolean forceClose) {
        BackupActivePath pending = pendingPaths.get(currentPath);
        if (pending != null) {
            if (pending.completed() || forceClose) {
                try {
                    repository.popActivePath(set.getId(), currentPath);
                    Set<String> includedPaths = pending.includedPaths();
                    if (currentPath.endsWith(PATH_SEPARATOR)) {
                        BackupDirectory directory = repository.directory(currentPath, null, false);
                        if (((directory == null && !includedPaths.isEmpty()) ||
                                (directory != null && (directory.getDeleted() != null || !directory.getFiles().equals(includedPaths))))) {
                            long timestamp;
                            if (directory != null && directory.getDeleted() == null && includedPaths.containsAll(directory.getFiles())) {
                                timestamp = directory.getAdded();
                            } else {
                                timestamp = Instant.now().toEpochMilli();
                            }
                            repository.addDirectory(new BackupDirectory(currentPath,
                                    timestamp,
                                    filesystem.extractPermissions(currentPath),
                                    Sets.newTreeSet(includedPaths), null));
                        }
                    }
                } catch (IOException e) {
                    log.error("Failed to record completing \"" + currentPath + "\"", e);
                }
                pendingPaths.remove(currentPath);
                debug(() -> log.debug("Completed processing \"{}\"", PathNormalizer.physicalPath(currentPath)));
                String parent = BackupActivePath.findParent(currentPath);
                if (parent != null) {
                    BackupActivePath parentActive = pendingPaths.get(parent);
                    if (parentActive != null) {
                        if (parentActive.unprocessedFile(currentPath)) {
                            parentActive.getFile(currentPath).setStatus(!pending.includedPaths().isEmpty()
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
                    log.error("Failed to record updating \"" + currentPath + "\"", e);
                }
            }
        }
    }
}