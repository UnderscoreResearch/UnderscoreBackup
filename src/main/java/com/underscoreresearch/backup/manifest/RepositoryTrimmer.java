package com.underscoreresearch.backup.manifest;

import static com.underscoreresearch.backup.model.BackupActivePath.findParent;
import static com.underscoreresearch.backup.model.BackupActivePath.stripPath;
import static com.underscoreresearch.backup.utils.LogUtil.debug;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.manifest.implementation.BackupContentsAccessPathOnly;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupLocation;
import com.underscoreresearch.backup.model.BackupSet;
import com.underscoreresearch.backup.utils.LogUtil;
import com.underscoreresearch.backup.utils.StatusLine;
import com.underscoreresearch.backup.utils.StatusLogger;

@RequiredArgsConstructor
@Slf4j
public class RepositoryTrimmer implements StatusLogger {
    private final MetadataRepository metadataRepository;
    private final BackupConfiguration configuration;
    private final boolean force;

    private String lastFetchedDirectory;
    private BackupDirectory lastFetchedDirectoryContents;

    private Stopwatch stopwatch = Stopwatch.createUnstarted();
    private AtomicLong processedSteps = new AtomicLong();
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
                                throughput, throughput + " steps/s"),
                        new StatusLine(getClass(), "TRIMMING_STEPS", "Trimming steps completed",
                                processedSteps.get(), processedSteps.get() + " steps"));
            }
        }
        return new ArrayList<>();
    }

    private static class InterruptedException extends RuntimeException {

    }

    private synchronized NavigableSet<String> fetchPath(BackupFile file) throws IOException {
        String parent = findParent(file.getPath());
        if (parent.equals(lastFetchedDirectory)) {
            if (lastFetchedDirectoryContents != null && lastFetchedDirectoryContents.getFiles() != null) {
                return lastFetchedDirectoryContents.getFiles();
            }
            return new TreeSet<>();
        }

        lastFetchedDirectory = parent;
        lastFetchedDirectoryContents = metadataRepository.lastDirectory(parent);
        if (lastFetchedDirectoryContents == null || lastFetchedDirectoryContents.getFiles() == null) {
            return new TreeSet<>();
        }
        return lastFetchedDirectoryContents.getFiles();
    }

    public void trimRepository() throws IOException {
        File tempFile = File.createTempFile("block", ".db");
        try {
            tempFile.delete();
            try (DB usedBlockDb = DBMaker
                    .fileDB(tempFile)
                    .fileMmapEnableIfSupported()
                    .fileDeleteAfterClose()
                    .make()) {
                HTreeMap<String, Boolean> usedBlockMap = usedBlockDb.hashMap("USED_BLOCKS", Serializer.STRING,
                        Serializer.BOOLEAN).createOrOpen();

                boolean hasActivePaths = trimActivePaths();

                try {
                    stopwatch.start();
                    lastHeartbeat = Duration.ZERO;

                    trimFilesAndDirectories(usedBlockMap, hasActivePaths);
                    trimBlocks(usedBlockMap, hasActivePaths);
                } catch (InterruptedException exc) {
                } finally {
                    stopwatch.stop();
                }
            }
        } finally {
            tempFile.delete();
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
        return anyFound;
    }

    private void trimBlocks(HTreeMap<String, Boolean> usedBlockMap, boolean hasActivePaths) throws IOException {
        log.info("Trimming blocks");

        AtomicLong deletedParts = new AtomicLong();
        AtomicLong deletedBlocks = new AtomicLong();

        Boolean FALSE = false;

        metadataRepository.allBlocks()
                .filter(t -> hasActivePaths
                        ? FALSE.equals(usedBlockMap.get(t.getHash()))
                        : !usedBlockMap.containsKey(t.getHash())).forEach(block -> {
                    if (InstanceFactory.isShutdown())
                        throw new InterruptedException();
                    try {
                        processedSteps.incrementAndGet();
                        deletedBlocks.incrementAndGet();
                        for (BackupBlockStorage storage : block.getStorage()) {
                            IOProvider provider = IOProviderFactory.getProvider(
                                    configuration.getDestinations().get(storage.getDestination()));
                            for (String key : storage.getParts()) {
                                if (key != null) {
                                    try {
                                        provider.delete(key);
                                        debug(() -> log.debug("Removing block part " + key));
                                        deletedParts.incrementAndGet();
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

        log.info("Deleted {} blocks with a total of {} parts", deletedBlocks, deletedParts);
    }

    private void trimFilesAndDirectories(HTreeMap<String, Boolean> usedBlockMap, boolean hasActivePath)
            throws IOException {
        log.info("Trimming files and directories");

        AtomicInteger deletedVersions = new AtomicInteger();
        AtomicInteger deletedFiles = new AtomicInteger();

        AtomicInteger deletedDirectoryVersions = new AtomicInteger();
        AtomicInteger deletedDirectories = new AtomicInteger();

        List<BackupFile> fileVersions = new ArrayList<>();
        NavigableSet<String> deletedPaths = hasActivePath ? null : new TreeSet<>();

        String[] lastParent = new String[1];

        metadataRepository.allFiles().forEach((file) -> {
            if (InstanceFactory.isShutdown())
                throw new InterruptedException();

            if (lastHeartbeat.toMinutes() != stopwatch.elapsed().toMinutes()) {
                lastHeartbeat = stopwatch.elapsed();
                log.info("Processing path {}", file.getPath());
            }

            processedSteps.incrementAndGet();

            if (fileVersions.size() > 0 && !file.getPath().equals(fileVersions.get(0).getPath())) {
                try {
                    processFiles(fileVersions, usedBlockMap, deletedPaths, deletedVersions, deletedFiles);

                    if (deletedPaths != null) {
                        String parent = findParent(file.getPath());
                        if (!parent.equals(lastParent[0])) {
                            lastParent[0] = parent;
                            processDeletedPaths(deletedPaths, parent, deletedDirectoryVersions, deletedDirectories);
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
            processFiles(fileVersions, usedBlockMap, deletedPaths, deletedVersions, deletedFiles);
        }

        if (deletedPaths != null) {
            processDeletedPaths(deletedPaths, null, deletedDirectoryVersions, deletedDirectories);
        }

        log.info("Deleted {} file versions and {} entire files from repository", deletedVersions, deletedFiles);
        log.info("Deleted {} directory versions and {} entire directories from repository",
                deletedDirectoryVersions, deletedDirectories);
    }

    private void processDeletedPaths(final NavigableSet<String> deletedPaths, final String parent,
                                     final AtomicInteger deletedDirectoryVersions, AtomicInteger deletedDirectories)
            throws IOException {
        while (deletedPaths.size() > 0) {
            String path = deletedPaths.last();
            if (parent == null || !parent.startsWith(path)) {
                processDirectories(path, deletedPaths, deletedDirectoryVersions, deletedDirectories);
                deletedPaths.remove(path);
            } else {
                break;
            }
        }
    }

    private void processDirectories(String path,
                                    NavigableSet<String> deletedPaths,
                                    AtomicInteger deletedVersions,
                                    AtomicInteger deletedDirectories) throws IOException {
        List<BackupDirectory> directoryVersions = metadataRepository.directory(path);
        if (directoryVersions == null) {
            return;
        }

        Long lastTimestamp = null;
        boolean any = false;
        for (BackupDirectory directory : directoryVersions) {
            if (directory.getPath().equals("")) {
                return;
            }
            BackupContentsAccessPathOnly contents = new BackupContentsAccessPathOnly(metadataRepository,
                    lastTimestamp);
            List<BackupFile> directoryFiles = contents.directoryFiles(directory.getPath());

            if (directoryFiles == null
                    || directoryFiles.stream().filter(t -> t.getAdded() != null).count() == 0) {
                debug(() -> log.debug("Removing " + directory.getPath() + " from "
                        + LogUtil.formatTimestamp(directory.getAdded())));
                metadataRepository.deleteDirectory(directory.getPath(), directory.getAdded());
                deletedVersions.incrementAndGet();
            } else {
                any = true;
                lastTimestamp = directory.getAdded() - 1;
            }
        }
        if (!any) {
            deletedPaths.add(findParent(path));
            deletedDirectories.incrementAndGet();
        }
    }

    private void processFiles(List<BackupFile> files, HTreeMap<String, Boolean> usedBlockMap,
                              Set<String> deletedPaths,
                              AtomicInteger deletedVersions, AtomicInteger deletedFiles) throws IOException {
        BackupSet set = findSet(files.get(0));
        if (set == null) {
            if (!force) {
                log.warn("File not in set {},} use force flag to delete", files.get(0).getPath());
                for (BackupFile file : files) {
                    markFileBlocks(usedBlockMap, deletedPaths == null, file, true);
                }
            } else {
                log.warn("File not in set, deleting {}", files.get(0).getPath());
                for (BackupFile file : files) {
                    metadataRepository.deleteFile(file);
                    markFileBlocks(usedBlockMap, deletedPaths == null, file, false);
                    deletedVersions.incrementAndGet();
                }
                deletedFiles.incrementAndGet();
                addToDeletedPaths(deletedPaths, files.get(0));
            }
        } else {
            NavigableSet<String> parent = fetchPath(files.get(0));
            boolean deleted = !parent.contains(stripPath(files.get(0).getPath()));
            BackupFile lastFile = null;
            boolean anyFound = false;
            for (BackupFile file : files) {
                if (set.getRetention().keepFile(file, lastFile, deleted)) {
                    lastFile = file;
                    markFileBlocks(usedBlockMap, deletedPaths == null, file, true);
                    anyFound = true;
                } else {
                    debug(() -> log.debug("Removing " + file.getPath() + " from "
                            + LogUtil.formatTimestamp(file.getAdded())));
                    metadataRepository.deleteFile(file);
                    markFileBlocks(usedBlockMap, deletedPaths == null, file, false);
                    deletedVersions.incrementAndGet();
                }
            }
            if (!anyFound) {
                deletedFiles.incrementAndGet();
                addToDeletedPaths(deletedPaths, files.get(0));
            }
        }
    }

    private void addToDeletedPaths(Set<String> deletedPaths, BackupFile backupFile) {
        if (deletedPaths != null) {
            deletedPaths.add(findParent(backupFile.getPath()));
        }
    }

    private void markFileBlocks(HTreeMap<String, Boolean> usedBlockMap, boolean hasActivePath, BackupFile file,
                                boolean used) throws IOException {
        if (file.getLocations() != null)
            for (BackupLocation location : file.getLocations()) {
                if (location.getParts() != null)
                    for (BackupFilePart part : location.getParts()) {
                        markFileLocationBlocks(usedBlockMap, hasActivePath, part.getBlockHash(), used);
                    }
            }
    }

    private void markFileLocationBlocks(HTreeMap<String, Boolean> usedBlockMap, boolean hasActivePath,
                                        String hash, boolean used) throws IOException {
        if (used) {
            usedBlockMap.put(hash, true);
        } else if (hasActivePath) {
            if (!usedBlockMap.containsKey(hash)) {
                usedBlockMap.put(hash, false);
            }
        }

        if (BackupBlock.isSuperBlock(hash)) {
            BackupBlock block = metadataRepository.block(hash);
            for (String partHash : block.getHashes()) {
                markFileLocationBlocks(usedBlockMap, hasActivePath, partHash, used);
            }
        }
    }

    private BackupSet findSet(BackupFile backupFile) {
        return configuration.getSets().stream().filter(t -> t.inRoot(backupFile.getPath())).findAny().orElse(null);
    }
}
