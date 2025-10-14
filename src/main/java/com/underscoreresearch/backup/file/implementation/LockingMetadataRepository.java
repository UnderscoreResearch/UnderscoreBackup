package com.underscoreresearch.backup.file.implementation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Stopwatch;
import com.underscoreresearch.backup.ui.desktop.UIHandler;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableMap;
import com.underscoreresearch.backup.file.CloseableSortedMap;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.LogFileRepository;
import com.underscoreresearch.backup.file.MapSerializer;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.MetadataRepositoryStorage;
import com.underscoreresearch.backup.file.RepositoryOpenMode;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockAdditional;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupLocation;
import com.underscoreresearch.backup.model.BackupPartialFile;
import com.underscoreresearch.backup.model.BackupPendingSet;
import com.underscoreresearch.backup.model.BackupUpdatedFile;
import com.underscoreresearch.backup.model.ExternalBackupFile;
import com.underscoreresearch.backup.utils.AccessLock;
import com.underscoreresearch.backup.utils.SingleTaskScheduler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import static com.underscoreresearch.backup.utils.log.LogUtil.debug;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Implementation of MetadataRepository that provides locking and transaction support.
 * Manages access to the repository from multiple processes and threads.
 * Handles repository upgrades and storage revisions.
 */
@Slf4j
public class LockingMetadataRepository implements MetadataRepository {
    public static final long MINIMUM_WAIT_UPDATE_MS = 2000;
    public static final int MAPDB_STORAGE = 1;
    public static final int MAPDB_STORAGE_VERSIONED = 4;
    public static final int MAPDB_STORAGE_LEAF_STORAGE = 5;
    public static final String COMPACT_TASK = "Upgrading metadata repository";
    private static final ObjectReader REPOSITORY_INFO_READER
            = MAPPER.readerFor(RepositoryInfo.class);
    private static final ObjectWriter REPOSITORY_INFO_WRITER
            = MAPPER.writerFor(RepositoryInfo.class);
    private static final String REQUEST_LOCK_FILE = "request.lock";
    private static final String LOCK_FILE = "access.lock";
    private static final String INFO_STORE = "info.json";
    private static final Map<String, LockingMetadataRepository> openRepositories = new HashMap<>();
    private static final int COMMIT_THRESHOLD = 1000000;
    private final String dataPath;
    private final boolean replayOnly;
    private final int defaultVersion;
    private final ReentrantLock updateLock = new ReentrantLock();
    private final ReentrantLock openLock = new ReentrantLock();
    private final AtomicInteger mutatingChanges = new AtomicInteger(0);
    protected RepositoryOpenMode openMode;
    protected ReentrantLock explicitLock = new ReentrantLock();
    private boolean open;
    private MetadataRepositoryStorage storage;
    private RepositoryInfo repositoryInfo;
    private AccessLock fileLock;
    private SingleTaskScheduler taskScheduler;
    private LogFileRepository logFileRepository;

    /**
     * Creates a new LockingMetadataRepository.
     *
     * @param dataPath The path to the repository data.
     * @param replayOnly Whether the repository is only for replay (Some operations are ignored).
     */
    public LockingMetadataRepository(String dataPath, boolean replayOnly) {
        this(dataPath, replayOnly, getDefaultVersion());
    }

    /**
     * Creates a new LockingMetadataRepository with a specific version.
     *
     * @param dataPath The path to the repository data.
     * @param replayOnly Whether the repository is only for replay (Some operations are ignored).
     * @param defaultVersion The default version of the repository.
     */
    LockingMetadataRepository(String dataPath, boolean replayOnly, int defaultVersion) {
        this.dataPath = dataPath;
        this.replayOnly = replayOnly;
        this.defaultVersion = defaultVersion;
    }

    /**
     * Gets the default repository version.
     *
     * @return The default repository version
     */
    public static int getDefaultVersion() {
        return MAPDB_STORAGE_LEAF_STORAGE;
    }

    /**
     * Closes all open repositories.
     * This is called during shutdown to ensure all repositories are properly closed.
     */
    public static void closeAllRepositories() {
        synchronized (LockingMetadataRepository.openRepositories) {
            for (Map.Entry<String, LockingMetadataRepository> entry : openRepositories.entrySet()) {
                debug(() -> log.debug("Closing unclosed repository \"{}\"", entry.getKey()));
                try {
                    entry.getValue().close();
                } catch (IOException e) {
                    log.info("Failed to close repository", e);
                }
            }
        }
    }

    /**
     * Gets the path to a file in the repository.
     *
     * @param file The file name
     * @return The path to the file
     */
    private Path getPath(String file) {
        return Paths.get(dataPath, file);
    }

