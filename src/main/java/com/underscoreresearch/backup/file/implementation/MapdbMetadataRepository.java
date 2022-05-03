package com.underscoreresearch.backup.file.implementation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.FileLockInterruptionException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupLocation;
import com.underscoreresearch.backup.model.BackupPartialFile;
import com.underscoreresearch.backup.model.BackupPendingSet;

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

    private static final String REQUEST_LOCK_FILE = "request.lock";
    private static final String LOCK_FILE = "access.lock";

    private static class AccessLock implements Closeable {
        private String filename;
        private RandomAccessFile file;
        private FileChannel channel;
        private FileLock lock;

        public AccessLock(String filename) throws FileNotFoundException {
            this.filename = filename;
        }

        public synchronized boolean tryLock(boolean exclusive) throws IOException {
            ensureOpenFile();
            if (lock == null) {
                while (true) {
                    try {
                        Thread.interrupted();
                        lock = channel.tryLock(0, Long.MAX_VALUE, !exclusive);
                        if (exclusive && lock != null) {
                            writePid();
                        }
                        return lock != null;
                    } catch (ClosedChannelException e) {
                        ensureOpenFile();
                    } catch (FileLockInterruptionException e) {
                    }
                }
            }
            return true;
        }

        private void ensureOpenFile() throws IOException {
            if (channel == null || !channel.isOpen()) {
                close();
                file = new RandomAccessFile(filename, "rw");
                channel = file.getChannel();
            }
        }

        public synchronized void lock(boolean exclusive) throws IOException {
            ensureOpenFile();
            if (lock == null) {
                while (true) {
                    try {
                        Thread.interrupted();
                        do {
                            lock = channel.lock(0, Long.MAX_VALUE, !exclusive);

                            if (exclusive) {
                                writePid();
                            }
                        } while (lock == null || !lock.isValid());
                        break;
                    } catch (ClosedChannelException e) {
                        ensureOpenFile();
                    } catch (FileLockInterruptionException e) {
                    }
                }
            }
        }

        private void writePid() throws ClosedChannelException {
            try {
                byte[] data = (ManagementFactory.getRuntimeMXBean().getPid() + "\n")
                        .getBytes(StandardCharsets.UTF_8);
                ByteBuffer buffer = ByteBuffer.wrap(data);
                lock.channel().position(0);
                lock.channel().write(buffer);
                lock.channel().truncate(data.length);
            } catch (IOException e) {
                log.warn("Failed to write PID to lock file", e);
                try {
                    lock.close();
                } catch (ClosedChannelException exc) {
                    lock = null;
                    throw exc;
                } catch (IOException exc) {
                    log.error("Failed to close lock", e);
                }
                lock = null;
            }
        }

        public synchronized void release() throws IOException {
            if (lock != null) {
                if (!lock.isShared()) {
                    try {
                        channel.truncate(0);
                    } catch (IOException exc) {
                        log.error("Failed to remove PID from lock file {}", filename, exc);
                    }
                }
                lock.close();
                lock = null;
            }
        }

        @Override
        public synchronized void close() throws IOException {
            release();
            if (channel != null) {
                channel.close();
            }
            if (file != null) {
                file.close();
            }
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
    }

    private static final String FILE_STORE = "files.db";
    private static final String BLOCK_STORE = "blocks.db";
    private static final String PARTS_STORE = "parts.db";
    private static final String DIRECTORY_STORE = "directories.db";
    private static final String ACTIVE_PATH_STORE = "paths.db";
    private static final String PENDING_SET_STORE = "pendingset.db";
    private static final String PARTIAL_FILE_STORE = "partialfiles.db";

    private final String dataPath;
    private boolean open;
    private boolean readOnly;

    private DB blockDb;
    private DB fileDb;
    private DB directoryDb;
    private DB partsDb;
    private DB activePathDb;
    private DB pendingSetDb;
    private DB partialFileDb;

    private AccessLock fileLock;

    private HTreeMap<String, byte[]> blockMap;
    private BTreeMap<Object[], byte[]> fileMap;
    private BTreeMap<Object[], byte[]> directoryMap;
    private BTreeMap<Object[], byte[]> partsMap;
    private BTreeMap<Object[], byte[]> activePathMap;
    private HTreeMap<String, byte[]> pendingSetMap;
    private HTreeMap<String, byte[]> partialFileMap;

    private ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    public MapdbMetadataRepository(String dataPath) throws IOException {
        this.dataPath = dataPath;
    }

    private void commit() {
        blockDb.commit();
        fileDb.commit();
        directoryDb.commit();
        partsDb.commit();
        activePathDb.commit();
        pendingSetDb.commit();
        partialFileDb.commit();
    }

    public synchronized void open(boolean readOnly) throws IOException {
        if (open && !readOnly && this.readOnly) {
            close();
        }
        if (!open) {
            open = true;
            this.readOnly = readOnly;

            File requestFile = Paths.get(dataPath, REQUEST_LOCK_FILE).toFile();
            if (readOnly) {
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
                scheduledThreadPoolExecutor.scheduleAtFixedRate(this::commit, 1, 1, TimeUnit.MINUTES);
                scheduledThreadPoolExecutor.scheduleAtFixedRate(this::checkAccessRequest, 1, 1, TimeUnit.SECONDS);

                fileLock = new AccessLock(Paths.get(dataPath, LOCK_FILE).toString());
                if (!fileLock.tryLock(true)) {
                    log.info("Waiting for repository access from other process");
                    fileLock.lock(true);
                }
            }

            blockDb = setupReadOnly(readOnly, DBMaker
                    .fileDB(Paths.get(dataPath, BLOCK_STORE).toString())
                    .fileMmapEnableIfSupported()
                    .transactionEnable())
                    .make();
            fileDb = setupReadOnly(readOnly, DBMaker
                    .fileDB(Paths.get(dataPath, FILE_STORE).toString())
                    .fileMmapEnableIfSupported()
                    .transactionEnable())
                    .make();
            directoryDb = setupReadOnly(readOnly, DBMaker
                    .fileDB(Paths.get(dataPath, DIRECTORY_STORE).toString())
                    .fileMmapEnableIfSupported()
                    .transactionEnable())
                    .make();
            partsDb = setupReadOnly(readOnly, DBMaker
                    .fileDB(Paths.get(dataPath, PARTS_STORE).toString())
                    .fileMmapEnableIfSupported()
                    .transactionEnable())
                    .make();
            activePathDb = setupReadOnly(readOnly, DBMaker
                    .fileDB(Paths.get(dataPath, ACTIVE_PATH_STORE).toString())
                    .fileMmapEnableIfSupported()
                    .transactionEnable())
                    .make();
            pendingSetDb = setupReadOnly(readOnly, DBMaker
                    .fileDB(Paths.get(dataPath, PENDING_SET_STORE).toString())
                    .fileMmapEnableIfSupported()
                    .transactionEnable())
                    .make();
            partialFileDb = setupReadOnly(readOnly, DBMaker
                    .fileDB(Paths.get(dataPath, PARTIAL_FILE_STORE).toString())
                    .fileMmapEnableIfSupported()
                    .transactionEnable())
                    .make();

            blockMap = blockDb.hashMap(BLOCK_STORE, Serializer.STRING, Serializer.BYTE_ARRAY).createOrOpen();
            fileMap = fileDb.treeMap(FILE_STORE)
                    .keySerializer(new SerializerArrayTuple(Serializer.STRING, Serializer.LONG))
                    .valueSerializer(Serializer.BYTE_ARRAY).createOrOpen();
            directoryMap = directoryDb.treeMap(FILE_STORE)
                    .keySerializer(new SerializerArrayTuple(Serializer.STRING, Serializer.LONG))
                    .valueSerializer(Serializer.BYTE_ARRAY).createOrOpen();
            activePathMap = activePathDb.treeMap(ACTIVE_PATH_STORE)
                    .keySerializer(new SerializerArrayTuple(Serializer.STRING, Serializer.STRING))
                    .valueSerializer(Serializer.BYTE_ARRAY).createOrOpen();
            partsMap = partsDb.treeMap(FILE_STORE)
                    .keySerializer(new SerializerArrayTuple(Serializer.STRING, Serializer.STRING))
                    .valueSerializer(Serializer.BYTE_ARRAY).createOrOpen();
            pendingSetMap = pendingSetDb.hashMap(BLOCK_STORE, Serializer.STRING, Serializer.BYTE_ARRAY).createOrOpen();
            partialFileMap = partialFileDb.hashMap(BLOCK_STORE, Serializer.STRING, Serializer.BYTE_ARRAY).createOrOpen();
        }
    }

    private synchronized void checkAccessRequest() {
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
        }
    }

    private DBMaker.Maker setupReadOnly(boolean readOnly, DBMaker.Maker dbMaker) {
        if (readOnly)
            return dbMaker.readOnly();
        return dbMaker;
    }

    public synchronized void close() throws IOException {
        if (open) {
            commit();

            if (scheduledThreadPoolExecutor != null) {
                scheduledThreadPoolExecutor.shutdownNow();
                scheduledThreadPoolExecutor = null;
            }

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

            fileLock.close();
            open = false;
        }
    }

    @Override
    public synchronized List<BackupFile> file(String path) throws IOException {
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

    private synchronized BackupFile decodeFile(Map.Entry<Object[], byte[]> entry) throws IOException {
        BackupFile readValue = decodeData(BACKUP_FILE_READER, entry.getValue());
        readValue.setPath((String) entry.getKey()[0]);
        readValue.setAdded((Long) entry.getKey()[1]);
        if (readValue.getLastChanged() == null)
            readValue.setLastChanged(readValue.getAdded());

        return readValue;
    }

    @Override
    public synchronized List<BackupFilePart> existingFilePart(String partHash) throws IOException {
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

    @Override
    public synchronized Stream<BackupFile> allFiles() throws IOException {
        ensureOpen();

        return fileMap.descendingMap().entrySet().stream().map((entry) -> {
            try {
                return decodeFile(entry);
            } catch (IOException e) {
                log.error("Invalid file " + entry.getKey()[0], e);
                return BackupFile.builder().build();
            }
        }).filter(t -> t.getPath() != null);
    }

    @Override
    public synchronized Stream<BackupBlock> allBlocks() throws IOException {
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
    public synchronized Stream<BackupFilePart> allFileParts() throws IOException {
        ensureOpen();

        return partsMap.entrySet().stream().map((entry) -> {
            try {
                return decodePath(entry);
            } catch (IOException e) {
                log.error("Invalid block " + entry.getKey(), e);
                return BackupFilePart.builder().build();
            }
        }).filter(t -> t.getPartHash() != null);
    }

    @Override
    public Stream<BackupDirectory> allDirectories() throws IOException {
        ensureOpen();

        return directoryMap.descendingMap().entrySet().stream().map((entry) -> {
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
                return null;
            }
        }).filter(t -> t != null).collect(Collectors.toSet());
    }

    private synchronized BackupFilePart decodePath(Map.Entry<Object[], byte[]> entry) throws IOException {
        BackupFilePart readValue = decodeData(BACKUP_PART_READER, entry.getValue());
        readValue.setPartHash((String) entry.getKey()[0]);
        readValue.setBlockHash((String) entry.getKey()[1]);
        return readValue;
    }

    @Override
    public synchronized BackupFile lastFile(String path) throws IOException {
        ensureOpen();

        NavigableMap<Object[], byte[]> query =
                fileMap.prefixSubMap(new Object[]{path});
        if (query.size() > 0) {
            Map.Entry<Object[], byte[]> entry = query.lastEntry();
            return decodeFile(entry);
        }
        return null;
    }

    @Override
    public synchronized BackupBlock block(String hash) throws IOException {
        ensureOpen();

        byte[] data = blockMap.get(hash);
        if (data != null) {
            BackupBlock block = decodeBlock(hash, data);
            block.setHash(hash);
            return block;
        }
        return null;
    }

    private synchronized BackupBlock decodeBlock(String hash, byte[] data) throws IOException {
        ensureOpen();

        BackupBlock block = decodeData(BACKUP_BLOCK_READER, data);
        block.setHash(hash);
        return block;
    }

    @Override
    public synchronized List<BackupDirectory> directory(String path) throws IOException {
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

    @Override
    public synchronized BackupDirectory lastDirectory(String path) throws IOException {
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

    @Override
    public synchronized void addFile(BackupFile file) throws IOException {
        ensureOpen();

        Long added;
        if (file.getAdded() == null)
            added = file.getLastChanged();
        else
            added = file.getAdded();

        fileMap.put(new Object[]{file.getPath(), added},
                encodeData(BACKUP_FILE_WRITER, strippedCopy(file)));

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

    private BackupFilePart strippedCopy(BackupFilePart part) {
        return BackupFilePart.builder().blockIndex(part.getBlockIndex()).build();
    }

    private BackupFile strippedCopy(BackupFile file) {
        return BackupFile.builder()
                .length(file.getLength())
                .locations(file.getLocations())
                .lastChanged(file.getLastChanged())
                .build();
    }

    @Override
    public synchronized void addBlock(BackupBlock block) throws IOException {
        ensureOpen();

        blockMap.put(block.getHash(), encodeData(BACKUP_BLOCK_WRITER, stripCopy(block)));
    }

    private BackupBlock stripCopy(BackupBlock block) {
        return BackupBlock.builder().storage(block.getStorage()).format(block.getFormat()).created(block.getCreated())
                .hashes(block.getHashes())
                .build();
    }

    @Override
    public synchronized void addDirectory(BackupDirectory directory) throws IOException {
        ensureOpen();

        directoryMap.put(new Object[]{directory.getPath(), directory.getAdded()},
                encodeData(BACKUP_DIRECTORY_WRITER, directory.getFiles()));
    }

    @Override
    public synchronized boolean deleteBlock(BackupBlock block) throws IOException {
        ensureOpen();

        return blockMap.remove(block.getHash()) != null;
    }

    @Override
    public synchronized boolean deleteFile(BackupFile file) throws IOException {
        ensureOpen();

        return fileMap.remove(new Object[]{file.getPath(), file.getAdded()}) != null;
    }

    @Override
    public synchronized boolean deleteFilePart(BackupFilePart part) throws IOException {
        ensureOpen();

        return partsMap.remove(new Object[]{part.getPartHash(), part.getBlockHash()}) != null;
    }

    @Override
    public synchronized boolean deleteDirectory(String path, long timestamp) throws IOException {
        ensureOpen();

        return directoryMap.remove(new Object[]{path, timestamp}) != null;
    }

    @Override
    public synchronized void pushActivePath(String setId, String path, BackupActivePath pendingFiles) throws IOException {
        ensureOpen();

        activePathMap.put(new Object[]{setId, path}, encodeData(BACKUP_ACTIVE_WRITER, pendingFiles));
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
    public synchronized boolean hasActivePath(String setId, String path) throws IOException {
        ensureOpen();

        return activePathMap.containsKey(new Object[]{setId, path});
    }

    @Override
    public synchronized void popActivePath(String setId, String path) throws IOException {
        ensureOpen();

        activePathMap.remove(new Object[]{setId, path});
    }

    @Override
    public synchronized boolean deletePartialFile(BackupPartialFile file) throws IOException {
        ensureOpen();

        return partialFileMap.remove(file.getFile().getPath()) != null;
    }

    @Override
    public synchronized void savePartialFile(BackupPartialFile file) throws IOException {
        ensureOpen();

        partialFileMap.put(file.getFile().getPath(), encodeData(BACKUP_PARTIAL_FILE_WRITER, file));
    }

    @Override
    public synchronized BackupPartialFile getPartialFile(BackupPartialFile file) throws IOException {
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

    @Override
    public synchronized TreeMap<String, BackupActivePath> getActivePaths(String setId) throws IOException {
        ensureOpen();

        Map<Object[], byte[]> readMap = activePathMap;
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

    private void ensureOpen() throws IOException {
        if (!open) {
            open(readOnly);
        }
    }

    @Override
    public synchronized void flushLogging() throws IOException {
        if (open) {
            commit();
        }
    }
}
