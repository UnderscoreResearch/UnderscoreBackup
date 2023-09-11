package com.underscoreresearch.backup.manifest;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_BLOCK_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_BLOCK_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_DIRECTORY_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_FILE_PART_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_FILE_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;
import static com.underscoreresearch.backup.utils.SerializationUtils.PUSH_ACTIVE_PATH_READER;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableMap;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.MapSerializer;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.MetadataRepositoryStorage;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.manifest.model.PushActivePath;
import com.underscoreresearch.backup.model.BackupActiveFile;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockAdditional;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupLocation;
import com.underscoreresearch.backup.model.BackupPartialFile;
import com.underscoreresearch.backup.model.BackupPendingSet;
import com.underscoreresearch.backup.model.BackupShare;
import com.underscoreresearch.backup.model.BackupUpdatedFile;
import com.underscoreresearch.backup.model.ExternalBackupFile;

@Slf4j
public class LoggingMetadataRepository implements MetadataRepository, LogConsumer {
    private static final long CURRENT_SPAN = 60 * 1000;
    private final MetadataRepository repository;
    @Getter(AccessLevel.PROTECTED)
    private final ManifestManager manifestManager;
    private final Map<String, BackupShare> shares;
    private final Map<String, LogReader> decoders;
    private final Map<String, PendingActivePath> pendingActivePaths = new HashMap<>();
    private final Set<String> missingActivePaths = new HashSet<>();
    private final ScheduledThreadPoolExecutor activePathSubmitters = new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder().setNameFormat("LoggingMetadataRepository-%d").build());
    private final Map<String, ShareManifestManager> shareManagers;
    @Getter
    @Setter
    private boolean recoveryMode = false;

    public LoggingMetadataRepository(MetadataRepository repository,
                                     ManifestManager manifestManager,
                                     Map<String, BackupShare> shares,
                                     Map<String, ShareManifestManager> shareManagers,
                                     boolean noDeleteReplay) {
        this(repository, manifestManager, shares, shareManagers, 60 * 1000, noDeleteReplay);
    }

    public LoggingMetadataRepository(MetadataRepository repository,
                                     ManifestManager manifestManager,
                                     boolean noDeleteReplay) {
        this(repository, manifestManager, null, null, 60 * 1000, noDeleteReplay);
    }

    public LoggingMetadataRepository(MetadataRepository repository,
                                     ManifestManager manifestManager,
                                     Map<String, BackupShare> shares,
                                     Map<String, ShareManifestManager> shareManagers,
                                     int activePathDelay,
                                     boolean noDeleteReplay) {
        this.repository = repository;
        this.manifestManager = manifestManager;
        this.shares = shares;
        this.shareManagers = shareManagers;

        activePathSubmitters.scheduleAtFixedRate(() -> submitPendingActivePaths(Duration.ofMillis(activePathDelay)),
                Math.min(activePathDelay, 1000), Math.min(activePathDelay, 1000), TimeUnit.MILLISECONDS);

        ImmutableMap.Builder<String, LogReader> decoderBuilder = ImmutableMap.<String, LogReader>builder()
                .put("file", (json) -> repository.addFile(BACKUP_FILE_READER.readValue(json)))
                .put("block", (json) -> repository.addBlock(BACKUP_BLOCK_READER.readValue(json)))
                .put("dir", (json) -> {
                    BackupDirectory dir = BACKUP_DIRECTORY_READER.readValue(json);
                    repository.addDirectory(dir);
                })
                .put("deletePath", (json) -> {
                    PushActivePath activePath = PUSH_ACTIVE_PATH_READER.readValue(json);
                    repository.popActivePath(activePath.getSetId(), activePath.getPath());
                })
                .put("path", (json) -> {
                    PushActivePath activePath = PUSH_ACTIVE_PATH_READER.readValue(json);
                    repository.pushActivePath(activePath.getSetId(), activePath.getPath(), activePath.getActivePath());
                })
                .put("previousFile", (json) -> {
                    if (!recoveryMode) {
                        String lastFile = MAPPER.readValue(json, String.class);
                        if (!lastFile.equals(repository.lastSyncedLogFile(null))) {
                            log.error("Expected previous log file to be {} but got {}, could mean either missing data or backup tampering", lastFile,
                                    repository.lastSyncedLogFile(null));
                        } else {
                            debug(() -> log.debug("Validated previous log file {}", lastFile));
                        }
                    }
                });

        if (noDeleteReplay) {
            decoderBuilder
                    .put("deleteFile", (json) -> {
                    })
                    .put("deletePart", (json) -> {
                    })
                    .put("deleteBlock", (json) -> {
                    })
                    .put("deleteDir", (json) -> {
                    })
                    .put("clear", (json) -> {
                    });
        } else {
            decoderBuilder
                    .put("deleteFile", (json) -> repository.deleteFile(BACKUP_FILE_READER.readValue(json)))
                    .put("deletePart", (json) -> repository.deleteFilePart(BACKUP_FILE_PART_READER.readValue(json)))
                    .put("deleteBlock", (json) -> repository.deleteBlock(BACKUP_BLOCK_READER.readValue(json)))
                    .put("deleteDir", (json) -> {
                        BackupDirectory dir = BACKUP_DIRECTORY_READER.readValue(json);
                        repository.deleteDirectory(dir.getPath(), dir.getAdded());
                    })
                    .put("clear", (json) -> repository.clear());
        }

        decoders = decoderBuilder.build();

        try {
            manifestManager.initialize(this, false);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize manifest manager", e);
        }
    }

    private void submitPendingActivePaths(Duration age) {
        Instant expired = Instant.now().minus(age);

        synchronized (pendingActivePaths) {
            HashSet<String> entriesToRemove = new HashSet<>();

            for (Map.Entry<String, PendingActivePath> entry : pendingActivePaths.entrySet()) {
                try {
                    if (!entry.getValue().getSubmitted().isAfter(expired)) {
                        int ind = entry.getKey().indexOf(PATH_SEPARATOR);
                        String setId = entry.getKey().substring(0, ind);
                        String path = entry.getKey().substring(ind + 1);

                        repository.pushActivePath(setId, path, entry.getValue().getPath());
                        writeLogEntry("path", new PushActivePath(setId, path, entry.getValue().getPath()));
                        missingActivePaths.remove(entry.getKey());
                        entriesToRemove.add(entry.getKey());
                    }
                } catch (IOException e) {
                    log.error("Failed to serialzie pending files", e);
                }
            }

            entriesToRemove.forEach(pendingActivePaths::remove);
        }
    }

    @Override
    public void replayLogEntry(String type, String jsonDefinition) throws IOException {
        decoders.get(type).applyJson(jsonDefinition);
    }

    protected synchronized void writeLogEntry(BaseManifestManager logger, String type, Object obj) {
        try {
            if (obj != null) {
                logger.addLogEntry(type, MAPPER.writeValueAsString(obj));
            } else {
                logger.addLogEntry(type, "");
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to process " + type, e);
        }
    }

    void writeLogEntry(String type, Object obj) {
        writeLogEntry(manifestManager, type, obj);
    }

    @Override
    public void addFile(BackupFile file) throws IOException {
        writeLogEntry("file", file);

        if (shares != null) {
            for (Map.Entry<String, ShareManifestManager> entry : getShareManagers().entrySet()) {
                BackupShare share = shares.get(entry.getKey());
                if (share != null && share.getContents().includeFile(file.getPath())) {
                    try {
                        if (file.getLocations() != null) {
                            for (BackupLocation location : file.getLocations()) {
                                for (BackupFilePart part : location.getParts()) {
                                    shareBlocks(entry.getKey(), entry.getValue(), part.getBlockHash());
                                }
                            }
                        }
                        writeLogEntry(entry.getValue(), "file", file);
                    } catch (Exception e) {
                        log.error("Failed to share file " + PathNormalizer.physicalPath(file.getPath()), e);
                    }
                }
            }
        }

        repository.addFile(file);
    }

    private void shareBlocks(String publicKey, ShareManifestManager shareManager, String blockHash) throws IOException {
        if (BackupBlock.isSuperBlock(blockHash)) {
            BackupBlock block = repository.block(blockHash);
            if (block != null && block.getHashes() != null) {
                for (String partHash : block.getHashes()) {
                    shareBlocks(publicKey, shareManager, partHash);
                }
                writeLogEntry(shareManager, "block", block);
            } else {
                throw new IOException(String.format("Missing superblock %s for share key %s", blockHash, publicKey));
            }
        } else {
            BackupBlockAdditional additional = repository.additionalBlock(publicKey, blockHash);
            if (additional == null) {
                throw new IOException(String.format("Missing block %s for share key %s", blockHash, publicKey));
            }
            if (!additional.isUsed()) {
                additional.setUsed(true);
                BackupBlock block = repository.block(blockHash);
                if (block.isSuperBlock()) {
                    for (String otherHash : block.getHashes())
                        shareBlocks(publicKey, shareManager, otherHash);
                } else if (block.getStorage() != null) {
                    for (BackupBlockStorage storage : block.getStorage())
                        shareManager.addUsedDestinations(storage.getDestination());
                }
                BackupBlock additionalBlock = block.createAdditionalBlock(additional);
                writeLogEntry(shareManager, "block", additionalBlock);

                repository.addAdditionalBlock(additional);
            }
        }
    }

    private Map<String, ShareManifestManager> getShareManagers() {
        if (shareManagers != null) {
            return shareManagers;
        }
        return manifestManager.getActivatedShares();
    }

    @Override
    public String lastSyncedLogFile(String share) throws IOException {
        return repository.lastSyncedLogFile(share);
    }

    @Override
    public void setLastSyncedLogFile(String share, String entry) throws IOException {
        repository.setLastSyncedLogFile(share, entry);
    }

    @Override
    public void addAdditionalBlock(BackupBlockAdditional block) throws IOException {
        repository.addAdditionalBlock(block);
    }

    @Override
    public BackupBlockAdditional additionalBlock(String publicKey, String blockHash) throws IOException {
        return repository.additionalBlock(publicKey, blockHash);
    }

    @Override
    public CloseableLock acquireUpdateLock() {
        return repository.acquireUpdateLock();
    }

    @Override
    public void deleteAdditionalBlock(String publicKey, String blockHash) throws IOException {
        repository.deleteAdditionalBlock(publicKey, blockHash);
    }

    @Override
    public boolean addUpdatedFile(BackupUpdatedFile file, long howOftenMs) throws IOException {
        return repository.addUpdatedFile(file, howOftenMs);
    }

    @Override
    public void removeUpdatedFile(BackupUpdatedFile file) throws IOException {
        repository.removeUpdatedFile(file);
    }

    @Override
    public CloseableStream<BackupUpdatedFile> getUpdatedFiles() throws IOException {
        return repository.getUpdatedFiles();
    }

    @Override
    public void upgradeStorage() throws IOException {
        repository.upgradeStorage();
    }

    @Override
    public MetadataRepositoryStorage createStorageRevision() throws IOException {
        return repository.createStorageRevision();
    }

    @Override
    public void cancelStorageRevision(MetadataRepositoryStorage newStorage) throws IOException {
        repository.cancelStorageRevision(newStorage);
    }

    @Override
    public void installStorageRevision(MetadataRepositoryStorage newStorage) throws IOException {
        repository.installStorageRevision(newStorage);
    }

    @Override
    public boolean isErrorsDetected() {
        return repository.isErrorsDetected();
    }

    @Override
    public void setErrorsDetected(boolean errorsDetected) throws IOException {
        repository.setErrorsDetected(errorsDetected);
    }

    @Override
    public <K, V> CloseableMap<K, V> temporaryMap(MapSerializer<K, V> serializer) throws IOException {
        return repository.temporaryMap(serializer);
    }

    @Override
    public CloseableLock exclusiveLock() throws IOException {
        return repository.exclusiveLock();
    }

    @Override
    public List<ExternalBackupFile> file(String path) throws IOException {
        return repository.file(path);
    }

    @Override
    public BackupFile file(String path, Long timestamp) throws IOException {
        return repository.file(path, timestamp);
    }

    @Override
    public boolean deleteFile(BackupFile file) throws IOException {
        writeLogEntry("deleteFile", file);

        if (shares != null) {
            for (Map.Entry<String, ShareManifestManager> entry : getShareManagers().entrySet()) {
                BackupShare share = shares.get(entry.getKey());
                if (share != null && share.getContents().includeFile(file.getPath())) {
                    writeLogEntry(entry.getValue(), "deleteFile", file);
                }
            }
        }

        return repository.deleteFile(file);
    }

    @Override
    public List<BackupFilePart> existingFilePart(String partHash) throws IOException {
        return repository.existingFilePart(partHash);
    }

    @Override
    public CloseableStream<BackupFile> allFiles(boolean ascending) throws IOException {
        return repository.allFiles(ascending);
    }

    @Override
    public CloseableStream<BackupBlock> allBlocks() throws IOException {
        return repository.allBlocks();
    }

    @Override
    public CloseableStream<BackupBlockAdditional> allAdditionalBlocks() throws IOException {
        return repository.allAdditionalBlocks();
    }

    @Override
    public CloseableStream<BackupFilePart> allFileParts() throws IOException {
        return repository.allFileParts();
    }

    @Override
    public CloseableStream<BackupDirectory> allDirectories(boolean ascending) throws IOException {
        return repository.allDirectories(ascending);
    }

    @Override
    public void addPendingSets(BackupPendingSet scheduledTime) throws IOException {
        repository.addPendingSets(scheduledTime);
    }

    @Override
    public void deletePendingSets(String setId) throws IOException {
        repository.deletePendingSets(setId);
    }

    @Override
    public Set<BackupPendingSet> getPendingSets() throws IOException {
        return repository.getPendingSets();
    }

    @Override
    public CloseableLock acquireLock() {
        return repository.acquireLock();
    }

    @Override
    public long getBlockCount() throws IOException {
        return repository.getBlockCount();
    }

    @Override
    public long getFileCount() throws IOException {
        return repository.getFileCount();
    }

    @Override
    public long getDirectoryCount() throws IOException {
        return repository.getDirectoryCount();
    }

    @Override
    public long getPartCount() throws IOException {
        return repository.getPartCount();
    }

    @Override
    public void clear() throws IOException {
        writeLogEntry("clear", null);
        repository.clear();
    }

    @Override
    public boolean deleteFilePart(BackupFilePart filePart) throws IOException {
        writeLogEntry("deletePart", filePart);
        return repository.deleteFilePart(filePart);
    }

    @Override
    public void addBlock(BackupBlock block) throws IOException {
        if (block.getStorage() != null && block.getStorage().size() > 0
                && block.getStorage().get(0).hasAdditionalStorageProperties()) {
            Map<String, BackupBlockAdditional> additionalBlocks = new HashMap<>();
            for (BackupBlockStorage storage : block.getStorage()) {
                for (Map.Entry<EncryptionKey, Map<String, String>> entry
                        : storage.getAdditionalStorageProperties().entrySet()) {
                    BackupBlockAdditional additional = additionalBlocks.computeIfAbsent(entry.getKey().getPublicKey(),
                            (key) -> BackupBlockAdditional.builder().used(false).publicKey(key).hash(block.getHash())
                                    .properties(new ArrayList<>())
                                    .build());
                    additional.getProperties().add(entry.getValue());
                }
            }

            for (BackupBlockAdditional blockAdditional : additionalBlocks.values()) {
                if (blockAdditional.getProperties().size() != block.getStorage().size()) {
                    throw new RuntimeException("Internal mismatch between block and additional block storage size");
                }
                BackupBlockAdditional existing = repository.additionalBlock(blockAdditional.getPublicKey(), blockAdditional.getHash());
                if (existing != null && existing.isUsed()) {
                    blockAdditional.setUsed(true);

                    BackupBlock newBlock = block.createAdditionalBlock(blockAdditional);
                    ShareManifestManager logWriter = getShareManagers().get(blockAdditional.getPublicKey());
                    if (logWriter == null)
                        throw new RuntimeException(String.format("Unknown log writer for public key share %s",
                                blockAdditional.getPublicKey()));
                    writeLogEntry(logWriter, "block", newBlock);
                }
                repository.addAdditionalBlock(blockAdditional);
            }
        }
        writeLogEntry("block", block);
        repository.addBlock(block);
    }

    @Override
    public BackupBlock block(String hash) throws IOException {
        return repository.block(hash);
    }

    @Override
    public boolean deleteBlock(BackupBlock block) throws IOException {
        writeLogEntry("deleteBlock", block);

        for (Map.Entry<String, ShareManifestManager> entry : getShareManagers().entrySet()) {
            BackupBlockAdditional additional = repository.additionalBlock(entry.getKey(), block.getHash());
            if (additional != null) {
                if (additional.isUsed()) {
                    BackupBlock additonalBlock = BackupBlock.builder().hash(block.getHash()).build();
                    entry.getValue().addLogEntry("deleteBlock", BACKUP_BLOCK_WRITER.writeValueAsString(additonalBlock));
                }
                repository.deleteAdditionalBlock(entry.getKey(), block.getHash());
            }
        }

        return repository.deleteBlock(block);
    }

    @Override
    public void addTemporaryBlock(BackupBlock block) throws IOException {
        repository.addTemporaryBlock(block);
    }

    @Override
    public void installTemporaryBlocks() throws IOException {
        repository.installTemporaryBlocks();
    }

    @Override
    public void addDirectory(BackupDirectory directory) throws IOException {
        BackupDirectory currentData;
        if (Instant.now().toEpochMilli() - directory.getAdded() < CURRENT_SPAN)
            currentData = repository.directory(directory.getPath(), null, false);
        else {
            currentData = repository.directory(directory.getPath(), directory.getAdded(), false);
        }

        // Need share implementation

        if (currentData == null || !directory.getFiles().equals(currentData.getFiles())) {
            if (shares != null) {
                for (Map.Entry<String, ShareManifestManager> entry : getShareManagers().entrySet()) {
                    BackupShare share = shares.get(entry.getKey());
                    String parent = directory.getPath();
                    if (!parent.endsWith(PATH_SEPARATOR))
                        parent += PATH_SEPARATOR;

                    if (share != null && share.getContents().includeForShare(parent)) {
                        NavigableSet<String> newContents = new TreeSet<>();
                        for (String file : directory.getFiles()) {
                            if (share.getContents().includeForShare(PathNormalizer.combinePaths(parent, file)))
                                newContents.add(file);
                        }
                        if (newContents.size() > 0) {
                            writeLogEntry(entry.getValue(), "dir", directory.toBuilder().files(newContents).build());
                        }
                    }
                }
            }

            writeLogEntry("dir", directory);
            repository.addDirectory(directory);
        }
    }

    @Override
    public BackupDirectory directory(String path, Long timestamp, boolean accumulative) throws IOException {
        return repository.directory(path, timestamp, accumulative);
    }

    @Override
    public boolean deleteDirectory(String path, long timestamp) throws IOException {
        BackupDirectory deletedDir = new BackupDirectory(path, timestamp, null);
        if (shares != null) {
            for (Map.Entry<String, ShareManifestManager> entry : getShareManagers().entrySet()) {
                BackupShare share = shares.get(entry.getKey());
                if (share != null && share.getContents().includeForShare(path)) {
                    writeLogEntry(entry.getValue(), "deleteDir", deletedDir);
                }
            }
        }
        writeLogEntry("deleteDir", deletedDir);
        return repository.deleteDirectory(path, timestamp);
    }

    @Override
    public void pushActivePath(String setId, String path, BackupActivePath pendingFiles) throws IOException {
        synchronized (pendingActivePaths) {
            String fullPath = setId + PATH_SEPARATOR + path;

            if (!pendingActivePaths.containsKey(fullPath) && !repository.hasActivePath(setId, path))
                missingActivePaths.add(fullPath);
            pendingActivePaths.put(fullPath, new PendingActivePath(new BackupActivePath(path,
                    pendingFiles
                            .getFiles()
                            .stream()
                            .map((file) -> new BackupActiveFile(file.getPath(), file.getStatus()))
                            .collect(Collectors.toSet()))));
        }
    }

    @Override
    public boolean hasActivePath(String setId, String path) throws IOException {
        return repository.hasActivePath(setId, path);
    }

    @Override
    public void popActivePath(String setId, String path) throws IOException {
        String fullPath = setId + PATH_SEPARATOR + path;
        synchronized (pendingActivePaths) {
            pendingActivePaths.remove(fullPath);
            if (!missingActivePaths.remove(fullPath)) {
                repository.popActivePath(setId, path);
                writeLogEntry("deletePath", new PushActivePath(setId, path, null));
            }
        }
    }

    @Override
    public boolean deletePartialFile(BackupPartialFile file) throws IOException {
        return repository.deletePartialFile(file);
    }

    @Override
    public void savePartialFile(BackupPartialFile file) throws IOException {
        repository.savePartialFile(file);
    }

    @Override
    public void clearPartialFiles() throws IOException {
        repository.clearPartialFiles();
    }

    @Override
    public BackupPartialFile getPartialFile(BackupPartialFile file) throws IOException {
        return repository.getPartialFile(file);
    }

    @Override
    public TreeMap<String, BackupActivePath> getActivePaths(String setId) throws IOException {
        flushActivePaths();
        return repository.getActivePaths(setId);
    }

    private void flushActivePaths() {
        synchronized (pendingActivePaths) {
            submitPendingActivePaths(Duration.ofMillis(0));
            pendingActivePaths.clear();
            missingActivePaths.clear();
        }
    }

    @Override
    public void flushLogging() throws IOException {
        flushActivePaths();

        repository.flushLogging();
    }

    @Override
    public void open(boolean readOnly) throws IOException {
        repository.open(readOnly);
    }

    @Override
    public void close() throws IOException {
        flushLogging();
        repository.close();
        activePathSubmitters.shutdownNow();
    }

    private interface LogReader {
        void applyJson(String json) throws IOException;
    }

    public static class Readonly extends LoggingMetadataRepository {
        public Readonly(MetadataRepository repository,
                        ManifestManager manifestManager,
                        boolean noDeleteReplay) {
            super(repository, manifestManager, null, null, noDeleteReplay);
        }

        @Override
        protected synchronized void writeLogEntry(String type, Object obj) {
            throw new RuntimeException("Tried to write to a read only repository");
        }
    }

    @Data
    private static class PendingActivePath {
        private BackupActivePath path;
        private Instant submitted;

        public PendingActivePath(BackupActivePath path) {
            this.path = path;
            submitted = Instant.now();
        }
    }
}
