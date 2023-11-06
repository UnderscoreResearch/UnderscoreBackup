package com.underscoreresearch.backup.cli.helpers;

import static com.underscoreresearch.backup.io.IOUtils.deleteFile;
import static com.underscoreresearch.backup.model.BackupActivePath.findParent;
import static com.underscoreresearch.backup.model.BackupActivePath.stripPath;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.lastProcessedPath;
import static com.underscoreresearch.backup.utils.LogUtil.readableEta;
import static com.underscoreresearch.backup.utils.LogUtil.readableNumber;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Stopwatch;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.cli.ui.UIHandler;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableMap;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.MapSerializer;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.file.implementation.ScannerSchedulerImpl;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupLocation;
import com.underscoreresearch.backup.model.BackupRetention;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.utils.LogUtil;
import com.underscoreresearch.backup.utils.ManualStatusLogger;
import com.underscoreresearch.backup.utils.StateLogger;
import com.underscoreresearch.backup.utils.StatusLine;

@Slf4j
public class RepositoryTrimmer implements ManualStatusLogger {
    private static final int MINIMUM_FILES_FOR_DIRECTORY = 50;
    private static final double MINIMUM_RATIO_DIRECTORY_DIFF = 0.75;
    private final MetadataRepository metadataRepository;
    private final BackupConfiguration configuration;
    private final ManifestManager manifestManager;
    private final boolean force;
    private final Stopwatch stopwatch = Stopwatch.createUnstarted();
    private final AtomicLong processedSteps = new AtomicLong();
    private final AtomicLong totalSteps = new AtomicLong();

    private final DirectoryCache directoryCache = new DirectoryCache((Integer size) -> CacheBuilder
            .newBuilder()
            .maximumSize(size)
            .concurrencyLevel(1)
            .build(new CacheLoader<>() {
                @Override
                public NavigableSet<String> load(String key) throws Exception {
                    BackupDirectory ret = metadataRepository.directory(key, null, false);
                    if (ret == null) {
                        return new TreeSet<>();
                    }
                    return ret.getFiles();
                }
            }));
    private final DirectoryCache orphanedCache = new DirectoryCache((Integer size) -> CacheBuilder
            .newBuilder()
            .maximumSize(size)
            .concurrencyLevel(1)
            .build(new CacheLoader<>() {
                @Override
                public NavigableSet<String> load(String key) throws Exception {
                    BackupDirectory ret = metadataRepository.directory(key, null, true);
                    if (ret == null) {
                        return new TreeSet<>();
                    }
                    return ret.getFiles();
                }
            }));
    private String lastProcessed;
    private Duration lastHeartbeat;

    public RepositoryTrimmer(MetadataRepository metadataRepository, BackupConfiguration configuration, ManifestManager manifestManager, boolean force) {
        this.metadataRepository = metadataRepository;
        this.configuration = configuration;
        this.manifestManager = manifestManager;
        this.force = force;

        StateLogger.addLogger(this);
    }

    private static boolean missingInDirectory(String path, DirectoryCache cache, int depth) throws IOException {
        String parent = findParent(path);
        try {
            Set<String> contents;
            if (parent != null)
                contents = cache.getCache().get(parent);
            else
                contents = new TreeSet<>();

            if (!contents.contains(stripPath(path))) {
                return !cache.getCache().get("").contains(path);
            }
        } catch (ExecutionException e) {
            throw new IOException(e.getCause());
        }
        if (cache.getCacheSize() - depth < 5) {
            cache.setCacheSize(cache.getCacheSize() + 10);
        }
        return missingInDirectory(parent, cache, depth + 1);
    }

    @Override
    public void resetStatus() {
        stopwatch.reset();
        processedSteps.set(0L);
        lastProcessed = null;
    }

