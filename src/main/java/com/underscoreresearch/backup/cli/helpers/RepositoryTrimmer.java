package com.underscoreresearch.backup.cli.helpers;

import static com.underscoreresearch.backup.model.BackupActivePath.findParent;
import static com.underscoreresearch.backup.model.BackupActivePath.stripPath;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableEta;
import static com.underscoreresearch.backup.utils.LogUtil.readableNumber;

import java.io.File;
import java.io.IOException;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import com.google.common.base.Stopwatch;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.implementation.ScannerSchedulerImpl;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.implementation.BackupContentsAccessPathOnly;
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
import com.underscoreresearch.backup.utils.StatusLine;
import com.underscoreresearch.backup.utils.StatusLogger;

@RequiredArgsConstructor
@Slf4j
public class RepositoryTrimmer implements StatusLogger {
    @Data
    public static class Statistics {
        private long files;
        private long fileVersions;
        private long totalSize;
        private long totalSizeLastVersion;
        private long blockParts;
        private long blocks;

        private long deletedBlocks;
        private long deletedVersions;
        private long deletedFiles;
        private long deletedDirectoryVersions;
        private long deletedDirectories;
        private long deletedBlockParts;
        private long deletedBlockPartReferences;

        private synchronized void addFiles(long files) {
            this.files += files;
        }

        private synchronized void addFileVersions(long fileVersions) {
            this.fileVersions += fileVersions;
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

        private synchronized void addBlocks(long blocks) {
            this.blocks += blocks;
        }

        private synchronized void addDeletedBlocks(long deletedBlocks) {
            this.deletedBlocks += deletedBlocks;
        }

        private synchronized void addDeletedVersions(long deletedVersions) {
            this.deletedVersions += deletedVersions;
        }

        private synchronized void addDeletedFiles(long deletedFiles) {
            this.deletedFiles += deletedFiles;
        }

        private synchronized void addDeletedDirectoryVersions(long deletedDirectoryVersions) {
            this.deletedDirectoryVersions += deletedDirectoryVersions;
        }

        private synchronized void addDeletedDirectories(long deletedDirectories) {
            this.deletedDirectories += deletedDirectories;
        }

        private synchronized void addDeletedBlockParts(long deletedParts) {
            this.deletedBlockParts += deletedParts;
        }

        private synchronized void addDeletedBlockPartReferences(long deletedParts) {
            this.deletedBlockPartReferences += deletedParts;
        }
    }

    private final MetadataRepository metadataRepository;
    private final BackupConfiguration configuration;
    private final ManifestManager manifestManager;
    private final boolean force;

    private Stopwatch stopwatch = Stopwatch.createUnstarted();
    private AtomicLong processedSteps = new AtomicLong();
    private AtomicLong totalSteps = new AtomicLong();
    private Duration lastHeartbeat;

    @Override
    public void resetStatus() {
        stopwatch.reset();
        processedSteps.set(0L);
    }

    @Override
    public List<StatusLine> status() {
        if (stopwatch.isRunning()) {
            long elapsedMilliseconds = stopwatch.elapsed().toMillis();
            if (elapsedMilliseconds > 0) {
                long throughput = 1000 * processedSteps.get() / elapsedMilliseconds;
                return Lists.newArrayList(
                        new StatusLine(getClass(), "TRIMMING_THROUGHPUT", "Trimming throughput",
                                throughput, readableNumber(throughput) + " steps/s"),
                        new StatusLine(getClass(), "TRIMMING_STEPS", "Trimming steps completed",
                                processedSteps.get(), totalSteps.get(),
                                readableNumber(processedSteps.get()) + " / "
                                        + readableNumber(totalSteps.get()) + " steps"
                                        + readableEta(processedSteps.get(), totalSteps.get(),
                                        Duration.ofMillis(elapsedMilliseconds))));
            }
        }
        return new ArrayList<>();
    }

    private static class InterruptedException extends RuntimeException {

    }

    private LoadingCache<String, NavigableSet<String>> directoryCache = CacheBuilder
            .newBuilder()
            .maximumSize(50)
            .build(new CacheLoader<>() {
                @Override
                public NavigableSet<String> load(String key) throws Exception {
                    BackupDirectory ret = metadataRepository.lastDirectory(key);
                    if (ret == null) {
                        return new TreeSet<>();
                    }
                    return ret.getFiles();
                }
            });

