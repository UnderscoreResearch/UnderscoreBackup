package com.underscoreresearch.backup.manifest.implementation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableMap;
import com.underscoreresearch.backup.file.CloseableSortedMap;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.LogFileRepository;
import com.underscoreresearch.backup.file.MapSerializer;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.MetadataRepositoryStorage;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.file.RepositoryOpenMode;
import com.underscoreresearch.backup.manifest.BaseManifestManager;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.ShareManifestManager;
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
import com.underscoreresearch.backup.utils.SingleTaskScheduler;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.utils.log.LogUtil.debug;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_BLOCK_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_BLOCK_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_DIRECTORY_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_FILE_PART_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_FILE_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_PENDING_SET_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;
import static com.underscoreresearch.backup.utils.SerializationUtils.PUSH_ACTIVE_PATH_READER;

/**
 * A metadata repository implementation that logs all changes.
 * This class wraps another metadata repository and logs all changes to a log file repository.
 * It's used to track changes to the backup metadata for recovery and synchronization purposes.
 */
@Slf4j
public class LoggingMetadataRepository implements MetadataRepository, LogConsumer {
    private final MetadataRepository repository;
    @Getter(AccessLevel.PROTECTED)
    private final ManifestManager manifestManager;
    private final Map<String, BackupShare> shares;
    private final Map<String, LogReader> decoders;
    private final Map<String, PendingActivePath> pendingActivePaths = new HashMap<>();
    private final Set<String> missingActivePaths = new HashSet<>();
    private final SingleTaskScheduler activePathSubmitters = new SingleTaskScheduler("LoggingMetadataRepository");
    private final Map<String, ShareManifestManager> shareManagers;
    @Getter
    @Setter
    private boolean recoveryMode = false;

    /**
     * Constructor for LoggingMetadataRepository with default active path delay.
     *
     * @param repository The underlying metadata repository
     * @param manifestManager The manifest manager for handling log entries
     * @param shares Map of share IDs to backup shares
     * @param shareManagers Map of share IDs to share manifest managers
     * @param noDeleteReplay Whether to ignore delete operations during replay
     */
    public LoggingMetadataRepository(MetadataRepository repository,
                                     ManifestManager manifestManager,
                                     Map<String, BackupShare> shares,
                                     Map<String, ShareManifestManager> shareManagers,
                                     boolean noDeleteReplay) {
        this(repository, manifestManager, shares, shareManagers, 60 * 1000, noDeleteReplay);
    }

    /**
     * Constructor for LoggingMetadataRepository without shares.
     *
     * @param repository The underlying metadata repository
     * @param manifestManager The manifest manager for handling log entries
     * @param noDeleteReplay Whether to ignore delete operations during replay
     */
    public LoggingMetadataRepository(MetadataRepository repository,
                                     ManifestManager manifestManager,
                                     boolean noDeleteReplay) {
        this(repository, manifestManager, null, null, 60 * 1000, noDeleteReplay);
    }

