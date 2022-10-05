package com.underscoreresearch.backup.file.implementation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.mapdb.serializer.SerializerArrayTuple;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupLocation;
import com.underscoreresearch.backup.model.BackupPartialFile;
import com.underscoreresearch.backup.model.BackupPendingSet;
import com.underscoreresearch.backup.utils.AccessLock;

@Slf4j
public class MapdbMetadataRepository implements MetadataRepository {
    private static final ObjectReader BACKUP_FILE_READER;
    private static final ObjectWriter BACKUP_FILE_WRITER;
    private static final ObjectReader BACKUP_BLOCK_READER;
    private static final ObjectWriter BACKUP_BLOCK_WRITER;
    private static final ObjectReader BACKUP_PART_READER;
    private static final ObjectWriter BACKUP_PART_WRITER;
    private static final ObjectReader BACKUP_ACTIVE_READER;
    private static final ObjectWriter BACKUP_ACTIVE_WRITER;
    private static final ObjectReader BACKUP_DIRECTORY_READER;
    private static final ObjectWriter BACKUP_DIRECTORY_WRITER;
    private static final ObjectReader BACKUP_PENDING_SET_READER;
    private static final ObjectWriter BACKUP_PENDING_SET_WRITER;
    private static final ObjectReader BACKUP_PARTIAL_FILE_READER;
    private static final ObjectWriter BACKUP_PARTIAL_FILE_WRITER;
    private static final ObjectReader REPOSITORY_INFO_READER;
    private static final ObjectWriter REPOSITORY_INFO_WRITER;

    private static final String REQUEST_LOCK_FILE = "request.lock";
    private static final String LOCK_FILE = "access.lock";

    private static final int INITIAL_VERSION = 0;
    private static final int CURRENT_VERSION = 1;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class RepositoryInfo {
        private int version;
        private String lastSyncedLogEntry;
    }

    private class MapDbRepositoryLock extends CloseableLock {
        public MapDbRepositoryLock() {
            synchronized (MapdbMetadataRepository.this.explicitRequestLock) {
                MapdbMetadataRepository.this.explicitRequested = true;
                try {
                    MapdbMetadataRepository.this.explicitLock.lock();
                } finally {
                    MapdbMetadataRepository.this.explicitRequested = false;
                }
            }
        }

        @Override
        public void close() {
            MapdbMetadataRepository.this.explicitLock.unlock();
        }

        @Override
        public boolean requested() {
            return MapdbMetadataRepository.this.explicitRequested;
        }
    }

    private class MapDbOpenLock extends MapDbRepositoryLock {
        public MapDbOpenLock() {
            super();
            MapdbMetadataRepository.this.openLock.lock();
        }

        @Override
        public void close() {
            MapdbMetadataRepository.this.openLock.unlock();
            super.close();
        }
    }

    static {
        ObjectMapper mapper = new ObjectMapper();

        BACKUP_FILE_READER = mapper.readerFor(BackupFile.class);
        BACKUP_FILE_WRITER = mapper.writerFor(BackupFile.class);
        BACKUP_BLOCK_READER = mapper.readerFor(BackupBlock.class);
        BACKUP_BLOCK_WRITER = mapper.writerFor(BackupBlock.class);
        BACKUP_PART_READER = mapper.readerFor(BackupFilePart.class);
        BACKUP_PART_WRITER = mapper.writerFor(BackupFilePart.class);
        BACKUP_ACTIVE_READER = mapper.readerFor(BackupActivePath.class);
        BACKUP_ACTIVE_WRITER = mapper.writerFor(BackupActivePath.class);
        BACKUP_DIRECTORY_READER = mapper.readerFor(new TypeReference<NavigableSet<String>>() {
        });
        BACKUP_DIRECTORY_WRITER = mapper.writerFor(new TypeReference<Set<String>>() {
        });
        BACKUP_PENDING_SET_READER = mapper.readerFor(BackupPendingSet.class);
        BACKUP_PENDING_SET_WRITER = mapper.writerFor(BackupPendingSet.class);
        BACKUP_PARTIAL_FILE_READER = mapper.readerFor(BackupPartialFile.class);
        BACKUP_PARTIAL_FILE_WRITER = mapper.writerFor(BackupPartialFile.class);
        REPOSITORY_INFO_READER = mapper.readerFor(RepositoryInfo.class);
        REPOSITORY_INFO_WRITER = mapper.writerFor(RepositoryInfo.class);
    }

