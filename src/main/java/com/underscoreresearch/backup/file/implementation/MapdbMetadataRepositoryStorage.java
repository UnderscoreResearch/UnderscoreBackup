package com.underscoreresearch.backup.file.implementation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableMap;
import com.underscoreresearch.backup.file.CloseableSortedMap;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.MapSerializer;
import com.underscoreresearch.backup.file.MetadataRepositoryStorage;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.file.RepositoryOpenMode;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockAdditional;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupPartialFile;
import com.underscoreresearch.backup.model.BackupPendingSet;
import com.underscoreresearch.backup.model.BackupUpdatedFile;
import com.underscoreresearch.backup.model.ExternalBackupFile;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.mapdb.serializer.SerializerArrayTuple;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.underscoreresearch.backup.file.implementation.LockingMetadataRepository.MAPDB_STORAGE_LEAF_STORAGE;
import static com.underscoreresearch.backup.file.implementation.LockingMetadataRepository.MINIMUM_WAIT_UPDATE_MS;
import static com.underscoreresearch.backup.io.IOUtils.clearTempFiles;
import static com.underscoreresearch.backup.io.IOUtils.deleteContents;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_ACTIVE_PATH_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_ACTIVE_PATH_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_BLOCK_ADDITIONAL_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_BLOCK_ADDITIONAL_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_BLOCK_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_BLOCK_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_DIRECTORY_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_DIRECTORY_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_FILE_PART_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_FILE_PART_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_FILE_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_FILE_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_PARTIAL_FILE_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_PARTIAL_FILE_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_PENDING_SET_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_PENDING_SET_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Implementation of MetadataRepositoryStorage using MapDB.
 * Provides persistent storage for backup metadata using MapDB database files.
 */
@Slf4j
public class MapdbMetadataRepositoryStorage implements MetadataRepositoryStorage {
    private static final ObjectReader BACKUP_DIRECTORY_FILES_LEGACY_READER
            = MAPPER.readerFor(new TypeReference<NavigableSet<String>>() {
    });

    private static final String FILE_STORE = "files.db";
    private static final String BLOCK_STORE = "blocks.db";
    private static final String BLOCK_ALT_STORE = "blocks2.db";
    private static final String PARTS_STORE = "parts.db";
    private static final String DIRECTORY_STORE = "directories.db";
    private static final String ACTIVE_PATH_STORE = "paths.db";
    private static final String PENDING_SET_STORE = "pendingset.db";
    private static final String PARTIAL_FILE_STORE = "partialfiles.db";
    private static final String ADDITIONAL_BLOCK_STORE = "additionalblocks.db";
    private static final String UPDATED_FILES_STORE = "updatedfiles.db";
    private static final String UPDATED_PENDING_FILES_STORE = "updatedpendingfiles.db";
    private static final long MAX_WRITES = 50000;
    private final String dataPath;
    private final int revision;
    private final int version;
    private final AtomicInteger writeCounter = new AtomicInteger();
    private DB blockDb;
    private DB blockTmpDb;
    private DB fileDb;
    private DB directoryDb;
    private DB partsDb;
    private DB activePathDb;
    private DB pendingSetDb;
    private DB partialFileDb;
    private DB additionalBlockDb;
    private DB updatedFilesDb;
    private DB updatedPendingFilesDb;

    private HTreeMap<String, byte[]> blockMap;
    private HTreeMap<String, byte[]> blockTmpMap;
    private TreeOrSink additionalBlockMap;
    private TreeOrSink fileMap;
    private TreeOrSink directoryMap;
    private TreeOrSink partsMap;
    private TreeOrSink activePathMap;
    private TreeOrSink updatedPendingFilesMap;
    private HTreeMap<String, byte[]> pendingSetMap;
    private HTreeMap<String, byte[]> partialFileMap;
    private HTreeMap<String, Long> updatedFilesMap;
    private boolean alternateBlockTable;
    private RepositoryOpenMode openMode;
    private boolean useLeafNodes;

    /**
     * Creates a new MapdbMetadataRepositoryStorage instance.
     *
     * @param dataPath            Path to the directory where database files will be stored
     * @param version             Version of the storage format
     * @param revision            Revision number of the storage
     * @param alternateBlockTable Whether to use the alternate block table
     */
    public MapdbMetadataRepositoryStorage(String dataPath, int version, int revision, boolean alternateBlockTable) {
        if (nonVersionedPath(version, revision)) {
            this.dataPath = dataPath;
            this.revision = revision;
            this.version = version;
        } else {
            if (revision > 0) {
                this.dataPath = Paths.get(dataPath, String.format("v%d-%d", version, revision)).toString();
            } else {
                this.dataPath = Paths.get(dataPath, String.format("v%d", version)).toString();
            }
            this.revision = revision;
            this.version = version;
            if (version == MAPDB_STORAGE_LEAF_STORAGE)
                this.useLeafNodes = true;
        }
        this.alternateBlockTable = alternateBlockTable;
    }

    /**
     * Determines if the path should not include version information.
     *
     * @param version  Version of the storage format
     * @param revision Revision number of the storage
     * @return true if the path should not include version information
     */
    private static boolean nonVersionedPath(int version, int revision) {
        return revision == 0 && version <= 1;
    }

    /**
     * Opens a tree map with the specified parameters.
     *
     * @param db          The database to open the tree map in
     * @param maker       The tree map maker
     * @param largeValues Whether the tree map will store large values
     * @return A TreeOrSink instance containing the opened tree map
     */
    private static TreeOrSink openTreeMap(DB db, DB.TreeMapMaker<Object[], byte[]> maker, boolean largeValues) {
        maker.counterEnable();
        if (largeValues)
            maker.valuesOutsideNodesEnable();
        if (db.nameCatalogLoad().isEmpty()) {
            return new TreeOrSink(maker.createFromSink());
        }
        return new TreeOrSink(maker.createOrOpen());
    }

    /**
     * Creates a database maker for the specified block store.
     *
     * @param blockStore Path to the block store file
     * @return A configured database maker
     */
    private static DBMaker.Maker createDbMaker(String blockStore) {
        DBMaker.Maker maker = DBMaker
                .fileDB(blockStore)
                .fileMmapPreclearDisable();
        if (SystemUtils.IS_OS_WINDOWS)
            maker.fileChannelEnable();
        else
            maker.fileMmapEnableIfSupported();
        return maker;
    }

    /**
     * Commits all pending changes to the database files.
     * This ensures that all changes are persisted to disk.
     */
    @Override
    public void commit() {
        additionalBlockDb.commit();
        blockDb.commit();
        fileDb.commit();
        partsDb.commit();
        directoryDb.commit();
        activePathDb.commit();
        pendingSetDb.commit();
        partialFileDb.commit();
        updatedFilesDb.commit();
        updatedPendingFilesDb.commit();

        if (blockTmpDb != null) {
            blockTmpDb.commit();
        }
        writeCounter.set(0);
    }

    /**
     * Determines if this storage implementation needs periodic commits.
     *
     * @return true, as MapDB requires periodic commits for optimal performance
     */
    @Override
    public boolean needPeriodicCommits() {
        return true;
    }