    /**
     * Main constructor for LoggingMetadataRepository.
     *
     * @param repository The underlying metadata repository
     * @param manifestManager The manifest manager for handling log entries
     * @param shares Map of share IDs to backup shares
     * @param shareManagers Map of share IDs to share manifest managers
     * @param activePathDelay Delay in milliseconds before submitting active paths
     * @param noDeleteReplay Whether to ignore delete operations during replay
     */
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
                            log.warn("Expected previous log file to be \"{}\" but got \"{}\", could mean either missing data or backup tampering", lastFile,
                                    repository.lastSyncedLogFile(null));
                        } else {
                            debug(() -> log.debug("Validated previous log file \"{}\"", lastFile));
                        }
                    }
                })
                .put("pendingSet", (json) -> repository.addPendingSets(BACKUP_PENDING_SET_READER.readValue(json)));

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
                    })
                    .put("deletePendingSet", (json) -> {
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
                    .put("clear", (json) -> repository.clear())
                    .put("deletePendingSet", (json) -> {
                        BackupPendingSet set = BACKUP_PENDING_SET_READER.readValue(json);
                        repository.deletePendingSets(set.getSetId());
                    });
        }

        decoders = decoderBuilder.build();

        try {
            manifestManager.initialize(this, false);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize manifest manager", e);
        }
    }

    /**
     * Submits pending active paths that have been waiting longer than the specified age.
     *
     * @param age The minimum age of paths to submit
     */
    private void submitPendingActivePaths(Duration age) {
        Instant expired = Instant.now().minus(age);

        try (CloseableLock ignored = acquireLock()) {
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

    /**
     * Replays a log entry by applying it to the repository.
     *
     * @param type The type of log entry
     * @param jsonDefinition The JSON definition of the log entry
     * @throws IOException If there's an error processing the log entry
     */
    @Override
    public void replayLogEntry(String type, String jsonDefinition) throws IOException {
        decoders.get(type).applyJson(jsonDefinition);
    }

    /**
     * Writes a log entry to the specified manifest manager.
     *
     * @param logger The manifest manager to write the log entry to
     * @param type The type of log entry
     * @param obj The object to serialize and write
     */
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

    /**
     * Writes a log entry to the default manifest manager.
     *
     * @param type The type of log entry
     * @param obj The object to serialize and write
     */
    void writeLogEntry(String type, Object obj) {
        writeLogEntry(manifestManager, type, obj);
    }

    /**
     * Adds a file to the repository and logs the operation.
     * Also handles sharing the file with other shares if applicable.
     *
     * @param file The file to add
     * @throws IOException If there's an error adding the file
     */
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
                        log.error("Failed to share file \"" + PathNormalizer.physicalPath(file.getPath()) + "\"", e);
                    }
                }
            }
        }

        repository.addFile(file);
    }

    /**
     * Shares blocks with a specific share.
     * Recursively processes superblocks to share all contained blocks.
     *
     * @param publicKey The public key of the share
     * @param shareManager The share manifest manager
     * @param blockHash The hash of the block to share
     * @throws IOException If there's an error sharing the block
     */
    private void shareBlocks(String publicKey, ShareManifestManager shareManager, String blockHash) throws IOException {
        if (BackupBlock.isSuperBlock(blockHash)) {
            BackupBlock block = repository.block(blockHash);
            if (block != null && block.getHashes() != null) {
                for (String partHash : block.getHashes()) {
                    shareBlocks(publicKey, shareManager, partHash);
                }
                writeLogEntry(shareManager, "block", block);
            } else {
                throw new IOException(String.format("Missing superblock \"%s\" for share key \"%s\"", blockHash, publicKey));
            }
        } else {
            BackupBlockAdditional additional = repository.additionalBlock(publicKey, blockHash);
            if (additional == null) {
                throw new IOException(String.format("Missing block \"%s\" for share key \"%s\"", blockHash, publicKey));
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

    /**
     * Gets the share manifest managers.
     * Returns the shareManagers field if not null, otherwise gets activated shares from the manifest manager.
     *
     * @return Map of share IDs to share manifest managers
     */
    private Map<String, ShareManifestManager> getShareManagers() {
        if (shareManagers != null) {
            return shareManagers;
        }
        return manifestManager.getActivatedShares();
    }

    /**
     * Gets the last synced log file for a share.
     *
     * @param share The share ID, or null for the main repository
     * @return The last synced log file
     * @throws IOException If there's an error retrieving the log file
     */
    @Override
    public String lastSyncedLogFile(String share) throws IOException {
        return repository.lastSyncedLogFile(share);
    }

    /**
     * Sets the last synced log file for a share.
     *
     * @param share The share ID, or null for the main repository
     * @param entry The log file entry
     * @throws IOException If there's an error setting the log file
     */
    @Override
    public void setLastSyncedLogFile(String share, String entry) throws IOException {
        repository.setLastSyncedLogFile(share, entry);
    }

    /**
     * Gets the metadata repository.
     * This implementation returns itself since it is a metadata repository.
     *
     * @return This metadata repository
     */
    @Override
    public MetadataRepository getMetadataRepository() {
        return this;
    }

    /**
     * Adds an additional block to the repository.
     *
     * @param block The additional block to add
     * @throws IOException If there's an error adding the block
     */
    @Override
    public void addAdditionalBlock(BackupBlockAdditional block) throws IOException {
        repository.addAdditionalBlock(block);
    }

    /**
     * Retrieves an additional block from the repository.
     *
     * @param publicKey The public key associated with the block
     * @param blockHash The hash of the block
     * @return The additional block, or null if not found
     * @throws IOException If there's an error retrieving the block
     */
    @Override
    public BackupBlockAdditional additionalBlock(String publicKey, String blockHash) throws IOException {
        return repository.additionalBlock(publicKey, blockHash);
    }

    /**
     * Acquires an update lock for the repository.
     *
     * @return A closeable lock
     */
    @Override
    public CloseableLock acquireUpdateLock() {
        return repository.acquireUpdateLock();
    }

    /**
     * Deletes an additional block from the repository.
     *
     * @param publicKey The public key associated with the block
     * @param blockHash The hash of the block
     * @throws IOException If there's an error deleting the block
     */
    @Override
    public void deleteAdditionalBlock(String publicKey, String blockHash) throws IOException {
        repository.deleteAdditionalBlock(publicKey, blockHash);
    }

    /**
     * Adds an updated file to the repository.
     *
     * @param file The updated file to add
     * @param howOftenMs How often the file should be updated (in milliseconds)
     * @return true if the file was added, false if it was already up to date
     * @throws IOException If there's an error adding the file
     */
    @Override
    public boolean addUpdatedFile(BackupUpdatedFile file, long howOftenMs) throws IOException {
        return repository.addUpdatedFile(file, howOftenMs);
    }

    /**
     * Removes an updated file from the repository.
     *
     * @param file The updated file to remove
     * @throws IOException If there's an error removing the file
     */
    @Override
    public void removeUpdatedFile(BackupUpdatedFile file) throws IOException {
        repository.removeUpdatedFile(file);
    }

    /**
     * Gets all updated files from the repository.
     *
     * @return A closeable stream of updated files
     * @throws IOException If there's an error retrieving the files
     */
    @Override
    public CloseableStream<BackupUpdatedFile> getUpdatedFiles() throws IOException {
        return repository.getUpdatedFiles();
    }

    /**
     * Performs a repository upgrade if one is needed.
     *
     * @throws IOException If there's an error upgrading the repository
     */
    @Override
    public void upgradeStorage() throws IOException {
        repository.upgradeStorage();
    }

    /**
     * Creates a new storage revision.
     *
     * @return The new storage instance
     * @throws IOException If there's an error creating the storage revision
     */
    @Override
    public MetadataRepositoryStorage createStorageRevision() throws IOException {
        return repository.createStorageRevision();
    }

    /**
     * Cancels a storage revision.
     *
     * @param newStorage The new storage to cancel
     * @throws IOException If there's an error canceling the storage revision
     */
    @Override
    public void cancelStorageRevision(MetadataRepositoryStorage newStorage) throws IOException {
        repository.cancelStorageRevision(newStorage);
    }

    /**
     * Installs a storage revision.
     *
     * @param newStorage The new storage to install
     * @throws IOException If there's an error installing the storage revision
     */
    @Override
    public void installStorageRevision(MetadataRepositoryStorage newStorage) throws IOException {
        repository.installStorageRevision(newStorage);
    }

    /**
     * Gets the configuration hash from the repository.
     *
     * @return The configuration hash
     * @throws IOException If there's an error retrieving the hash
     */
    @Override
    public String getConfigurationHash() throws IOException {
        return repository.getConfigurationHash();
    }

    /**
     * Sets the configuration hash in the repository.
     *
     * @param hash The configuration hash to set
     * @throws IOException If there's an error setting the hash
     */
    @Override
    public void setConfigurationHash(String hash) throws IOException {
        repository.setConfigurationHash(hash);
    }

    /**
     * Checks if errors were detected in the repository.
     *
     * @return true if errors were detected, false otherwise
     */
    @Override
    public boolean isErrorsDetected() {
        return repository.isErrorsDetected();
    }

    /**
     * Sets whether errors were detected in the repository.
     *
     * @param errorsDetected true if errors were detected, false otherwise
     * @throws IOException If there's an error setting the errors detected flag
     */
    @Override
    public void setErrorsDetected(boolean errorsDetected) throws IOException {
        repository.setErrorsDetected(errorsDetected);
    }

    /**
     * Creates a temporary map for storing key-value pairs.
     *
     * @param serializer The serializer for the map keys and values
     * @param <K> The key type
     * @param <V> The value type
     * @return A closeable map
     * @throws IOException If there's an error creating the map
     */
    @Override
    public <K, V> CloseableMap<K, V> temporaryMap(MapSerializer<K, V> serializer) throws IOException {
        return repository.temporaryMap(serializer);
    }

    /**
     * Creates a temporary sorted map for storing key-value pairs.
     *
     * @param serializer The serializer for the map keys and values
     * @param <K> The key type
     * @param <V> The value type
     * @return A closeable sorted map
     * @throws IOException If there's an error creating the map
     */
    @Override
    public <K, V> CloseableSortedMap<K, V> temporarySortedMap(MapSerializer<K, V> serializer) throws IOException {
        return repository.temporarySortedMap(serializer);
    }

    /**
     * Acquires an exclusive lock on the repository.
     *
     * @return A closeable lock
     * @throws IOException If there's an error acquiring the lock
     */
    @Override
    public CloseableLock exclusiveLock() throws IOException {
        return repository.exclusiveLock();
    }

    /**
     * Compacts the repository.
     *
     * @throws IOException If there's an error compacting the repository
     */
    @Override
    public void compact() throws IOException {
        repository.compact();
    }

    /**
     * Retrieves all files with the specified path.
     *
     * @param path The path to search for
     * @return A list of external backup files matching the path, or null if none found
     * @throws IOException If there's an error retrieving the files
     */
    @Override
    public List<ExternalBackupFile> file(String path) throws IOException {
        return repository.file(path);
    }

    /**
     * Retrieves a file by path and timestamp.
     *
     * @param path The path of the file
     * @param timestamp The timestamp to match, or null for the latest version
     * @return The backup file, or null if not found
     * @throws IOException If there's an error retrieving the file
     */
    @Override
    public BackupFile file(String path, Long timestamp) throws IOException {
        return repository.file(path, timestamp);
    }

    /**
     * Deletes a file from the repository and logs the operation.
     * Also handles removing the file from shares if applicable.
     *
     * @param file The file to delete
     * @return true if the file was deleted, false if it was not found
     * @throws IOException If there's an error deleting the file
     */
    @Override
    public boolean deleteFile(BackupFile file) throws IOException {
        BackupFile deletedFile = BackupFile.builder().path(file.getPath()).added(file.getAdded()).build();
        writeLogEntry("deleteFile", deletedFile);

        if (shares != null) {
            for (Map.Entry<String, ShareManifestManager> entry : getShareManagers().entrySet()) {
                BackupShare share = shares.get(entry.getKey());
                if (share != null && share.getContents().includeFile(file.getPath())) {
                    writeLogEntry(entry.getValue(), "deleteFile", deletedFile);
                }
            }
        }

        return repository.deleteFile(file);
    }

    /**
     * Retrieves all file parts with the specified hash.
     *
     * @param partHash The hash to search for
     * @return A list of backup file parts matching the hash, or null if none found
     * @throws IOException If there's an error retrieving the file parts
     */
    @Override
    public List<BackupFilePart> existingFilePart(String partHash) throws IOException {
        return repository.existingFilePart(partHash);
    }

    /**
     * Retrieves all files in the repository.
     *
     * @param ascending Whether to return files in ascending order
     * @return A closeable stream of backup files
     * @throws IOException If there's an error retrieving the files
     */
    @Override
    public CloseableStream<BackupFile> allFiles(boolean ascending) throws IOException {
        return repository.allFiles(ascending);
    }

    /**
     * Retrieves all blocks in the repository.
     *
     * @return A closeable stream of backup blocks
     * @throws IOException If there's an error retrieving the blocks
     */
    @Override
    public CloseableStream<BackupBlock> allBlocks() throws IOException {
        return repository.allBlocks();
    }

    /**
     * Retrieves all additional blocks in the repository.
     *
     * @return A closeable stream of backup additional blocks
     * @throws IOException If there's an error retrieving the additional blocks
     */
    @Override
    public CloseableStream<BackupBlockAdditional> allAdditionalBlocks() throws IOException {
        return repository.allAdditionalBlocks();
    }

    /**
     * Retrieves all file parts in the repository.
     *
     * @return A closeable stream of backup file parts
     * @throws IOException If there's an error retrieving the file parts
     */
    @Override
    public CloseableStream<BackupFilePart> allFileParts() throws IOException {
        return repository.allFileParts();
    }

    /**
     * Retrieves all directories in the repository.
     *
     * @param ascending Whether to return directories in ascending order
     * @return A closeable stream of backup directories
     * @throws IOException If there's an error retrieving the directories
     */
    @Override
    public CloseableStream<BackupDirectory> allDirectories(boolean ascending) throws IOException {
        return repository.allDirectories(ascending);
    }

    /**
     * Adds a pending set to the repository and logs the operation.
     *
     * @param scheduledTime The scheduled time information
     * @throws IOException If there's an error adding the pending set
     */
    @Override
    public void addPendingSets(BackupPendingSet scheduledTime) throws IOException {
        writeLogEntry("pendingSet", scheduledTime);
        repository.addPendingSets(scheduledTime);
    }

    /**
     * Deletes a pending set from the repository and logs the operation.
     *
     * @param setId The ID of the set to delete
     * @throws IOException If there's an error deleting the pending set
     */
    @Override
    public void deletePendingSets(String setId) throws IOException {
        writeLogEntry("deletePendingSet", BackupPendingSet.builder().setId(setId).build());
        repository.deletePendingSets(setId);
    }

    /**
     * Gets all pending sets from the repository.
     *
     * @return A set of all pending backup sets
     * @throws IOException If there's an error retrieving the pending sets
     */
    @Override
    public Set<BackupPendingSet> getPendingSets() throws IOException {
        return repository.getPendingSets();
    }

    /**
     * Acquires a repository lock.
     *
     * @return A closeable lock
     */
    @Override
    public CloseableLock acquireLock() {
        return repository.acquireLock();
    }

    /**
     * Gets the total number of blocks in the repository.
     *
     * @return The block count
     * @throws IOException If there's an error retrieving the count
     */
    @Override
    public long getBlockCount() throws IOException {
        return repository.getBlockCount();
    }

    /**
     * Gets the total number of files in the repository.
     *
     * @return The file count
     * @throws IOException If there's an error retrieving the count
     */
    @Override
    public long getFileCount() throws IOException {
        return repository.getFileCount();
    }

    /**
     * Gets the total number of directories in the repository.
     *
     * @return The directory count
     * @throws IOException If there's an error retrieving the count
     */
    @Override
    public long getDirectoryCount() throws IOException {
        return repository.getDirectoryCount();
    }

    /**
     * Gets the total number of file parts in the repository.
     *
     * @return The part count
     * @throws IOException If there's an error retrieving the count
     */
    @Override
    public long getPartCount() throws IOException {
        return repository.getPartCount();
    }

    /**
     * Clears all data from the repository and logs the operation.
     *
     * @throws IOException If there's an error clearing the repository
     */
    @Override
    public void clear() throws IOException {
        writeLogEntry("clear", null);
        repository.clear();
    }

    /**
     * Deletes a file part from the repository and logs the operation.
     *
     * @param filePart The file part to delete
     * @return true if the file part was deleted, false if it was not found
     * @throws IOException If there's an error deleting the file part
     */
    @Override
    public boolean deleteFilePart(BackupFilePart filePart) throws IOException {
        writeLogEntry("deletePart", filePart);
        return repository.deleteFilePart(filePart);
    }

    /**
     * Adds a block to the repository and logs the operation.
     * Also handles additional block properties for shares if applicable.
     *
     * @param block The block to add
     * @throws IOException If there's an error adding the block
     */
    @Override
    public void addBlock(BackupBlock block) throws IOException {
        if (block.getStorage() != null && !block.getStorage().isEmpty()
                && block.getStorage().get(0).hasAdditionalStorageProperties()) {
            Map<String, BackupBlockAdditional> additionalBlocks = new HashMap<>();
            for (BackupBlockStorage storage : block.getStorage()) {
                for (Map.Entry<IdentityKeys, Map<String, String>> entry
                        : storage.getAdditionalStorageProperties().entrySet()) {
                    BackupBlockAdditional additional = additionalBlocks.computeIfAbsent(entry.getKey().getKeyIdentifier(),
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

    /**
     * Retrieves a block by its hash.
     *
     * @param hash The hash of the block to retrieve
     * @return The backup block, or null if not found
     * @throws IOException If there's an error retrieving the block
     */
    @Override
    public BackupBlock block(String hash) throws IOException {
        return repository.block(hash);
    }

    /**
     * Deletes a block from the repository and logs the operation.
     * Also handles removing the block from shares if applicable.
     *
     * @param block The block to delete
     * @return true if the block was deleted, false if it was not found
     * @throws IOException If there's an error deleting the block
     */
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

    /**
     * Adds a temporary block to the repository.
     * Temporary blocks are stored separately until they are installed.
     *
     * @param block The block to add temporarily
     * @throws IOException If there's an error adding the temporary block
     */
    @Override
    public void addTemporaryBlock(BackupBlock block) throws IOException {
        repository.addTemporaryBlock(block);
    }

    /**
     * Installs all temporary blocks into the main repository.
     * This makes temporary blocks permanent and available for normal operations.
     *
     * @throws IOException If there's an error installing the temporary blocks
     */
    @Override
    public void installTemporaryBlocks() throws IOException {
        repository.installTemporaryBlocks();
    }

    /**
     * Adds a directory to the repository and logs the operation.
     * Also handles sharing the directory with other shares if applicable.
     * Only adds the directory if it's different from the existing one.
     *
     * @param directory The directory to add
     * @throws IOException If there's an error adding the directory
     */
    @Override
    public void addDirectory(BackupDirectory directory) throws IOException {
        BackupDirectory currentData = repository.directory(directory.getPath(), directory.getAdded(), false);

        if (currentData == null || !directory.getFiles().equals(currentData.getFiles()) ||
                ((directory.getDeleted() == null) != (currentData.getDeleted() == null))) {
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
                        writeLogEntry(entry.getValue(), "dir", directory.toBuilder().files(newContents).build());
                    }
                }
            }

            writeLogEntry("dir", directory);
            repository.addDirectory(directory);
        }
    }

    /**
     * Retrieves a directory by path and timestamp.
     *
     * @param path The path of the directory
     * @param timestamp The timestamp to match, or null for the latest version
     * @param accumulative Whether to accumulate files from earlier directory versions
     * @return The backup directory, or null if not found
     * @throws IOException If there's an error retrieving the directory
     */
    @Override
    public BackupDirectory directory(String path, Long timestamp, boolean accumulative) throws IOException {
        return repository.directory(path, timestamp, accumulative);
    }

    /**
     * Deletes a directory from the repository and logs the operation.
     * Also handles removing the directory from shares if applicable.
     *
     * @param path The path of the directory to delete
     * @param timestamp The timestamp of the directory to delete
     * @return true if the directory was deleted, false if it was not found
     * @throws IOException If there's an error deleting the directory
     */
    @Override
    public boolean deleteDirectory(String path, long timestamp) throws IOException {
        BackupDirectory deletedDir = new BackupDirectory(path, timestamp, null, null, null);
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

    /**
     * Pushes an active path to the repository.
     * Active paths represent directories that are currently being processed for backup.
     *
     * @param setId The ID of the backup set
     * @param path The path to push
     * @param pendingFiles The active path information containing pending files
     * @throws IOException If there's an error pushing the active path
     */
    @Override
    public void pushActivePath(String setId, String path, BackupActivePath pendingFiles) throws IOException {
        try (CloseableLock ignored = acquireLock()) {
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

    /**
     * Checks if an active path exists for the specified set ID and path.
     *
     * @param setId The ID of the backup set
     * @param path The path to check
     * @return true if the active path exists, false otherwise
     * @throws IOException If there's an error checking for the active path
     */
    @Override
    public boolean hasActivePath(String setId, String path) throws IOException {
        return repository.hasActivePath(setId, path);
    }

    /**
     * Removes an active path from the repository and logs the operation.
     * This indicates that processing for the specified path is complete.
     *
     * @param setId The ID of the backup set
     * @param path The path to remove
     * @throws IOException If there's an error removing the active path
     */
    @Override
    public void popActivePath(String setId, String path) throws IOException {
        String fullPath = setId + PATH_SEPARATOR + path;
        try (CloseableLock ignore = acquireLock()) {
            pendingActivePaths.remove(fullPath);
            if (!missingActivePaths.remove(fullPath)) {
                repository.popActivePath(setId, path);
                writeLogEntry("deletePath", new PushActivePath(setId, path, null));
            }
        }
    }

    /**
     * Deletes a partial file from the repository.
     * Partial files represent files that are in the process of being backed up.
     *
     * @param file The partial file to delete
     * @return true if the partial file was deleted, false if it was not found
     * @throws IOException If there's an error deleting the partial file
     */
    @Override
    public boolean deletePartialFile(BackupPartialFile file) throws IOException {
        return repository.deletePartialFile(file);
    }

    /**
     * Saves a partial file to the repository.
     * This stores information about a file that is in the process of being backed up.
     *
     * @param file The partial file to save
     * @throws IOException If there's an error saving the partial file
     */
    @Override
    public void savePartialFile(BackupPartialFile file) throws IOException {
        repository.savePartialFile(file);
    }

    /**
     * Clears all partial files from the repository.
     * This removes all information about files that are in the process of being backed up.
     *
     * @throws IOException If there's an error clearing the partial files
     */
    @Override
    public void clearPartialFiles() throws IOException {
        repository.clearPartialFiles();
    }

    /**
     * Gets the log file repository associated with this metadata repository.
     * The log file repository is used to store and retrieve log entries.
     *
     * @return The log file repository
     * @throws IOException If there's an error retrieving the log file repository
     */
    @Override
    public LogFileRepository getLogFileRepository() throws IOException {
        return repository.getLogFileRepository();
    }

    /**
     * Retrieves a partial file from the repository.
     * This gets information about a file that is in the process of being backed up.
     *
     * @param file The partial file to retrieve (only path and timestamp are used for lookup)
     * @return The complete partial file information, or null if not found
     * @throws IOException If there's an error retrieving the partial file
     */
    @Override
    public BackupPartialFile getPartialFile(BackupPartialFile file) throws IOException {
        return repository.getPartialFile(file);
    }

    /**
     * Gets all active paths for a specific backup set.
     * This method flushes any pending active paths before retrieving the results.
     *
     * @param setId The ID of the backup set
     * @return A map of paths to active path information
     * @throws IOException If there's an error retrieving the active paths
     */
    @Override
    public TreeMap<String, BackupActivePath> getActivePaths(String setId) throws IOException {
        flushActivePaths();
        return repository.getActivePaths(setId);
    }

    /**
     * Flushes all pending active paths to the repository.
     * This ensures that any active paths that have been queued are immediately processed.
     */
    private void flushActivePaths() {
        try (CloseableLock ignored = acquireLock()) {
            submitPendingActivePaths(Duration.ofMillis(0));
            pendingActivePaths.clear();
            missingActivePaths.clear();
        }
    }

    /**
     * Flushes all logging operations to ensure they are written to storage.
     * This method flushes active paths and then delegates to the underlying repository.
     *
     * @throws IOException If there's an error flushing the logs
     */
    @Override
    public void flushLogging() throws IOException {
        flushActivePaths();

        repository.flushLogging();
    }

    /**
     * Opens the repository with the specified mode.
     * This initializes the repository for use.
     *
     * @param openMode The mode to open the repository in (e.g., read-only, read-write)
     * @throws IOException If there's an error opening the repository
     */
    @Override
    public void open(RepositoryOpenMode openMode) throws IOException {
        repository.open(openMode);
    }

    /**
     * Closes the repository and releases any resources.
     * This method flushes all logs and shuts down the active path submitter.
     *
     * @throws IOException If there's an error closing the repository
     */
    @Override
    public void close() throws IOException {
        flushLogging();
        repository.close();
        activePathSubmitters.shutdownNow();
    }

    /**
     * Interface for log entry readers that can apply JSON data to the repository.
     * This is used to replay log entries during recovery.
     */
    private interface LogReader {
        /**
         * Applies a JSON log entry to the repository.
         *
         * @param json The JSON data to apply
         * @throws IOException If there's an error processing the JSON
         */
        void applyJson(String json) throws IOException;
    }

    /**
     * Read-only implementation of LoggingMetadataRepository.
     * This class overrides the writeLogEntry method to prevent any write operations.
     */
    public static class Readonly extends LoggingMetadataRepository {
        /**
         * Constructor for the read-only repository.
         *
         * @param repository The underlying metadata repository
         * @param manifestManager The manifest manager for handling log entries
         * @param noDeleteReplay Whether to ignore delete operations during replay
         */
        public Readonly(MetadataRepository repository,
                        ManifestManager manifestManager,
                        boolean noDeleteReplay) {
            super(repository, manifestManager, null, null, noDeleteReplay);
        }

        /**
         * Overrides the writeLogEntry method to throw an exception if any write is attempted.
         *
         * @param type The type of log entry
         * @param obj The object to serialize and write
         * @throws RuntimeException Always thrown to prevent writing to a read-only repository
         */
        @Override
        protected synchronized void writeLogEntry(String type, Object obj) {
            throw new RuntimeException("Tried to write to a read only repository");
        }
    }

    /**
     * Class to track active paths that are pending submission to the repository.
     * This allows for batching active path updates to improve performance.
     */
    @Data
    private static class PendingActivePath {
        private BackupActivePath path;
        private Instant submitted;

        /**
         * Constructor for a pending active path.
         *
         * @param path The active path information
         */
        public PendingActivePath(BackupActivePath path) {
            this.path = path;
            submitted = Instant.now();
        }
    }
}
