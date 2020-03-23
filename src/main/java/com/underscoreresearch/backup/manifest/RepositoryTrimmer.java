package com.underscoreresearch.backup.manifest;

import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.manifest.implementation.BackupContentsAccessPathOnly;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.*;
import com.underscoreresearch.backup.utils.LogUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.concurrent.atomic.AtomicInteger;

import static com.underscoreresearch.backup.model.BackupActivePath.findParent;
import static com.underscoreresearch.backup.model.BackupActivePath.stripPath;
import static com.underscoreresearch.backup.utils.LogUtil.debug;

@RequiredArgsConstructor
@Slf4j
public class RepositoryTrimmer {
    private static final int MAXIMUM_PATH_DRIFT = 60 * 1000;
    private final MetadataRepository metadataRepository;
    private final BackupConfiguration configuration;
    private final boolean force;

    private String lastFetchedDirectory;
    private BackupDirectory lastFetchedDirectoryContents;

    private synchronized NavigableSet<String> fetchPath(BackupFile file) throws IOException {
        String parent = findParent(file.getPath());
        if (parent.equals(lastFetchedDirectory))
            return lastFetchedDirectoryContents.getFiles();

        lastFetchedDirectory = parent;
        lastFetchedDirectoryContents = metadataRepository.lastDirectory(parent);
        return lastFetchedDirectoryContents.getFiles();
    }

    public void trimRepository() throws IOException {
        File tempFile = File.createTempFile("block", ".db");
        try {
            tempFile.delete();
            try (DB usedBlockDb = DBMaker
                    .fileDB(tempFile)
                    .fileMmapEnableIfSupported()
                    .make()) {
                HTreeMap<String, Boolean> usedBlockMap = usedBlockDb.hashMap("USED_BLOCKS", Serializer.STRING,
                        Serializer.BOOLEAN).createOrOpen();

                boolean hasActivePaths = trimActivepaths();

                trimFiles(usedBlockMap, hasActivePaths);

                trimBlocks(usedBlockMap, hasActivePaths);

                trimDirectories(hasActivePaths);
            }
        } finally {
            tempFile.delete();
        }
    }

    private boolean trimActivepaths() throws IOException {
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

    private void trimDirectories(boolean hasActivePaths) throws IOException {
        if (hasActivePaths) {
            log.info("Not trimming directories because there are sets in process");
            return;
        }
        log.info("Trimming directories");

        AtomicInteger deletedVersions = new AtomicInteger();
        AtomicInteger deletedDirectories = new AtomicInteger();

        List<BackupDirectory> pathVersions = new ArrayList<>();
        metadataRepository.allDirectories().forEach((dir) -> {
            if (pathVersions.size() > 0 && !dir.getPath().equals(pathVersions.get(0).getPath())) {
                try {
                    processDirectories(pathVersions, deletedVersions, deletedDirectories);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                pathVersions.clear();
            }
            pathVersions.add(dir);
        });
        if (pathVersions.size() > 0) {
            processDirectories(pathVersions, deletedVersions, deletedDirectories);
        }

        log.info("Deleted {} directory versions and {} entire directories from repository",
                deletedVersions, deletedDirectories);
    }

    private void processDirectories(List<BackupDirectory> directoryVersions,
                                    AtomicInteger deletedVersions,
                                    AtomicInteger deletedDirectories) throws IOException {
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
                    || directoryFiles.stream().filter(t -> t.getLastChanged() != null).count() == 0) {
                debug(() -> log.debug("Removing " + directory.getPath() + " from " + LogUtil.formatTimestamp(directory.getTimestamp())));
                metadataRepository.deleteDirectory(directory.getPath(), directory.getTimestamp());
                deletedVersions.incrementAndGet();
            } else {
                any = true;
                lastTimestamp = directory.getTimestamp() - 1;
            }
        }
        if (!any)
            deletedDirectories.incrementAndGet();
    }

