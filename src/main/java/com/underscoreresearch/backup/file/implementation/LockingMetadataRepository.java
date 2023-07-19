package com.underscoreresearch.backup.file.implementation;

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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.SystemUtils;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.underscoreresearch.backup.cli.UIManager;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableMap;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.MapSerializer;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.MetadataRepositoryStorage;
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
import com.underscoreresearch.backup.utils.AccessLock;

@Slf4j
public class LockingMetadataRepository implements MetadataRepository {
    public static final long MINIMUM_WAIT_UPDATE_MS = 2000;
    public static final int LEGACY_MAPDB_STORAGE = 0;
    public static final int MAPDB_STORAGE = 1;
    public static final int LMDB_STORAGE = 2;
    private static final ObjectReader REPOSITORY_INFO_READER
            = MAPPER.readerFor(RepositoryInfo.class);
    private static final ObjectWriter REPOSITORY_INFO_WRITER
            = MAPPER.writerFor(RepositoryInfo.class);
    private static final String REQUEST_LOCK_FILE = "request.lock";
    private static final String LOCK_FILE = "access.lock";
    private static final String INFO_STORE = "info.json";
    private static final Map<String, LockingMetadataRepository> openRepositories = new HashMap<>();
    private final String dataPath;
    private final boolean replayOnly;
    private final int defaultVersion;
    private final Object explicitRequestLock = new Object();
    private final ReentrantLock updateLock = new ReentrantLock();
    private final ReentrantLock openLock = new ReentrantLock();
    protected boolean readOnly;
    protected ReentrantLock explicitLock = new ReentrantLock();
    private boolean open;
    private MetadataRepositoryStorage storage;
    private RepositoryInfo repositoryInfo;
    private AccessLock fileLock;
    private boolean explicitRequested = false;
    private ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    public LockingMetadataRepository(String dataPath, boolean replayOnly) {
        this(dataPath, replayOnly, getDefaultVersion());
    }

    LockingMetadataRepository(String dataPath, boolean replayOnly, int defaultVersion) {
        this.dataPath = dataPath;
        this.replayOnly = replayOnly;
        this.defaultVersion = defaultVersion;
    }

    public static int getDefaultVersion() {
        // OSX support on aarch64 is coming soon to lmdb (It is in the dev repository but not yet released
        // as a stable build) but for now we will have to stay on MapDB for this platform.

        if (SystemUtils.IS_OS_LINUX || SystemUtils.IS_OS_WINDOWS) {
            return LMDB_STORAGE;
        }
        return MAPDB_STORAGE;
    }

    private Path getPath(String file) {
        return Paths.get(dataPath, file);
    }

    public void open(boolean readOnly) throws IOException {
        try (RepositoryLock ignored = new OpenLock()) {
            if (open && !readOnly && this.readOnly) {
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
                this.readOnly = readOnly;

                if (readOnly) {
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

                prepareOpen(readOnly);

                try {
                    openAllDataFiles(readOnly);
                } catch (Exception e) {
                    open = false;
                    throw e;
                }
            }
        }
    }

    private void prepareOpen(boolean readOnly) throws IOException {
        readRepositoryInfo(readOnly);

        storage = createStorage(repositoryInfo.version);

        if (!readOnly) {
            if (scheduledThreadPoolExecutor == null) {
                scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1,
                        new ThreadFactoryBuilder().setNameFormat(getClass().getSimpleName() + "-%d").build());
                if (storage.needPeriodicCommits()) {
                    scheduledThreadPoolExecutor.scheduleAtFixedRate(this::commit, 30, 30, TimeUnit.SECONDS);
                }
                scheduledThreadPoolExecutor.scheduleAtFixedRate(this::checkAccessRequest, 1, 1, TimeUnit.SECONDS);
            }
        }
    }