    public synchronized Statistics trimRepository(boolean filesOnly) throws IOException {
        File tempFile = File.createTempFile("block", ".db");

        manifestManager.setDisabledFlushing(true);
        try {
            tempFile.delete();

            Statistics statistics = new Statistics();

            try (DB usedBlockDb = DBMaker
                    .fileDB(tempFile)
                    .fileMmapEnableIfSupported()
                    .fileDeleteAfterClose()
                    .make()) {
                HTreeMap<String, Boolean> usedBlockMap = usedBlockDb.hashMap("USED_BLOCKS", Serializer.STRING,
                        Serializer.BOOLEAN).createOrOpen();

                manifestManager.initialize(null, true);

                boolean hasActivePaths = trimActivePaths();
                filesOnly |= hasActivePaths;

                try (CloseableLock ignored = metadataRepository.acquireLock()) {
                    if (filesOnly) {
                        totalSteps.set(metadataRepository.getFileCount());
                    } else {
                        totalSteps.set(metadataRepository.getBlockCount()
                                + metadataRepository.getPartCount()
                                + metadataRepository.getFileCount());
                    }
                    stopwatch.start();
                    lastHeartbeat = Duration.ZERO;

                    trimFilesAndDirectories(usedBlockMap, filesOnly, hasActivePaths, statistics);
                    trimBlocks(usedBlockMap, filesOnly, statistics);

                    if (!filesOnly)
                        metadataRepository.clearPartialFiles();
                } catch (InterruptedException exc) {
                } finally {
                    stopwatch.stop();
                }
            }

            if (!filesOnly) {
                ScannerSchedulerImpl.updateTrimSchedule(metadataRepository,
                        configuration.getManifest().getTrimSchedule());
            }
            return statistics;
        } finally {
            tempFile.delete();
            manifestManager.setDisabledFlushing(false);
        }
    }

