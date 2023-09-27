package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.file.implementation.LockingMetadataRepository.MINIMUM_WAIT_UPDATE_MS;
import static com.underscoreresearch.backup.io.IOUtils.clearTempFiles;
import static com.underscoreresearch.backup.io.IOUtils.deleteContents;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_ACTIVE_PATH_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_ACTIVE_PATH_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_BLOCK_ADDITIONAL_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_BLOCK_ADDITIONAL_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_BLOCK_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_BLOCK_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_FILE_PART_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_FILE_PART_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_FILE_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_FILE_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_PARTIAL_FILE_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_PARTIAL_FILE_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_PENDING_SET_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_PENDING_SET_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.mapdb.serializer.SerializerArrayTuple;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableMap;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.MapSerializer;
import com.underscoreresearch.backup.file.MetadataRepositoryStorage;
import com.underscoreresearch.backup.file.PathNormalizer;
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

@Slf4j
public class MapdbMetadataRepositoryStorage implements MetadataRepositoryStorage {
    private static final ObjectReader BACKUP_DIRECTORY_FILES_READER
            = MAPPER.readerFor(new TypeReference<NavigableSet<String>>() {
    });
    private static final ObjectWriter BACKUP_DIRECTORY_FILES_WRITER
            = MAPPER.writerFor(new TypeReference<Set<String>>() {
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
    private final String dataPath;
    private final int revision;
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
    private boolean readOnly;
    private boolean alternateBlockTable;

    public MapdbMetadataRepositoryStorage(String dataPath, int revision, boolean alternateBlockTable) {
        if (revision == 0) {
            this.dataPath = dataPath;
            this.revision = revision;
        } else {
            this.dataPath = Paths.get(dataPath, String.format("v2-%d", revision)).toString();
            this.revision = revision;
            IOUtils.createDirectory(new File(this.dataPath), true);
        }
        this.alternateBlockTable = alternateBlockTable;
    }

    private static TreeOrSink openTreeMap(DB db, DB.TreeMapMaker<Object[], byte[]> maker) {
        maker.counterEnable();
        if (db.nameCatalogLoad().isEmpty()) {
            return new TreeOrSink(maker.createFromSink());
        }
        return new TreeOrSink(maker.createOrOpen());
    }

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
    }

    @Override
    public boolean needPeriodicCommits() {
        return true;
    }

    @Override
    public <K, V> CloseableMap<K, V> temporaryMap(MapSerializer<K, V> serializer) throws IOException {
        return new TemporaryMapdbMap<>(serializer);
    }

    @Override
    public boolean needExclusiveCommitLock() {
        return false;
    }

    @Override
    public CloseableLock exclusiveLock() throws IOException {
        return new CloseableLock() {
            @Override
            public void close() {
            }

            @Override
            public boolean requested() {
                return false;
            }
        };
    }

    @Override
    public void open(boolean readOnly) throws IOException {
        this.readOnly = readOnly;

        blockDb = createDb(readOnly, alternateBlockTable ? BLOCK_ALT_STORE : BLOCK_STORE);
        fileDb = createDb(readOnly, FILE_STORE);
        directoryDb = createDb(readOnly, DIRECTORY_STORE);
        partsDb = createDb(readOnly, PARTS_STORE);
        activePathDb = createDb(readOnly, ACTIVE_PATH_STORE);
        pendingSetDb = createDb(readOnly, PENDING_SET_STORE);
        partialFileDb = createDb(readOnly, PARTIAL_FILE_STORE);
        additionalBlockDb = createDb(readOnly, ADDITIONAL_BLOCK_STORE);
        updatedFilesDb = createDb(readOnly, UPDATED_FILES_STORE);
        updatedPendingFilesDb = createDb(readOnly, UPDATED_PENDING_FILES_STORE);

        blockMap = openHashMap(blockDb.hashMap(BLOCK_STORE, Serializer.STRING, Serializer.BYTE_ARRAY));
        additionalBlockMap = openTreeMap(additionalBlockDb, additionalBlockDb.treeMap(ADDITIONAL_BLOCK_STORE)
                .keySerializer(new SerializerArrayTuple(Serializer.STRING, Serializer.STRING))
                .valueSerializer(Serializer.BYTE_ARRAY));
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
        updatedPendingFilesMap = openTreeMap(updatedPendingFilesDb, updatedPendingFilesDb.treeMap(UPDATED_PENDING_FILES_STORE)
                .keySerializer(new SerializerArrayTuple(Serializer.LONG, Serializer.STRING))
                .valueSerializer(Serializer.BYTE_ARRAY));
        pendingSetMap = openHashMap(pendingSetDb.hashMap(BLOCK_STORE, Serializer.STRING, Serializer.BYTE_ARRAY));
        partialFileMap = openHashMap(partialFileDb.hashMap(BLOCK_STORE, Serializer.STRING, Serializer.BYTE_ARRAY));
        updatedFilesMap = openHashMap(updatedFilesDb.hashMap(UPDATED_FILES_STORE, Serializer.STRING, Serializer.LONG));

        if (!readOnly) {
            clearTempFiles();
        }
    }

    private HTreeMap<String, byte[]> getBlockTmpMap() {
        if (blockTmpMap == null) {
            deleteAlternativeBlocksTable();
            blockTmpDb = createDb(readOnly, alternateBlockTable ? BLOCK_STORE : BLOCK_ALT_STORE);
            // Need to be BLOCK_STORE here, as we are copying from BLOCK_TMP_STORE to BLOCK_STORE eventually.
            blockTmpMap = openHashMap(blockTmpDb.hashMap(BLOCK_STORE, Serializer.STRING, Serializer.BYTE_ARRAY));
        }
        return blockTmpMap;
    }

    private <T> HTreeMap<String, T> openHashMap(DB.HashMapMaker<String, T> maker) {
        hashSetup(maker);
        maker.counterEnable();
        return maker.createOrOpen();
    }

    protected <T> void hashSetup(DB.HashMapMaker<String, T> maker) {
        maker.layout(16, 64, 4);
    }

    private DB createDb(boolean readOnly, String blockStore) {
        DBMaker.Maker maker = DBMaker
                .fileDB(getPath(blockStore).toString())
                .fileMmapEnableIfSupported()
                .fileMmapPreclearDisable()
                .transactionEnable();
        if (readOnly)
            maker.readOnly();
        return maker.make();
    }

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

    private BackupFile decodeFile(Map.Entry<Object[], byte[]> entry) throws IOException {
        try {
            BackupFile readValue = decodeData(BACKUP_FILE_READER, entry.getValue());
            readValue.setPath((String) entry.getKey()[0]);
            readValue.setAdded((Long) entry.getKey()[1]);
            if (readValue.getLastChanged() == null)
                readValue.setLastChanged(readValue.getAdded());

            return readValue;
        } catch (Exception exc) {
            throw new IOException(String.format("Failed to decode file %s:%s", entry.getKey()[0], entry.getKey()[1]),
                    exc);
        }
    }

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

    private String invalidRepositoryLogEntry(String msg) {
        if (readOnly)
            return msg + " (Read only repository)";
        return msg + " (Removing from repository)";
    }

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
                log.error(invalidRepositoryLogEntry("Invalid file {}:{}"), PathNormalizer.physicalPath((String) entry.getKey()[0]), entry.getKey()[1], e);
                if (!readOnly) {
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

    @Override
    public CloseableStream<BackupBlock> allBlocks() throws IOException {
        Stream<BackupBlock> stream = blockMap.entrySet().stream().map((entry) -> {
            try {
                return decodeBlock(entry.getKey(), entry.getValue());
            } catch (IOException e) {
                log.error(invalidRepositoryLogEntry("Invalid block {}"), entry.getKey(), e);
                if (!readOnly) {
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

    @Override
    public CloseableStream<BackupFilePart> allFileParts() {
        Stream<BackupFilePart> stream = partsMap.ascendingMap().entrySet().stream().map((entry) -> {
            try {
                return decodePath(entry);
            } catch (IOException e) {
                log.error(invalidRepositoryLogEntry("Invalid filePart {}:{}"), entry.getKey()[0], entry.getKey()[1], e);
                if (!readOnly) {
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

    @Override
    public CloseableStream<BackupDirectory> allDirectories(boolean ascending) {
        final Map<Object[], byte[]> map;
        if (ascending) {
            map = directoryMap.ascendingMap();
        } else {
            map = directoryMap.descendingMap();
        }

        Stream<BackupDirectory> stream = map.entrySet().stream().map((entry) -> {
            try {
                return new BackupDirectory((String) entry.getKey()[0], (Long) entry.getKey()[1],
                        decodeData(BACKUP_DIRECTORY_FILES_READER, entry.getValue()));
            } catch (IOException e) {
                log.error(invalidRepositoryLogEntry("Invalid directory {}:{}"), entry.getKey()[0], entry.getKey()[1], e);
                if (!readOnly) {
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
                log.error(invalidRepositoryLogEntry("Invalid additional block {}:{}"), entry.getKey()[0], entry.getKey()[1], e);
                if (!readOnly) {
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

    @Override
    public void addPendingSets(BackupPendingSet scheduledTime) throws IOException {
        pendingSetMap.put(scheduledTime.getSetId(), encodeData(BACKUP_PENDING_SET_WRITER, scheduledTime));
    }

    @Override
    public void deletePendingSets(String setId) throws IOException {
        pendingSetMap.remove(setId);
    }

    @Override
    public Set<BackupPendingSet> getPendingSets() throws IOException {
        return pendingSetMap.entrySet().stream().map((entry) -> {
            try {
                BackupPendingSet set = decodeData(BACKUP_PENDING_SET_READER, entry.getValue());
                set.setSetId(entry.getKey());
                return set;
            } catch (IOException e) {
                log.error("Invalid pending set " + entry.getKey(), e);
                try {
                    pendingSetMap.remove(entry.getKey());
                } catch (Exception exc) {
                    log.error("Failed to delete invalid entry", exc);
                }
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private BackupFilePart decodePath(Map.Entry<Object[], byte[]> entry) throws IOException {
        try {
            BackupFilePart readValue = decodeData(BACKUP_FILE_PART_READER, entry.getValue());
            readValue.setPartHash((String) entry.getKey()[0]);
            readValue.setBlockHash((String) entry.getKey()[1]);
            return readValue;
        } catch (IOException e) {
            throw new IOException(String.format("Invalid path %s:%s", entry.getKey()[0], entry.getKey()[1]), e);
        }
    }

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

    private BackupBlock decodeBlock(String hash, byte[] data) throws IOException {
        try {
            BackupBlock block = decodeData(BACKUP_BLOCK_READER, data);
            block.setHash(hash);
            return block;
        } catch (IOException e) {
            throw new IOException(String.format("Invalid block %s", hash), e);
        }
    }

    private BackupDirectory decodeDirectory(Map.Entry<Object[], byte[]> entry) throws IOException {
        try {
            return new BackupDirectory((String) entry.getKey()[0],
                    (Long) entry.getKey()[1],
                    decodeData(BACKUP_DIRECTORY_FILES_READER, entry.getValue()));
        } catch (IOException e) {
            throw new IOException(String.format("Invalid directory %s:%s", entry.getKey()[0], entry.getKey()[1]), e);
        }
    }

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

    @Override
    public void addFile(BackupFile file) throws IOException {
        Long added;
        if (file.getAdded() == null)
            added = file.getLastChanged();
        else
            added = file.getAdded();

        fileMap.put(new Object[]{file.getPath(), added},
                encodeData(BACKUP_FILE_WRITER, strippedCopy(file)));
    }

    @Override
    public void addFilePart(BackupFilePart part) throws IOException {
        partsMap.put(new Object[]{part.getPartHash(), part.getBlockHash()},
                encodeData(BACKUP_FILE_PART_WRITER, strippedCopy(part)));
    }

    private BackupFilePart strippedCopy(BackupFilePart part) {
        return BackupFilePart.builder().blockIndex(part.getBlockIndex()).build();
    }

    private BackupFile strippedCopy(BackupFile file) {
        return BackupFile.builder()
                .length(file.getLength())
                .locations(file.getLocations())
                .deleted(file.getDeleted())
                .permissions(file.getPermissions())
                .lastChanged(file.getLastChanged())
                .build();
    }

    @Override
    public void addBlock(BackupBlock block) throws IOException {
        blockMap.put(block.getHash(), encodeData(BACKUP_BLOCK_WRITER, stripCopy(block)));
    }

    @Override
    public void addTemporaryBlock(BackupBlock block) throws IOException {
        getBlockTmpMap().put(block.getHash(), encodeData(BACKUP_BLOCK_WRITER, stripCopy(block)));
    }

    private Path getPath(String file) {
        return Paths.get(dataPath, file);
    }

    private void deleteAlternativeBlocksTable() {
        String file = alternateBlockTable ? BLOCK_STORE : BLOCK_ALT_STORE;
        File oldFile = getPath(file).toFile();
        IOUtils.deleteFile(oldFile);
    }

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

    private BackupBlock stripCopy(BackupBlock block) {
        return BackupBlock.builder().storage(block.getStorage()).format(block.getFormat()).created(block.getCreated())
                .hashes(block.getHashes()).offsets(block.getOffsets())
                .build();
    }

    @Override
    public void addDirectory(BackupDirectory directory) throws IOException {
        directoryMap.put(new Object[]{directory.getPath(), directory.getAdded()},
                encodeData(BACKUP_DIRECTORY_FILES_WRITER, directory.getFiles()));
    }

    @Override
    public boolean deleteBlock(BackupBlock block) throws IOException {
        return blockMap.remove(block.getHash()) != null;
    }

    @Override
    public boolean deleteFile(BackupFile file) throws IOException {
        return fileMap.remove(new Object[]{file.getPath(), file.getAdded()}) != null;
    }

    @Override
    public boolean deleteFilePart(BackupFilePart part) throws IOException {
        return partsMap.remove(new Object[]{part.getPartHash(), part.getBlockHash()}) != null;
    }

    @Override
    public boolean deleteDirectory(String path, long timestamp) throws IOException {
        return directoryMap.remove(new Object[]{path, timestamp}) != null;
    }

    @Override
    public void pushActivePath(String setId, String path, BackupActivePath pendingFiles) throws IOException {
        activePathMap.put(new Object[]{setId, path}, encodeData(BACKUP_ACTIVE_PATH_WRITER, pendingFiles));
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
        try {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
                try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                    return reader.readValue(gzipInputStream);
                }
            }
        } catch (JsonMappingException exc) {
            try {
                try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
                    try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                        byte[] plaintextData = IOUtils.readAllBytes(gzipInputStream);
                        log.error("Failed decoding: {}", new String(plaintextData, StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception decodeTest2) {
                log.error("Failed to decode string for supposed JSON object", decodeTest2);
            }
            throw exc;
        }
    }

    @Override
    public boolean hasActivePath(String setId, String path) {
        return activePathMap.containsKey(new Object[]{setId, path});
    }

    @Override
    public void popActivePath(String setId, String path) throws IOException {
        activePathMap.remove(new Object[]{setId, path});
    }

    @Override
    public boolean deletePartialFile(BackupPartialFile file) {
        return partialFileMap.remove(file.getFile().getPath()) != null;
    }

    @Override
    public void savePartialFile(BackupPartialFile file) throws IOException {
        partialFileMap.put(file.getFile().getPath(), encodeData(BACKUP_PARTIAL_FILE_WRITER, file));
    }

    @Override
    public void clearPartialFiles() {
        partialFileMap.clear();
    }

    @Override
    public BackupPartialFile getPartialFile(BackupPartialFile file) {
        return getPartialFileInternal(file);
    }

    private BackupPartialFile getPartialFileInternal(BackupPartialFile file) {
        byte[] data = partialFileMap.get(file.getFile().getPath());
        if (data != null) {
            BackupPartialFile ret;
            try {
                ret = decodeData(BACKUP_PARTIAL_FILE_READER, data);
            } catch (IOException exc) {
                log.error("Invalid partialFile {} reprocessing entire file",
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
                log.error("Invalid activePath {} for set {}. Skipping during this run.", entry.getKey()[1], entry.getKey()[0],
                        exc);
            }
        }
        return ret;
    }

    public long getBlockCount() throws IOException {
        return blockMap.size();
    }

    public long getFileCount() throws IOException {
        return fileMap.size();
    }

    public long getDirectoryCount() {
        return directoryMap.size();
    }

    public long getPartCount() {
        return partsMap.size();
    }

    @Override
    public long getAdditionalBlockCount() throws IOException {
        return additionalBlockMap.size();
    }

    @Override
    public long getUpdatedFileCount() throws IOException {
        return updatedPendingFilesMap.size();
    }

    public void clear() throws IOException {
        File parent = new File(dataPath);
        if (revision == 0) {
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

    private BackupBlockAdditional stripCopy(BackupBlockAdditional block) {
        return BackupBlockAdditional.builder().used(block.isUsed()).properties(block.getProperties()).build();
    }

    @Override
    public void addAdditionalBlock(BackupBlockAdditional block) throws IOException {
        additionalBlockMap.put(new Object[]{block.getPublicKey(), block.getHash()},
                encodeData(BACKUP_BLOCK_ADDITIONAL_WRITER, stripCopy(block)));
    }

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
                throw new IOException(String.format("Invalid additionalBlock %s:%s", publicKey, blockHash), exc);
            }
        }
        return null;
    }

    @Override
    public void deleteAdditionalBlock(String publicKey, String blockHash) {
        if (blockHash != null) {
            additionalBlockMap.remove(new Object[]{publicKey, blockHash});
        } else {
            Map<Object[], byte[]> query = additionalBlockMap.prefixSubMap(new Object[]{publicKey});
            for (Object[] key : query.keySet())
                additionalBlockMap.remove(key);
        }
    }

    @Override
    public boolean addUpdatedFile(BackupUpdatedFile file, long howOften) throws IOException {
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
    }

    @Override
    public void removeUpdatedFile(BackupUpdatedFile file) {
        updatedFilesMap.remove(file.getPath());
        updatedPendingFilesMap.remove(new Object[]{file.getLastUpdated(), file.getPath()});
    }

    @Override
    public CloseableStream<BackupUpdatedFile> getUpdatedFiles() {
        final Map<Object[], byte[]> map = updatedPendingFilesMap.ascendingMap();

        Stream<BackupUpdatedFile> stream = map.keySet().stream()
                .map(keys -> new BackupUpdatedFile((String) keys[1], (Long) keys[0]));

        return new MapdbCloseableStream<>(stream);
    }

    public static class TreeOrSink implements Closeable {
        private BTreeMap<Object[], byte[]> tree;
        private DB.TreeMapSink<Object[], byte[]> sink;
        private Object[] lastKey;

        public TreeOrSink(BTreeMap<Object[], byte[]> tree) {
            this.tree = tree;
        }

        public TreeOrSink(DB.TreeMapSink<Object[], byte[]> sink) {
            this.sink = sink;
        }

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

        @Override
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

        public byte[] get(Object[] objects) {
            if (tree == null) {
                closeSink();
            }
            return tree.get(objects);
        }

        public long size() {
            if (tree == null) {
                closeSink();
            }
            return tree.size();
        }
    }

    private static class MapdbCloseableStream<T> implements CloseableStream<T> {
        private final Stream<T> stream;
        @Setter
        private boolean reportErrorsAsNull;

        private MapdbCloseableStream(Stream<T> stream) {
            this.stream = stream;
        }


        @Override
        public Stream<T> stream() {
            if (reportErrorsAsNull)
                return stream;
            return stream.filter(Objects::nonNull);
        }
    }

    private static class TemporaryMapdbMap<K, V> implements CloseableMap<K, V> {
        private final MapSerializer<K, V> serializer;
        private final File root;
        private final DB db;
        private final TreeOrSink map;
        private int writeCount;

        public TemporaryMapdbMap(MapSerializer<K, V> serializer) throws IOException {
            this.serializer = serializer;

            root = File.createTempFile("underscorebackup", ".db");
            IOUtils.deleteFile(root);

            DBMaker.Maker maker = DBMaker
                    .fileDB(root)
                    .fileMmapEnableIfSupported()
                    .fileMmapPreclearDisable()
                    .transactionEnable();
            db = maker.make();

            map = openTreeMap(db, db.treeMap("map")
                    .keySerializer(new SerializerArrayTuple(Serializer.BYTE_ARRAY))
                    .valueSerializer(Serializer.BYTE_ARRAY));
        }

        @Override
        public void close() {
            map.close();
            db.close();

            IOUtils.deleteFile(root);
        }

        @Override
        public synchronized void put(K k, V v) {
            byte[] kb = serializer.encodeKey(k);
            byte[] vb = serializer.encodeValue(v);

            increaseWrite();
            map.put(new Object[]{kb}, vb);
        }

        private void increaseWrite() {
            writeCount++;
            if (writeCount >= 50000) {
                writeCount = 0;
                db.commit();
            }
        }

        @Override
        public boolean delete(K k) {
            increaseWrite();
            return map.remove(new Object[]{serializer.encodeKey(k)}) != null;
        }

        @Override
        public V get(K k) {
            byte[] ret = map.get(new Object[]{serializer.encodeKey(k)});
            if (ret != null) {
                return serializer.decodeValue(ret);
            }
            return null;
        }

        @Override
        public Stream<Map.Entry<K, V>> readOnlyEntryStream() {
            return map.ascendingMap().entrySet().stream().map((entry) -> Map.entry(serializer.decodeKey((byte[]) entry.getKey()[0]),
                    serializer.decodeValue(entry.getValue())));
        }
    }
}