    @Override
    public List<StatusLine> status() {
        if (stopwatch.isRunning()) {
            long elapsedMilliseconds = stopwatch.elapsed().toMillis();
            if (elapsedMilliseconds > 0) {
                long throughput = 1000 * processedSteps.get() / elapsedMilliseconds;

                List<StatusLine> ret = Lists.newArrayList(
                        new StatusLine(getClass(), "TRIMMING_THROUGHPUT", "Trimming throughput",
                                throughput, readableNumber(throughput) + " steps/s"),
                        new StatusLine(getClass(), "TRIMMING_STEPS", "Trimming repository",
                                processedSteps.get(), totalSteps.get(),
                                readableNumber(processedSteps.get()) + " / "
                                        + readableNumber(totalSteps.get()) + " steps"
                                        + readableEta(processedSteps.get(), totalSteps.get(),
                                        Duration.ofMillis(elapsedMilliseconds))));
                lastProcessedPath(getClass(), ret, lastProcessed, "TRIMMING_PROCESSED_PATH");
                return ret;
            }
        }

        return new ArrayList<>();
    }

    public synchronized Statistics trimRepository(boolean noBlocks) throws IOException {
        File tempFile = File.createTempFile("block", ".db");

        manifestManager.setDisabledFlushing(true);
        try (Closeable ignored2 = UIHandler.registerTask("Trimming repository")) {
            deleteFile(tempFile);

            Statistics statistics = new Statistics();

            boolean filesOnly = trimActivePaths();
            if (!filesOnly && metadataRepository.isErrorsDetected()) {
                log.warn("Repository contains errors, will only trim files");
                filesOnly = true;
            }

            CloseableMap<String, Boolean> usedBlockMap = !filesOnly || !noBlocks ? metadataRepository.temporaryMap(new MapSerializer<String, Boolean>() {
                @Override
                public byte[] encodeKey(String s) {
                    return s.getBytes(StandardCharsets.UTF_8);
                }

                @Override
                public byte[] encodeValue(Boolean aBoolean) {
                    if (aBoolean)
                        return new byte[]{1};
                    else
                        return new byte[]{0};
                }

                @Override
                public Boolean decodeValue(byte[] data) {
                    return data.length > 0 && data[0] != 0;
                }

                @Override
                public String decodeKey(byte[] data) {
                    return new String(data, StandardCharsets.UTF_8);
                }
            }) : null;

            try {
                manifestManager.initialize((LogConsumer) metadataRepository, true);

                try (CloseableLock ignored = metadataRepository.acquireLock()) {
                    if (filesOnly) {
                        totalSteps.set(metadataRepository.getFileCount());
                    } else {
                        totalSteps.set(metadataRepository.getFileCount()
                                + metadataRepository.getDirectoryCount()
                                + metadataRepository.getPartCount());

                        if (!noBlocks) {
                            totalSteps.addAndGet(metadataRepository.getBlockCount());
                        }
                    }

                    stopwatch.start();
                    lastHeartbeat = Duration.ZERO;

                    if (!trimFiles(usedBlockMap, filesOnly, statistics)) {
                        log.warn("Found error in repository, will only trim files");
                        metadataRepository.setErrorsDetected(true);
                        filesOnly = true;
                    }

                    if (!filesOnly) {
                        trimDirectories(statistics);

                        if (!noBlocks) {
                            metadataRepository.clearPartialFiles();

                            trimBlocks(usedBlockMap, statistics);
                            ScannerSchedulerImpl.updateTrimSchedule(metadataRepository,
                                    configuration.getManifest().getTrimSchedule());
                        } else {
                            log.info("Skipping block trimming");
                        }
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    if (stopwatch.isRunning()) {
                        stopwatch.stop();
                    }
                }
            } finally {
                if (usedBlockMap != null) {
                    usedBlockMap.close();
                }
            }

            return statistics;
        } finally {
            deleteFile(tempFile);
            manifestManager.setDisabledFlushing(false);
        }
    }

    private void trimDirectories(Statistics statistics) throws IOException {
        log.info("Trimming directories");

        try (CloseableStream<BackupDirectory> directories = metadataRepository.allDirectories(false)) {
            AtomicReference<BackupDirectory> lastDirectory = new AtomicReference<>();
            AtomicReference<Long> lastTimestamp = new AtomicReference<>();
            List<BackupDirectory> deletions = new ArrayList<>();
            directories.stream().forEach((directory) -> {
                if (InstanceFactory.isShutdown())
                    throw new InterruptedException();
                processedSteps.incrementAndGet();

                if (lastHeartbeat.toMinutes() != stopwatch.elapsed().toMinutes()) {
                    lastHeartbeat = stopwatch.elapsed();
                    log.info("Processing directory {}", PathNormalizer.physicalPath(directory.getPath()));
                }

                if (directory.getPath().equals("")) {
                    return;
                }

                if (lastDirectory.get() != null && lastDirectory.get().getPath().equals(directory.getPath())) {
                    TreeSet<String> files = new TreeSet<>(directory.getFiles());
                    files.addAll(lastDirectory.get().getFiles());
                    if (files.size() < MINIMUM_FILES_FOR_DIRECTORY || (!directory.getFiles().isEmpty() && files.size() * MINIMUM_RATIO_DIRECTORY_DIFF < directory.getFiles().size())) {
                        directory.setFiles(files);
                        lastDirectory.get().getFiles().clear();
                        deletions.add(lastDirectory.get());
                    } else {
                        lastTimestamp.set(processMergedDirectories(false, lastDirectory.get(), deletions, lastTimestamp.get(), statistics));
                    }
                } else {
                    if (lastDirectory.get() != null) {
                        processMergedDirectories(true, lastDirectory.get(), deletions, lastTimestamp.get(), statistics);
                    }
                    lastTimestamp.set(null);
                }
                lastDirectory.set(directory);
                lastProcessed = directory.getPath();
            });
            if (lastDirectory.get() != null) {
                processMergedDirectories(true, lastDirectory.get(), deletions, lastTimestamp.get(), statistics);
            }

            lastProcessed = null;

            log.info("Removed {} directory versions and {} entire directories from repository",
                    readableNumber(statistics.getDeletedDirectoryVersions()),
                    readableNumber(statistics.getDeletedDirectories()));
        }
    }

    private long processMergedDirectories(boolean last, BackupDirectory directory, List<BackupDirectory> deletions, Long laterVersion, Statistics statistics) {
        HashSet<String> deletedPaths = new HashSet<>();

        Long searchVersion = laterVersion != null ? laterVersion - 1 : null;
        for (String path : directory.getFiles()) {
            String fullPath = PathNormalizer.combinePaths(directory.getPath(), path);
            try {
                if (path.endsWith(PathNormalizer.PATH_SEPARATOR)) {
                    BackupDirectory child = metadataRepository.directory(fullPath, searchVersion, false);
                    if (child == null) {
                        deletedPaths.add(path);
                    }
                } else {
                    BackupFile file = metadataRepository.file(fullPath, searchVersion);
                    if (file == null) {
                        deletedPaths.add(path);
                    }
                }
            } catch (IOException e) {
                log.error("Failed to check directory existence of {}", PathNormalizer.physicalPath(fullPath));
            }
        }

        if (!deletions.isEmpty() || !deletedPaths.isEmpty()) {
            directory.getFiles().removeAll(deletedPaths);
            try {
                if (last && directory.getFiles().isEmpty()) {
                    deleteDirectory(directory, statistics);
                    if (laterVersion == null) {
                        statistics.addDeletedDirectory();
                    }
                } else {
                    metadataRepository.addDirectory(directory);
                    statistics.addDirectoryVersion();
                }
                for (BackupDirectory delDirectory : deletions) {
                    deleteDirectory(delDirectory, statistics);
                }
            } catch (IOException e) {
                log.error("Failed to merge directories at path {}", PathNormalizer.physicalPath(directory.getPath()));
            }
            deletions.clear();
        }

        if (last && !(directory.getFiles().isEmpty() && laterVersion == null)) {
            statistics.addDirectory();
        }

        return directory.getAdded();
    }

    private boolean trimActivePaths() throws IOException {
        Map<String, BackupActivePath> activepaths = metadataRepository.getActivePaths(null);
        boolean anyFound = false;
        for (Map.Entry<String, BackupActivePath> paths : activepaths.entrySet()) {
            for (String setId : paths.getValue().getSetIds()) {
                if (configuration.getSets().stream().noneMatch(t -> t.getId().equals(setId))) {
                    log.warn("Removing active path {} from non existing set {}",
                            PathNormalizer.physicalPath(paths.getKey()), setId);
                    metadataRepository.popActivePath(setId, paths.getKey());
                } else {
                    anyFound = true;
                }
            }
        }
        if (anyFound) {
            log.info("Active backup detected, some repository trimming will be postponed");
        }
        return anyFound;
    }

    private void trimBlocks(CloseableMap<String, Boolean> usedBlockMap,
                            Statistics statistics) throws IOException {
        log.info("Trimming blocks");

        try (CloseableStream<BackupBlock> blocks = metadataRepository.allBlocks()) {
            blocks.stream().filter(t -> {
                processedSteps.incrementAndGet();
                boolean ret = !usedBlockMap.containsKey(t.getHash());
                if (!ret) {
                    statistics.addBlock();
                    if (t.getStorage() != null)
                        t.getStorage().forEach(s -> statistics.addBlockParts(s.getParts().size()));
                }
                return ret;
            }).forEach(block -> {
                if (InstanceFactory.isShutdown())
                    throw new InterruptedException();
                try {
                    statistics.addDeletedBlock();
                    for (BackupBlockStorage storage : block.getStorage()) {
                        IOProvider provider = IOProviderFactory.getProvider(
                                configuration.getDestinations().get(storage.getDestination()));
                        for (String key : storage.getParts()) {
                            if (key != null) {
                                try {
                                    provider.delete(key);
                                    debug(() -> log.debug("Removing block part " + key));
                                    statistics.addDeletedBlockPart();
                                } catch (IOException exc) {
                                    log.error("Failed to delete part " + key + " from " + storage.getDestination(), exc);
                                }
                            }
                        }
                    }
                    debug(() -> log.debug("Removing block " + block.getHash()));
                    metadataRepository.deleteBlock(block);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        log.info("Trimming partial references");
        try (CloseableStream<BackupFilePart> fileParts = metadataRepository.allFileParts()) {
            fileParts.stream().forEach((part) -> {
                if (InstanceFactory.isShutdown())
                    throw new InterruptedException();
                processedSteps.incrementAndGet();

                try {
                    if (metadataRepository.block(part.getBlockHash()) == null) {
                        debug(() -> log.debug("Removing file part {} references non existing block {}",
                                part.getPartHash(), part.getBlockHash()));
                        statistics.addDeletedBlockPartReference();
                        metadataRepository.deleteFilePart(part);
                    }
                } catch (IOException exc) {
                    log.error("Encountered issue validating part {} for block {}", part.getPartHash(),
                            part.getBlockHash());
                }
            });

            log.info("Removed {} blocks with a total of {} parts and {} part references",
                    readableNumber(statistics.getDeletedBlocks()),
                    readableNumber(statistics.getDeletedBlockParts()),
                    readableNumber(statistics.getDeletedBlockPartReferences()));
        }
    }

    private boolean trimFiles(CloseableMap<String, Boolean> usedBlockMap,
                              boolean filesOnly, Statistics statistics)
            throws IOException {
        log.info("Trimming files");

        List<BackupFile> fileVersions = new ArrayList<>();

        AtomicBoolean foundError = new AtomicBoolean(false);

        try (CloseableStream<BackupFile> files = metadataRepository.allFiles(false)) {
            files.setReportErrorsAsNull(true);
            files.stream().forEachOrdered((file) -> {
                if (file == null) {
                    foundError.set(true);
                    return;
                }
                if (InstanceFactory.isShutdown())
                    throw new InterruptedException();

                if (lastHeartbeat.toMinutes() != stopwatch.elapsed().toMinutes()) {
                    lastHeartbeat = stopwatch.elapsed();
                    log.info("Processing file {}", PathNormalizer.physicalPath(file.getPath()));
                }
                lastProcessed = file.getPath();

                processedSteps.incrementAndGet();

                if (!fileVersions.isEmpty() && !file.getPath().equals(fileVersions.get(0).getPath())) {
                    try {
                        processFiles(fileVersions, usedBlockMap, filesOnly, statistics);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    fileVersions.clear();
                }
                fileVersions.add(file);
            });
        }

        if (!fileVersions.isEmpty()) {
            processFiles(fileVersions, usedBlockMap, filesOnly, statistics);
        }

        log.info("Removed {} file versions and {} entire files from repository",
                readableNumber(statistics.getDeletedVersions()),
                readableNumber(statistics.getDeletedFiles()));

        lastProcessed = null;

        return !foundError.get();
    }

    private void deleteDirectory(BackupDirectory directory, Statistics statistics) throws IOException {
        debug(() -> log.debug("Removing " + PathNormalizer.physicalPath(directory.getPath()) + " from "
                + LogUtil.formatTimestamp(directory.getAdded())));
        metadataRepository.deleteDirectory(directory.getPath(), directory.getAdded());
        statistics.addDeletedDirectoryVersion();
    }

    private void processFiles(List<BackupFile> files, CloseableMap<String, Boolean> usedBlockMap,
                              boolean filesOnly, Statistics statistics) throws IOException {
        BackupSet set = findSet(files.get(0));
        BackupRetention retention;

        if (set != null) {
            retention = set.getRetention();
        } else {
            retention = configuration.getMissingRetention();
        }

        if (retention == null) {
            if (!force) {
                log.warn("File not in set {}, use force flag to delete", PathNormalizer.physicalPath(files.get(0).getPath()));
                boolean anyFound = false;
                for (BackupFile file : files) {
                    markFileBlocks(usedBlockMap, filesOnly, file, true);
                    statistics.addTotalSize(file.getLength());
                    if (!anyFound) {
                        statistics.addTotalSizeLastVersion(file.getLength());
                        anyFound = true;
                    }
                }
                statistics.addFileVersions(files.size());
                statistics.addFile();
            } else {
                log.warn("File not in set, deleting {}", PathNormalizer.physicalPath(files.get(0).getPath()));
                for (BackupFile file : files) {
                    metadataRepository.deleteFile(file);
                    markFileBlocks(usedBlockMap, filesOnly, file, false);
                    statistics.addDeletedVersion();
                }
                statistics.addDeletedFile();
            }
        } else {
            BackupFile lastFile = null;
            int keptCopies = 0;
            Boolean orphaned = null;
            for (BackupFile file : files) {
                boolean deleted;
                boolean remove = false;
                if (!filesOnly) {
                    deleted = isDeleted(file.getPath());
                    if (deleted) {
                        if (orphaned == null) {
                            orphaned = isOrphaned(file.getPath());
                            if (orphaned) {
                                log.warn("Deleting orphaned file {} not referenced in any directory",
                                        PathNormalizer.physicalPath(file.getPath()));
                            }
                        }
                        if (orphaned) {
                            remove = true;
                        } else {
                            if (file.getDeleted() == null) {
                                if (!retention.deletedImmediate()) {
                                    file.setDeleted(Instant.now().toEpochMilli());
                                    metadataRepository.addFile(file);
                                    debug(() -> log.debug("Marking " + PathNormalizer.physicalPath(file.getPath())
                                            + " as deleted at " + LogUtil.formatTimestamp(file.getDeleted())));
                                }
                            }
                        }
                    }
                } else {
                    deleted = false;
                }

                if (!remove) {
                    if (!retention.keepFile(file, lastFile, deleted)) {
                        remove = true;
                    } else if (retention.getMaximumVersions() != null && keptCopies >= retention.getMaximumVersions()) {
                        remove = true;
                    }
                    if (remove) {
                        debug(() -> log.debug("Removing " + PathNormalizer.physicalPath(file.getPath()) + " from "
                                + LogUtil.formatTimestamp(file.getAdded())));
                    }
                }

                if (remove) {
                    metadataRepository.deleteFile(file);
                    markFileBlocks(usedBlockMap, filesOnly, file, false);
                    statistics.addDeletedVersion();
                } else {
                    lastFile = file;
                    markFileBlocks(usedBlockMap, filesOnly, file, true);
                    if (keptCopies == 0) {
                        statistics.addTotalSizeLastVersion(file.getLength());
                        statistics.addFile();
                    }
                    keptCopies++;
                    statistics.addTotalSize(file.getLength());
                    statistics.addFileVersions(1);
                }
            }
            if (keptCopies == 0) {
                statistics.addDeletedFile();
            }
        }
    }

    private boolean isOrphaned(String path) throws IOException {
        return missingInDirectory(path, orphanedCache, 1);
    }

    private boolean isDeleted(String path) throws IOException {
        return missingInDirectory(path, directoryCache, 1);
    }

    private void markFileBlocks(CloseableMap<String, Boolean> usedBlockMap, boolean filesOnly, BackupFile file,
                                boolean used) throws IOException {
        if (!filesOnly && usedBlockMap != null) {
            if (file.getLocations() != null)
                for (BackupLocation location : file.getLocations()) {
                    if (location.getParts() != null)
                        for (BackupFilePart part : location.getParts()) {
                            markFileLocationBlocks(usedBlockMap, part.getBlockHash(), used);
                        }
                }
        }
    }

    private void markFileLocationBlocks(CloseableMap<String, Boolean> usedBlockMap,
                                        String hash, boolean used) throws IOException {
        if (used) {
            usedBlockMap.put(hash, true);
        }

        if (BackupBlock.isSuperBlock(hash)) {
            BackupBlock block = metadataRepository.block(hash);
            if (block != null && block.getHashes() != null) {
                for (String partHash : block.getHashes()) {
                    markFileLocationBlocks(usedBlockMap, partHash, used);
                }
            } else {
                log.error("Missing referenced super block {}, run validate-blocks to remedy", hash);
            }
        }
    }

    @Override
    public void filterItems(List<StatusLine> lines) {
        if (stopwatch.isRunning()) {
            for (int i = 0; i < lines.size(); ) {
                String code = lines.get(i).getCode();
                if (code.startsWith("TRIMMING_") || code.startsWith("HEAP_")) {
                    i++;
                } else {
                    lines.remove(i);
                }
            }
        }
    }

    private BackupSet findSet(BackupFile backupFile) {
        return configuration.getSets().stream().filter(t -> t.inRoot(backupFile.getPath())).findAny().orElse(null);
    }

    @Data
    public static class Statistics {
        private long files;
        private long fileVersions;
        private long totalSize;
        private long totalSizeLastVersion;
        private long blockParts;
        private long blocks;
        private long directories;
        private long directoryVersions;

        private long deletedBlocks;
        private long deletedVersions;
        private long deletedFiles;
        private long deletedDirectoryVersions;
        private long deletedDirectories;
        private long deletedBlockParts;
        private long deletedBlockPartReferences;

        private boolean needActivation;

        private synchronized void addFile() {
            this.files++;
        }

        private synchronized void addFileVersions(long fileVersions) {
            this.fileVersions += fileVersions;
        }

        private synchronized void addDirectory() {
            this.directories++;
        }

        private synchronized void addDirectoryVersion() {
            this.directoryVersions++;
        }

        private synchronized void addTotalSize(long totalSize) {
            this.totalSize += totalSize;
        }

        private synchronized void addTotalSizeLastVersion(long totalSizeLastVersion) {
            this.totalSizeLastVersion += totalSizeLastVersion;
        }

        private synchronized void addBlockParts(long blockParts) {
            this.blockParts += blockParts;
        }

        private synchronized void addBlock() {
            this.blocks++;
        }

        private synchronized void addDeletedBlock() {
            this.deletedBlocks++;
        }

        private synchronized void addDeletedVersion() {
            this.deletedVersions++;
        }

        private synchronized void addDeletedFile() {
            this.deletedFiles++;
        }

        private synchronized void addDeletedDirectoryVersion() {
            this.deletedDirectoryVersions++;
        }

        private synchronized void addDeletedDirectory() {
            this.deletedDirectories++;
        }

        private synchronized void addDeletedBlockPart() {
            this.deletedBlockParts++;
        }

        private synchronized void addDeletedBlockPartReference() {
            this.deletedBlockPartReferences++;
        }
    }

    private static class InterruptedException extends RuntimeException {

    }
}