    private static final String FILE_STORE = "files.db";
    private static final String BLOCK_STORE = "blocks.db";
    private static final String PARTS_STORE = "parts.db";
    private static final String DIRECTORY_STORE = "directories.db";
    private static final String ACTIVE_PATH_STORE = "paths.db";
    private static final String PENDING_SET_STORE = "pendingset.db";
    private static final String PARTIAL_FILE_STORE = "partialfiles.db";
    private static final String INFO_STORE = "info.json";

    private final String dataPath;
    private final boolean replayOnly;
    private boolean open;
    private boolean readOnly;

    private RepositoryInfo repositoryInfo;

    private DB blockDb;
    private DB fileDb;
    private DB directoryDb;
    private DB partsDb;
    private DB activePathDb;
    private DB pendingSetDb;
    private DB partialFileDb;

    private AccessLock fileLock;

    private HTreeMap<String, byte[]> blockMap;
    private TreeOrSink fileMap;
    private TreeOrSink directoryMap;
    private TreeOrSink partsMap;
    private TreeOrSink activePathMap;
    private HTreeMap<String, byte[]> pendingSetMap;
    private HTreeMap<String, byte[]> partialFileMap;

    public static class TreeOrSink {
        private BTreeMap<Object[], byte[]> tree;
        private DB.TreeMapSink<Object[], byte[]> sink;
        private Object[] lastKey;

        public TreeOrSink(BTreeMap<Object[], byte[]> tree) {
            this.tree = tree;
        }

        public TreeOrSink(DB.TreeMapSink<Object[], byte[]> sink) {
            this.sink = sink;
        }

        private void closeSink() {
            if (sink != null) {
                tree = sink.create();
                sink = null;
                lastKey = null;
            }
        }

        public void put(Object[] key, byte[] val) {
            if (tree != null) {
                tree.put(key, val);
            } else {
                if (lastKey != null) {
                    if (compareKeys(key, lastKey) <= 0) {
                        closeSink();
                        put(key, val);
                        return;
                    }
                }
                sink.put(key, val);
                lastKey = key;
            }
        }

        @SuppressWarnings("unchecked")
        private static int compareKeys(Object[] key1, Object[] key2) {
            for (int i = 0; i < key1.length; i++) {
                int compare = ((Comparable) key1[i]).compareTo(key2[i]);
                if (compare != 0) {
                    return compare;
                }
            }
            return 0;
        }

        public void close() {
            if (tree == null) {
                closeSink();
                tree.getStore().commit();
            }
            tree.close();
        }

        public NavigableMap<Object[], byte[]> prefixSubMap(Object[] objects) {
            if (tree == null) {
                closeSink();
            }
            return tree.prefixSubMap(objects);
        }

        public NavigableMap<Object[], byte[]> descendingMap() {
            if (tree == null) {
                closeSink();
            }
            return tree.descendingMap();
        }

        public NavigableMap<Object[], byte[]> ascendingMap() {
            if (tree == null) {
                closeSink();
            }
            return tree;
        }

        public byte[] remove(Object[] objects) {
            if (tree == null) {
                closeSink();
            }
            return tree.remove(objects);
        }

        public boolean containsKey(Object[] objects) {
            if (tree == null) {
                closeSink();
            }
            return tree.containsKey(objects);
        }

        public long size() {
            if (tree == null) {
                closeSink();
            }
            return tree.size();
        }
    }

    private ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    private Object explicitRequestLock = new Object();
    private boolean explicitRequested = false;
    private ReentrantLock explicitLock = new ReentrantLock();
    private ReentrantLock openLock = new ReentrantLock();

    private static Map<String, MapdbMetadataRepository> openRepositories = new HashMap<>();

    public MapdbMetadataRepository(String dataPath, boolean replayOnly) throws IOException {
        this.dataPath = dataPath;
        this.replayOnly = replayOnly;
    }

    private void commit() {
        openLock.lock();
        try {
            if (open) {
                blockDb.commit();
                fileDb.commit();
                directoryDb.commit();
                partsDb.commit();
                activePathDb.commit();
                pendingSetDb.commit();
                partialFileDb.commit();
            }
        } finally {
            openLock.unlock();
        }
    }