    /**
     * Creates a temporary map for storing key-value pairs.
     *
     * @param serializer The serializer to use for keys and values
     * @return A closeable map that will be automatically cleaned up when closed
     * @throws IOException if an I/O error occurs
     */
    @Override
    public <K, V> CloseableMap<K, V> temporaryMap(MapSerializer<K, V> serializer) throws IOException {
        return new TemporaryMapdbMap<>(serializer);
    }

    /**
     * Creates a temporary sorted map for storing key-value pairs in sorted order.
     *
     * @param serializer The serializer to use for keys and values
     * @return A closeable sorted map that will be automatically cleaned up when closed
     * @throws IOException if an I/O error occurs
     */
    @Override
    public <K, V> CloseableSortedMap<K, V> temporarySortedMap(MapSerializer<K, V> serializer) throws IOException {
        return new TemporaryMapdbSortedMap<>(serializer);
    }

    /**
     * Determines if this storage implementation needs an exclusive lock during commits.
     *
     * @return true, as MapDB requires exclusive access during commits
     */
    @Override
    public boolean needExclusiveCommitLock() {
        return true;
    }

    /**
     * Acquires an exclusive lock for this storage.
     * This implementation returns a dummy lock that does nothing.
     *
     * @return A dummy lock
     * @throws IOException if an I/O error occurs
     */
    @Override
    public CloseableLock exclusiveLock() throws IOException {
        return new CloseableLock() {
            /**
             * Closes the map and releases resources.
             * First closes the underlying tree map, then calls the parent close method.
             */
            @Override
            public void close() {
            }

            @Override
            public boolean requested() {
                return false;
            }
        };
    }

    /**
     * Opens all database files and initializes the storage.
     *
     * @param openMode The mode to open the repository in (read-only, read-write, etc.)
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void open(RepositoryOpenMode openMode) throws IOException {
        this.openMode = openMode;
        writeCounter.set(0);

        blockDb = createDb(openMode, alternateBlockTable ? BLOCK_ALT_STORE : BLOCK_STORE);
        fileDb = createDb(openMode, FILE_STORE);
        directoryDb = createDb(openMode, DIRECTORY_STORE);
        partsDb = createDb(openMode, PARTS_STORE);
        activePathDb = createDb(openMode, ACTIVE_PATH_STORE);
        pendingSetDb = createDb(openMode, PENDING_SET_STORE);
        partialFileDb = createDb(openMode, PARTIAL_FILE_STORE);
        additionalBlockDb = createDb(openMode, ADDITIONAL_BLOCK_STORE);
        updatedFilesDb = createDb(openMode, UPDATED_FILES_STORE);
        updatedPendingFilesDb = createDb(openMode, UPDATED_PENDING_FILES_STORE);

        blockMap = openHashMap(blockDb.hashMap(BLOCK_STORE, Serializer.STRING, Serializer.BYTE_ARRAY));
        additionalBlockMap = openTreeMap(additionalBlockDb, additionalBlockDb.treeMap(ADDITIONAL_BLOCK_STORE)
                .keySerializer(new SerializerArrayTuple(Serializer.STRING, Serializer.STRING))
                .valueSerializer(Serializer.BYTE_ARRAY), false);
        fileMap = openTreeMap(fileDb, fileDb.treeMap(FILE_STORE)
                .keySerializer(new SerializerArrayTuple(Serializer.STRING, Serializer.LONG))
                .valueSerializer(Serializer.BYTE_ARRAY), useLeafNodes);
        directoryMap = openTreeMap(directoryDb, directoryDb.treeMap(FILE_STORE)
                .keySerializer(new SerializerArrayTuple(Serializer.STRING, Serializer.LONG))
                .valueSerializer(Serializer.BYTE_ARRAY), useLeafNodes);
        activePathMap = openTreeMap(activePathDb, activePathDb.treeMap(ACTIVE_PATH_STORE)
                .keySerializer(new SerializerArrayTuple(Serializer.STRING, Serializer.STRING))
                .valueSerializer(Serializer.BYTE_ARRAY), useLeafNodes);
        partsMap = openTreeMap(partsDb, partsDb.treeMap(FILE_STORE)
                .keySerializer(new SerializerArrayTuple(Serializer.STRING, Serializer.STRING))
                .valueSerializer(Serializer.BYTE_ARRAY), false);
        updatedPendingFilesMap = openTreeMap(updatedPendingFilesDb, updatedPendingFilesDb.treeMap(UPDATED_PENDING_FILES_STORE)
                .keySerializer(new SerializerArrayTuple(Serializer.LONG, Serializer.STRING))
                .valueSerializer(Serializer.BYTE_ARRAY), useLeafNodes);
        pendingSetMap = openHashMap(pendingSetDb.hashMap(BLOCK_STORE, Serializer.STRING, Serializer.BYTE_ARRAY));
        partialFileMap = openHashMap(partialFileDb.hashMap(BLOCK_STORE, Serializer.STRING, Serializer.BYTE_ARRAY));
        updatedFilesMap = openHashMap(updatedFilesDb.hashMap(UPDATED_FILES_STORE, Serializer.STRING, Serializer.LONG));

        if (openMode != RepositoryOpenMode.READ_ONLY) {
            clearTempFiles();
        }
    }

    /**
     * Gets the temporary block map, creating it if it does not exist.
     *
     * @return The temporary block map
     */
    private HTreeMap<String, byte[]> getBlockTmpMap() {
        if (blockTmpMap == null) {
            deleteAlternativeBlocksTable();
            blockTmpDb = createDb(openMode, alternateBlockTable ? BLOCK_STORE : BLOCK_ALT_STORE);
            // Need to be BLOCK_STORE here, as we are copying from BLOCK_TMP_STORE to BLOCK_STORE eventually.
            blockTmpMap = openHashMap(blockTmpDb.hashMap(BLOCK_STORE, Serializer.STRING, Serializer.BYTE_ARRAY));
        }
        return blockTmpMap;
    }

    /**
     * Opens a hash map with the specified parameters.
     *
     * @param maker The hash map maker
     * @return The opened hash map
     */
    private <T> HTreeMap<String, T> openHashMap(DB.HashMapMaker<String, T> maker) {
        hashSetup(maker);
        maker.counterEnable();
        return maker.createOrOpen();
    }

    /**
     * Configures a hash map maker with optimal layout parameters.
     *
     * @param maker The hash map maker to configure
     */
    protected <T> void hashSetup(DB.HashMapMaker<String, T> maker) {
        maker.layout(16, 64, 4);
    }

    /**
     * Creates a database with the specified parameters.
     *
     * @param openMode   The mode to open the database in
     * @param blockStore The name of the block store file
     * @return The created database
     */
    private DB createDb(RepositoryOpenMode openMode, String blockStore) {
        IOUtils.createDirectory(new File(this.dataPath), true);

        DBMaker.Maker maker = createDbMaker(getPath(blockStore).toString());

        if (openMode != RepositoryOpenMode.WITHOUT_TRANSACTION) {
            maker.transactionEnable();
        }

        if (openMode == RepositoryOpenMode.READ_ONLY)
            maker.readOnly();
        return maker.make();
    }