    /**
     * Opens the repository with the specified mode.
     *
     * @param openMode The mode to open the repository in
     * @throws IOException if an I/O error occurs
     */
    public void open(RepositoryOpenMode openMode) throws IOException {
        try (RepositoryLock ignored = new OpenLock()) {
            if (open && openMode != this.openMode) {
                close();
            }
            if (!open) {
                synchronized (LockingMetadataRepository.openRepositories) {
                    while (LockingMetadataRepository.openRepositories.containsKey(dataPath)) {
                        LockingMetadataRepository.openRepositories.get(dataPath).close();
                    }
                    LockingMetadataRepository.openRepositories.put(dataPath, this);
                }
                open = true;
                this.openMode = openMode;

                if (openMode == RepositoryOpenMode.READ_ONLY) {
                    log.info("Opened repository in read only mode");
                    File requestFile = getPath(LockingMetadataRepository.REQUEST_LOCK_FILE).toFile();
                    fileLock = new AccessLock(getPath(LockingMetadataRepository.LOCK_FILE).toString());

                    AccessLock requestLock = new AccessLock(requestFile.getAbsolutePath());
                    if (!fileLock.tryLock(false)) {
                        LockingMetadataRepository.log.info("Waiting for repository access from other process");
                        requestLock.lock(false);
                    }
                    fileLock.lock(false);

                    requestLock.close();
                } else {
                    fileLock = new AccessLock(getPath(LockingMetadataRepository.LOCK_FILE).toString());
                    if (!fileLock.tryLock(true)) {
                        LockingMetadataRepository.log.info("Waiting for repository access from other process");
                        fileLock.lock(true);
                    }
                }

                prepareOpen(openMode);

                try {
                    openAllDataFiles(openMode);
                } catch (Exception e) {
                    open = false;
                    throw e;
                }
            }
        }
    }

    /**
     * Prepares to open the repository.
     * Reads the repository info and creates the storage.
     *
     * @param openMode The mode to open the repository in
     * @throws IOException if an I/O error occurs
     */
    private void prepareOpen(RepositoryOpenMode openMode) throws IOException {
        readRepositoryInfo(openMode);

        storage = createStorage(repositoryInfo.version, repositoryInfo.revision);

        if (openMode != RepositoryOpenMode.READ_ONLY) {
            if (taskScheduler == null) {
                taskScheduler = new SingleTaskScheduler(getClass().getSimpleName());
                if (storage.needPeriodicCommits()) {
                    taskScheduler.scheduleAtFixedRate(this::commitIfManyChanges, 30, 30, TimeUnit.SECONDS);
                    taskScheduler.scheduleAtFixedRate(this::commit, 10, 10, TimeUnit.MINUTES);
                }
                taskScheduler.scheduleAtFixedRate(this::checkAccessRequest, 1, 1, TimeUnit.SECONDS);
            }
        }
    }

    /**
     * Commits changes to the repository if there are many pending changes.
     */
    private void commitIfManyChanges() {
        if (mutatingChanges.get() > COMMIT_THRESHOLD) {
            commit();
        }
    }

    /**
     * Creates a storage implementation based on the repository version.
     *
     * @param version The repository version
     * @param revision The repository revision
     * @return The storage implementation
     */
    private MetadataRepositoryStorage createStorage(int version, int revision) {
        return switch (version) {
            case MAPDB_STORAGE, MAPDB_STORAGE_VERSIONED, MAPDB_STORAGE_LEAF_STORAGE ->
                    new MapdbMetadataRepositoryStorage(dataPath, version, revision, repositoryInfo.alternateBlockTable);
            default -> throw new IllegalArgumentException("Unsupported repository version");
        };
    }

    /**
     * Commits changes to the repository.
     * This ensures that all changes are persisted to disk.
     */
    public void commit() {
        Stopwatch stopwatch = null;
        int changes = mutatingChanges.get();
        if (changes > 0 && storage != null) {
            if (storage.needExclusiveCommitLock()) {
                try (UpdateLock ignored = new UpdateLock(false)) {
                    try (RepositoryLock ignored2 = new OpenLock()) {
                        try {
                            stopwatch = Stopwatch.createStarted();
                            storage.commit();
                        } catch (Exception exc) {
                            log.error("Failed to commit", exc);
                        }
                    } finally {
                        mutatingChanges.set(0);
                    }
                }
            } else {
                openLock.lock();
                try {
                    stopwatch = Stopwatch.createStarted();
                    storage.commit();
                } catch (Exception exc) {
                    log.error("Failed to commit", exc);
                } finally {
                    mutatingChanges.set(0);
                    openLock.unlock();
                }
            }
            if (stopwatch != null) {
                double time = Math.ceil(stopwatch.elapsed(TimeUnit.MILLISECONDS) / 100.0) / 10;
                if (time >= 10) {
                    log.warn("Committed {} changes in {} seconds", changes, time);
                } else {
                    debug(() -> log.debug("Committed {} changes in {} seconds", changes, time));
                }
            }
        }
    }

    /**
     * Reads the repository info from disk.
     *
     * @param openMode The mode to open the repository in
     * @throws IOException if an I/O error occurs
     */
    private void readRepositoryInfo(RepositoryOpenMode openMode) throws IOException {
        if (repositoryInfo != null && repositoryInfo.stopSaving)
            return;

        File file = getPath(LockingMetadataRepository.INFO_STORE).toFile();
        if (file.exists()) {
            repositoryInfo = LockingMetadataRepository.REPOSITORY_INFO_READER.readValue(file);
        } else {
            repositoryInfo = RepositoryInfo.builder().version(defaultVersion).build();

            if (openMode != RepositoryOpenMode.READ_ONLY) {
                saveRepositoryInfo();
            }
        }
    }