    private void trimBlocks(HTreeMap<String, Boolean> usedBlockMap, boolean hasActivePaths) throws IOException {
        log.info("Trimming blocks");

        AtomicInteger deletedParts = new AtomicInteger();
        AtomicInteger deletedBlocks = new AtomicInteger();

        Boolean FALSE = false;

        metadataRepository.allBlocks()
                .filter(t -> hasActivePaths
                        ? FALSE.equals(usedBlockMap.get(t.getHash()))
                        : !usedBlockMap.containsKey(t.getHash())).forEach(block -> {
            try {
                deletedBlocks.incrementAndGet();
                for (BackupBlockStorage storage : block.getStorage()) {
                    IOProvider provider = IOProviderFactory.getProvider(
                            configuration.getDestinations().get(storage.getDestination()));
                    for (String key : storage.getParts()) {
                        try {
                            provider.delete(key);
                            debug(() -> log.debug("Removing block part " + key));
                            deletedParts.incrementAndGet();
                        } catch (IOException exc) {
                            log.error("Failed to delete part {} from {}", key, storage.getDestination());
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

    private void trimFiles(HTreeMap<String, Boolean> usedBlockMap, boolean hasActivePath) throws IOException {
        log.info("Trimming files");

        AtomicInteger deletedVersions = new AtomicInteger();
        AtomicInteger deletedFiles = new AtomicInteger();
        List<BackupFile> fileVersions = new ArrayList<>();
        metadataRepository.allFiles().forEach((file) -> {
            if (fileVersions.size() > 0 && !file.getPath().equals(fileVersions.get(0).getPath())) {
                try {
                    processFiles(fileVersions, usedBlockMap, hasActivePath, deletedVersions, deletedFiles);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                fileVersions.clear();
            }
            fileVersions.add(file);
        });
        if (fileVersions.size() > 0) {
            processFiles(fileVersions, usedBlockMap, hasActivePath, deletedVersions, deletedFiles);
        }

        log.info("Deleted {} file versions and {} entire files from repository", deletedVersions, deletedFiles);
    }

    private void processFiles(List<BackupFile> files, HTreeMap<String, Boolean> usedBlockMap,
                              boolean hasActivePath, AtomicInteger deletedVersions, AtomicInteger deletedFiles) throws IOException {
        BackupSet set = findSet(files.get(0));
        if (set == null) {
            if (!force) {
                log.warn("File not in set {},} use force flag to delete", files.get(0).getPath());
                for (BackupFile file : files) {
                    markFileBlocks(usedBlockMap, hasActivePath, file, true);
                }
            } else {
                log.warn("File not in set, deleting {}", files.get(0).getPath());
                for (BackupFile file : files) {
                    metadataRepository.deleteFile(file);
                    markFileBlocks(usedBlockMap, hasActivePath, file, false);
                    deletedVersions.incrementAndGet();
                }
                deletedFiles.incrementAndGet();
            }
        } else {
            NavigableSet<String> parent = fetchPath(files.get(0));
            boolean deleted = !parent.contains(stripPath(files.get(0).getPath()));
            BackupFile lastFile = null;
            boolean anyFound = false;
            for (BackupFile file : files) {
                if (set.getRetention().keepFile(file, lastFile, deleted)) {
                    lastFile = file;
                    markFileBlocks(usedBlockMap, hasActivePath, file, true);
                    anyFound = true;
                } else {
                    debug(() -> log.debug("Removing " + file.getPath() + " from " + LogUtil.formatTimestamp(file.getLastChanged())));
                    metadataRepository.deleteFile(file);
                    markFileBlocks(usedBlockMap, hasActivePath, file, false);
                    deletedVersions.incrementAndGet();
                }
            }
            if (!anyFound) {
                deletedFiles.incrementAndGet();
            }
        }
    }

    private void markFileBlocks(HTreeMap<String, Boolean> usedBlockMap, boolean hasActivePath, BackupFile file, boolean used) {
        if (file.getLocations() != null)
            file.getLocations()
                    .forEach(location -> {
                        if (location.getParts() != null)
                            location.getParts()
                                    .forEach(part -> markFileLocationBlocks(usedBlockMap, hasActivePath, part, used));
                    });
    }

    private void markFileLocationBlocks(HTreeMap<String, Boolean> usedBlockMap, boolean hasActivePath,
                                        BackupFilePart part, boolean used) {
        if (used) {
            usedBlockMap.put(part.getBlockHash(), true);
        } else if (hasActivePath) {
            if (!usedBlockMap.containsKey(part.getBlockHash())) {
                usedBlockMap.put(part.getBlockHash(), false);
            }
        }
    }

    private BackupSet findSet(BackupFile backupFile) {
        return configuration.getSets().stream().filter(t -> t.inRoot(backupFile.getPath())).findAny().orElse(null);
    }
}