    /**
     * Closes all database files and releases resources.
     * This method commits any pending changes before closing.
     */
    @Override
    public void close() {
        commit();
        additionalBlockMap.close();
        blockMap.close();
        fileMap.close();
        partsMap.close();
        directoryMap.close();
        activePathMap.close();
        pendingSetMap.close();
        partialFileMap.close();
        updatedFilesMap.close();
        updatedPendingFilesMap.close();

        additionalBlockDb.close();
        blockDb.close();
        fileDb.close();
        partsDb.close();
        directoryDb.close();
        activePathDb.close();
        pendingSetDb.close();
        partialFileDb.close();
        updatedFilesDb.close();
        updatedPendingFilesDb.close();

        if (blockTmpMap != null) {
            blockTmpMap.close();
            blockTmpMap = null;
        }
        if (blockTmpDb != null) {
            blockTmpDb.close();
            blockTmpDb = null;
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
        NavigableMap<Object[], byte[]> query =
                fileMap.prefixSubMap(new Object[]{path});
        List<ExternalBackupFile> files = null;
        for (Map.Entry<Object[], byte[]> entry : query.entrySet()) {
            if (entry != null) {
                if (files == null) {
                    files = new ArrayList<>();
                }
                files.add(new ExternalBackupFile(decodeFile(entry)));
            }
        }
        return files;
    }

    /**
     * Decodes a file entry from the database.
     *
     * @param entry The map entry containing the encoded file data
     * @return The decoded BackupFile
     * @throws IOException if an I/O error occurs during decoding
     */
    private BackupFile decodeFile(Map.Entry<Object[], byte[]> entry) throws IOException {
        try {
            BackupFile readValue = decodeData(BACKUP_FILE_READER, entry.getValue());
            readValue.setPath((String) entry.getKey()[0]);
            readValue.setAdded((Long) entry.getKey()[1]);
            if (readValue.getLastChanged() == null)
                readValue.setLastChanged(readValue.getAdded());

            return readValue;
        } catch (Exception exc) {
            throw new IOException(String.format("Failed to decode file \"%s:%s\"", entry.getKey()[0], entry.getKey()[1]),
                    exc);
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

    /**
     * Creates a log message for invalid repository entries.
     * The message will indicate whether the entry will be removed or not based on the open mode.
     *
     * @param msg The base message
     * @return The formatted log message
     */
    private String invalidRepositoryLogEntry(String msg) {
        if (openMode == RepositoryOpenMode.READ_ONLY)
            return msg + " (Read only repository)";
        return msg + " (Removing from repository)";
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
        final Map<Object[], byte[]> map;
        if (ascending) {
            map = fileMap.ascendingMap();
        } else {
            map = fileMap.descendingMap();
        }

        Stream<BackupFile> stream = map.entrySet().stream().map((entry) -> {
            try {
                return decodeFile(entry);
            } catch (IOException e) {
                log.error(invalidRepositoryLogEntry("Invalid file \"{}:{}\""), PathNormalizer.physicalPath((String) entry.getKey()[0]), entry.getKey()[1], e);
                if (openMode != RepositoryOpenMode.READ_ONLY) {
                    try {
                        if (fileMap.remove(entry.getKey()) == null) {
                            log.error("Delete indicated no entry was deleted");
                        }
                    } catch (Exception exc) {
                        log.error("Failed to delete invalid entry", exc);
                    }
                }
                return null;
            }
        });

        return new MapdbCloseableStream<>(stream);
    }

    /**
     * Retrieves all blocks in the repository.
     *
     * @return A closeable stream of backup blocks
     * @throws IOException if an I/O error occurs
     */
    @Override
    public CloseableStream<BackupBlock> allBlocks() throws IOException {
        Stream<BackupBlock> stream = blockMap.entrySet().stream().map((entry) -> {
            try {
                return decodeBlock(entry.getKey(), entry.getValue());
            } catch (IOException e) {
                log.error(invalidRepositoryLogEntry("Invalid block \"{}\""), entry.getKey(), e);
                if (openMode != RepositoryOpenMode.READ_ONLY) {
                    try {
                        if (blockMap.remove(entry.getKey()) == null) {
                            log.error("Delete indicated no entry was deleted");
                        }
                    } catch (Exception exc) {
                        log.error("Failed to delete invalid entry", exc);
                    }
                }
                return null;
            }
        });

        return new MapdbCloseableStream<>(stream);
    }

    /**
     * Retrieves all file parts in the repository.
     *
     * @return A closeable stream of backup file parts
     */
    @Override
    public CloseableStream<BackupFilePart> allFileParts() {
        Stream<BackupFilePart> stream = partsMap.ascendingMap().entrySet().stream().map((entry) -> {
            try {
                return decodePath(entry);
            } catch (IOException e) {
                log.error(invalidRepositoryLogEntry("Invalid filePart \"{}:{}\""), entry.getKey()[0], entry.getKey()[1], e);
                if (openMode != RepositoryOpenMode.READ_ONLY) {
                    try {
                        if (partsMap.remove(entry.getKey()) == null) {
                            log.error("Delete indicated no entry was deleted");
                        }
                    } catch (Exception exc) {
                        log.error("Failed to delete invalid entry", exc);
                    }
                }
                return null;
            }
        });

        return new MapdbCloseableStream<>(stream);
    }

    /**
     * Retrieves all directories in the repository.
     *
     * @return A closeable stream of backup directories
     * @throws IOException if an I/O error occurs
     */
    @Override
    public CloseableStream<BackupDirectory> allDirectories(boolean ascending) throws IOException {
        final Map<Object[], byte[]> map;
        if (ascending) {
            map = directoryMap.ascendingMap();
        } else {
            map = directoryMap.descendingMap();
        }

        Stream<BackupDirectory> stream = map.entrySet().stream().map((entry) -> {
            try {
                return decodeDirectory(entry);
            } catch (IOException e) {
                log.error(invalidRepositoryLogEntry("Invalid directory \"{}:{}\""), entry.getKey()[0], entry.getKey()[1], e);
                if (openMode != RepositoryOpenMode.READ_ONLY) {
                    try {
                        if (directoryMap.remove(entry.getKey()) == null) {
                            log.error("Delete indicated no entry was deleted");
                        }
                    } catch (Exception exc) {
                        log.error("Failed to delete invalid entry", exc);
                    }
                }
                return null;
            }
        });

        return new MapdbCloseableStream<>(stream);
    }

    /**
     * Retrieves all updated pending files in the repository.
     *
     * @return A closeable stream of backup updated pending files
     * @throws IOException if an I/O error occurs
     */
    @Override
    public CloseableStream<BackupBlockAdditional> allAdditionalBlocks() throws IOException {
        final Map<Object[], byte[]> map = additionalBlockMap.ascendingMap();

        Stream<BackupBlockAdditional> stream = map.entrySet().stream().map((entry) -> {
            try {
                BackupBlockAdditional ret = decodeData(BACKUP_BLOCK_ADDITIONAL_READER, entry.getValue());
                ret.setPublicKey((String) entry.getKey()[0]);
                ret.setHash((String) entry.getKey()[1]);
                return ret;
            } catch (IOException e) {
                log.error(invalidRepositoryLogEntry("Invalid additional block \"{}:{}\""), entry.getKey()[0], entry.getKey()[1], e);
                if (openMode != RepositoryOpenMode.READ_ONLY) {
                    try {
                        if (additionalBlockMap.remove(entry.getKey()) == null) {
                            log.error("Delete indicated no entry was deleted");
                        }
                    } catch (Exception exc) {
                        log.error("Failed to delete invalid entry", exc);
                    }
                }
                return null;
            }
        });

        return new MapdbCloseableStream<>(stream);
    }

    /**
     * Adds a pending set to the repository.
     *
     * @param scheduledTime The scheduled time information
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void addPendingSets(BackupPendingSet scheduledTime) throws IOException {
        pendingSetMap.put(scheduledTime.getSetId(), encodeData(BACKUP_PENDING_SET_WRITER,
                scheduledTime.toBuilder().setId(null).build()));
        increaseWrite();
    }

    /**
     * Deletes a pending set from the repository.
     *
     * @param setId The ID of the set to delete
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void deletePendingSets(String setId) throws IOException {
        pendingSetMap.remove(setId);
    }

    /**
     * Gets all pending sets from the repository.
     *
     * @return A set of all pending backup sets
     * @throws IOException if an I/O error occurs
     */
    @Override
    public Set<BackupPendingSet> getPendingSets() throws IOException {
        return pendingSetMap.entrySet().stream().map((entry) -> {
            try {
                BackupPendingSet set = decodeData(BACKUP_PENDING_SET_READER, entry.getValue());
                set.setSetId(entry.getKey());
                return set;
            } catch (IOException e) {
                log.error("Invalid pending set \"" + entry.getKey() + "\"", e);
                try {
                    pendingSetMap.remove(entry.getKey());
                } catch (Exception exc) {
                    log.error("Failed to delete invalid entry", exc);
                }
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * Decodes a file part entry from the database.
     *
     * @param entry The map entry containing the encoded file part data
     * @return The decoded BackupFilePart
     * @throws IOException if an I/O error occurs during decoding
     */
    private BackupFilePart decodePath(Map.Entry<Object[], byte[]> entry) throws IOException {
        try {
            BackupFilePart readValue = decodeData(BACKUP_FILE_PART_READER, entry.getValue());
            readValue.setPartHash((String) entry.getKey()[0]);
            readValue.setBlockHash((String) entry.getKey()[1]);
            return readValue;
        } catch (IOException e) {
            throw new IOException(String.format("Invalid path \"%s:%s\"", entry.getKey()[0], entry.getKey()[1]), e);
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
        NavigableMap<Object[], byte[]> query =
                fileMap.prefixSubMap(new Object[]{path});
        for (Map.Entry<Object[], byte[]> entry : query.descendingMap().entrySet()) {
            if (entry != null) {
                if (timestamp == null || ((Long) entry.getKey()[1]) <= timestamp)
                    return decodeFile(entry);
            }
        }
        return null;
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
        byte[] data = blockMap.get(hash);
        if (data != null) {
            BackupBlock block = decodeBlock(hash, data);
            block.setHash(hash);
            return block;
        }
        return null;
    }

    /**
     * Decodes a block from its serialized form.
     *
     * @param hash The hash of the block
     * @param data The serialized block data
     * @return The decoded backup block
     * @throws IOException if an I/O error occurs during decoding
     */
    private BackupBlock decodeBlock(String hash, byte[] data) throws IOException {
        try {
            BackupBlock block = decodeData(BACKUP_BLOCK_READER, data);
            block.setHash(hash);
            return block;
        } catch (IOException e) {
            throw new IOException(String.format("Invalid block \"%s\"", hash), e);
        }
    }

    /**
     * Decodes a directory entry from the database.
     *
     * @param entry The map entry containing the encoded directory data
     * @return The decoded BackupDirectory
     * @throws IOException if an I/O error occurs during decoding
     */
    private BackupDirectory decodeDirectory(Map.Entry<Object[], byte[]> entry) throws IOException {
        try {
            BackupDirectory directory = decodeData(BACKUP_DIRECTORY_READER, entry.getValue(), true);
            directory.setPath((String) entry.getKey()[0]);
            directory.setAdded((Long) entry.getKey()[1]);
            return directory;
        } catch (IOException exc) {
            try {
                NavigableSet<String> files = decodeData(BACKUP_DIRECTORY_FILES_LEGACY_READER, entry.getValue(), false);
                return new BackupDirectory((String) entry.getKey()[0],
                        (Long) entry.getKey()[1],
                        null,
                        files,
                        null);
            } catch (Exception exc2) {
                // Intentionally throwing the first exception here.
                throw new IOException(String.format("Invalid directory \"%s:%s\"", entry.getKey()[0], entry.getKey()[1]), exc);
            }
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
        NavigableMap<Object[], byte[]> query =
                directoryMap.prefixSubMap(new Object[]{path});
        BackupDirectory ret = null;
        for (Map.Entry<Object[], byte[]> entry : query.descendingMap().entrySet()) {
            if (entry != null) {
                if (timestamp == null || ((Long) entry.getKey()[1]) <= timestamp) {
                    BackupDirectory cd = decodeDirectory(entry);
                    if (!accumulative) {
                        return cd;
                    }
                    if (ret == null) {
                        ret = cd;
                    } else {
                        ret.getFiles().addAll(cd.getFiles());
                    }
                }
            }
        }
        return ret;
    }

    /**
     * Adds a file to the repository.
     *
     * @param file The file to add
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void addFile(BackupFile file) throws IOException {
        Long added;
        if (file.getAdded() == null)
            added = file.getLastChanged();
        else
            added = file.getAdded();

        fileMap.put(new Object[]{file.getPath(), added},
                encodeData(BACKUP_FILE_WRITER, strippedCopy(file)));
        increaseWrite();
    }

    /**
     * Adds a file part to the repository.
     *
     * @param part The file part to add
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void addFilePart(BackupFilePart part) throws IOException {
        partsMap.put(new Object[]{part.getPartHash(), part.getBlockHash()},
                encodeData(BACKUP_FILE_PART_WRITER, strippedCopy(part)));
        increaseWrite();
    }

    /**
     * Creates a stripped copy of a file part with only essential fields.
     * This reduces storage space by removing redundant information.
     *
     * @param part The file part to strip
     * @return A new file part with only the essential fields
     */
    private BackupFilePart strippedCopy(BackupFilePart part) {
        return BackupFilePart.builder().blockIndex(part.getBlockIndex()).build();
    }

    /**
     * Creates a stripped copy of a file with only essential fields.
     * This reduces storage space by removing redundant information.
     *
     * @param file The file to strip
     * @return A new file with only the essential fields
     */
    private BackupFile strippedCopy(BackupFile file) {
        return BackupFile.builder()
                .length(file.getLength())
                .locations(file.getLocations())
                .deleted(file.getDeleted())
                .permissions(file.getPermissions())
                .lastChanged(file.getLastChanged())
                .build();
    }

    /**
     * Adds a block to the repository.
     *
     * @param block The block to add
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void addBlock(BackupBlock block) throws IOException {
        blockMap.put(block.getHash(), encodeData(BACKUP_BLOCK_WRITER, stripCopy(block)));
        increaseWrite();
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
        getBlockTmpMap().put(block.getHash(), encodeData(BACKUP_BLOCK_WRITER, stripCopy(block)));
        increaseWrite();
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
     * Deletes the alternative blocks table.
     * This is called when switching between block tables.
     */
    private void deleteAlternativeBlocksTable() {
        String file = alternateBlockTable ? BLOCK_STORE : BLOCK_ALT_STORE;
        File oldFile = getPath(file).toFile();
        IOUtils.deleteFile(oldFile);
    }

    /**
     * Switches between the primary and alternate block tables.
     * This is used to implement atomic updates to the block table.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void switchBlocksTable() throws IOException {
        if (blockTmpMap == null) {
            throw new IOException("Switching alternative block table when it is empty");
        }
        commit();
        blockMap.close();
        blockDb.close();
        blockDb = blockTmpDb;
        blockMap = blockTmpMap;

        blockTmpDb = null;
        blockTmpMap = null;

        alternateBlockTable = !alternateBlockTable;

        // Opening this will erase all contents.
        deleteAlternativeBlocksTable();
    }

    /**
     * Increments the write counter and commits changes if necessary.
     * This helps ensure that large numbers of writes are periodically committed.
     */
    private void increaseWrite() {
        if (writeCounter.incrementAndGet() > MAX_WRITES) {
            commit();
        }
    }

    /**
     * Creates a stripped copy of a block with only essential fields.
     * This reduces storage space by removing redundant information.
     *
     * @param block The block to strip
     * @return A new block with only the essential fields
     */
    private BackupBlock stripCopy(BackupBlock block) {
        return BackupBlock.builder().storage(block.getStorage()).format(block.getFormat()).created(block.getCreated())
                .hashes(block.getHashes()).offsets(block.getOffsets())
                .build();
    }

    /**
     * Adds a directory to the repository.
     *
     * @param directory The directory to add
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void addDirectory(BackupDirectory directory) throws IOException {
        directoryMap.put(new Object[]{directory.getPath(), directory.getAdded()},
                encodeDirectoryData(directory));
        increaseWrite();
    }

    /**
     * Encodes a directory for storage in the repository.
     *
     * @param directory The directory to encode
     * @return The encoded directory data
     * @throws IOException if an I/O error occurs
     */
    private byte[] encodeDirectoryData(BackupDirectory directory) throws IOException {
        return encodeData(BACKUP_DIRECTORY_WRITER, BackupDirectory.builder()
                .files(directory.getFiles())
                .deleted(directory.getDeleted())
                .permissions(directory.getPermissions())
                .build());
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
        if (blockMap.remove(block.getHash()) != null) {
            increaseWrite();
            return true;
        }
        return false;
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
        if (fileMap.remove(new Object[]{file.getPath(), file.getAdded()}) != null) {
            increaseWrite();
            return true;
        }
        return false;
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
        if (partsMap.remove(new Object[]{part.getPartHash(), part.getBlockHash()}) != null) {
            increaseWrite();
            return true;
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
        if (directoryMap.remove(new Object[]{path, timestamp}) != null) {
            increaseWrite();
            return true;
        }
        return false;
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
        activePathMap.put(new Object[]{setId, path}, encodeData(BACKUP_ACTIVE_PATH_WRITER, pendingFiles));
        increaseWrite();
    }

    /**
     * Encodes data for storage in the repository.
     *
     * @param writer The object writer to use for serialization
     * @param obj The object to encode
     * @return The encoded data
     * @throws IOException if an I/O error occurs
     */
    private byte[] encodeData(ObjectWriter writer, Object obj) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
                writer.writeValue(gzipOutputStream, obj);
            }
            return outputStream.toByteArray();
        }
    }

    /**
     * Decodes data from its serialized form.
     *
     * @param reader The object reader to use for deserialization
     * @param data The serialized data
     * @return The decoded object
     * @throws IOException if an I/O error occurs
     */
    private <T> T decodeData(ObjectReader reader, byte[] data) throws IOException {
        return decodeData(reader, data, false);
    }

    /**
     * Decodes data from its serialized form with error handling options.
     *
     * @param reader The object reader to use for deserialization
     * @param data The serialized data
     * @param expectError Whether errors are expected during decoding
     * @return The decoded object
     * @throws IOException if an I/O error occurs
     */
    private <T> T decodeData(ObjectReader reader, byte[] data, boolean expectError) throws IOException {
        try {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
                try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                    return reader.readValue(gzipInputStream);
                }
            }
        } catch (IOException exc) {
            if (expectError) {
                throw exc;
            }
            StringBuilder sb = new StringBuilder();
            try {
                try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
                    try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                        try (InputStreamReader streamReader = new InputStreamReader(gzipInputStream,
                                StandardCharsets.UTF_8)) {
                            int chr = streamReader.read();
                            while (chr >= 0) {
                                sb.append((char) chr);
                                chr = streamReader.read();
                            }
                        }
                    }
                }
                log.error("Failed to decode JSON data: \"{}\"", sb);
            } catch (Exception decodeTest2) {
                log.error("Failed to decode JSON data: \"{}\" (Only partially data decoded)", sb,
                        decodeTest2);
            }
            throw exc;
        }
    }

    /**
     * Checks if an active path exists in the repository.
     *
     * @param setId The ID of the set
     * @param path The path to check
     * @return true if the active path exists, false otherwise
     */
    @Override
    public boolean hasActivePath(String setId, String path) {
        return activePathMap.containsKey(new Object[]{setId, path});
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
        if (activePathMap.remove(new Object[]{setId, path}) != null) {
            increaseWrite();
        }
    }

    /**
     * Deletes a partial file from the repository.
     *
     * @param file The partial file to delete
     * @return true if the partial file was deleted, false if it was not found
     */
    @Override
    public boolean deletePartialFile(BackupPartialFile file) {
        if (partialFileMap.remove(file.getFile().getPath()) != null) {
            increaseWrite();
            return true;
        }
        return false;
    }

    /**
     * Saves a partial file to the repository.
     *
     * @param file The partial file to save
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void savePartialFile(BackupPartialFile file) throws IOException {
        partialFileMap.put(file.getFile().getPath(), encodeData(BACKUP_PARTIAL_FILE_WRITER, file));
        increaseWrite();
    }

    /**
     * Clears all partial files from the repository.
     */
    @Override
    public void clearPartialFiles() {
        partialFileMap.clear();
    }

    /**
     * Retrieves a partial file from the repository.
     *
     * @param file The partial file to retrieve
     * @return The retrieved partial file, or null if not found
     */
    @Override
    public BackupPartialFile getPartialFile(BackupPartialFile file) {
        return getPartialFileInternal(file);
    }

    /**
     * Internal method to retrieve a partial file from the repository.
     *
     * @param file The partial file to retrieve
     * @return The retrieved partial file, or null if not found or if the file has changed
     */
    private BackupPartialFile getPartialFileInternal(BackupPartialFile file) {
        byte[] data = partialFileMap.get(file.getFile().getPath());
        if (data != null) {
            BackupPartialFile ret;
            try {
                ret = decodeData(BACKUP_PARTIAL_FILE_READER, data);
            } catch (IOException exc) {
                log.error("Invalid partialFile \"{}\" reprocessing entire file",
                        PathNormalizer.physicalPath(file.getFile().getPath()), exc);
                return null;
            }
            if (file.getFile().getLength() == null) {
                return ret;
            }
            if (Objects.equals(ret.getFile().getLength(), file.getFile().getLength())
                    && Objects.equals(ret.getFile().getLastChanged(), file.getFile().getLastChanged())) {
                return ret;
            }
        }
        return null;
    }

    /**
     * Retrieves all active paths for a set.
     *
     * @param setId The ID of the set, or null to retrieve all active paths
     * @return A map of paths to active path information
     * @throws IOException if an I/O error occurs
     */
    @Override
    public TreeMap<String, BackupActivePath> getActivePaths(String setId) throws IOException {
        Map<Object[], byte[]> readMap = activePathMap.ascendingMap();
        if (setId != null)
            readMap = activePathMap.prefixSubMap(new Object[]{setId});

        TreeMap<String, BackupActivePath> ret = new TreeMap<>();
        for (Map.Entry<Object[], byte[]> entry : readMap.entrySet()) {
            try {
                BackupActivePath activePath = decodeData(BACKUP_ACTIVE_PATH_READER, entry.getValue());
                String path = (String) entry.getKey()[1];
                activePath.setParentPath(path);
                activePath.setSetIds(Lists.newArrayList((String) entry.getKey()[0]));

                BackupActivePath existingActive = ret.get(path);
                if (existingActive != null) {
                    activePath.mergeChanges(existingActive);
                }

                ret.put(path, activePath);
            } catch (IOException exc) {
                log.error("Invalid activePath \"{}\" for set \"{}\". Skipping during this run.", entry.getKey()[1], entry.getKey()[0],
                        exc);
            }
        }
        return ret;
    }

    /**
     * Gets the count of blocks in the repository.
     *
     * @return The number of blocks
     * @throws IOException if an I/O error occurs
     */
    public long getBlockCount() throws IOException {
        return blockMap.size();
    }

    /**
     * Gets the count of files in the repository.
     *
     * @return The number of files
     * @throws IOException if an I/O error occurs
     */
    public long getFileCount() throws IOException {
        return fileMap.size();
    }

    /**
     * Gets the count of directories in the repository.
     *
     * @return The number of directories
     */
    public long getDirectoryCount() {
        return directoryMap.size();
    }

    /**
     * Gets the count of file parts in the repository.
     *
     * @return The number of file parts
     */
    public long getPartCount() {
        return partsMap.size();
    }

    /**
     * Gets the count of additional blocks in the repository.
     *
     * @return The number of additional blocks
     * @throws IOException if an I/O error occurs
     */
    @Override
    public long getAdditionalBlockCount() throws IOException {
        return additionalBlockMap.size();
    }

    /**
     * Gets the count of updated files in the repository.
     *
     * @return The number of updated files
     * @throws IOException if an I/O error occurs
     */
    @Override
    public long getUpdatedFileCount() throws IOException {
        return updatedPendingFilesMap.size();
    }

    /**
     * Clears all data from the repository.
     * This deletes all database files.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void clear() throws IOException {
        File parent = new File(dataPath);
        if (nonVersionedPath(version, revision)) {
            String[] allFiles = new String[]{
                    FILE_STORE,
                    BLOCK_STORE,
                    BLOCK_ALT_STORE,
                    PARTS_STORE,
                    DIRECTORY_STORE,
                    ACTIVE_PATH_STORE,
                    PENDING_SET_STORE,
                    PARTIAL_FILE_STORE,
                    ADDITIONAL_BLOCK_STORE,
                    UPDATED_FILES_STORE,
                    UPDATED_PENDING_FILES_STORE
            };

            File[] files = parent.listFiles((file, name) -> {
                for (String startName : allFiles) {
                    if (name.startsWith(startName)) {
                        return true;
                    }
                }
                return false;
            });
            if (files != null) {
                for (File file : files) {
                    IOUtils.deleteFile(file);
                }
            }
        } else {
            deleteContents(parent);
            IOUtils.deleteFile(parent);
        }
    }

    /**
     * Creates a copy of the additional block with only the necessary fields.
     * This helps reduce storage space by removing redundant information.
     *
     * @param block The block to strip
     * @return A new block with only the essential fields
     */
    private BackupBlockAdditional stripCopy(BackupBlockAdditional block) {
        return BackupBlockAdditional.builder().used(block.isUsed()).properties(block.getProperties()).build();
    }

    /**
     * Adds an additional block to the repository.
     *
     * @param block The additional block to add
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void addAdditionalBlock(BackupBlockAdditional block) throws IOException {
        additionalBlockMap.put(new Object[]{block.getPublicKey(), block.getHash()},
                encodeData(BACKUP_BLOCK_ADDITIONAL_WRITER, stripCopy(block)));
        increaseWrite();
    }

    /**
     * Retrieves an additional block from the repository.
     *
     * @param publicKey The public key associated with the block
     * @param blockHash The hash of the block to retrieve
     * @return The additional block, or null if not found
     * @throws IOException if an I/O error occurs
     */
    @Override
    public BackupBlockAdditional additionalBlock(String publicKey, String blockHash) throws IOException {
        byte[] data = additionalBlockMap.get(new Object[]{publicKey, blockHash});
        if (data != null) {
            try {
                BackupBlockAdditional block = decodeData(BACKUP_BLOCK_ADDITIONAL_READER, data);
                block.setHash(blockHash);
                block.setPublicKey(publicKey);
                return block;
            } catch (IOException exc) {
                throw new IOException(String.format("Invalid additionalBlock \"%s:%s\"", publicKey, blockHash), exc);
            }
        }
        return null;
    }

    /**
     * Deletes an additional block from the repository.
     *
     * @param publicKey The public key associated with the block
     * @param blockHash The hash of the block to delete, or null to delete all blocks for the given public key
     */
    @Override
    public void deleteAdditionalBlock(String publicKey, String blockHash) {
        if (blockHash != null) {
            additionalBlockMap.remove(new Object[]{publicKey, blockHash});
        } else {
            Map<Object[], byte[]> query = additionalBlockMap.prefixSubMap(new Object[]{publicKey});
            for (Object[] key : query.keySet())
                additionalBlockMap.remove(key);
        }
        increaseWrite();
    }

    /**
     * Adds an updated file to the repository.
     *
     * @param file     The updated file to add
     * @param howOften How often the file should be updated (in milliseconds)
     * @return true if the file was added or updated, false otherwise
     * @throws IOException if an I/O error occurs
     */
    @Override
    public boolean addUpdatedFile(BackupUpdatedFile file, long howOften) throws IOException {
        try {
            if (howOften < 0) {
                updatedPendingFilesMap.put(new Object[]{file.getLastUpdated(), file.getPath()}, new byte[0]);
                updatedFilesMap.put(file.getPath(), file.getLastUpdated());
                return false;
            }
            Long updated = updatedFilesMap.get(file.getPath());
            if (updated == null) {
                long lastExisting = 0;
                if (file.getPath().endsWith(PathNormalizer.PATH_SEPARATOR)) {
                    BackupDirectory dir = directory(file.getPath(), null, false);
                    if (dir != null) {
                        lastExisting = dir.getAdded();
                    }
                } else {
                    BackupPartialFile partialFile = getPartialFileInternal(new BackupPartialFile(
                            BackupFile.builder().path(file.getPath()).build()));
                    if (partialFile != null) {
                        lastExisting = partialFile.getFile().getLastChanged();
                    } else {
                        BackupFile existingFile = file(file.getPath(), null);
                        if (existingFile != null) {
                            lastExisting = existingFile.getLastChanged();
                        }
                    }
                }

                long when = System.currentTimeMillis();
                if (lastExisting + howOften > when) {
                    when = lastExisting + howOften;
                }
                when += MINIMUM_WAIT_UPDATE_MS;

                updatedFilesMap.put(file.getPath(), when);
                updatedPendingFilesMap.put(new Object[]{when, file.getPath()}, new byte[0]);
                return true;
            } else if (file.getLastUpdated() + MINIMUM_WAIT_UPDATE_MS > updated) {
                updatedPendingFilesMap.remove(new Object[]{updated, file.getPath()});
                updatedPendingFilesMap.put(new Object[]{file.getLastUpdated() + MINIMUM_WAIT_UPDATE_MS, file.getPath()}, new byte[0]);
                updatedFilesMap.put(file.getPath(), file.getLastUpdated() + MINIMUM_WAIT_UPDATE_MS);
                return true;
            }
            return false;
        } finally {
            increaseWrite();
        }
    }

    /**
     * Removes an updated file from the repository.
     *
     * @param file The updated file to remove
     */
    @Override
    public void removeUpdatedFile(BackupUpdatedFile file) {
        updatedFilesMap.remove(file.getPath());
        updatedPendingFilesMap.remove(new Object[]{file.getLastUpdated(), file.getPath()});
        increaseWrite();
    }

    /**
     * Retrieves a stream of all updated files.
     *
     * @return A closeable stream of BackupUpdatedFile objects
     */
    @Override
    public CloseableStream<BackupUpdatedFile> getUpdatedFiles() {
        final Map<Object[], byte[]> map = updatedPendingFilesMap.ascendingMap();

        Stream<BackupUpdatedFile> stream = map.keySet().stream()
                .map(keys -> new BackupUpdatedFile((String) keys[1], (Long) keys[0]));

        return new MapdbCloseableStream<>(stream);
    }

    /**
     * Utility class that can work with either a BTreeMap or a TreeMapSink.
     * This allows for efficient operations on tree maps, including bulk loading.
     */
    public static class TreeOrSink implements Closeable {
        private BTreeMap<Object[], byte[]> tree;
        private DB.TreeMapSink<Object[], byte[]> sink;
        private Object[] lastKey;

        /**
         * Creates a TreeOrSink instance with a BTreeMap.
         *
         * @param tree The BTreeMap to use
         */
        public TreeOrSink(BTreeMap<Object[], byte[]> tree) {
            this.tree = tree;
        }

        /**
         * Creates a TreeOrSink instance with a TreeMapSink.
         *
         * @param sink The TreeMapSink to use
         */
        public TreeOrSink(DB.TreeMapSink<Object[], byte[]> sink) {
            this.sink = sink;
        }

        /**
         * Compares two keys for ordering.
         * This method handles both comparable objects and byte arrays.
         *
         * @param key1 The first key to compare
         * @param key2 The second key to compare
         * @return A negative integer, zero, or a positive integer as the first argument is less than,
         * equal to, or greater than the second
         */
        @SuppressWarnings("unchecked")
        private static int compareKeys(Object[] key1, Object[] key2) {
            for (int i = 0; i < key1.length; i++) {
                Object k1 = key1[i];
                // Its either comparable or a byte array.

                if (k1 instanceof byte[] b1) {
                    byte[] b2 = (byte[]) key2[i];

                    for (int j = 0; i < Math.min(b1.length, b2.length); i++) {
                        if (b1[j] != b2[j]) {
                            int v1 = b1[j];
                            int v2 = b2[j];
                            if (v1 < 0) {
                                v1 += 256;
                            }
                            if (v2 < 0) {
                                v2 += 256;
                            }
                            return v1 - v2;
                        }
                    }

                    int ld = b1.length - b2.length;
                    if (ld != 0)
                        return ld;
                } else {
                    int compare = ((Comparable<Object>) k1).compareTo(key2[i]);
                    if (compare != 0) {
                        return compare;
                    }
                }
            }
            return 0;
        }

        /**
         * Closes the sink and creates a tree from it.
         * This is called when operations need to be performed on the tree.
         */
        private void closeSink() {
            if (sink != null) {
                tree = sink.create();
                sink = null;
                lastKey = null;
            }
        }

        /**
         * Puts a key-value pair into the map.
         * If using a sink, ensures keys are added in order.
         *
         * @param key The key as an array of objects
         * @param val The value as a byte array
         */
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

        /**
         * Closes the TreeOrSink and releases resources.
         * If using a sink, converts it to a tree first.
         */
        @Override
        public void close() {
            if (tree == null) {
                closeSink();
                tree.getStore().commit();
            }
            tree.close();
        }

        /**
         * Returns a navigable map containing all entries with keys that start with the given prefix.
         *
         * @param objects The prefix to match
         * @return A navigable map of matching entries
         */
        public NavigableMap<Object[], byte[]> prefixSubMap(Object[] objects) {
            if (tree == null) {
                closeSink();
            }
            return tree.prefixSubMap(objects);
        }

        /**
         * Returns a navigable map with entries in descending order.
         *
         * @return A navigable map with entries in descending order
         */
        public NavigableMap<Object[], byte[]> descendingMap() {
            if (tree == null) {
                closeSink();
            }
            return tree.descendingMap();
        }

        /**
         * Returns a navigable map with entries in ascending order.
         *
         * @return A navigable map with entries in ascending order
         */
        public NavigableMap<Object[], byte[]> ascendingMap() {
            if (tree == null) {
                closeSink();
            }
            return tree;
        }

        /**
         * Removes an entry with the specified key.
         *
         * @param objects The key to remove
         * @return The value that was removed, or null if no entry was found
         */
        public byte[] remove(Object[] objects) {
            if (tree == null) {
                closeSink();
            }
            return tree.remove(objects);
        }

        /**
         * Checks if the map contains an entry with the specified key.
         *
         * @param objects The key to check
         * @return true if the map contains the key, false otherwise
         */
        public boolean containsKey(Object[] objects) {
            if (tree == null) {
                closeSink();
            }
            return tree.containsKey(objects);
        }

        /**
         * Gets the value associated with the specified key.
         *
         * @param objects The key to look up
         * @return The value associated with the key, or null if not found
         */
        public byte[] get(Object[] objects) {
            if (tree == null) {
                closeSink();
            }
            return tree.get(objects);
        }

        /**
         * Gets the number of entries in the map.
         *
         * @return The number of entries
         */
        public long size() {
            if (tree == null) {
                closeSink();
            }
            return tree.size();
        }
    }

    /**
     * Implementation of CloseableStream for MapDB.
     * This class wraps a Stream to provide the CloseableStream interface.
     *
     * @param <T> The type of elements in the stream
     */
    private static class MapdbCloseableStream<T> implements CloseableStream<T> {
        private final Stream<T> stream;
        @Setter
        private boolean reportErrorsAsNull;

        /**
         * Creates a new MapdbCloseableStream wrapping the provided stream.
         *
         * @param stream The stream to wrap
         */
        private MapdbCloseableStream(Stream<T> stream) {
            this.stream = stream;
        }

        /**
         * Returns the underlying stream, optionally filtering out null values.
         *
         * @return The stream of elements
         */
        @Override
        public Stream<T> stream() {
            if (reportErrorsAsNull)
                return stream;
            return stream.filter(Objects::nonNull);
        }
    }

    /**
     * Base class for temporary maps using MapDB.
     * This abstract class provides common functionality for temporary maps.
     *
     * @param <K> The key type
     * @param <V> The value type
     */
    private static abstract class TemporaryBaseMap<K, V> implements CloseableMap<K, V> {
        @Getter(AccessLevel.PROTECTED)
        private final MapSerializer<K, V> serializer;
        @Getter(AccessLevel.PROTECTED)
        private final DB db;
        private int writeCount;

        /**
         * Creates a new temporary base map using the provided serializer.
         *
         * @param serializer The serializer to use for keys and values
         * @throws IOException If there's an error creating the temporary file
         */
        public TemporaryBaseMap(MapSerializer<K, V> serializer) throws IOException {
            this.serializer = serializer;

            File root = File.createTempFile("underscorebackup", ".db");
            IOUtils.deleteFile(root);

            // Don't need transaction here since if we crash we will never open the file again.
            DBMaker.Maker maker = createDbMaker(root.toString());
            maker.fileDeleteAfterClose();
            db = maker.make();
        }

        /**
         * Closes the map and releases resources.
         * This closes the underlying database.
         */
        @Override
        public void close() {
            db.close();
        }

        /**
         * Increments the write counter and commits changes if necessary.
         * This helps ensure that large numbers of writes are periodically committed.
         */
        private void increaseWrite() {
            writeCount++;
            if (writeCount >= 50000) {
                writeCount = 0;
                db.commit();
            }
        }

        /**
         * Adds or updates an entry in the map.
         *
         * @param k The key
         * @param v The value
         */
        @Override
        public synchronized void put(K k, V v) {
            byte[] kb = serializer.encodeKey(k);
            byte[] vb = serializer.encodeValue(v);

            increaseWrite();
            internalPut(kb, vb);
        }

        /**
         * Removes an entry from the map by its key.
         *
         * @param k The key to remove
         * @return True if an entry was removed, false otherwise
         */
        @Override
        public boolean delete(K k) {
            increaseWrite();
            return internalDelete(serializer.encodeKey(k));
        }

        /**
         * Retrieves a value from the map by its key.
         *
         * @param k The key
         * @return The value, or null if not found
         */
        @Override
        public V get(K k) {
            byte[] ret = internalGet(serializer.encodeKey(k));
            if (ret != null) {
                return serializer.decodeValue(ret);
            }
            return null;
        }

        /**
         * Adds or updates an entry in the map with serialized key and value.
         *
         * @param kb The serialized key
         * @param vb The serialized value
         */
        protected abstract void internalPut(byte[] kb, byte[] vb);

        /**
         * Removes an entry from the map by its serialized key.
         *
         * @param bytes The serialized key to remove
         * @return True if an entry was removed, false otherwise
         */
        protected abstract boolean internalDelete(byte[] bytes);

        /**
         * Retrieves a serialized value from the map by its serialized key.
         *
         * @param bytes The serialized key
         * @return The serialized value, or null if not found
         */
        protected abstract byte[] internalGet(byte[] bytes);
    }

    /**
     * Implementation of a temporary map using MapDB.
     * This class provides a map that is automatically cleaned up when closed.
     *
     * @param <K> The key type
     * @param <V> The value type
     */
    private static class TemporaryMapdbMap<K, V> extends TemporaryBaseMap<K, V> {
        HTreeMap<byte[], byte[]> map;

        /**
         * Creates a new temporary hash map using the provided serializer.
         *
         * @param serializer The serializer to use for keys and values
         * @throws IOException If there's an error creating the map
         */
        public TemporaryMapdbMap(MapSerializer<K, V> serializer) throws IOException {
            super(serializer);

            map = getDb().hashMap("map", Serializer.BYTE_ARRAY, Serializer.BYTE_ARRAY)
                    .layout(16, 64, 4).create();
        }

        /**
         * Closes the map and releases resources.
         * First closes the underlying hash map, then calls the parent close method.
         */
        @Override
        public void close() {
            map.close();

            super.close();
        }

        /**
         * Adds or updates an entry in the map.
         *
         * @param kb The serialized key
         * @param vb The serialized value
         */
        @Override
        protected void internalPut(byte[] kb, byte[] vb) {
            map.put(kb, vb);
        }

        /**
         * Removes an entry from the map by its key.
         *
         * @param bytes The serialized key to remove
         * @return True if an entry was removed, false otherwise
         */
        @Override
        protected boolean internalDelete(byte[] bytes) {
            return map.remove(bytes) != null;
        }

        /**
         * Retrieves a value from the map by its key.
         *
         * @param bytes The serialized key
         * @return The serialized value, or null if not found
         */
        @Override
        protected byte[] internalGet(byte[] bytes) {
            return map.get(bytes);
        }
    }

    /**
     * Implementation of a temporary sorted map using MapDB.
     * This class provides a sorted map that is automatically cleaned up when closed.
     *
     * @param <K> The key type
     * @param <V> The value type
     */
    private static class TemporaryMapdbSortedMap<K, V> extends TemporaryBaseMap<K, V> implements CloseableSortedMap<K, V> {
        private final TreeOrSink map;

        /**
         * Creates a new temporary sorted map using the provided serializer.
         *
         * @param serializer The serializer to use for keys and values
         * @throws IOException If there's an error creating the map
         */
        public TemporaryMapdbSortedMap(MapSerializer<K, V> serializer) throws IOException {
            super(serializer);

            map = openTreeMap(getDb(), getDb().treeMap("map")
                    .keySerializer(new SerializerArrayTuple(Serializer.BYTE_ARRAY))
                    .valueSerializer(Serializer.BYTE_ARRAY), false);
        }

        /**
         * Closes the map and releases resources.
         * First closes the underlying hash map, then calls the parent close method.
         */
        @Override
        public void close() {
            map.close();

            super.close();
        }

        /**
         * Adds or updates an entry in the map with serialized key and value.
         *
         * @param kb The serialized key
         * @param vb The serialized value
         */
        @Override
        protected void internalPut(byte[] kb, byte[] vb) {
            map.put(new Object[]{kb}, vb);
        }

        /**
         * Removes an entry from the map by its serialized key.
         *
         * @param bytes The serialized key to remove
         * @return True if an entry was removed, false otherwise
         */
        @Override
        protected boolean internalDelete(byte[] bytes) {
            return map.remove(new Object[]{bytes}) != null;
        }

        /**
         * Retrieves a serialized value from the map by its serialized key.
         *
         * @param bytes The serialized key
         * @return The serialized value, or null if not found
         */
        @Override
        protected byte[] internalGet(byte[] bytes) {
            return map.get(new Object[]{bytes});
        }

        /**
         * Returns a stream of map entries in either ascending or descending order.
         *
         * @param ascending If true, returns entries in ascending order; if false, in descending order
         * @return A stream of map entries with decoded keys and values
         */
        @Override
        public Stream<Map.Entry<K, V>> readOnlyEntryStream(boolean ascending) {
            if (ascending)
                return map.ascendingMap().entrySet().stream().map((entry) -> Map.entry(getSerializer().decodeKey((byte[]) entry.getKey()[0]),
                        getSerializer().decodeValue(entry.getValue())));
            else
                return map.descendingMap().entrySet().stream().map((entry) -> Map.entry(getSerializer().decodeKey((byte[]) entry.getKey()[0]),
                        getSerializer().decodeValue(entry.getValue())));
        }
    }
}