    private boolean trimActivePaths() throws IOException {
        Map<String, BackupActivePath> activepaths = metadataRepository.getActivePaths(null);
        boolean anyFound = false;
        for (Map.Entry<String, BackupActivePath> paths : activepaths.entrySet()) {
            for (String setId : paths.getValue().getSetIds()) {
                if (!configuration.getSets().stream().anyMatch(t -> t.getId().equals(setId))) {
                    log.warn("Removing active path {} from non existing set {}",
                            paths.getKey(), setId);
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

    private void trimBlocks(HTreeMap<String, Boolean> usedBlockMap,
                            boolean filesOnly,
                            Statistics statistics) throws IOException {
        if (!filesOnly) {
            log.info("Trimming blocks");

            metadataRepository.allBlocks()
                    .filter(t -> {
                        processedSteps.incrementAndGet();
                        boolean ret = !usedBlockMap.containsKey(t.getHash());
                        if (!ret) {
                            statistics.addBlocks(1);
                            if (t.getStorage() != null)
                                t.getStorage().stream().forEach(s -> statistics.addBlockParts(s.getParts().size()));
                        }
                        return ret;
                    }).forEach(block -> {
                        if (InstanceFactory.isShutdown())
                            throw new InterruptedException();
                        try {
                            statistics.addDeletedBlocks(1);
                            for (BackupBlockStorage storage : block.getStorage()) {
                                IOProvider provider = IOProviderFactory.getProvider(
                                        configuration.getDestinations().get(storage.getDestination()));
                                for (String key : storage.getParts()) {
                                    if (key != null) {
                                        try {
                                            provider.delete(key);
                                            debug(() -> log.debug("Removing block part " + key));
                                            statistics.addDeletedBlockParts(1);
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

            log.info("Trimming partial references");
            metadataRepository.allFileParts().forEach((part) -> {
                if (InstanceFactory.isShutdown())
                    throw new InterruptedException();
                processedSteps.incrementAndGet();

                try {
                    if (metadataRepository.block(part.getBlockHash()) == null) {
                        debug(() -> log.debug("Removing file part {} references non existing block {}",
                                part.getPartHash(), part.getBlockHash()));
                        statistics.addDeletedBlockPartReferences(1);
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

    private void trimFilesAndDirectories(HTreeMap<String, Boolean> usedBlockMap,
                                         boolean filesOnly, boolean hasActivePaths,
                                         Statistics statistics)
            throws IOException {
        log.info("Trimming files and directories");

        List<BackupFile> fileVersions = new ArrayList<>();
        NavigableSet<String> deletedPaths = hasActivePaths ? null : new TreeSet<>();

        AtomicReference<String> lastParent = new AtomicReference<>(null);

        metadataRepository.allFiles(false).forEachOrdered((file) -> {
            if (InstanceFactory.isShutdown())
                throw new InterruptedException();

            if (lastHeartbeat.toMinutes() != stopwatch.elapsed().toMinutes()) {
                lastHeartbeat = stopwatch.elapsed();
                log.info("Processing path {}", file.getPath());
            }

            processedSteps.incrementAndGet();

            if (fileVersions.size() > 0 && !file.getPath().equals(fileVersions.get(0).getPath())) {
                try {
                    processFiles(fileVersions, usedBlockMap, deletedPaths, filesOnly, statistics);

                    if (deletedPaths != null) {
                        String parent = findParent(file.getPath());
                        if (!parent.equals(lastParent.get())) {
                            lastParent.set(parent);
                            processDeletedPaths(deletedPaths, parent, statistics);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                fileVersions.clear();
            }
            fileVersions.add(file);
        });

        if (fileVersions.size() > 0) {
            processFiles(fileVersions, usedBlockMap, deletedPaths, filesOnly, statistics);
        }

        if (deletedPaths != null) {
            processDeletedPaths(deletedPaths, null, statistics);
        }

        log.info("Removed {} file versions and {} entire files from repository",
                readableNumber(statistics.getDeletedVersions()),
                readableNumber(statistics.getDeletedFiles()));
        log.info("Removed {} directory versions and {} entire directories from repository",
                readableNumber(statistics.getDeletedDirectoryVersions()),
                readableNumber(statistics.getDeletedDirectories()));
    }

    private void processDeletedPaths(final NavigableSet<String> deletedPaths, final String parent,
                                     final Statistics statistics)
            throws IOException {
        while (deletedPaths.size() > 0) {
            String path = deletedPaths.last();
            if (parent == null || !parent.startsWith(path)) {
                processDirectories(path, deletedPaths, statistics);
                deletedPaths.remove(path);
            } else {
                break;
            }
        }
    }

    private void processDirectories(String path,
                                    NavigableSet<String> deletedPaths,
                                    Statistics statistics) throws IOException {
        List<BackupDirectory> directoryVersions = metadataRepository.directory(path);

        if (directoryVersions == null
                || directoryVersions.size() == 0
                || directoryVersions.get(0).getPath().equals("")) {
            return;
        }

        Long lastTimestamp = null;
        Set<String> lastContents = new HashSet<>();
        BackupDirectory lastDirectory = null;
        int deleted = 0;
        for (int i = directoryVersions.size() - 1; i >= 0; i--) {
            BackupDirectory directory = directoryVersions.get(i);
            BackupContentsAccessPathOnly contents = new BackupContentsAccessPathOnly(metadataRepository,
                    lastTimestamp, false);
            List<BackupFile> directoryFiles = contents.directoryFiles(directory.getPath());

            if (directoryFiles != null) {
                Set<String> directoryContents = directoryFiles
                        .stream()
                        .filter(t -> t.getAdded() != null)
                        .map(t -> t.getPath())
                        .collect(Collectors.toSet());
                if (lastDirectory != null && directoryContents.equals(lastContents)) {
                    deleteDirectory(lastDirectory, statistics);
                    deleted++;
                }
                if (i == 0) {
                    if (directoryContents.size() == 0) {
                        deleteDirectory(directory, statistics);
                        deleted++;
                    }
                } else {
                    lastContents = directoryContents;
                    lastDirectory = directory;
                }
            } else {
                deleteDirectory(directory, statistics);
                deleted++;
            }

            lastTimestamp = directory.getAdded() - 1;
        }
        if (deleted == directoryVersions.size()) {
            deletedPaths.add(findParent(path));
            statistics.addDeletedDirectories(1);
        }
    }

    private void deleteDirectory(BackupDirectory directory, Statistics statistics) throws IOException {
        debug(() -> log.debug("Removing " + directory.getPath() + " from "
                + LogUtil.formatTimestamp(directory.getAdded())));
        metadataRepository.deleteDirectory(directory.getPath(), directory.getAdded());
        statistics.addDeletedDirectoryVersions(1);
    }

    private void processFiles(List<BackupFile> files, HTreeMap<String, Boolean> usedBlockMap,
                              Set<String> deletedPaths,
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
                log.warn("File not in set {}, use force flag to delete", files.get(0).getPath());
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
                statistics.addFiles(1);
            } else {
                log.warn("File not in set, deleting {}", files.get(0).getPath());
                for (BackupFile file : files) {
                    metadataRepository.deleteFile(file);
                    markFileBlocks(usedBlockMap, filesOnly, file, false);
                    statistics.addDeletedVersions(1);
                }
                statistics.addDeletedFiles(1);
                addToDeletedPaths(deletedPaths, files.get(0));
            }
        } else {
            BackupFile lastFile = null;
            int keptCopies = 0;
            for (BackupFile file : files) {
                boolean deleted;
                if (!filesOnly) {
                    deleted = isDeleted(file.getPath());
                    if (deleted) {
                        if (file.getDeleted() == null) {
                            if (!retention.deletedImmediate()) {
                                file.setDeleted(Instant.now().toEpochMilli());
                                metadataRepository.addFile(file);
                            }
                        }
                    } else {
                        if (file.getDeleted() != null) {
                            file.setDeleted(null);
                            metadataRepository.addFile(file);
                        }
                    }
                } else {
                    deleted = false;
                }

                if (retention.keepFile(file, lastFile, deleted)
                        && (retention.getMaximumVersions() == null
                        || keptCopies < retention.getMaximumVersions())) {
                    lastFile = file;
                    markFileBlocks(usedBlockMap, filesOnly, file, true);
                    if (keptCopies == 0) {
                        statistics.addTotalSizeLastVersion(file.getLength());
                        statistics.addFiles(1);
                    }
                    keptCopies++;
                    statistics.addTotalSize(file.getLength());
                    statistics.addFileVersions(1);
                } else {
                    debug(() -> log.debug("Removing " + file.getPath() + " from "
                            + LogUtil.formatTimestamp(file.getAdded())));
                    metadataRepository.deleteFile(file);
                    markFileBlocks(usedBlockMap, filesOnly, file, false);
                    statistics.addDeletedVersions(1);
                }
            }
            if (keptCopies == 0) {
                statistics.addDeletedFiles(1);
                addToDeletedPaths(deletedPaths, files.get(0));
            }
        }
    }

    private boolean isDeleted(String path) throws IOException {
        String parent = findParent(path);
        try {
            NavigableSet<String> contents;
            if (parent != null)
                contents = directoryCache.get(findParent(path));
            else
                contents = new TreeSet<>();

            if (!contents.contains(stripPath(path))) {
                return !directoryCache.get("").contains(path);
            }
        } catch (ExecutionException e) {
            throw new IOException(e.getCause());
        }
        return isDeleted(parent);
    }

    private void addToDeletedPaths(Set<String> deletedPaths, BackupFile backupFile) {
        if (deletedPaths != null) {
            deletedPaths.add(findParent(backupFile.getPath()));
        }
    }

    private void markFileBlocks(HTreeMap<String, Boolean> usedBlockMap, boolean filesOnly, BackupFile file,
                                boolean used) throws IOException {
        if (!filesOnly) {
            if (file.getLocations() != null)
                for (BackupLocation location : file.getLocations()) {
                    if (location.getParts() != null)
                        for (BackupFilePart part : location.getParts()) {
                            markFileLocationBlocks(usedBlockMap, part.getBlockHash(), used);
                        }
                }
        }
    }

    private void markFileLocationBlocks(HTreeMap<String, Boolean> usedBlockMap,
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
    public void filterItems(List<StatusLine> lines, boolean temporal) {
        if (stopwatch.isRunning() && temporal == temporal()) {
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
}