    private MetadataRepositoryStorage createStorage(int version) {
        return switch (version) {
            case LEGACY_MAPDB_STORAGE ->
                    new MapdbMetadataRepositoryStorage.Legacy(dataPath, repositoryInfo.alternateBlockTable);
            case MAPDB_STORAGE -> new MapdbMetadataRepositoryStorage(dataPath, repositoryInfo.alternateBlockTable);
            case LMDB_STORAGE -> new LmdbMetadataRepositoryStorage(dataPath, repositoryInfo.alternateBlockTable);
            default -> throw new IllegalArgumentException("Unsupported repository version");
        };
    }

    public void commit() {
        if (storage != null) {
            if (storage.needExclusiveCommitLock()) {
                try (UpdateLock ignored = new UpdateLock()) {
                    try (RepositoryLock ignored2 = new OpenLock()) {
                        try {
                            storage.commit();
                        } catch (Exception exc) {
                            log.error("Failed to commit", exc);
                        }
                    }
                }
            } else {
                openLock.lock();
                try {
                    storage.commit();
                } catch (Exception exc) {
                    log.error("Failed to commit", exc);
                } finally {
                    openLock.unlock();
                }
            }
        }
    }

    private void readRepositoryInfo(boolean readonly) throws IOException {
        File file = getPath(LockingMetadataRepository.INFO_STORE).toFile();
        if (file.exists()) {
            repositoryInfo = LockingMetadataRepository.REPOSITORY_INFO_READER.readValue(file);
        } else {
            if (getPath("files.db").toFile().exists()) {
                repositoryInfo = RepositoryInfo.builder().version(LEGACY_MAPDB_STORAGE).build();
            } else {
                repositoryInfo = RepositoryInfo.builder().version(defaultVersion).build();
            }
            if (!readonly) {
                saveRepositoryInfo();
            }
        }
    }

    private void saveRepositoryInfo() throws IOException {
        File file = getPath(LockingMetadataRepository.INFO_STORE).toFile();
        LockingMetadataRepository.REPOSITORY_INFO_WRITER.writeValue(file, repositoryInfo);
    }

    private void checkAccessRequest() {
        try {
            if (!explicitLock.tryLock(500, TimeUnit.MILLISECONDS)) {
                return;
            }
        } catch (InterruptedException e) {
            return;
        }
        try {
            AccessLock requestLock = new AccessLock(getPath(LockingMetadataRepository.REQUEST_LOCK_FILE).toString());

            if (!requestLock.tryLock(true)) {
                LockingMetadataRepository.log.info("Detected request for access to metadata from other process");

                MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
                repository.flushLogging();

                close();

                requestLock.lock(true);
                requestLock.close();
                open(readOnly);
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
        try (UpdateLock ignored = new UpdateLock()) {
            try (RepositoryLock ignored2 = new OpenLock()) {
                if (open) {
                    if (scheduledThreadPoolExecutor != null) {
                        scheduledThreadPoolExecutor.shutdownNow();
                        scheduledThreadPoolExecutor = null;
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

    private void ensureOpen() throws IOException {
        if (!open) {
            open(readOnly);
        }
    }

    public void clear() throws IOException {
        if (readOnly) {
            throw new IOException("Tried to clear read only repository");
        }
        try (UpdateLock ignored = new UpdateLock()) {
            try (OpenLock ignored2 = new OpenLock()) {
                try (RepositoryLock ignored3 = new RepositoryLock()) {
                    if (scheduledThreadPoolExecutor != null) {
                        scheduledThreadPoolExecutor.shutdownNow();
                        scheduledThreadPoolExecutor = null;
                    }

                    closeAllDataFiles();

                    storage.clear();

                    prepareOpen(false);

                    openAllDataFiles(false);
                }
            }
        }
    }

    @Override
    public String lastSyncedLogFile(String share) {
        return repositoryInfo.getLastSyncedLogFile(share);
    }

    @Override
    public void setLastSyncedLogFile(String share, String entry) throws IOException {
        repositoryInfo.setLastSyncedLogFile(share, entry);
        saveRepositoryInfo();
    }


    @Override
    public CloseableLock acquireUpdateLock() {
        return new UpdateLock();
    }

    @Override
    public CloseableLock acquireLock() {
        return new RepositoryLock();
    }

    private void openAllDataFiles(boolean readOnly) throws IOException {
        storage.open(readOnly);
    }

    private void closeAllDataFiles() throws IOException {
        storage.close();
    }

    @Override
    public List<BackupFile> file(String path) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            return storage.file(path);
        }
    }

    @Override
    public List<BackupFilePart> existingFilePart(String partHash) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

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
            ensureOpen();
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
            try (RepositoryLock ignored = new RepositoryLock()) {
                ensureOpen();

                storage.addPendingSets(scheduledTime);
            }
    }

    @Override
    public void deletePendingSets(String setId) throws IOException {
        if (!replayOnly)
            try (RepositoryLock ignored = new RepositoryLock()) {
                ensureOpen();

                storage.deletePendingSets(setId);
            }
    }

    @Override
    public Set<BackupPendingSet> getPendingSets() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            return storage.getPendingSets();
        }
    }

    @Override
    public BackupFile lastFile(String path) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            return storage.lastFile(path);
        }
    }