    public void open(boolean readOnly) throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbOpenLock()) {
            if (open && !readOnly && this.readOnly) {
                close();
            }
            if (!open) {
                synchronized (openRepositories) {
                    while (openRepositories.containsKey(dataPath)) {
                        openRepositories.get(dataPath).close();
                    }
                    openRepositories.put(dataPath, this);
                }
                open = true;
                this.readOnly = readOnly;

                if (readOnly) {
                    File requestFile = Paths.get(dataPath, REQUEST_LOCK_FILE).toFile();
                    fileLock = new AccessLock(Paths.get(dataPath, LOCK_FILE).toString());

                    AccessLock requestLock = new AccessLock(requestFile.getAbsolutePath());
                    if (!fileLock.tryLock(false)) {
                        log.info("Waiting for repository access from other process");
                        requestLock.lock(false);
                    }
                    fileLock.lock(false);

                    requestLock.release();
                } else {
                    scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1,
                            new ThreadFactoryBuilder().setNameFormat(getClass().getSimpleName() + "-%d").build());
                    scheduledThreadPoolExecutor.scheduleAtFixedRate(this::commit, 30, 30, TimeUnit.SECONDS);
                    scheduledThreadPoolExecutor.scheduleAtFixedRate(this::checkAccessRequest, 1, 1, TimeUnit.SECONDS);

                    fileLock = new AccessLock(Paths.get(dataPath, LOCK_FILE).toString());
                    if (!fileLock.tryLock(true)) {
                        log.info("Waiting for repository access from other process");
                        fileLock.lock(true);
                    }
                }

                openAllDataFiles(readOnly);
            }
        }
    }

    private void openAllDataFiles(boolean readOnly) throws IOException {
        readRepositoryInfo(readOnly);

        blockDb = createDb(readOnly, BLOCK_STORE);
        fileDb = createDb(readOnly, FILE_STORE);
        directoryDb = createDb(readOnly, DIRECTORY_STORE);
        partsDb = createDb(readOnly, PARTS_STORE);
        activePathDb = createDb(readOnly, ACTIVE_PATH_STORE);
        pendingSetDb = createDb(readOnly, PENDING_SET_STORE);
        partialFileDb = createDb(readOnly, PARTIAL_FILE_STORE);

        blockMap = openHashMap(blockDb.hashMap(BLOCK_STORE, Serializer.STRING, Serializer.BYTE_ARRAY));
        fileMap = openTreeMap(fileDb, fileDb.treeMap(FILE_STORE)
                .keySerializer(new SerializerArrayTuple(Serializer.STRING, Serializer.LONG))
                .valueSerializer(Serializer.BYTE_ARRAY));
        directoryMap = openTreeMap(directoryDb, directoryDb.treeMap(FILE_STORE)
                .keySerializer(new SerializerArrayTuple(Serializer.STRING, Serializer.LONG))
                .valueSerializer(Serializer.BYTE_ARRAY));
        activePathMap = openTreeMap(activePathDb, activePathDb.treeMap(ACTIVE_PATH_STORE)
                .keySerializer(new SerializerArrayTuple(Serializer.STRING, Serializer.STRING))
                .valueSerializer(Serializer.BYTE_ARRAY));
        partsMap = openTreeMap(partsDb, partsDb.treeMap(FILE_STORE)
                .keySerializer(new SerializerArrayTuple(Serializer.STRING, Serializer.STRING))
                .valueSerializer(Serializer.BYTE_ARRAY));
        pendingSetMap = openHashMap(pendingSetDb.hashMap(BLOCK_STORE, Serializer.STRING, Serializer.BYTE_ARRAY));
        partialFileMap = openHashMap(partialFileDb.hashMap(BLOCK_STORE, Serializer.STRING, Serializer.BYTE_ARRAY));
    }

    private TreeOrSink openTreeMap(DB db, DB.TreeMapMaker<Object[], byte[]> maker) throws IOException {
        switch (repositoryInfo.getVersion()) {
            case INITIAL_VERSION:
            case CURRENT_VERSION:
                // No storage parameters originally specified.
                break;
            default:
                throw new IOException(String.format("Unknown repository version {}",
                        repositoryInfo.getVersion()));
        }
        maker.counterEnable();
        if (db.nameCatalogLoad().size() == 0) {
            return new TreeOrSink(maker.createFromSink());
        }
        return new TreeOrSink(maker.createOrOpen());
    }

    private HTreeMap<String, byte[]> openHashMap(DB.HashMapMaker<String, byte[]> maker) throws IOException {
        switch (repositoryInfo.getVersion()) {
            case INITIAL_VERSION:
                // No storage parameters originally specified.
                break;
            case CURRENT_VERSION:
                maker.layout(16, 64, 4);
                // 268,435,456 entries before hash collisions
                break;
            default:
                throw new IOException(String.format("Unknown repository version {}",
                        repositoryInfo.getVersion()));
        }
        maker.counterEnable();
        return maker.createOrOpen();
    }

    private void readRepositoryInfo(boolean readonly) throws IOException {
        File file = Paths.get(dataPath, INFO_STORE).toFile();
        if (file.exists()) {
            repositoryInfo = REPOSITORY_INFO_READER.readValue(file);
        } else {
            if (Paths.get(dataPath, FILE_STORE).toFile().exists()) {
                repositoryInfo = RepositoryInfo.builder().version(INITIAL_VERSION).build();
            } else {
                repositoryInfo = RepositoryInfo.builder().version(CURRENT_VERSION).build();
            }
            if (!readonly) {
                saveRepositoryInfo();
            }
        }
    }

    private void saveRepositoryInfo() throws IOException {
        File file = Paths.get(dataPath, INFO_STORE).toFile();
        REPOSITORY_INFO_WRITER.writeValue(file, repositoryInfo);
    }

    private DB createDb(boolean readOnly, String blockStore) {
        DBMaker.Maker maker = DBMaker
                .fileDB(Paths.get(dataPath, blockStore).toString())
                .fileMmapEnableIfSupported()
                .fileMmapPreclearDisable()
                .transactionEnable();
        if (readOnly)
            maker.readOnly();
        return maker.make();
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
            AccessLock requestLock = new AccessLock(Paths.get(dataPath, REQUEST_LOCK_FILE).toString());

            if (!requestLock.tryLock(true)) {
                log.info("Detected request for access to metadata from other process");

                MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
                repository.flushLogging();

                close();

                requestLock.lock(true);
                requestLock.close();
                open(readOnly);
                log.info("Metadata access restored from other process");
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
        try (MapDbRepositoryLock ignored = new MapDbOpenLock()) {
            if (open) {
                closeAllDataFiles();

                fileLock.close();
                open = false;

                synchronized (openRepositories) {
                    openRepositories.remove(dataPath);
                    openRepositories.notify();
                }
            }
        }
    }

    private void closeAllDataFiles() {
        if (scheduledThreadPoolExecutor != null) {
            scheduledThreadPoolExecutor.shutdownNow();
            scheduledThreadPoolExecutor = null;
        }

        commit();

        blockMap.close();
        fileMap.close();
        directoryMap.close();
        activePathMap.close();
        partsMap.close();
        pendingSetMap.close();
        partialFileMap.close();

        blockDb.close();
        fileDb.close();
        directoryDb.close();
        partsDb.close();
        activePathDb.close();
        pendingSetDb.close();
        partialFileDb.close();
    }

    @Override
    public List<BackupFile> file(String path) throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();

            Map<Object[], byte[]> query =
                    fileMap.prefixSubMap(new Object[]{path});
            List<BackupFile> files = null;
            for (Map.Entry<Object[], byte[]> entry : query.entrySet()) {
                if (files == null) {
                    files = new ArrayList<>();
                }
                files.add(decodeFile(entry));
            }
            return files;
        }
    }

    private BackupFile decodeFile(Map.Entry<Object[], byte[]> entry) throws IOException {
        BackupFile readValue = decodeData(BACKUP_FILE_READER, entry.getValue());
        readValue.setPath((String) entry.getKey()[0]);
        readValue.setAdded((Long) entry.getKey()[1]);
        if (readValue.getLastChanged() == null)
            readValue.setLastChanged(readValue.getAdded());

        return readValue;
    }

    @Override
    public List<BackupFilePart> existingFilePart(String partHash) throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();

            Map<Object[], byte[]> query =
                    partsMap.prefixSubMap(new Object[]{partHash});
            List<BackupFilePart> parts = null;
            for (Map.Entry<Object[], byte[]> entry : query.entrySet()) {
                if (parts == null)
                    parts = new ArrayList<>();
                parts.add(decodePath(entry));
            }
            return parts;
        }
    }

    private void ensureLocked() {
        if (!explicitLock.isHeldByCurrentThread()) {
            throw new IllegalAccessError("Called without acquiring lock");
        }
    }

    @Override
    public Stream<BackupFile> allFiles(boolean ascending) throws IOException {
        ensureLocked();
        ensureOpen();

        final Map<Object[], byte[]> map;
        if (ascending) {
            map = fileMap.ascendingMap();
        } else {
            map = fileMap.descendingMap();
        }

        return map.entrySet().stream().map((entry) -> {
            try {
                return decodeFile(entry);
            } catch (IOException e) {
                log.error("Invalid file " + entry.getKey()[0], e);
                return BackupFile.builder().build();
            }
        }).filter(t -> t.getPath() != null);
    }

    @Override
    public Stream<BackupBlock> allBlocks() throws IOException {
        ensureLocked();
        ensureOpen();

        return blockMap.entrySet().stream().map((entry) -> {
            try {
                return decodeBlock(entry.getKey(), entry.getValue());
            } catch (IOException e) {
                log.error("Invalid block " + entry.getKey(), e);
                return BackupBlock.builder().build();
            }
        }).filter(t -> t.getHash() != null);
    }

    @Override
    public Stream<BackupFilePart> allFileParts() throws IOException {
        ensureLocked();
        ensureOpen();

        return partsMap.ascendingMap().entrySet().stream().map((entry) -> {
            try {
                return decodePath(entry);
            } catch (IOException e) {
                log.error("Invalid block " + entry.getKey(), e);
                return BackupFilePart.builder().build();
            }
        }).filter(t -> t.getPartHash() != null);
    }

    @Override
    public Stream<BackupDirectory> allDirectories(boolean ascending) throws IOException {
        ensureLocked();
        ensureOpen();

        final Map<Object[], byte[]> map;
        if (ascending) {
            map = directoryMap.ascendingMap();
        } else {
            map = directoryMap.descendingMap();
        }

        return map.entrySet().stream().map((entry) -> {
            try {
                return new BackupDirectory((String) entry.getKey()[0], (Long) entry.getKey()[1],
                        decodeData(BACKUP_DIRECTORY_READER, entry.getValue()));
            } catch (IOException e) {
                log.error("Invalid directory " + entry.getKey()[0], e);
                return new BackupDirectory();
            }
        });
    }

    @Override
    public void addPendingSets(BackupPendingSet scheduledTime) throws IOException {
        if (!replayOnly) {
            try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
                ensureOpen();

                pendingSetMap.put(scheduledTime.getSetId(), encodeData(BACKUP_PENDING_SET_WRITER, scheduledTime));
            }
        }
    }

    @Override
    public void deletePendingSets(String setId) throws IOException {
        if (!replayOnly) {
            try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
                ensureOpen();

                pendingSetMap.remove(setId);
            }
        }
    }

    @Override
    public Set<BackupPendingSet> getPendingSets() throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();

            return pendingSetMap.entrySet().stream().map((entry) -> {
                try {
                    BackupPendingSet set = decodeData(BACKUP_PENDING_SET_READER, entry.getValue());
                    set.setSetId(entry.getKey());
                    return set;
                } catch (IOException e) {
                    log.error("Invalid pending set " + entry.getKey(), e);
                    return null;
                }
            }).filter(t -> t != null).collect(Collectors.toSet());
        }
    }

    @Override
    public CloseableLock acquireLock() {
        return new MapDbRepositoryLock();
    }

    private BackupFilePart decodePath(Map.Entry<Object[], byte[]> entry) throws IOException {
        BackupFilePart readValue = decodeData(BACKUP_PART_READER, entry.getValue());
        readValue.setPartHash((String) entry.getKey()[0]);
        readValue.setBlockHash((String) entry.getKey()[1]);
        return readValue;
    }

    @Override
    public BackupFile lastFile(String path) throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();

            NavigableMap<Object[], byte[]> query =
                    fileMap.prefixSubMap(new Object[]{path});
            if (query.size() > 0) {
                Map.Entry<Object[], byte[]> entry = query.lastEntry();
                return decodeFile(entry);
            }
            return null;
        }
    }

    @Override
    public BackupBlock block(String hash) throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();

            byte[] data = blockMap.get(hash);
            if (data != null) {
                BackupBlock block = decodeBlock(hash, data);
                block.setHash(hash);
                return block;
            }
            return null;
        }
    }

    private BackupBlock decodeBlock(String hash, byte[] data) throws IOException {
        BackupBlock block = decodeData(BACKUP_BLOCK_READER, data);
        block.setHash(hash);
        return block;
    }

    @Override
    public List<BackupDirectory> directory(String path) throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();

            Map<Object[], byte[]> query =
                    directoryMap.prefixSubMap(new Object[]{path});
            List<BackupDirectory> directories = null;
            for (Map.Entry<Object[], byte[]> entry : query.entrySet()) {
                if (directories == null) {
                    directories = new ArrayList<>();
                }
                directories.add(new BackupDirectory(path,
                        (Long) entry.getKey()[1],
                        decodeData(BACKUP_DIRECTORY_READER, entry.getValue())));
            }
            return directories;
        }
    }

    @Override
    public BackupDirectory lastDirectory(String path) throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();

            NavigableMap<Object[], byte[]> query =
                    directoryMap.prefixSubMap(new Object[]{path});
            if (query.size() > 0) {
                Map.Entry<Object[], byte[]> entry = query.lastEntry();
                return new BackupDirectory(path, (Long) entry.getKey()[1],
                        decodeData(BACKUP_DIRECTORY_READER, entry.getValue()));
            }
            return null;
        }
    }

    @Override
    public void addFile(BackupFile file) throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();

            Long added;
            if (file.getAdded() == null)
                added = file.getLastChanged();
            else
                added = file.getAdded();

            fileMap.put(new Object[]{file.getPath(), added},
                    encodeData(BACKUP_FILE_WRITER, strippedCopy(file)));

            if (!replayOnly) {
                if (file.getLocations() != null) {
                    for (BackupLocation location : file.getLocations()) {
                        for (BackupFilePart part : location.getParts()) {
                            if (part.getPartHash() != null) {
                                partsMap.put(new Object[]{part.getPartHash(), part.getBlockHash()},
                                        encodeData(BACKUP_PART_WRITER, strippedCopy(part)));
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public String lastSyncedLogFile() throws IOException {
        return repositoryInfo.getLastSyncedLogEntry();
    }

    @Override
    public void setLastSyncedLogFile(String entry) throws IOException {
        repositoryInfo.setLastSyncedLogEntry(entry);
        saveRepositoryInfo();
    }

    private BackupFilePart strippedCopy(BackupFilePart part) {
        return BackupFilePart.builder().blockIndex(part.getBlockIndex()).build();
    }

    private BackupFile strippedCopy(BackupFile file) {
        return BackupFile.builder()
                .length(file.getLength())
                .locations(file.getLocations())
                .deleted(file.getDeleted())
                .lastChanged(file.getLastChanged())
                .build();
    }

    @Override
    public void addBlock(BackupBlock block) throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();

            blockMap.put(block.getHash(), encodeData(BACKUP_BLOCK_WRITER, stripCopy(block)));
        }
    }

    private BackupBlock stripCopy(BackupBlock block) {
        return BackupBlock.builder().storage(block.getStorage()).format(block.getFormat()).created(block.getCreated())
                .hashes(block.getHashes()).offsets(block.getOffsets())
                .build();
    }

    @Override
    public void addDirectory(BackupDirectory directory) throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();

            directoryMap.put(new Object[]{directory.getPath(), directory.getAdded()},
                    encodeData(BACKUP_DIRECTORY_WRITER, directory.getFiles()));
        }
    }

    @Override
    public boolean deleteBlock(BackupBlock block) throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();

            return blockMap.remove(block.getHash()) != null;
        }
    }

    @Override
    public boolean deleteFile(BackupFile file) throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();

            return fileMap.remove(new Object[]{file.getPath(), file.getAdded()}) != null;
        }
    }

    @Override
    public boolean deleteFilePart(BackupFilePart part) throws IOException {
        if (!replayOnly) {
            try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
                ensureOpen();

                return partsMap.remove(new Object[]{part.getPartHash(), part.getBlockHash()}) != null;
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean deleteDirectory(String path, long timestamp) throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();

            return directoryMap.remove(new Object[]{path, timestamp}) != null;
        }
    }

    @Override
    public void pushActivePath(String setId, String path, BackupActivePath pendingFiles) throws IOException {
        if (!replayOnly) {
            try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
                ensureOpen();

                activePathMap.put(new Object[]{setId, path}, encodeData(BACKUP_ACTIVE_WRITER, pendingFiles));
            }
        }
    }

    private byte[] encodeData(ObjectWriter writer, Object obj) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
                writer.writeValue(gzipOutputStream, obj);
            }
            return outputStream.toByteArray();
        }
    }

    private <T> T decodeData(ObjectReader reader, byte[] data) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
            try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                return reader.readValue(gzipInputStream);
            }
        }
    }

    @Override
    public boolean hasActivePath(String setId, String path) throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();

            return activePathMap.containsKey(new Object[]{setId, path});
        }
    }

    @Override
    public void popActivePath(String setId, String path) throws IOException {
        if (!replayOnly) {
            try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
                ensureOpen();

                activePathMap.remove(new Object[]{setId, path});
            }
        }
    }

    @Override
    public boolean deletePartialFile(BackupPartialFile file) throws IOException {
        if (!replayOnly) {
            try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
                ensureOpen();

                return partialFileMap.remove(file.getFile().getPath()) != null;
            }
        } else {
            return false;
        }
    }

    @Override
    public void savePartialFile(BackupPartialFile file) throws IOException {
        if (!replayOnly) {
            try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
                ensureOpen();

                partialFileMap.put(file.getFile().getPath(), encodeData(BACKUP_PARTIAL_FILE_WRITER, file));
            }
        }
    }

    @Override
    public void clearPartialFiles() throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();

            partialFileMap.clear();
        }
    }

    @Override
    public BackupPartialFile getPartialFile(BackupPartialFile file) throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();

            byte[] data = partialFileMap.get(file.getFile().getPath());
            if (data != null) {
                BackupPartialFile ret = decodeData(BACKUP_PARTIAL_FILE_READER, data);
                if (Objects.equals(ret.getFile().getLength(), file.getFile().getLength())
                        && Objects.equals(ret.getFile().getLastChanged(), file.getFile().getLastChanged())) {
                    return ret;
                }
            }
            return null;
        }
    }

    @Override
    public TreeMap<String, BackupActivePath> getActivePaths(String setId) throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();

            Map<Object[], byte[]> readMap = activePathMap.ascendingMap();
            if (setId != null)
                readMap = activePathMap.prefixSubMap(new Object[]{setId});

            TreeMap<String, BackupActivePath> ret = new TreeMap<>();
            for (Map.Entry<Object[], byte[]> entry : readMap.entrySet()) {
                BackupActivePath activePath = decodeData(BACKUP_ACTIVE_READER, entry.getValue());
                String path = (String) entry.getKey()[1];
                activePath.setParentPath(path);
                activePath.setSetIds(Lists.newArrayList((String) entry.getKey()[0]));

                BackupActivePath existingActive = ret.get(path);
                if (existingActive != null) {
                    activePath.mergeChanges(existingActive);
                }

                ret.put(path, activePath);
            }
            return ret;
        }
    }

    private void ensureOpen() throws IOException {
        if (!open) {
            open(readOnly);
        }
    }

    @Override
    public void flushLogging() throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            if (open) {
                commit();
            }
        }
    }

    public long getBlockCount() throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();
            return blockMap.size();
        }
    }

    public long getFileCount() throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();
            return fileMap.size();
        }
    }

    public long getDirectoryCount() throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();
            return directoryMap.size();
        }
    }

    public long getPartCount() throws IOException {
        try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
            ensureOpen();
            return partsMap.size();
        }
    }

    public void clear() throws IOException {
        if (readOnly) {
            throw new IOException("Tried to clear read only repository");
        }
        try (MapDbOpenLock ignoed2 = new MapDbOpenLock()) {
            try (MapDbRepositoryLock ignored = new MapDbRepositoryLock()) {
                closeAllDataFiles();

                for (File file : new File(dataPath).listFiles()) {
                    if (file.isFile() &&
                            !file.getName().equals(LOCK_FILE) &&
                            !file.getName().equals(REQUEST_LOCK_FILE)) {
                        if (!file.delete()) {
                            log.error("Failed to delete file {} clearing repository", file.getAbsolutePath());
                        }
                    }
                }

                openAllDataFiles(false);
            }
        }
    }
}