    /**
     * Saves the repository info to disk.
     *
     * @throws IOException if an I/O error occurs
     */
    private void saveRepositoryInfo() throws IOException {
        if (!repositoryInfo.stopSaving) {
            File file = getPath(LockingMetadataRepository.INFO_STORE).toFile();
            LockingMetadataRepository.REPOSITORY_INFO_WRITER.writeValue(file, repositoryInfo);
        }
    }

    /**
     * Checks if another process has requested access to the repository.
     * If so, closes the repository and reopens it after the other process is done.
     */
    private void checkAccessRequest() {
        try {
            if (!explicitLock.tryLock(500, TimeUnit.MILLISECONDS)) {
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            AccessLock requestLock = new AccessLock(getPath(LockingMetadataRepository.REQUEST_LOCK_FILE).toString());

            if (!requestLock.tryLock(true)) {
                LockingMetadataRepository.log.info("Detected request for access to metadata from other process");

                try (CloseableLock ignored = acquireUpdateLock()) {
                    try (RepositoryLock ignored2 = new OpenLock()) {
                        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
                        repository.flushLogging();

                        close();

                        requestLock.lock(true);
                        requestLock.close();

                        open(openMode);
                    }
                }
                LockingMetadataRepository.log.info("Metadata access restored from other process");
            } else {
                requestLock.close();
            }
        } catch (Exception exc) {
            log.error("Failed to give access to other process", exc);
        } finally {
            explicitLock.unlock();
        }
    }

    /**
     * Closes the repository.
     * This ensures that all changes are persisted to disk and all resources are released.
     *
     * @throws IOException if an I/O error occurs
     */
    public void close() throws IOException {
        try (UpdateLock ignored = new UpdateLock(false)) {
            try (RepositoryLock ignored2 = new OpenLock()) {
                if (open) {
                    if (taskScheduler != null) {
                        taskScheduler.shutdownNow();
                        taskScheduler = null;
                    }

                    closeAllDataFiles();

                    fileLock.close();
                    open = false;

                    synchronized (LockingMetadataRepository.openRepositories) {
                        LockingMetadataRepository.openRepositories.remove(dataPath);
                        LockingMetadataRepository.openRepositories.notify();
                    }
                }
            }
        }
    }

    /**
     * Ensures that the repository is open and in the correct mode.
     *
     * @param readOnly Whether the operation is read-only
     * @throws IOException if an I/O error occurs
     */
    private void ensureOpen(boolean readOnly) throws IOException {
        if (!open) {
            open(openMode);
        }
        if (!readOnly && openMode == RepositoryOpenMode.READ_ONLY) {
            throw new IOException("Tried to write to read only repository");
        }
    }

    /**
     * Clears the repository.
     * This removes all data from the repository.
     *
     * @throws IOException if an I/O error occurs
     */
    public void clear() throws IOException {
        if (openMode == RepositoryOpenMode.READ_ONLY) {
            throw new IOException("Tried to clear read only repository");
        }
        try (UpdateLock ignored = new UpdateLock(false)) {
            try (OpenLock ignored2 = new OpenLock()) {
                try (RepositoryLock ignored3 = new RepositoryLock(false)) {
                    if (taskScheduler != null) {
                        taskScheduler.shutdownNow();
                        taskScheduler = null;
                    }

                    closeAllDataFiles();

                    storage.clear();

                    prepareOpen(openMode);

                    openAllDataFiles(openMode);
                }
            }
        }
    }

    /**
     * Gets the last synced log file for a share.
     *
     * @param share The share ID, or null for the main repository
     * @return The last synced log file, or null if none
     */
    @Override
    public String lastSyncedLogFile(String share) {
        if (repositoryInfo == null) {
            try {
                readRepositoryInfo(RepositoryOpenMode.READ_ONLY);
            } catch (IOException e) {
                return null;
            }
        }
        return repositoryInfo.getLastSyncedLogFile(share);
    }

    /**
     * Sets the last synced log file for a share.
     *
     * @param share The share ID, or null for the main repository
     * @param entry The log file entry
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void setLastSyncedLogFile(String share, String entry) throws IOException {
        if (repositoryInfo == null) {
            readRepositoryInfo(RepositoryOpenMode.READ_WRITE);
        }
        repositoryInfo.setLastSyncedLogFile(share, entry);
        saveRepositoryInfo();
    }

    /**
     * Acquires an update lock for the repository.
     * This lock is used for operations that update the repository.
     *
     * @return A closeable lock
     */
    @Override
    public CloseableLock acquireUpdateLock() {
        return new UpdateLock(true);
    }

    /**
     * Acquires a repository lock.
     * This lock is used for general repository operations.
     *
     * @return A closeable lock
     */
    @Override
    public CloseableLock acquireLock() {
        return new RepositoryLock(true);
    }

    /**
     * Opens all data files in the repository.
     *
     * @param openMode The mode to open the repository in
     * @throws IOException if an I/O error occurs
     */
    private void openAllDataFiles(RepositoryOpenMode openMode) throws IOException {
        storage.open(openMode);

        logFileRepository = new LogFileRepositoryImpl(getPath("logs.log"));
    }

    /**
     * Closes all data files in the repository.
     *
     * @throws IOException if an I/O error occurs
     */
    private void closeAllDataFiles() throws IOException {
        storage.close();

        if (logFileRepository != null) {
            logFileRepository.close();
            logFileRepository = null;
        }
    }

    /**
     * Retrieves all files with the specified path.
     *
     * @param path The path to search for
     * @return A list of external backup files matching the path, or null if none found
     * @throws IOException if an I/O error occurs
     */
    @Override
    public List<ExternalBackupFile> file(String path) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.file(path);
        }
    }

    /**
     * Retrieves all file parts with the specified hash.
     *
     * @param partHash The hash to search for
     * @return A list of backup file parts matching the hash, or null if none found
     * @throws IOException if an I/O error occurs
     */
    @Override
    public List<BackupFilePart> existingFilePart(String partHash) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.existingFilePart(partHash);
        }
    }

    /**
     * Retrieves all files in the repository.
     *
     * @param ascending Whether to return files in ascending order
     * @return A closeable stream of backup files
     * @throws IOException if an I/O error occurs
     */
    @Override
    public CloseableStream<BackupFile> allFiles(boolean ascending) throws IOException {
        CloseableLock lock = acquireStreamLock();

        return new LockedStream<>(storage.allFiles(ascending), lock);
    }

    /**
     * Acquires a lock for streaming operations.
     *
     * @return A closeable lock
     * @throws IOException if an I/O error occurs
     */
    private CloseableLock acquireStreamLock() throws IOException {
        CloseableLock lock = acquireLock();
        try {
            ensureOpen(true);
        } catch (IOException exc) {
            lock.close();
            throw exc;
        }
        return lock;
    }

    /**
     * Retrieves all blocks in the repository.
     *
     * @return A closeable stream of backup blocks
     * @throws IOException if an I/O error occurs
     */
    @Override
    public CloseableStream<BackupBlock> allBlocks() throws IOException {
        CloseableLock lock = acquireStreamLock();

        return new LockedStream<>(storage.allBlocks(), lock);
    }

    /**
     * Retrieves all additional blocks in the repository.
     *
     * @return A closeable stream of backup additional blocks
     * @throws IOException if an I/O error occurs
     */
    @Override
    public CloseableStream<BackupBlockAdditional> allAdditionalBlocks() throws IOException {
        return storage.allAdditionalBlocks();
    }

    /**
     * Retrieves all file parts in the repository.
     *
     * @return A closeable stream of backup file parts
     * @throws IOException if an I/O error occurs
     */
    @Override
    public CloseableStream<BackupFilePart> allFileParts() throws IOException {
        CloseableLock lock = acquireStreamLock();

        return new LockedStream<>(storage.allFileParts(), lock);
    }

    /**
     * Retrieves all directories in the repository.
     *
     * @param ascending Whether to return directories in ascending order
     * @return A closeable stream of backup directories
     * @throws IOException if an I/O error occurs
     */
    @Override
    public CloseableStream<BackupDirectory> allDirectories(boolean ascending) throws IOException {
        CloseableLock lock = acquireStreamLock();

        return new LockedStream<>(storage.allDirectories(ascending), lock);
    }

    /**
     * Adds a pending set to the repository.
     *
     * @param scheduledTime The scheduled time information
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void addPendingSets(BackupPendingSet scheduledTime) throws IOException {
        if (!replayOnly)
            try (RepositoryLock ignored = new RepositoryLock(true)) {
                ensureOpen(false);

                storage.addPendingSets(scheduledTime);
            }
    }

    /**
     * Deletes a pending set from the repository.
     *
     * @param setId The ID of the set to delete
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void deletePendingSets(String setId) throws IOException {
        if (!replayOnly)
            try (RepositoryLock ignored = new RepositoryLock(true)) {
                ensureOpen(false);

                storage.deletePendingSets(setId);
            }
    }

    /**
     * Gets all pending sets from the repository.
     *
     * @return A set of all pending backup sets
     * @throws IOException if an I/O error occurs
     */
    @Override
    public Set<BackupPendingSet> getPendingSets() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.getPendingSets();
        }
    }

    /**
     * Retrieves a file by path and timestamp.
     *
     * @param path The path of the file
     * @param timestamp The timestamp to match, or null for the latest version
     * @return The backup file, or null if not found
     * @throws IOException if an I/O error occurs
     */
    @Override
    public BackupFile file(String path, Long timestamp) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.file(path, timestamp);
        }
    }

    /**
     * Retrieves a block by its hash.
     *
     * @param hash The hash of the block
     * @return The backup block, or null if not found
     * @throws IOException if an I/O error occurs
     */
    @Override
    public BackupBlock block(String hash) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.block(hash);
        }
    }

    /**
     * Retrieves a directory by path and timestamp.
     *
     * @param path The path of the directory
     * @param timestamp The timestamp to match, or null for the latest version
     * @param accumulative Whether to accumulate files from multiple directory versions
     * @return The backup directory, or null if not found
     * @throws IOException if an I/O error occurs
     */
    @Override
    public BackupDirectory directory(String path, Long timestamp, boolean accumulative) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.directory(path, timestamp, accumulative);
        }
    }

    /**
     * Adds a file to the repository.
     *
     * @param file The file to add
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void addFile(BackupFile file) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            storage.addFile(file);

            if (!replayOnly && file.getLocations() != null) {
                for (BackupLocation location : file.getLocations()) {
                    for (BackupFilePart part : location.getParts()) {
                        if (part.getPartHash() != null) {
                            storage.addFilePart(part);
                        }
                    }
                }
            }
        }
    }

    /**
     * Adds a block to the repository.
     *
     * @param block The block to add
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void addBlock(BackupBlock block) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            storage.addBlock(block);
        }
    }

    /**
     * Adds a temporary block to the repository.
     * Temporary blocks are stored in a separate table until they are committed.
     *
     * @param block The block to add
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void addTemporaryBlock(BackupBlock block) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            storage.addTemporaryBlock(block);
        }
    }

    /**
     * Installs temporary blocks into the main block table.
     * This switches the block table to use the temporary blocks.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void installTemporaryBlocks() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            repositoryInfo.alternateBlockTable = !repositoryInfo.alternateBlockTable;
            saveRepositoryInfo();

            storage.switchBlocksTable();
        }
    }

    /**
     * Adds a directory to the repository.
     *
     * @param directory The directory to add
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void addDirectory(BackupDirectory directory) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            storage.addDirectory(directory);
        }
    }

    /**
     * Deletes a block from the repository.
     *
     * @param block The block to delete
     * @return true if the block was deleted, false if it was not found
     * @throws IOException if an I/O error occurs
     */
    @Override
    public boolean deleteBlock(BackupBlock block) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            return storage.deleteBlock(block);
        }
    }

    /**
     * Deletes a file from the repository.
     *
     * @param file The file to delete
     * @return true if the file was deleted, false if it was not found
     * @throws IOException if an I/O error occurs
     */
    @Override
    public boolean deleteFile(BackupFile file) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            return storage.deleteFile(file);
        }
    }

    /**
     * Deletes a file part from the repository.
     *
     * @param part The file part to delete
     * @return true if the file part was deleted, false if it was not found
     * @throws IOException if an I/O error occurs
     */
    @Override
    public boolean deleteFilePart(BackupFilePart part) throws IOException {
        if (!replayOnly) {
            try (RepositoryLock ignored = new RepositoryLock(true)) {
                ensureOpen(false);

                return storage.deleteFilePart(part);
            }
        }
        return false;
    }

    /**
     * Deletes a directory from the repository.
     *
     * @param path The path of the directory to delete
     * @param timestamp The timestamp of the directory version to delete
     * @return true if the directory was deleted, false if it was not found
     * @throws IOException if an I/O error occurs
     */
    @Override
    public boolean deleteDirectory(String path, long timestamp) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            return storage.deleteDirectory(path, timestamp);
        }
    }

    /**
     * Adds an active path to the repository.
     *
     * @param setId The ID of the set
     * @param path The path to add
     * @param pendingFiles The active path information
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void pushActivePath(String setId, String path, BackupActivePath pendingFiles) throws IOException {
        if (!replayOnly) {
            try (RepositoryLock ignored = new RepositoryLock(true)) {
                ensureOpen(false);

                storage.pushActivePath(setId, path, pendingFiles);
            }
        }
    }

    /**
     * Checks if an active path exists in the repository.
     *
     * @param setId The ID of the set
     * @param path The path to check
     * @return true if the active path exists, false otherwise
     * @throws IOException if an I/O error occurs
     */
    @Override
    public boolean hasActivePath(String setId, String path) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(true);

            return storage.hasActivePath(setId, path);
        }
    }

    /**
     * Removes an active path from the repository.
     *
     * @param setId The ID of the set
     * @param path The path to remove
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void popActivePath(String setId, String path) throws IOException {
        if (!replayOnly) {
            try (RepositoryLock ignored = new RepositoryLock(true)) {
                ensureOpen(false);

                storage.popActivePath(setId, path);
            }
        }
    }

    /**
     * Deletes a partial file from the repository.
     *
     * @param file The partial file to delete
     * @return true if the partial file was deleted, false if it was not found
     * @throws IOException if an I/O error occurs
     */
    @Override
    public boolean deletePartialFile(BackupPartialFile file) throws IOException {
        if (!replayOnly) {
            try (RepositoryLock ignored = new RepositoryLock(true)) {
                ensureOpen(false);

                return storage.deletePartialFile(file);
            }
        } else {
            return false;
        }
    }

    /**
     * Saves a partial file to the repository.
     *
     * @param file The partial file to save
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void savePartialFile(BackupPartialFile file) throws IOException {
        if (!replayOnly) {
            try (RepositoryLock ignored = new RepositoryLock(true)) {
                ensureOpen(false);

                storage.savePartialFile(file);
            }
        }
    }

    /**
     * Clears all partial files from the repository.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void clearPartialFiles() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            storage.clearPartialFiles();
        }
    }

    /**
     * Gets the log file repository.
     * This provides access to log files for the repository.
     *
     * @return The log file repository
     * @throws IOException if an I/O error occurs
     */
    @Override
    public LogFileRepository getLogFileRepository() throws IOException {
        // There is a weird case where you have an exclusive lock but want to write log files where you could
        // get a deadlock if you get here before the repository is open.
        while (true) {
            if (logFileRepository != null)
                return logFileRepository;

            try {
                if (explicitLock.tryLock(1000, TimeUnit.SECONDS)) {
                    try {
                        ensureOpen(true);
                        return logFileRepository;
                    } finally {
                        explicitLock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Retrieves a partial file from the repository.
     *
     * @param file The partial file to retrieve
     * @return The partial file, or null if not found
     * @throws IOException if an I/O error occurs
     */
    @Override
    public BackupPartialFile getPartialFile(BackupPartialFile file) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.getPartialFile(file);
        }
    }

    /**
     * Gets all active paths for a set.
     *
     * @param setId The ID of the set
     * @return A map of paths to active path information
     * @throws IOException if an I/O error occurs
     */
    @Override
    public TreeMap<String, BackupActivePath> getActivePaths(String setId) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.getActivePaths(setId);
        }
    }

    /**
     * Flushes any pending log entries.
     * This implementation does nothing as logging is handled elsewhere.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void flushLogging() throws IOException {
    }

    /**
     * Gets the number of blocks in the repository.
     *
     * @return The number of blocks
     * @throws IOException if an I/O error occurs
     */
    @Override
    public long getBlockCount() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.getBlockCount();
        }
    }

    /**
     * Gets the number of files in the repository.
     *
     * @return The number of files
     * @throws IOException if an I/O error occurs
     */
    @Override
    public long getFileCount() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.getFileCount();
        }
    }

    /**
     * Gets the number of directories in the repository.
     *
     * @return The number of directories
     * @throws IOException if an I/O error occurs
     */
    @Override
    public long getDirectoryCount() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.getDirectoryCount();
        }
    }

    /**
     * Gets the number of file parts in the repository.
     *
     * @return The number of file parts
     * @throws IOException if an I/O error occurs
     */
    @Override
    public long getPartCount() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.getPartCount();
        }
    }

    /**
     * Adds an additional block to the repository.
     *
     * @param block The additional block to add
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void addAdditionalBlock(BackupBlockAdditional block) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            storage.addAdditionalBlock(block);
        }
    }

    /**
     * Retrieves an additional block from the repository.
     *
     * @param publicKey The public key associated with the block
     * @param blockHash The hash of the block
     * @return The additional block, or null if not found
     * @throws IOException if an I/O error occurs
     */
    @Override
    public BackupBlockAdditional additionalBlock(String publicKey, String blockHash) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.additionalBlock(publicKey, blockHash);
        }
    }

    /**
     * Deletes an additional block from the repository.
     *
     * @param publicKey The public key associated with the block
     * @param blockHash The hash of the block
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void deleteAdditionalBlock(String publicKey, String blockHash) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            storage.deleteAdditionalBlock(publicKey, blockHash);
        }
    }

    /**
     * Adds an updated file to the repository.
     *
     * @param file The updated file to add
     * @param howOften How often the file should be updated (in milliseconds)
     * @return true if the file was added, false if it was already up to date
     * @throws IOException if an I/O error occurs
     */
    @Override
    public boolean addUpdatedFile(BackupUpdatedFile file, long howOften) throws IOException {
        try (CloseableLock ignored = acquireUpdateLock()) {
            ensureOpen(false);

            return storage.addUpdatedFile(file, howOften);
        }
    }

    /**
     * Removes an updated file from the repository.
     *
     * @param file The updated file to remove
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void removeUpdatedFile(BackupUpdatedFile file) throws IOException {
        try (CloseableLock ignored = acquireUpdateLock()) {
            ensureOpen(false);

            storage.removeUpdatedFile(file);
        }
    }

    /**
     * Gets all updated files from the repository.
     *
     * @return A closeable stream of updated files
     * @throws IOException if an I/O error occurs
     */
    @Override
    public CloseableStream<BackupUpdatedFile> getUpdatedFiles() throws IOException {
        CloseableLock lock = acquireStreamLock();

        return new LockedStream<>(storage.getUpdatedFiles(), lock);
    }

    /**
     * Performs a repository upgrade if one is needed.
     * This migrates the repository to the latest version.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void upgradeStorage() throws IOException {
        if (shouldUpgrade() &&
                !repositoryInfo.errorsDetected && !repositoryInfo.stopSaving) {
            performUpgrade();
        }
    }

    /**
     * Performs the actual upgrade of the repository.
     * @throws IOException if an I/O error occurs
     */
    private void performUpgrade() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            try (Closeable ignored2 = UIHandler.registerTask("Upgrading metadata repository", true)) {
                RepositoryOpenMode originalOpenMode = openMode;
                open(RepositoryOpenMode.READ_ONLY);

                try {
                    int version = getDefaultVersion();
                    MetadataRepositoryStorage upgradedStorage = createStorage(version, 0);

                    try {
                        new RepositoryUpgrader(storage, upgradedStorage).upgrade();

                        repositoryInfo.version = version;
                        repositoryInfo.revision = 0;

                        storage.close();
                        storage.clear();
                        storage = upgradedStorage;
                        close();
                    } catch (CancellationException exc) {
                        log.warn("Repository migration cancelled", exc);
                    } catch (RepositoryUpgrader.RepositoryErrorException exc) {
                        log.error("Detected repository errors during migration", exc);
                        repositoryInfo.errorsDetected = true;
                    }
                    saveRepositoryInfo();
                } finally {
                    open(originalOpenMode);
                }
            }
        }
    }

    /**
     * Checks if the repository should be upgraded.
     *
     * @return true if the repository should be upgraded, false otherwise
     */
    private boolean shouldUpgrade() {
        return repositoryInfo.version != getDefaultVersion();
    }

    /**
     * Creates a new storage revision.
     * This creates a new storage instance with an incremented revision number.
     *
     * @return The new storage instance
     * @throws IOException if an I/O error occurs
     */
    @Override
    public MetadataRepositoryStorage createStorageRevision() throws IOException {
        if (repositoryInfo == null) {
            readRepositoryInfo(RepositoryOpenMode.WITHOUT_TRANSACTION);
        }
        repositoryInfo.stopSaving = true;
        repositoryInfo.errorsDetected = false;
        repositoryInfo.revision++;
        MetadataRepositoryStorage ret = createStorage(repositoryInfo.version, repositoryInfo.revision);
        ret.clear();
        if (storage != null) {
            storage.close();
        }
        storage = ret;
        if (open) {
            storage.open(openMode);
        }
        return ret;
    }

    /**
     * Cancels a storage revision.
     * This reverts to the previous storage revision.
     *
     * @param newStorage The new storage to cancel
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void cancelStorageRevision(MetadataRepositoryStorage newStorage) throws IOException {
        close();
        newStorage.clear();
        repositoryInfo.stopSaving = false;

        readRepositoryInfo(RepositoryOpenMode.READ_WRITE);
        storage = createStorage(repositoryInfo.version, repositoryInfo.revision);
    }

    /**
     * Installs a storage revision.
     * This makes the new storage revision the current one.
     *
     * @param newStorage The new storage to install
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void installStorageRevision(MetadataRepositoryStorage newStorage) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            repositoryInfo.stopSaving = false;
            saveRepositoryInfo();

            MetadataRepositoryStorage oldStorage = createStorage(repositoryInfo.version, repositoryInfo.revision - 1);
            oldStorage.clear();
        }
    }

    /**
     * Gets the configuration hash from the repository.
     *
     * @return The configuration hash
     * @throws IOException if an I/O error occurs
     */
    @Override
    public String getConfigurationHash() throws IOException {
        readRepositoryInfo(RepositoryOpenMode.READ_ONLY);
        return repositoryInfo.configurationHash;
    }

    /**
     * Sets the configuration hash in the repository.
     *
     * @param hash The configuration hash to set
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void setConfigurationHash(String hash) throws IOException {
        readRepositoryInfo(RepositoryOpenMode.READ_ONLY);
        repositoryInfo.configurationHash = hash;
        saveRepositoryInfo();
    }

    /**
     * Checks if errors were detected in the repository.
     *
     * @return true if errors were detected, false otherwise
     */
    @Override
    public boolean isErrorsDetected() {
        if (repositoryInfo == null) {
            try {
                readRepositoryInfo(RepositoryOpenMode.READ_ONLY);
            } catch (IOException e) {
                return false;
            }
        }
        return repositoryInfo != null && repositoryInfo.isErrorsDetected();
    }

    /**
     * Sets whether errors were detected in the repository.
     *
     * @param errorsDetected true if errors were detected, false otherwise
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void setErrorsDetected(boolean errorsDetected) throws IOException {
        if (repositoryInfo == null) {
            readRepositoryInfo(RepositoryOpenMode.READ_WRITE);
        }
        assert repositoryInfo != null;
        repositoryInfo.setErrorsDetected(errorsDetected);
        saveRepositoryInfo();
    }

    /**
     * Creates a temporary map for storing key-value pairs.
     *
     * @param serializer The serializer for the map keys and values
     * @param <K> The key type
     * @param <V> The value type
     * @return A closeable map
     * @throws IOException if an I/O error occurs
     */
    @Override
    public <K, V> CloseableMap<K, V> temporaryMap(MapSerializer<K, V> serializer) throws IOException {
        return storage.temporaryMap(serializer);
    }

    /**
     * Creates a temporary sorted map for storing key-value pairs.
     *
     * @param serializer The serializer for the map keys and values
     * @param <K> The key type
     * @param <V> The value type
     * @return A closeable sorted map
     * @throws IOException if an I/O error occurs
     */
    @Override
    public <K, V> CloseableSortedMap<K, V> temporarySortedMap(MapSerializer<K, V> serializer) throws IOException {
        return storage.temporarySortedMap(serializer);
    }

    /**
     * Acquires an exclusive lock on the repository.
     * This lock prevents any other operations from being performed on the repository.
     *
     * @return A closeable lock
     * @throws IOException if an I/O error occurs
     */
    @Override
    public CloseableLock exclusiveLock() throws IOException {
        return storage.exclusiveLock();
    }

    /**
     * Compacts the repository.
     * This optimizes the storage by removing unused space and reorganizing data.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void compact() throws IOException {
        try (UpdateLock ignored = new UpdateLock(true)) {
            try (RepositoryLock ignored2 = new OpenLock()) {
                try (Closeable ignored3 = UIHandler.registerTask(COMPACT_TASK, true)) {
                    ensureOpen(false);

                    MetadataRepositoryStorage oldStorage = storage;

                    MetadataRepositoryStorage newStorage = createStorageRevision();
                    oldStorage.open(RepositoryOpenMode.READ_ONLY);
                    try {
                        new RepositoryUpgrader(oldStorage, newStorage).upgrade();
                        oldStorage.close();

                        installStorageRevision(newStorage);
                    } catch (CancellationException exc) {
                        log.warn("Repository migration cancelled", exc);
                        cancelStorageRevision(newStorage);
                    } catch (Throwable exc) {
                        cancelStorageRevision(newStorage);
                        log.error("Failed defrag operation", exc);
                    }
                }
            }
        }
    }

    /**
     * Repository information class.
     * Stores metadata about the repository.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class RepositoryInfo {
        private int version;
        private int revision;
        private String configurationHash;
        private boolean alternateBlockTable;
        private boolean errorsDetected;
        @JsonIgnore
        private boolean stopSaving;
        private String lastSyncedLogEntry;
        private Map<String, String> shareLastSyncedLogEntry;

        /**
         * Gets the last synced log file for a share.
         *
         * @param share The share ID, or null for the main repository
         * @return The last synced log file, or null if none
         */
        public String getLastSyncedLogFile(String share) {
            if (share != null) {
                if (shareLastSyncedLogEntry != null) {
                    return shareLastSyncedLogEntry.get(share);
                }
                return null;
            }
            return lastSyncedLogEntry;
        }

        /**
         * Sets the last synced log file for a share.
         *
         * @param share The share ID, or null for the main repository
         * @param entry The log file entry
         */
        public void setLastSyncedLogFile(String share, String entry) {
            if (share != null) {
                if (shareLastSyncedLogEntry == null) {
                    shareLastSyncedLogEntry = new HashMap<>();
                }
                shareLastSyncedLogEntry.put(share, entry);
            } else {
                lastSyncedLogEntry = entry;
            }
        }
    }

    /**
     * Stream implementation that closes a lock when the stream is closed.
     *
     * @param <T> The type of elements in the stream
     */
    @RequiredArgsConstructor
    private static class LockedStream<T> implements CloseableStream<T> {
        private final CloseableStream<T> stream;
        private final CloseableLock lock;

        @Override
        public Stream<T> stream() {
            return stream.stream();
        }

        @Override
        public void setReportErrorsAsNull(boolean reportErrorsAsNull) {
            stream.setReportErrorsAsNull(reportErrorsAsNull);
        }

        @Override
        public void close() throws IOException {
            stream.close();
            lock.close();
        }
    }

    /**
     * Lock implementation for repository operations.
     * Acquires the explicit lock and increments the mutating changes counter if the operation is mutating.
     */
    private class RepositoryLock extends CloseableLock {
        public RepositoryLock(boolean mutating) {
            LockingMetadataRepository.this.explicitLock.lock();
            if (mutating) {
                mutatingChanges.incrementAndGet();
            }
        }

        @Override
        public void close() {
            LockingMetadataRepository.this.explicitLock.unlock();
        }

        @Override
        public boolean requested() {
            return LockingMetadataRepository.this.explicitLock.hasQueuedThreads();
        }
    }

    /**
     * Lock implementation for open operations.
     * Acquires both the explicit lock and the open lock.
     */
    private class OpenLock extends RepositoryLock {
        public OpenLock() {
            super(true);
            LockingMetadataRepository.this.openLock.lock();
        }

        @Override
        public void close() {
            LockingMetadataRepository.this.openLock.unlock();
            super.close();
        }
    }

    /**
     * Lock implementation for update operations.
     * Acquires the update lock and increments the mutating changes counter if the operation is mutating.
     */
    private class UpdateLock extends CloseableLock {
        public UpdateLock(boolean mutating) {
            LockingMetadataRepository.this.updateLock.lock();
            if (mutating)
                mutatingChanges.incrementAndGet();
        }

        @Override
        public void close() {
            LockingMetadataRepository.this.updateLock.unlock();
        }

        @Override
        public boolean requested() {
            return false;
        }
    }
}
