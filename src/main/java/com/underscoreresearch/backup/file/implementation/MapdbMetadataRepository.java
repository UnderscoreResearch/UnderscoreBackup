package com.underscoreresearch.backup.file.implementation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.*;
import lombok.extern.slf4j.Slf4j;
import org.mapdb.*;
import org.mapdb.serializer.SerializerArrayTuple;

import java.io.*;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.FileLockInterruptionException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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
    private static final ObjectReader BACKUP_SET_READER;

    private static final ObjectWriter BACKUP_SET_WRITER;
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
                lock = channel.tryLock(0, Long.MAX_VALUE, exclusive);
            }
            return lock != null;
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
                        lock = channel.lock(0, Long.MAX_VALUE, exclusive);
                        break;
                    } catch (ClosedChannelException e) {
                        ensureOpenFile();
                    } catch (FileLockInterruptionException e) {
                    }
                }
            }
        }

        public synchronized void release() throws IOException {
            if (lock != null) {
                lock.close();
                lock = null;
            }
        }

        @Override
        public void close() throws IOException {
            if (lock != null) {
                lock.close();
                lock = null;
            }
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
        BACKUP_SET_READER = mapper.readerFor(new TypeReference<NavigableSet<String>>() {
        });

        BACKUP_SET_WRITER = mapper.writerFor(new TypeReference<Set<String>>() {
        });

    }

    private static final String FILE_STORE = "files.db";
    private static final String BLOCK_STORE = "blocks.db";
    private static final String PARTS_STORE = "parts.db";
    private static final String DIRECTORY_STORE = "directories.db";
    private static final String ACTIVE_PATH_STORE = "paths.db";

    private final String dataPath;
    private boolean open;
    private boolean readOnly;

    private DB blockDb;
    private DB fileDb;
    private DB directoryDb;
    private DB partsDb;
    private DB activePathDb;

    private AccessLock fileLock;

    private HTreeMap<String, byte[]> blockMap;
    private BTreeMap<Object[], byte[]> fileMap;
    private BTreeMap<Object[], byte[]> directoryMap;
    private BTreeMap<Object[], byte[]> partsMap;
    private BTreeMap<Object[], byte[]> activePathMap;

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
    }

    public synchronized void open(boolean readOnly) throws IOException {
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
                scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);
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
            }

            blockMap.close();
            fileMap.close();
            directoryMap.close();
            activePathMap.close();
            partsMap.close();

            blockDb.close();
            fileDb.close();
            directoryDb.close();
            partsDb.close();
            activePathDb.close();

            open = false;
            fileLock.close();
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
            files.add(decodrateFile(entry));
        }
        return files;
    }

    private synchronized BackupFile decodrateFile(Map.Entry<Object[], byte[]> entry) throws IOException {
        BackupFile readValue = decodeData(BACKUP_FILE_READER, entry.getValue());
        readValue.setPath((String) entry.getKey()[0]);
        readValue.setLastChanged((Long) entry.getKey()[1]);
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
                return decodrateFile(entry);
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
    public Stream<BackupDirectory> allDirectories() throws IOException {
        ensureOpen();

        return directoryMap.descendingMap().entrySet().stream().map((entry) -> {
            try {
                return new BackupDirectory((String) entry.getKey()[0], (Long) entry.getKey()[1],
                        decodeData(BACKUP_SET_READER, entry.getValue()));
            } catch (IOException e) {
                log.error("Invalid directory " + entry.getKey()[0], e);
                return new BackupDirectory();
            }
        });
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
            return decodrateFile(entry);
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
                    decodeData(BACKUP_SET_READER, entry.getValue())));
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
                    decodeData(BACKUP_SET_READER, entry.getValue()));
        }
        return null;
    }

    @Override
    public synchronized void addFile(BackupFile file) throws IOException {
        ensureOpen();

        fileMap.put(new Object[]{file.getPath(), file.getLastChanged()},
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
        return BackupFile.builder().length(file.getLength()).locations(file.getLocations()).build();
    }

    @Override
    public synchronized void addBlock(BackupBlock block) throws IOException {
        ensureOpen();

        blockMap.put(block.getHash(), encodeData(BACKUP_BLOCK_WRITER, stripCopy(block)));
    }

    private BackupBlock stripCopy(BackupBlock block) {
        return BackupBlock.builder().storage(block.getStorage()).format(block.getFormat()).created(block.getCreated())
                .build();
    }

    @Override
    public synchronized void addDirectory(BackupDirectory directory) throws IOException {
        ensureOpen();

        directoryMap.put(new Object[]{directory.getPath(), directory.getTimestamp()},
                encodeData(BACKUP_SET_WRITER, directory.getFiles()));
    }

    @Override
    public synchronized boolean deleteBlock(BackupBlock block) throws IOException {
        ensureOpen();

        return blockMap.remove(block.getHash()) != null;
    }

    @Override
    public synchronized boolean deleteFile(BackupFile file) throws IOException {
        ensureOpen();

        return fileMap.remove(new Object[]{file.getPath(), file.getLastChanged()}) != null;
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
    public void flushLogging() throws IOException {
    }
}