    @Override
    public BackupBlock block(String hash) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            return storage.block(hash);
        }
    }

    @Override
    public List<BackupDirectory> directory(String path) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            return storage.directory(path);
        }
    }

    @Override
    public BackupDirectory lastDirectory(String path) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            return storage.lastDirectory(path);
        }
    }

    @Override
    public void addFile(BackupFile file) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

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
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            storage.addBlock(block);
        }
    }

    @Override
    public void addTemporaryBlock(BackupBlock block) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            storage.addTemporaryBlock(block);
        }
    }

    @Override
    public void installTemporaryBlocks() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            repositoryInfo.alternateBlockTable = !repositoryInfo.alternateBlockTable;
            saveRepositoryInfo();

            storage.switchBlocksTable();
        }
    }

    @Override
    public void addDirectory(BackupDirectory directory) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            storage.addDirectory(directory);
        }
    }

    @Override
    public boolean deleteBlock(BackupBlock block) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            return storage.deleteBlock(block);
        }
    }

    @Override
    public boolean deleteFile(BackupFile file) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            return storage.deleteFile(file);
        }
    }

    @Override
    public boolean deleteFilePart(BackupFilePart part) throws IOException {
        if (!replayOnly) {
            try (RepositoryLock ignored = new RepositoryLock()) {
                ensureOpen();

                return storage.deleteFilePart(part);
            }
        }
        return false;
    }

    @Override
    public boolean deleteDirectory(String path, long timestamp) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            return storage.deleteDirectory(path, timestamp);
        }
    }

    @Override
    public void pushActivePath(String setId, String path, BackupActivePath pendingFiles) throws IOException {
        if (!replayOnly) {
            try (RepositoryLock ignored = new RepositoryLock()) {
                ensureOpen();

                storage.pushActivePath(setId, path, pendingFiles);
            }
        }
    }

    @Override
    public boolean hasActivePath(String setId, String path) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            return storage.hasActivePath(setId, path);
        }
    }

    @Override
    public void popActivePath(String setId, String path) throws IOException {
        if (!replayOnly) {
            try (RepositoryLock ignored = new RepositoryLock()) {
                ensureOpen();

                storage.popActivePath(setId, path);
            }
        }
    }

    @Override
    public boolean deletePartialFile(BackupPartialFile file) throws IOException {
        if (!replayOnly) {
            try (RepositoryLock ignored = new RepositoryLock()) {
                ensureOpen();

                return storage.deletePartialFile(file);
            }
        } else {
            return false;
        }
    }

    @Override
    public void savePartialFile(BackupPartialFile file) throws IOException {
        if (!replayOnly) {
            try (RepositoryLock ignored = new RepositoryLock()) {
                ensureOpen();

                storage.savePartialFile(file);
            }
        }
    }

    @Override
    public void clearPartialFiles() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            storage.clearPartialFiles();
        }
    }

    @Override
    public BackupPartialFile getPartialFile(BackupPartialFile file) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            return storage.getPartialFile(file);
        }
    }

    @Override
    public TreeMap<String, BackupActivePath> getActivePaths(String setId) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            return storage.getActivePaths(setId);
        }
    }

    @Override
    public void flushLogging() throws IOException {
    }

    @Override
    public long getBlockCount() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            return storage.getBlockCount();
        }
    }

    @Override
    public long getFileCount() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            return storage.getFileCount();
        }
    }

    @Override
    public long getDirectoryCount() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            return storage.getDirectoryCount();
        }
    }

    @Override
    public long getPartCount() throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            return storage.getPartCount();
        }
    }

    @Override
    public void addAdditionalBlock(BackupBlockAdditional block) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            storage.addAdditionalBlock(block);
        }
    }

    @Override
    public BackupBlockAdditional additionalBlock(String publicKey, String blockHash) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            return storage.additionalBlock(publicKey, blockHash);
        }
    }

    @Override
    public void deleteAdditionalBlock(String publicKey, String blockHash) throws IOException {
        try (RepositoryLock ignored = new RepositoryLock()) {
            ensureOpen();

            storage.deleteAdditionalBlock(publicKey, blockHash);
        }
    }

    @Override
    public boolean addUpdatedFile(BackupUpdatedFile file, long howOften) throws IOException {
        try (CloseableLock ignored = acquireUpdateLock()) {
            ensureOpen();

            return storage.addUpdatedFile(file, howOften);
        }
    }

    @Override
    public void removeUpdatedFile(BackupUpdatedFile file) throws IOException {
        try (CloseableLock ignored = acquireUpdateLock()) {
            ensureOpen();

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
        if (repositoryInfo.version != getDefaultVersion()) {
            try (RepositoryLock ignored = new RepositoryLock()) {
                try (Closeable ignored2 = UIManager.registerTask("Upgrading metadata repository")) {
                    ensureOpen();

                    MetadataRepositoryStorage upgradedStorage = createStorage(getDefaultVersion());

                    new RepositoryUpgrader(storage, upgradedStorage).upgrade();

                    repositoryInfo.version = getDefaultVersion();
                    saveRepositoryInfo();

                    storage.close();
                    storage.clear();
                    storage = upgradedStorage;
                }
            }
        }
    }

    @Override
    public <K, V> CloseableMap<K, V> temporaryMap(MapSerializer<K, V> serializer) throws IOException {
        return storage.temporaryMap(serializer);
    }

    @Override
    public CloseableLock exclusiveLock() throws IOException {
        return storage.exclusiveLock();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class RepositoryInfo {
        private int version;
        private boolean alternateBlockTable;
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
        public void close() throws IOException {
            stream.close();
            lock.close();
        }
    }

    private class RepositoryLock extends CloseableLock {
        public RepositoryLock() {
            if (!LockingMetadataRepository.this.explicitLock.tryLock()) {
                synchronized (LockingMetadataRepository.this.explicitRequestLock) {
                    LockingMetadataRepository.this.explicitRequested = true;
                    try {
                        LockingMetadataRepository.this.explicitLock.lock();
                    } finally {
                        LockingMetadataRepository.this.explicitRequested = false;
                    }
                }
            }
        }

        @Override
        public void close() {
            LockingMetadataRepository.this.explicitLock.unlock();
        }

        @Override
        public boolean requested() {
            return LockingMetadataRepository.this.explicitRequested;
        }
    }

    private class OpenLock extends RepositoryLock {
        public OpenLock() {
            super();
            LockingMetadataRepository.this.openLock.lock();
        }

        @Override
        public void close() {
            LockingMetadataRepository.this.openLock.unlock();
            super.close();
        }
    }

    private class UpdateLock extends CloseableLock {
        public UpdateLock() {
            LockingMetadataRepository.this.updateLock.lock();
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
