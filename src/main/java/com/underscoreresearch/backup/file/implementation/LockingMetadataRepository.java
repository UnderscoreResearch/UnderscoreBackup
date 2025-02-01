package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

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

import com.google.common.base.Stopwatch;
import com.underscoreresearch.backup.file.LogFileRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.cli.ui.UIHandler;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableMap;
import com.underscoreresearch.backup.file.CloseableSortedMap;
import com.underscoreresearch.backup.file.CloseableStream;
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
    protected RepositoryOpenMode openMode;
    protected ReentrantLock explicitLock = new ReentrantLock();
    private boolean open;
    private MetadataRepositoryStorage storage;
    private RepositoryInfo repositoryInfo;
    private AccessLock fileLock;
    private final AtomicInteger mutatingChanges = new AtomicInteger(0);
    private SingleTaskScheduler taskScheduler;
    private LogFileRepository logFileRepository;

    public LockingMetadataRepository(String dataPath, boolean replayOnly) {
        this(dataPath, replayOnly, getDefaultVersion());
    }

    LockingMetadataRepository(String dataPath, boolean replayOnly, int defaultVersion) {
        this.dataPath = dataPath;
        this.replayOnly = replayOnly;
        this.defaultVersion = defaultVersion;
    }

    public static int getDefaultVersion() {
        return MAPDB_STORAGE_LEAF_STORAGE;
    }

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

    private Path getPath(String file) {
        return Paths.get(dataPath, file);
    }

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

    private void commitIfManyChanges() {
        if (mutatingChanges.get() > COMMIT_THRESHOLD) {
            commit();
        }
    }

    private MetadataRepositoryStorage createStorage(int version, int revision) {
        return switch (version) {
            case MAPDB_STORAGE, MAPDB_STORAGE_VERSIONED, MAPDB_STORAGE_LEAF_STORAGE ->
                    new MapdbMetadataRepositoryStorage(dataPath, version, revision, repositoryInfo.alternateBlockTable);
            default -> throw new IllegalArgumentException("Unsupported repository version");
        };
    }

    public void commit() {
        Stopwatch stopwatch = null;
        int changes = mutatingChanges.get();
        if (storage != null) {
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

    private void saveRepositoryInfo() throws IOException {
        if (!repositoryInfo.stopSaving) {
            File file = getPath(LockingMetadataRepository.INFO_STORE).toFile();
            LockingMetadataRepository.REPOSITORY_INFO_WRITER.writeValue(file, repositoryInfo);
        }
    }

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

    private void ensureOpen(boolean readOnly) throws IOException {
        if (!open) {
            open(openMode);
        }
        if (!readOnly && openMode == RepositoryOpenMode.READ_ONLY) {
            throw new IOException("Tried to write to read only repository");
        }
    }

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

    @Override
    public void setLastSyncedLogFile(String share, String entry) throws IOException {
        if (repositoryInfo == null) {
            readRepositoryInfo(RepositoryOpenMode.READ_WRITE);
        }
        repositoryInfo.setLastSyncedLogFile(share, entry);
        saveRepositoryInfo();
    }

    @Override
    public CloseableLock acquireUpdateLock() {
        return new UpdateLock(true);
    }

    @Override
    public CloseableLock acquireLock() {
        return new RepositoryLock(true);
    }

    private void openAllDataFiles(RepositoryOpenMode openMode) throws IOException {
        storage.open(openMode);

        logFileRepository = new LogFileRepositoryImpl(getPath("logs.log"));
    }

    private void closeAllDataFiles() throws IOException {
        storage.close();

        if (logFileRepository != null) {
            logFileRepository.close();
            logFileRepository = null;
        }
    }

    @Override
    public List<ExternalBackupFile> file(String path) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.file(path);
        }
    }

    @Override
    public List<BackupFilePart> existingFilePart(String partHash) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.existingFilePart(partHash);
        }
    }

    @Override
    public CloseableStream<BackupFile> allFiles(boolean ascending) throws IOException {
        CloseableLock lock = acquireStreamLock();

        return new LockedStream<>(storage.allFiles(ascending), lock);
    }

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

    @Override
    public CloseableStream<BackupBlock> allBlocks() throws IOException {
        CloseableLock lock = acquireStreamLock();

        return new LockedStream<>(storage.allBlocks(), lock);
    }

    @Override
    public CloseableStream<BackupBlockAdditional> allAdditionalBlocks() throws IOException {
        return storage.allAdditionalBlocks();
    }

    @Override
    public CloseableStream<BackupFilePart> allFileParts() throws IOException {
        CloseableLock lock = acquireStreamLock();

        return new LockedStream<>(storage.allFileParts(), lock);
    }

    @Override
    public CloseableStream<BackupDirectory> allDirectories(boolean ascending) throws IOException {
        CloseableLock lock = acquireStreamLock();

        return new LockedStream<>(storage.allDirectories(ascending), lock);
    }

    @Override
    public void addPendingSets(BackupPendingSet scheduledTime) throws IOException {
        if (!replayOnly)
            try (RepositoryLock ignored = new RepositoryLock(true)) {
                ensureOpen(false);

                storage.addPendingSets(scheduledTime);
            }
    }

    @Override
    public void deletePendingSets(String setId) throws IOException {
        if (!replayOnly)
            try (RepositoryLock ignored = new RepositoryLock(true)) {
                ensureOpen(false);

                storage.deletePendingSets(setId);
            }
    }

    @Override
    public Set<BackupPendingSet> getPendingSets() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.getPendingSets();
        }
    }

    @Override
    public BackupFile file(String path, Long timestamp) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.file(path, timestamp);
        }
    }

    @Override
    public BackupBlock block(String hash) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.block(hash);
        }
    }

    @Override
    public BackupDirectory directory(String path, Long timestamp, boolean accumulative) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.directory(path, timestamp, accumulative);
        }
    }

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

    @Override
    public void addBlock(BackupBlock block) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            storage.addBlock(block);
        }
    }

    @Override
    public void addTemporaryBlock(BackupBlock block) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            storage.addTemporaryBlock(block);
        }
    }

    @Override
    public void installTemporaryBlocks() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            repositoryInfo.alternateBlockTable = !repositoryInfo.alternateBlockTable;
            saveRepositoryInfo();

            storage.switchBlocksTable();
        }
    }

    @Override
    public void addDirectory(BackupDirectory directory) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            storage.addDirectory(directory);
        }
    }

    @Override
    public boolean deleteBlock(BackupBlock block) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            return storage.deleteBlock(block);
        }
    }

    @Override
    public boolean deleteFile(BackupFile file) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            return storage.deleteFile(file);
        }
    }

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

    @Override
    public boolean deleteDirectory(String path, long timestamp) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            return storage.deleteDirectory(path, timestamp);
        }
    }

    @Override
    public void pushActivePath(String setId, String path, BackupActivePath pendingFiles) throws IOException {
        if (!replayOnly) {
            try (RepositoryLock ignored = new RepositoryLock(true)) {
                ensureOpen(false);

                storage.pushActivePath(setId, path, pendingFiles);
            }
        }
    }

    @Override
    public boolean hasActivePath(String setId, String path) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(true);

            return storage.hasActivePath(setId, path);
        }
    }

    @Override
    public void popActivePath(String setId, String path) throws IOException {
        if (!replayOnly) {
            try (RepositoryLock ignored = new RepositoryLock(true)) {
                ensureOpen(false);

                storage.popActivePath(setId, path);
            }
        }
    }

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

    @Override
    public void savePartialFile(BackupPartialFile file) throws IOException {
        if (!replayOnly) {
            try (RepositoryLock ignored = new RepositoryLock(true)) {
                ensureOpen(false);

                storage.savePartialFile(file);
            }
        }
    }

    @Override
    public void clearPartialFiles() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            storage.clearPartialFiles();
        }
    }

    @Override
    public LogFileRepository getLogFileRepository() throws IOException {
        // There is a weird case where you have an exclusive lock but want to write log files where you could
        // get a deadlock if you get here before the repository is open.
        while(true) {
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

    @Override
    public BackupPartialFile getPartialFile(BackupPartialFile file) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.getPartialFile(file);
        }
    }

    @Override
    public TreeMap<String, BackupActivePath> getActivePaths(String setId) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.getActivePaths(setId);
        }
    }

    @Override
    public void flushLogging() throws IOException {
    }

    @Override
    public long getBlockCount() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.getBlockCount();
        }
    }

    @Override
    public long getFileCount() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.getFileCount();
        }
    }

    @Override
    public long getDirectoryCount() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.getDirectoryCount();
        }
    }

    @Override
    public long getPartCount() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.getPartCount();
        }
    }

    @Override
    public void addAdditionalBlock(BackupBlockAdditional block) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            storage.addAdditionalBlock(block);
        }
    }

    @Override
    public BackupBlockAdditional additionalBlock(String publicKey, String blockHash) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(false)) {
            ensureOpen(true);

            return storage.additionalBlock(publicKey, blockHash);
        }
    }

    @Override
    public void deleteAdditionalBlock(String publicKey, String blockHash) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            ensureOpen(false);

            storage.deleteAdditionalBlock(publicKey, blockHash);
        }
    }

    @Override
    public boolean addUpdatedFile(BackupUpdatedFile file, long howOften) throws IOException {
        try (CloseableLock ignored = acquireUpdateLock()) {
            ensureOpen(false);

            return storage.addUpdatedFile(file, howOften);
        }
    }

    @Override
    public void removeUpdatedFile(BackupUpdatedFile file) throws IOException {
        try (CloseableLock ignored = acquireUpdateLock()) {
            ensureOpen(false);

            storage.removeUpdatedFile(file);
        }
    }

    @Override
    public CloseableStream<BackupUpdatedFile> getUpdatedFiles() throws IOException {
        CloseableLock lock = acquireStreamLock();

        return new LockedStream<>(storage.getUpdatedFiles(), lock);
    }

    @Override
    public void upgradeStorage() throws IOException {
        if (shouldUpgrade() &&
                !repositoryInfo.errorsDetected && !repositoryInfo.stopSaving) {
            performUpgrade();
        }
    }

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

    private boolean shouldUpgrade() {
        return repositoryInfo.version != getDefaultVersion();
    }

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

    @Override
    public void cancelStorageRevision(MetadataRepositoryStorage newStorage) throws IOException {
        close();
        newStorage.clear();
        repositoryInfo.stopSaving = false;

        readRepositoryInfo(RepositoryOpenMode.READ_WRITE);
        storage = createStorage(repositoryInfo.version, repositoryInfo.revision);
    }

    @Override
    public void installStorageRevision(MetadataRepositoryStorage newStorage) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock(true)) {
            repositoryInfo.stopSaving = false;
            saveRepositoryInfo();

            MetadataRepositoryStorage oldStorage = createStorage(repositoryInfo.version, repositoryInfo.revision - 1);
            oldStorage.clear();
        }
    }

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

    @Override
    public void setErrorsDetected(boolean errorsDetected) throws IOException {
        if (repositoryInfo == null) {
            readRepositoryInfo(RepositoryOpenMode.READ_WRITE);
        }
        assert repositoryInfo != null;
        repositoryInfo.setErrorsDetected(errorsDetected);
        saveRepositoryInfo();
    }

    @Override
    public <K, V> CloseableMap<K, V> temporaryMap(MapSerializer<K, V> serializer) throws IOException {
        return storage.temporaryMap(serializer);
    }

    @Override
    public <K, V> CloseableSortedMap<K, V> temporarySortedMap(MapSerializer<K, V> serializer) throws IOException {
        return storage.temporarySortedMap(serializer);
    }

    @Override
    public CloseableLock exclusiveLock() throws IOException {
        return storage.exclusiveLock();
    }

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class RepositoryInfo {
        private int version;
        private int revision;
        private boolean alternateBlockTable;
        private boolean errorsDetected;
        @JsonIgnore
        private boolean stopSaving;
        private String lastSyncedLogEntry;
        private Map<String, String> shareLastSyncedLogEntry;

        public String getLastSyncedLogFile(String share) {
            if (share != null) {
                if (shareLastSyncedLogEntry != null) {
                    return shareLastSyncedLogEntry.get(share);
                }
                return null;
            }
            return lastSyncedLogEntry;
        }

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
