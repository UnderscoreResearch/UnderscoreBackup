package com.underscoreresearch.backup.file.implementation;

import static com.underscoreresearch.backup.file.implementation.LockingMetadataRepository.MINIMUM_WAIT_UPDATE_MS;
import static com.underscoreresearch.backup.io.IOUtils.clearTempFiles;
import static com.underscoreresearch.backup.io.IOUtils.createDirectory;
import static com.underscoreresearch.backup.io.IOUtils.deleteContents;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;
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
import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.EnvFlags.MDB_RDONLY_ENV;
import static org.lmdbjava.EnvFlags.MDB_WRITEMAP;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.Nullable;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Stat;
import org.lmdbjava.Txn;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import com.fasterxml.jackson.databind.util.ByteBufferBackedOutputStream;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.underscoreresearch.backup.encryption.Hash;
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

@Slf4j
public class LmdbMetadataRepositoryStorage implements MetadataRepositoryStorage {
    private static final ObjectReader BACKUP_DIRECTORY_FILES_READER
            = MAPPER.readerFor(DirectoryEncoding.class);
    private static final ObjectWriter BACKUP_DIRECTORY_FILES_WRITER
            = MAPPER.writerFor(DirectoryEncoding.class);
    private static final String FILE_STORE = "files";
    private static final String BLOCK_STORE = "blocks";
    private static final String BLOCK_ALT_STORE = "blocks2";
    private static final String PARTS_STORE = "parts";
    private static final String DIRECTORY_STORE = "directories";
    private static final String ACTIVE_PATH_STORE = "paths";
    private static final String PENDING_SET_STORE = "pendingset";
    private static final String PARTIAL_FILE_STORE = "partialfiles";
    private static final String ADDITIONAL_BLOCK_STORE = "additionalblocks";
    private static final String UPDATED_FILES_STORE = "updatedfiles";
    private static final String UPDATED_PENDING_FILES_STORE = "updatedpendingfiles";
    private static final double ASSUMED_OVERHEAD = 1.6;
    private static final int MAX_KEY_SIZE = 511;
    private static final long SMALL_MAP_SIZE = 64L * 1024 * 1024;
    private static final long LARGE_MAP_SIZE = 512L * 1024 * 1024;
    private static final boolean SPARSE_MAP = !SystemUtils.IS_OS_WINDOWS;
    private static final long INCREASE_SPACE = SPARSE_MAP ? (LARGE_MAP_SIZE / 2) : SMALL_MAP_SIZE;
    private static final long MINIMUM_FREE_SPACE = SPARSE_MAP ? (LARGE_MAP_SIZE / 4) : SMALL_MAP_SIZE;
    private static final String STORAGE_ROOT = "v2";
    private static final int CHECK_EXPAND_FREQUENCY = 1000;
    private static final int MAXIMUM_UNCOMMITTED_UPDATES = 50000;
    private static final ByteBuffer EMPTY_STRING_BUFFER = ByteBuffer.allocateDirect(1);
    private static final int MAX_PATH_LENGTH = 500;
    private final List<Runnable> preCommitActions = new ArrayList<>();
    private final File root;
    private final Object exclusiveLock = new Object();
    private ExecutorService executor;
    private Env<ByteBuffer> db;
    private long mapSize;
    private Dbi<ByteBuffer> blockMap;
    private Dbi<ByteBuffer> blockTmpMap;
    private Dbi<ByteBuffer> additionalBlockMap;
    private Dbi<ByteBuffer> fileMap;
    private Dbi<ByteBuffer> directoryMap;
    private Dbi<ByteBuffer> partsMap;
    private Dbi<ByteBuffer> activePathMap;
    private Dbi<ByteBuffer> updatedPendingFilesMap;
    private Dbi<ByteBuffer> pendingSetMap;
    private Dbi<ByteBuffer> partialFileMap;
    private Dbi<ByteBuffer> updatedFilesMap;
    private boolean alternateBlockTable;
    private Txn<ByteBuffer> sharedTransaction;
    private boolean readOnly;
    private int updateCount;
    private Thread exclusiveThread;
    private Stopwatch sharedTransactionAge;

    public LmdbMetadataRepositoryStorage(String dataPath, int revision, boolean alternateBlockTable) {
        this(Paths.get(dataPath, revision == 0 ? STORAGE_ROOT : String.format("%s-%d", STORAGE_ROOT, revision))
                .toFile(), alternateBlockTable);
    }

    protected LmdbMetadataRepositoryStorage(File root, boolean alternateBlockTable) {
        this.alternateBlockTable = alternateBlockTable;
        this.root = root;
    }

    private static PathTimestamp decodeTimestampPath(ByteBuffer buffer) {
        long when = buffer.getLong();
        return new PathTimestamp(
                StandardCharsets.UTF_8.decode(buffer.slice(Long.BYTES, buffer.limit() - Long.BYTES)).toString(),
                when);
    }

    private static PathTimestamp decodePathTimestamp(ByteBuffer buffer) {
        String path;
        if (!missingPath(buffer)) {
            path = StandardCharsets.UTF_8.decode(buffer.slice(0, buffer.limit() - Long.BYTES - 1)).toString();
        } else {
            path = null;
        }
        Long timestamp = buffer.slice(buffer.limit() - Long.BYTES, Long.BYTES).getLong();
        return new PathTimestamp(path, timestamp);
    }

    private static boolean missingPath(ByteBuffer buffer) {
        return buffer.get(0) == 0 && buffer.limit() > Long.BYTES + 1;
    }

    private static KeyRange<ByteBuffer> prefixRange(ByteBuffer prefix) {
        ByteBuffer clone = upperRangeBuffer(prefix);
        if (clone == null)
            return KeyRange.atLeast(prefix);
        return KeyRange.openClosed(prefix, clone);
    }

    @Nullable
    private static ByteBuffer upperRangeBuffer(ByteBuffer prefix) {
        ByteBuffer clone = ByteBuffer.allocateDirect(prefix.limit());
        prefix.rewind();//copy from the beginning
        int lastIndex = prefix.limit() - 1;
        clone.put(0, prefix, 0, lastIndex);
        // Lot of work dealing with the special case where our prefix ends in max value (Which is -128)
        // assuming the comparison is unsigned.
        if (prefix.get(lastIndex) == -128) {
            clone.put((byte) 0);
            for (int i = lastIndex - 1; ; i--) {
                if (i < 0) {
                    return null;
                }
                byte b = clone.get(i);
                if (b != -128) {
                    clone.put(i, (byte) (b + 1));
                    break;
                }
            }
        } else {
            clone.put(lastIndex, (byte) (prefix.get(lastIndex) + 1));
        }
        clone.limit(prefix.limit());
        prefix.rewind();
        return clone;
    }

    private static ByteBuffer encodeDoubleString(String str1, String str2) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(MAX_KEY_SIZE);
        byteBuffer.put(str1.getBytes(StandardCharsets.UTF_8));
        byteBuffer.put((byte) 0);
        if (str2 != null) {
            byte[] secondString = str2.getBytes(StandardCharsets.UTF_8);
            byteBuffer.put(secondString);
        }
        byteBuffer.flip();
        return byteBuffer;
    }

    private static String[] decodeDoubleString(ByteBuffer buffer) {
        byte[] data = new byte[buffer.limit()];
        for (int i = 0; i < buffer.limit(); i++) {
            byte c = buffer.get(i);
            if (c == 0) {
                return new String[]{
                        new String(data, 0, i, StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8.decode(buffer.slice(i + 1, buffer.limit() - i - 1)).toString()
                };
            }
            data[i] = c;
        }
        throw new IllegalArgumentException("Didn't find string separator");
    }

    private static ByteBuffer encodeLong(long when) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Long.BYTES);
        byteBuffer.putLong(when);
        byteBuffer.flip();
        return byteBuffer;
    }

    private static long decodeLong(ByteBuffer buffer) {
        return buffer.getLong();
    }

    private static ByteBuffer encodeTimestampPath(long when, String path) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(MAX_KEY_SIZE);
        byteBuffer.putLong(when);
        byteBuffer.put(path.getBytes(StandardCharsets.UTF_8));
        byteBuffer.flip();
        return byteBuffer;
    }

    private static String decodeString(ByteBuffer buffer) {
        if (buffer.limit() == 1 && buffer.get(0) == 0) {
            return StringUtils.EMPTY;
        }
        return StandardCharsets.UTF_8.decode(buffer).toString();
    }

    private static ByteBuffer encodeString(String val) {
        byte[] data = val.getBytes(StandardCharsets.UTF_8);
        if (data.length == 0) {
            if (EMPTY_STRING_BUFFER.limit() == 0) {
                EMPTY_STRING_BUFFER.put((byte) 0);
                EMPTY_STRING_BUFFER.flip();
            } else {
                EMPTY_STRING_BUFFER.rewind();
            }
            return EMPTY_STRING_BUFFER;
        }
        ByteBuffer ret = ByteBuffer.allocateDirect(data.length);
        ret.put(data);
        ret.flip();
        return ret;
    }

    private static KeyRange<ByteBuffer> prefixRangeReverse(ByteBuffer prefix) {
        ByteBuffer clone = upperRangeBuffer(prefix);
        if (clone == null)
            return KeyRange.atLeastBackward(prefix);
        return KeyRange.openClosedBackward(clone, prefix);
    }

    private static <T> T decodeData(ObjectReader reader, ByteBuffer data) throws IOException {
        try {
            try (ByteBufferBackedInputStream inputStream = new ByteBufferBackedInputStream(data)) {
                try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                    return reader.readValue(gzipInputStream);
                }
            }
        } catch (JsonMappingException exc) {
            try {
                try (ByteBufferBackedInputStream inputStream = new ByteBufferBackedInputStream(data)) {
                    try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                        byte[] plaintextData = IOUtils.readAllBytes(gzipInputStream);
                        log.error("Failed decoding: \"{}\"", new String(plaintextData, StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception decodeTest2) {
                log.error("Failed to decode string for supposed JSON object (Size \u200E{}\u200E)", data.limit(), decodeTest2);
            }
            throw exc;
        }
    }

    private static BackupFilePart decodePath(CursorIterable.KeyVal<ByteBuffer> entry) throws IOException {
        String[] keys = decodeDoubleString(entry.key());
        try {
            BackupFilePart readValue = decodeData(BACKUP_FILE_PART_READER, entry.val());
            readValue.setPartHash(keys[0]);
            readValue.setBlockHash(keys[1]);
            return readValue;
        } catch (IOException e) {
            throw new IOException(String.format("Invalid path \"%s:%s\"", keys[0], keys[1]), e);
        }
    }

    private static ByteBuffer encodeData(ObjectWriter writer, Object obj) throws IOException {
        int bufferSize = 16384;

        while (true) {
            try {
                try {
                    ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
                    try (ByteBufferBackedOutputStream outputStream = new ByteBufferBackedOutputStream(buffer)) {
                        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
                            writer.writeValue(gzipOutputStream, obj);
                        }
                    }
                    buffer.flip();
                    return buffer;
                } catch (JsonMappingException exc) {
                    if (exc.getCause() instanceof BufferOverflowException bufferOverflowException) {
                        throw bufferOverflowException;
                    }
                    throw exc;
                }
            } catch (BufferOverflowException exc) {
                bufferSize *= 2;
                final int finalBuffer = bufferSize;
                debug(() -> log.debug("Buffer too small, retrying with double the size: {}", finalBuffer));
            }
        }
    }

    private static BackupFile decodeFile(CursorIterable.KeyVal<ByteBuffer> entry) throws IOException {
        PathTimestamp pathTimestamp = decodePathTimestamp(entry.key());
        try {
            BackupFile readValue = decodeData(BACKUP_FILE_READER, entry.val());
            if (pathTimestamp.path != null)
                readValue.setPath(pathTimestamp.path);
            readValue.setAdded(pathTimestamp.timestamp);
            if (readValue.getLastChanged() == null)
                readValue.setLastChanged(readValue.getAdded());

            return readValue;
        } catch (Exception exc) {
            throw new IOException(String.format("Failed to decode file \"%s:%s\"", pathTimestamp.path, pathTimestamp.timestamp),
                    exc);
        }
    }

    private static BackupBlock decodeBlock(CursorIterable.KeyVal<ByteBuffer> keyVal) throws IOException {
        return decodeBlock(decodeString(keyVal.key()), keyVal.val());
    }

    private static BackupBlock decodeBlock(String hash, ByteBuffer val) throws IOException {
        try {
            BackupBlock block = decodeData(BACKUP_BLOCK_READER, val);
            block.setHash(hash);
            return block;
        } catch (IOException e) {
            throw new IOException(String.format("Invalid block \"%s\"", hash), e);
        }
    }

    private static BackupDirectory decodeDirectory(CursorIterable.KeyVal<ByteBuffer> entry) throws IOException {
        PathTimestamp pathTimestamp = decodePathTimestamp(entry.key());
        try {
            DirectoryEncoding encoding = decodeData(BACKUP_DIRECTORY_FILES_READER, entry.val());
            return new BackupDirectory(pathTimestamp.path == null ? encoding.getPath() : pathTimestamp.getPath(),
                    pathTimestamp.timestamp,
                    encoding.permissions,
                    encoding.files,
                    encoding.deleted);
        } catch (IOException e) {
            throw new IOException(String.format("Invalid directory \"%s:%s\"", pathTimestamp.path, pathTimestamp.timestamp), e);
        }
    }

    private static BackupFilePart strippedCopy(BackupFilePart part) {
        return BackupFilePart.builder().blockIndex(part.getBlockIndex()).build();
    }

    private static BackupFile strippedCopy(ByteBuffer buffer, BackupFile file) {
        BackupFile ret = BackupFile.builder()
                .length(file.getLength())
                .locations(file.getLocations())
                .permissions(file.getPermissions())
                .deleted(file.getDeleted())
                .lastChanged(file.getLastChanged())
                .build();
        if (missingPath(buffer)) {
            ret.setPath(file.getPath());
        }
        return ret;
    }

    private static BackupBlock strippedCopy(BackupBlock block) {
        return BackupBlock.builder().storage(block.getStorage()).format(block.getFormat()).created(block.getCreated())
                .hashes(block.getHashes()).offsets(block.getOffsets())
                .build();
    }

    private static long calculateStatSize(Stat dbStat) {
        long pageSizes = dbStat.pageSize * (dbStat.leafPages + dbStat.branchPages + dbStat.overflowPages);
        long entrySizes = dbStat.entries * (MAX_KEY_SIZE + 1);
        return pageSizes + entrySizes;
    }

    private static void throwException(Throwable exc) throws IOException {
        if (exc != null) {
            if (exc instanceof IOException ioException)
                throw ioException;
            if (exc instanceof RuntimeException runtimeException)
                throw runtimeException;
            throw new RuntimeException(exc);
        }
    }

    private static BackupBlockAdditional strippedCopy(BackupBlockAdditional block) {
        return BackupBlockAdditional.builder().used(block.isUsed()).properties(block.getProperties()).build();
    }

    protected long openMapSize(File root) {
        File dataFile = new File(root, "data.mdb");
        if (dataFile.exists()) {
            return dataFile.length();
        } else if (SPARSE_MAP) {
            return LARGE_MAP_SIZE;
        } else {
            return SMALL_MAP_SIZE;
        }
    }

    protected Env<ByteBuffer> createDb(File root, long mapSize, boolean readOnly) {
        EnvFlags[] flags = createDbFlags(readOnly);

        createDirectory(root, true);

        return Env.create()
                .setMapSize(mapSize)
                .setMaxDbs(30)
                .setMaxReaders(10)
                .open(root, flags);
    }

    protected EnvFlags[] createDbFlags(boolean readOnly) {
        if (readOnly)
            return new EnvFlags[]{MDB_RDONLY_ENV, MDB_WRITEMAP};
        else
            return new EnvFlags[]{MDB_WRITEMAP};
    }

    private <T> CloseableStream<T> createStream(Dbi<ByteBuffer> db,
                                                boolean forward,
                                                Function<CursorIterable.KeyVal<ByteBuffer>, T> decoder) {
        TransactionIterator<T> iterator = new TransactionIterator<>(db, decoder, forward);
        Stream<T> stream = StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        iterator,
                        Spliterator.ORDERED),
                false);

        return new CloseableStream<>() {
            @Setter
            private boolean reportErrorsAsNull;

            @Override
            public Stream<T> stream() {
                if (reportErrorsAsNull)
                    return stream;
                return stream.filter(Objects::nonNull);
            }

            @Override
            public void close() {
                stream.close();
            }
        };
    }

    @Override
    public CloseableLock exclusiveLock() throws IOException {
        synchronized (exclusiveLock) {
            if (exclusiveThread != null)
                throw new RuntimeException("Exclusive lock already granted");
            commit();

            removeExecutor();
            exclusiveThread = Thread.currentThread();
        }

        return new CloseableLock() {
            @Override
            public void close() {
                internalCommit();
                synchronized (exclusiveLock) {
                    exclusiveThread = null;
                    createExecutor();
                }
            }

            @Override
            public boolean requested() {
                return false;
            }
        };
    }

    @Override
    public void open(RepositoryOpenMode openMode) throws IOException {
        this.readOnly = openMode == RepositoryOpenMode.READ_ONLY;
        if (db == null) {
            db = createDb(readOnly);
        }

        blockMap = openCreateMap(db, alternateBlockTable ? BLOCK_ALT_STORE : BLOCK_STORE);
        additionalBlockMap = openCreateMap(db, ADDITIONAL_BLOCK_STORE);
        fileMap = openCreateMap(db, FILE_STORE);
        directoryMap = openCreateMap(db, DIRECTORY_STORE);
        activePathMap = openCreateMap(db, ACTIVE_PATH_STORE);
        partsMap = openCreateMap(db, PARTS_STORE);
        updatedPendingFilesMap = openCreateMap(db, UPDATED_PENDING_FILES_STORE);
        pendingSetMap = openCreateMap(db, PENDING_SET_STORE);
        partialFileMap = openCreateMap(db, PARTIAL_FILE_STORE);
        updatedFilesMap = openCreateMap(db, UPDATED_FILES_STORE);

        createExecutor();

        if (!readOnly) {
            migrateTimestampPaths(fileMap, FILE_STORE);
            migrateTimestampPaths(directoryMap, DIRECTORY_STORE);

            clearTempFiles();
        }
    }

    /**
     * This method deals with a problem where the original encoding of path timestamps clashed with the root path
     * when doing ranged queries. The problem only exists for extremely long files so a repository should not have
     * too many of them hopefully. This method does a single pass to find all the problematic paths and stores them
     * into a new keyspace that does not clash. It only has to run once for files and directories to re-encode any
     * problematic entries.
     */
    private void migrateTimestampPaths(Dbi<ByteBuffer> map, String name) throws IOException {
        synchronizeDbAccess(() -> {
            internalCommit();
            Dbi<ByteBuffer> metadata = openCreateMap(db, "metadata");
            Txn<ByteBuffer> txn = getWriteTransaction();
            try {
                if (metadata.get(txn, encodeString(name)) == null) {
                    try (CursorIterable<ByteBuffer> ci = map.iterate(txn,
                            prefixRangeReverse(encodePathTimestamp("", null)))) {
                        for (final CursorIterable.KeyVal<ByteBuffer> kv : ci) {
                            if (missingPath(kv.key())) {
                                if (kv.key().limit() == 42) {
                                    ByteBuffer newKey = ByteBuffer.allocateDirect(43).put((byte) 0).put((byte) 255);
                                    newKey.put(kv.key().slice(1, 41));
                                    newKey.flip();
                                    map.put(txn, newKey, kv.val());
                                    map.delete(txn, kv.key());
                                } else if (kv.key().limit() != 43) {
                                    log.error("Unexpected key size for empty record: \u200E{}\u200E", kv.key().limit());
                                }
                            }
                        }
                    }
                    metadata.put(txn, encodeString(name), ByteBuffer.allocateDirect(1).put((byte) 0).flip());
                    internalCommit();
                }
            } finally {
                metadata.close();
            }
        });
    }

    private void createExecutor() {
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(getClass().getSimpleName())
                .build());
    }

    private <T> T synchronizeDbAccess(ExceptionSupplier<T> function) throws IOException {
        AtomicReference<T> result;
        AtomicReference<Throwable> exception = new AtomicReference<>();

        synchronized (exception) {
            synchronized (exclusiveLock) {
                if (exclusiveThread != null) {
                    if (exclusiveThread != Thread.currentThread()) {
                        throw new IllegalStateException("Exclusive lock from other thread");
                    }
                    try {
                        return function.get();
                    } catch (Exception e) {
                        logUsage(e);
                        throwException(e);
                    }
                }

                result = new AtomicReference<>();

                executor.submit(() -> {
                    synchronized (exception) {
                        try {
                            result.set(function.get());
                        } catch (Throwable e) {
                            logUsage(e);
                            exception.set(e);
                        }
                        exception.notify();
                    }
                });
            }

            try {
                exception.wait();
            } catch (InterruptedException e) {
                log.error("Failed to wait", e);
            }

            throwException(exception.get());
            return result.get();
        }
    }

    private void synchronizeDbAccess(ExceptionRunnable function) throws IOException {
        synchronizeDbAccess(() -> {
            function.run();
            return null;
        });
    }

    private void logUsage(Throwable exc) {
        if (exc instanceof Env.MapFullException) {
            preCommitActions.forEach(Runnable::run);
            preCommitActions.clear();
            sharedTransaction.close();
            sharedTransaction = null;
            long estimated = getTotalUsedEstimatedSize();
            log.error("Estimated usage at full map exception: \u200E{} of {} ({} overhead and {} uncommitted transactions)\u200E",
                    readableSize(estimated),
                    readableSize(mapSize),
                    readableSize((long) Math.ceil(estimated * ASSUMED_OVERHEAD)),
                    updateCount);
        }
    }

    private Dbi<ByteBuffer> openCreateMap(Env<ByteBuffer> blockDb, String dbName) {
        return blockDb.openDbi(dbName, MDB_CREATE);
    }

    private Dbi<ByteBuffer> getBlockTmpMap() {
        if (blockTmpMap == null) {
            internalCommit();
            blockTmpMap = openCreateMap(db, alternateBlockTable ? BLOCK_STORE : BLOCK_ALT_STORE);
            if (blockTmpMap.stat(getWriteTransaction()).entries > 0) {
                blockTmpMap.drop(getWriteTransaction(), false);
            }
        }
        return blockTmpMap;
    }

    private Env<ByteBuffer> createDb(boolean readOnly) {
        mapSize = openMapSize(root);
        return createDb(root, mapSize, readOnly);
    }

    @Override
    public void close() throws IOException {
        synchronized (exclusiveLock) {
            if (executor != null) {
                synchronizeDbAccess(() -> {
                    internalCommit();

                    closeFiles(true);
                });

                removeExecutor();
            } else {
                closeFiles(true);
            }
        }
    }

    private void removeExecutor() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS)) {
                throw new RuntimeException("Failed to wait for executor shutdown");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        executor = null;
    }

    private void closeFiles(boolean closeDb) {
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

        if (blockTmpMap != null) {
            blockTmpMap.close();
            blockTmpMap = null;
        }

        if (closeDb) {
            db.close();
            db = null;
        }
    }

    private ByteBuffer encodePathTimestampBytes(String path, ByteBuffer timestampBytes) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(MAX_KEY_SIZE);
        byte[] pathData = path.getBytes(StandardCharsets.UTF_8);
        if (pathData.length > MAX_PATH_LENGTH) {
            byteBuffer.put((byte) 0);
            byteBuffer.put((byte) 255);
            pathData = Hashing.sha256().hashBytes(pathData).asBytes();
        }
        byteBuffer.put(pathData);
        byteBuffer.put((byte) 0);
        if (timestampBytes != null) {
            byteBuffer.put(timestampBytes);
        }
        byteBuffer.flip();
        return byteBuffer;
    }

    private ByteBuffer encodePathTimestamp(String path, Long timestamp) {
        if (timestamp != null) {
            return encodePathTimestampBytes(path, encodeLong(timestamp));
        }
        return encodePathTimestampBytes(path, null);
    }

    private Txn<ByteBuffer> getReadTransaction() {
        if (sharedTransaction == null) {
            sharedTransaction = readOnly ? db.txnRead() : db.txnWrite();
            sharedTransactionAge = Stopwatch.createStarted();
        }
        return sharedTransaction;
    }

    private Txn<ByteBuffer> getWriteTransaction() {
        if (readOnly) {
            throw new IllegalArgumentException("Read only database");
        }
        return getReadTransaction();
    }

    @Override
    public List<ExternalBackupFile> file(String path) throws IOException {
        return synchronizeDbAccess(() -> {
            List<ExternalBackupFile> files = null;
            Txn<ByteBuffer> txn = getReadTransaction();
            try (CursorIterable<ByteBuffer> ci = fileMap.iterate(txn,
                    createPathRange(path, null, true))) {
                for (final CursorIterable.KeyVal<ByteBuffer> kv : ci) {
                    if (files == null)
                        files = new ArrayList<>();
                    files.add(new ExternalBackupFile(decodeFile(kv)));
                }
            }
            return files;
        });
    }

    @Override
    public List<BackupFilePart> existingFilePart(String partHash) throws IOException {
        return synchronizeDbAccess(() -> {
            List<BackupFilePart> parts = null;
            Txn<ByteBuffer> txn = getReadTransaction();
            try (CursorIterable<ByteBuffer> ci = partsMap.iterate(txn, prefixRange(encodeDoubleString(partHash, null)))) {
                for (final CursorIterable.KeyVal<ByteBuffer> kv : ci) {
                    if (parts == null)
                        parts = new ArrayList<>();
                    parts.add(decodePath(kv));
                }
            }
            return parts;
        });
    }

    private String invalidRepositoryLogEntry(String msg) {
        if (readOnly)
            return msg + " (Read only repository)";
        return msg + " (Removing from repository)";
    }

    @Override
    public CloseableStream<BackupFile> allFiles(boolean ascending) throws IOException {
        return createStream(fileMap, ascending, (entry) -> {
            try {
                return decodeFile(entry);
            } catch (IOException e) {
                entry.key().position(0);
                PathTimestamp key = decodePathTimestamp(entry.key());
                log.error(invalidRepositoryLogEntry("Invalid file \"{}:{}\""), PathNormalizer.physicalPath(key.path), key.path, e);
                if (!readOnly) {
                    try {
                        entry.key().position(0);
                        fileMap.delete(getWriteTransaction(), entry.key());
                        checkExpand();
                    } catch (Exception exc) {
                        log.error("Failed to delete invalid entry", exc);
                    }
                }
                return null;
            }
        });
    }

    @Override
    public CloseableStream<BackupBlock> allBlocks() throws IOException {
        return createStream(blockMap, true, (entry) -> {
            try {
                return decodeBlock(entry);
            } catch (IOException e) {
                entry.key().position(0);
                String key = decodeString(entry.key());
                log.error(invalidRepositoryLogEntry("Invalid block \"{}\""), key, e);
                if (!readOnly) {
                    try {
                        entry.key().position(0);
                        blockMap.delete(getWriteTransaction(), entry.key());
                        checkExpand();
                    } catch (Exception exc) {
                        log.error("Failed to delete invalid entry", exc);
                    }
                }
                return null;
            }
        });
    }

    @Override
    public CloseableStream<BackupBlockAdditional> allAdditionalBlocks() {
        return createStream(additionalBlockMap, true, (entry) -> {
            ByteBuffer data = entry.key();
            String[] keys = decodeDoubleString(data);
            try {
                BackupBlockAdditional block = decodeData(BACKUP_BLOCK_ADDITIONAL_READER, entry.val());
                block.setHash(keys[1]);
                block.setPublicKey(keys[0]);
                return block;
            } catch (IOException e) {
                log.error(invalidRepositoryLogEntry("Invalid additional block \"{}:{}\""), keys[0], keys[1], e);
                if (!readOnly) {
                    try {
                        entry.key().position(0);
                        additionalBlockMap.delete(getWriteTransaction(), entry.key());
                        checkExpand();
                    } catch (Exception exc) {
                        log.error("Failed to delete invalid entry", exc);
                    }
                }
                return null;
            }
        });
    }

    @Override
    public CloseableStream<BackupFilePart> allFileParts() {
        return createStream(partsMap, true, (entry) -> {
            try {
                return decodePath(entry);
            } catch (IOException e) {
                entry.key().position(0);
                String[] keys = decodeDoubleString(entry.key());
                log.error(invalidRepositoryLogEntry("Invalid filePart \"{}:{}\""), keys[0], keys[1], e);
                if (!readOnly) {
                    try {
                        entry.key().position(0);
                        partsMap.delete(getWriteTransaction(), entry.key());
                        checkExpand();
                    } catch (Exception exc) {
                        log.error("Failed to delete invalid entry", exc);
                    }
                }
                return null;
            }
        });
    }

    @Override
    public CloseableStream<BackupDirectory> allDirectories(boolean ascending) {
        return createStream(directoryMap, ascending, (entry) -> {
            try {
                return decodeDirectory(entry);
            } catch (IOException e) {
                entry.key().position(0);
                PathTimestamp pathTimestamp = decodePathTimestamp(entry.key());
                log.error(invalidRepositoryLogEntry("Invalid directory \"{}:{}\""), pathTimestamp.path, pathTimestamp.timestamp, e);
                if (!readOnly) {
                    try {
                        entry.key().position(0);
                        directoryMap.delete(getWriteTransaction(), entry.key());
                        checkExpand();
                    } catch (Exception exc) {
                        log.error("Failed to delete invalid entry", exc);
                    }
                }
                return null;
            }
        });
    }

    @Override
    public void addPendingSets(BackupPendingSet scheduledTime) throws IOException {
        synchronizeDbAccess(() -> {
            pendingSetMap.put(getWriteTransaction(), encodeString(scheduledTime.getSetId()),
                    encodeData(BACKUP_PENDING_SET_WRITER,
                            scheduledTime.toBuilder().setId(null).build()));
            checkExpand();
        });
    }

    @Override
    public void deletePendingSets(String setId) throws IOException {
        synchronizeDbAccess(() -> {
            pendingSetMap.delete(getWriteTransaction(), encodeString(setId));
            checkExpand();
        });
    }

    @Override
    public Set<BackupPendingSet> getPendingSets() throws IOException {
        try (CloseableStream<BackupPendingSet> sets = createStream(pendingSetMap, true, (entry) -> {
            String setId = decodeString(entry.key());
            try {
                BackupPendingSet set = decodeData(BACKUP_PENDING_SET_READER, entry.val());
                set.setSetId(setId);
                return set;
            } catch (IOException e) {
                log.error("Invalid pending set \"" + setId + "\"", e);
                try {
                    entry.key().position(0);
                    pendingSetMap.delete(getWriteTransaction(), entry.key());
                    checkExpand();
                } catch (Exception exc) {
                    log.error("Failed to delete invalid entry", exc);
                }
                return null;
            }
        })) {
            return sets.stream().collect(Collectors.toSet());
        }
    }

    @Override
    public BackupFile file(String path, Long timestamp) throws IOException {
        return synchronizeDbAccess(() -> fileInternal(getReadTransaction(), path, timestamp));
    }

    private BackupFile fileInternal(Txn<ByteBuffer> txn, String path, Long timestamp) throws IOException {
        try (CursorIterable<ByteBuffer> ci = fileMap.iterate(txn,
                createPathRange(path, timestamp, false))) {
            for (final CursorIterable.KeyVal<ByteBuffer> kv : ci) {
                return decodeFile(kv);
            }
        }
        return null;
    }

    private ByteBuffer timestampRangeBuffer(Long timestamp) {
        if (timestamp != null) {
            return encodeLong(timestamp);
        }
        return ByteBuffer.allocateDirect(1).put((byte) 0xff).flip();
    }

    @Override
    public BackupBlock block(String hash) throws IOException {
        return synchronizeDbAccess(() -> {
            Txn<ByteBuffer> txn = getReadTransaction();
            ByteBuffer data = blockMap.get(txn, encodeString(hash));
            if (data != null) {
                return decodeBlock(hash, data);
            }
            return null;
        });
    }

    @Override
    public BackupDirectory directory(String path, Long timestamp, boolean accumulative) throws IOException {
        return synchronizeDbAccess(() -> {
            CursorIterable<ByteBuffer> ci;
            ci = directoryMap.iterate(getReadTransaction(), createPathRange(path, timestamp, false));

            BackupDirectory directory = null;
            for (final CursorIterable.KeyVal<ByteBuffer> kv : ci) {
                BackupDirectory curDir = decodeDirectory(kv);
                if (!accumulative)
                    return curDir;
                if (directory == null)
                    directory = curDir;
                else
                    directory.getFiles().addAll(curDir.getFiles());
            }
            return directory;
        });
    }

    private KeyRange<ByteBuffer> createPathRange(String path, Long timestamp, boolean ascending) {
        ByteBuffer upper = encodePathTimestampBytes(path, timestampRangeBuffer(timestamp));
        ByteBuffer lower = encodePathTimestamp(path, 0L);
        if (ascending) {
            if (timestamp != null)
                return KeyRange.closed(lower, upper);
            return KeyRange.closedOpen(lower, upper);
        }
        if (timestamp != null)
            return KeyRange.closedBackward(upper, lower);
        return KeyRange.openClosedBackward(upper, lower);
    }

    private BackupDirectory lastDirectoryInternal(String path) throws IOException {
        Txn<ByteBuffer> txn = getReadTransaction();
        try (CursorIterable<ByteBuffer> ci = directoryMap.iterate(txn,
                createPathRange(path, null, false))) {
            for (final CursorIterable.KeyVal<ByteBuffer> kv : ci) {
                return decodeDirectory(kv);
            }
        }

        return null;
    }

    @Override
    public void addFile(BackupFile file) throws IOException {
        Long added;
        if (file.getAdded() == null)
            added = file.getLastChanged();
        else
            added = file.getAdded();

        synchronizeDbAccess(() -> {
            ByteBuffer key = encodePathTimestamp(file.getPath(), added);
            fileMap.put(getWriteTransaction(), key, encodeData(BACKUP_FILE_WRITER, strippedCopy(key, file)));
            checkExpand();
        });
    }

    @Override
    public void addFilePart(BackupFilePart part) throws IOException {
        synchronizeDbAccess(() -> {
            partsMap.put(getWriteTransaction(), encodeDoubleString(part.getPartHash(), part.getBlockHash()),
                    encodeData(BACKUP_FILE_PART_WRITER, strippedCopy(part)));
            checkExpand();
        });
    }

    @Override
    public void addBlock(BackupBlock block) throws IOException {
        synchronizeDbAccess(() -> {
            blockMap.put(getWriteTransaction(), encodeString(block.getHash()), encodeData(BACKUP_BLOCK_WRITER, strippedCopy(block)));
            checkExpand();
        });
    }

    @Override
    public void addTemporaryBlock(BackupBlock block) throws IOException {
        synchronizeDbAccess(() -> {
            getBlockTmpMap().put(getWriteTransaction(), encodeString(block.getHash()), encodeData(BACKUP_BLOCK_WRITER, strippedCopy(block)));
            checkExpand();
        });
    }

    @Override
    public void switchBlocksTable() throws IOException {
        synchronizeDbAccess(() -> {
            if (blockTmpMap == null) {
                throw new IOException("Switching alternative block table when it is empty");
            }
            internalCommit();
            if (sharedTransaction != null) {
                throw new IOException("Failed to commit changes before switching block table");
            }
            blockMap.close();
            blockMap = blockTmpMap;
            blockTmpMap = null;
            alternateBlockTable = !alternateBlockTable;

            // Opening this will erase all contents.
            getBlockTmpMap();
        });
    }

    @Override
    public void addDirectory(BackupDirectory directory) throws IOException {
        synchronizeDbAccess(() -> {
            ByteBuffer key = encodePathTimestamp(directory.getPath(), directory.getAdded());
            DirectoryEncoding encoding = new DirectoryEncoding(directory);
            if (missingPath(key))
                encoding.setPath(directory.getPath());
            directoryMap.put(getWriteTransaction(), key,
                    encodeData(BACKUP_DIRECTORY_FILES_WRITER, encoding));
            checkExpand();
        });
    }

    @Override
    public boolean deleteBlock(BackupBlock block) throws IOException {
        return synchronizeDbAccess(() -> {
            boolean ret = blockMap.delete(getWriteTransaction(), encodeString(block.getHash()));
            if (ret)
                checkExpand();
            return ret;
        });
    }

    @Override
    public boolean deleteFile(BackupFile file) throws IOException {
        return synchronizeDbAccess(() -> {
            boolean ret = fileMap.delete(getWriteTransaction(), encodePathTimestamp(file.getPath(), file.getAdded()));
            if (ret)
                checkExpand();
            return ret;
        });
    }

    @Override
    public boolean deleteFilePart(BackupFilePart part) throws IOException {
        return synchronizeDbAccess(() -> {
            boolean ret = partsMap.delete(getWriteTransaction(), encodeDoubleString(part.getPartHash(), part.getBlockHash()));
            if (ret)
                checkExpand();
            return ret;
        });
    }

    @Override
    public boolean deleteDirectory(String path, long timestamp) throws IOException {
        return synchronizeDbAccess(() -> {
            boolean ret = directoryMap.delete(getWriteTransaction(), encodePathTimestamp(path, timestamp));
            if (ret)
                checkExpand();
            return ret;
        });
    }

    @Override
    public void pushActivePath(String setId, String path, BackupActivePath pendingFiles) throws IOException {
        synchronizeDbAccess(() -> {
            pendingFiles.setSavedRealPath(path);
            activePathMap.put(getWriteTransaction(), encodeDoubleString(setId, Hash.hash64(path.getBytes(StandardCharsets.UTF_8))),
                    encodeData(BACKUP_ACTIVE_PATH_WRITER, pendingFiles));
            checkExpand();
        });
    }

    @Override
    public boolean hasActivePath(String setId, String path) throws IOException {
        return synchronizeDbAccess(() -> activePathMap.get(getReadTransaction(), encodeDoubleString(setId, Hash.hash64(path.getBytes(StandardCharsets.UTF_8)))) != null);
    }

    @Override
    public void popActivePath(String setId, String path) throws IOException {
        synchronizeDbAccess(() -> {
            activePathMap.delete(getWriteTransaction(), encodeDoubleString(setId, Hash.hash64(path.getBytes(StandardCharsets.UTF_8))));
            checkExpand();
        });
    }

    @Override
    public boolean deletePartialFile(BackupPartialFile file) throws IOException {
        return synchronizeDbAccess(() -> {
            boolean ret = partialFileMap.delete(getWriteTransaction(), encodeString(Hash.hash64(file.getFile().getPath().getBytes(StandardCharsets.UTF_8))));
            if (ret)
                checkExpand();
            return ret;
        });
    }

    @Override
    public void savePartialFile(BackupPartialFile file) throws IOException {
        synchronizeDbAccess(() -> {
            partialFileMap.put(getWriteTransaction(), encodeString(Hash.hash64(file.getFile().getPath().getBytes(StandardCharsets.UTF_8))),
                    encodeData(BACKUP_PARTIAL_FILE_WRITER, file));
            checkExpand();
        });
    }

    @Override
    public void clearPartialFiles() throws IOException {
        synchronizeDbAccess(() -> {
            Txn<ByteBuffer> txn = getWriteTransaction();
            partialFileMap.drop(txn, false);
            checkExpand();
        });
    }

    @Override
    public BackupPartialFile getPartialFile(BackupPartialFile file) throws IOException {
        return synchronizeDbAccess(() -> {
            Txn<ByteBuffer> txn = getReadTransaction();
            return getPartialFileInternal(txn, file);
        });
    }

    private BackupPartialFile getPartialFileInternal(Txn<ByteBuffer> txn, BackupPartialFile file) {
        ByteBuffer data = partialFileMap.get(txn, encodeString(Hash.hash64(file.getFile().getPath().getBytes(StandardCharsets.UTF_8))));
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

    @Override
    public TreeMap<String, BackupActivePath> getActivePaths(String setId) throws IOException {
        return synchronizeDbAccess(() -> {
            TreeMap<String, BackupActivePath> ret = new TreeMap<>();
            Txn<ByteBuffer> txn = getReadTransaction();
            try (CursorIterable<ByteBuffer> ci = activePathMap.iterate(txn,
                    setId != null ? prefixRange(encodeDoubleString(setId, null)) : KeyRange.all())) {
                for (final CursorIterable.KeyVal<ByteBuffer> entry : ci) {
                    String[] keys = decodeDoubleString(entry.key());
                    try {
                        BackupActivePath activePath = decodeData(BACKUP_ACTIVE_PATH_READER, entry.val());
                        activePath.setParentPath(activePath.getSavedRealPath());
                        activePath.setSetIds(Lists.newArrayList(keys[0]));

                        BackupActivePath existingActive = ret.get(activePath.getSavedRealPath());
                        if (existingActive != null) {
                            activePath.mergeChanges(existingActive);
                        }

                        ret.put(activePath.getSavedRealPath(), activePath);
                    } catch (IOException exc) {
                        log.error("Invalid activePath \"{}\" for set \"{}\". Skipping during this run.", keys[1], keys[0],
                                exc);
                    }
                }
            }

            return ret;
        });
    }

    @Override
    public long getBlockCount() throws IOException {
        return synchronizeDbAccess(() -> blockMap.stat(getReadTransaction()).entries);
    }

    @Override
    public long getFileCount() throws IOException {
        return synchronizeDbAccess(() -> fileMap.stat(getReadTransaction()).entries);
    }

    @Override
    public long getDirectoryCount() throws IOException {
        return synchronizeDbAccess(() -> directoryMap.stat(getReadTransaction()).entries);
    }

    @Override
    public long getPartCount() throws IOException {
        return synchronizeDbAccess(() -> partsMap.stat(getReadTransaction()).entries);
    }

    @Override
    public long getAdditionalBlockCount() throws IOException {
        return synchronizeDbAccess(() -> additionalBlockMap.stat(getReadTransaction()).entries);
    }

    @Override
    public long getUpdatedFileCount() throws IOException {
        return synchronizeDbAccess(() -> {
            return updatedPendingFilesMap.stat(getReadTransaction()).entries;
        });
    }

    @Override
    public void addAdditionalBlock(BackupBlockAdditional block) throws IOException {
        synchronizeDbAccess(() -> {
            additionalBlockMap.put(getWriteTransaction(), encodeDoubleString(block.getPublicKey(), block.getHash()),
                    encodeData(BACKUP_BLOCK_ADDITIONAL_WRITER, strippedCopy(block)));
            checkExpand();
        });
    }

    @Override
    public BackupBlockAdditional additionalBlock(String publicKey, String blockHash) throws IOException {
        return synchronizeDbAccess(() -> {
            Txn<ByteBuffer> txn = getReadTransaction();
            ByteBuffer data = additionalBlockMap.get(txn, encodeDoubleString(publicKey, blockHash));
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
        });
    }

    @Override
    public void deleteAdditionalBlock(String publicKey, String blockHash) throws IOException {
        synchronizeDbAccess(() -> {
            if (blockHash != null) {
                additionalBlockMap.delete(getWriteTransaction(), encodeDoubleString(publicKey, blockHash));
            } else {
                Txn<ByteBuffer> txn = getWriteTransaction();
                try (CursorIterable<ByteBuffer> ci = additionalBlockMap.iterate(txn, prefixRange(encodeDoubleString(publicKey, null)))) {
                    for (final CursorIterable.KeyVal<ByteBuffer> entry : ci) {
                        additionalBlockMap.delete(txn, entry.key());
                    }
                }
            }
            checkExpand();
        });
    }

    @Override
    public boolean addUpdatedFile(BackupUpdatedFile file, long howOften) throws IOException {
        return synchronizeDbAccess(() -> {
            Txn<ByteBuffer> txn = getWriteTransaction();
            String hashedPath = Hash.hash64(file.getPath().getBytes(StandardCharsets.UTF_8));
            if (howOften < 0) {
                updatedFilesMap.put(txn, encodeString(hashedPath), encodeLong(file.getLastUpdated()));
                updatedPendingFilesMap.put(txn, encodeTimestampPath(file.getLastUpdated(), hashedPath),
                        encodeString(file.getPath()));
                checkExpand();
                return false;
            } else {
                ByteBuffer buffer = updatedFilesMap.get(txn, encodeString(hashedPath));
                if (buffer == null) {
                    long lastExisting = 0;
                    if (file.getPath().endsWith(PathNormalizer.PATH_SEPARATOR)) {
                        BackupDirectory dir = lastDirectoryInternal(file.getPath());
                        if (dir != null) {
                            lastExisting = dir.getAdded();
                        }
                    } else {
                        BackupPartialFile partialFile = getPartialFileInternal(txn, new BackupPartialFile(
                                BackupFile.builder().path(file.getPath()).build()));
                        if (partialFile != null) {
                            lastExisting = partialFile.getFile().getLastChanged();
                        } else {
                            BackupFile existingFile = fileInternal(txn, file.getPath(), null);
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

                    updatedFilesMap.put(txn, encodeString(hashedPath), encodeLong(when));
                    updatedPendingFilesMap.put(txn, encodeTimestampPath(when, hashedPath),
                            encodeString(file.getPath()));
                    checkExpand();
                    return true;
                } else {
                    long updated = decodeLong(buffer);
                    if (file.getLastUpdated() + MINIMUM_WAIT_UPDATE_MS > updated) {
                        updatedPendingFilesMap.delete(txn, encodeTimestampPath(updated, hashedPath));
                        updatedPendingFilesMap.put(txn, encodeTimestampPath(
                                        file.getLastUpdated() + MINIMUM_WAIT_UPDATE_MS, hashedPath),
                                encodeString(file.getPath()));
                        updatedFilesMap.put(txn, encodeString(hashedPath),
                                encodeLong(file.getLastUpdated() + MINIMUM_WAIT_UPDATE_MS));
                        checkExpand();
                        return true;
                    }
                    return false;
                }
            }
        });
    }

    @Override
    public void removeUpdatedFile(BackupUpdatedFile file) throws IOException {
        synchronizeDbAccess(() -> {
            Txn<ByteBuffer> txn = getWriteTransaction();
            String hashedPath = Hash.hash64(file.getPath().getBytes(StandardCharsets.UTF_8));
            updatedFilesMap.delete(txn, encodeString(hashedPath));
            updatedPendingFilesMap.delete(txn, encodeTimestampPath(file.getLastUpdated(), hashedPath));
            checkExpand();
        });
    }

    @Override
    public CloseableStream<BackupUpdatedFile> getUpdatedFiles() {
        return createStream(updatedPendingFilesMap, true, entry -> {
            PathTimestamp timestamp = decodeTimestampPath(entry.key());
            return new BackupUpdatedFile(decodeString(entry.val()), timestamp.timestamp);
        });
    }

    @Override
    public void clear() throws IOException {
        deleteContents(root);
        IOUtils.deleteFile(root);
    }

    @Override
    public void commit() throws IOException {
        synchronized (exclusiveLock) {
            if (exclusiveThread == null || exclusiveThread == Thread.currentThread()) {
                synchronizeDbAccess(this::internalCommit);
            }
        }
    }

    private void internalCommit() {
        if (sharedTransaction != null) {
            preCommitActions.forEach(Runnable::run);
            preCommitActions.clear();
            sharedTransaction.commit();
            sharedTransaction.close();
            sharedTransaction = null;
            updateCount = 0;
        }
    }

    protected void checkExpand() throws IOException {
        if ((++updateCount) % CHECK_EXPAND_FREQUENCY == 0) {
            long usedSize = getTotalUsedEstimatedSize();

            if (usedSize * ASSUMED_OVERHEAD > mapSize - MINIMUM_FREE_SPACE) {
                internalCommit();
                closeFiles(false);
                mapSize += INCREASE_SPACE;
                debug(() -> log.debug("Increasing repository size to {}", readableSize(mapSize)));
                db.setMapSize(mapSize);
                open(readOnly ? RepositoryOpenMode.READ_ONLY : RepositoryOpenMode.READ_WRITE);
            }
        }

        commitIfNeeded();
    }

    protected void commitIfNeeded() {
        if (sharedTransactionAge.elapsed(TimeUnit.SECONDS) >= 60 || updateCount > MAXIMUM_UNCOMMITTED_UPDATES) {
            internalCommit();
        }
    }

    private long getTotalUsedEstimatedSize() {
        Stat stat = db.stat();
        long usedSize = calculateStatSize(stat);

        usedSize += Stream.of(
                        blockMap,
                        additionalBlockMap,
                        fileMap,
                        directoryMap,
                        partsMap,
                        activePathMap,
                        updatedPendingFilesMap,
                        pendingSetMap,
                        partialFileMap,
                        updatedFilesMap,
                        blockTmpMap
                ).filter(Objects::nonNull)
                .map(map -> map.stat(getReadTransaction()))
                .mapToLong(LmdbMetadataRepositoryStorage::calculateStatSize)
                .sum();

        return usedSize;
    }

    @Override
    public boolean needPeriodicCommits() {
        return false;
    }

    @Override
    public <K, V> CloseableMap<K, V> temporaryMap(MapSerializer<K, V> serializer) throws IOException {
        return new TemporaryLmdbMap<>(this, serializer);
    }

    @Override
    public <K, V> CloseableSortedMap<K, V> temporarySortedMap(MapSerializer<K, V> serializer) throws IOException {
        return new TemporaryLmdbMap<>(this, serializer);
    }

    @Override
    public boolean needExclusiveCommitLock() {
        return true;
    }

    private interface ExceptionSupplier<T> {
        T get() throws Exception;
    }

    private interface ExceptionRunnable {
        void run() throws Exception;
    }

    @Data
    @NoArgsConstructor
    private static final class DirectoryEncoding {
        @JsonProperty("p")
        private String path;
        @JsonProperty("permissions")
        private String permissions;
        @JsonProperty("files")
        private NavigableSet<String> files;
        @JsonProperty("deleted")
        private Long deleted;

        public DirectoryEncoding(BackupDirectory directory) {
            this.files = directory.getFiles();
            this.permissions = directory.getPermissions();
            this.deleted = directory.getDeleted();
        }
    }

    @AllArgsConstructor
    @Getter
    private static class PathTimestamp {
        private String path;
        private Long timestamp;
    }

    private static class TemporaryLmdbMap<K, V> implements CloseableSortedMap<K, V> {
        private final MapSerializer<K, V> serializer;
        private final Env<ByteBuffer> db;
        private final File root;
        private Dbi<ByteBuffer> map;
        private int writeCount;
        private Txn<ByteBuffer> txn;
        private long mapSize;

        public TemporaryLmdbMap(LmdbMetadataRepositoryStorage storage, MapSerializer<K, V> serializer) throws IOException {
            this.serializer = serializer;

            root = Files.createTempDirectory("underscorebackup").toFile();
            db = createDb(storage);
            map = db.openDbi((String) null, MDB_CREATE);
        }

        private Env<ByteBuffer> createDb(LmdbMetadataRepositoryStorage storage) {
            mapSize = storage.openMapSize(root);
            return storage.createDb(root, mapSize, false);
        }

        @Override
        public void close() {
            if (txn != null) {
                txn.close();
            }
            map.close();
            db.close();

            deleteContents(root);
            IOUtils.deleteFile(root);
        }

        @Override
        public synchronized void put(K k, V v) {
            ByteBuffer kb = encodeKey(k);
            ByteBuffer vb = encodeValue(v);

            increaseWrite();
            map.put(getMapTransaction(), kb, vb);
        }

        private void increaseWrite() {
            writeCount++;
            if (writeCount >= 1000) {
                commitAndGrowIfNeeded();
                writeCount = 0;
            }
        }

        protected void commit() {
            txn.commit();
            txn.close();
            txn = null;
        }

        protected void commitAndGrowIfNeeded() {
            Stat stat = db.stat();
            long usedSize = calculateStatSize(stat);
            stat = map.stat(txn);
            usedSize += calculateStatSize(stat);

            commit();

            if (usedSize * ASSUMED_OVERHEAD > mapSize - MINIMUM_FREE_SPACE) {
                map.close();
                mapSize += INCREASE_SPACE;
                debug(() -> log.debug("Increasing temporary map size to {}", readableSize(mapSize)));
                db.setMapSize(mapSize);
                map = db.openDbi((String) null, MDB_CREATE);
            }
        }

        private ByteBuffer encodeValue(V v) {
            byte[] va = serializer.encodeValue(v);
            ByteBuffer vb = ByteBuffer.allocateDirect(va.length);
            vb.put(va);
            vb.flip();
            return vb;
        }

        private ByteBuffer encodeKey(K k) {
            byte[] ka = serializer.encodeKey(k);
            ByteBuffer kb = ByteBuffer.allocateDirect(ka.length);
            kb.put(ka);
            kb.flip();
            return kb;
        }

        private Txn<ByteBuffer> getMapTransaction() {
            if (txn == null) {
                txn = db.txnWrite();
            }
            return txn;
        }

        @Override
        public synchronized boolean delete(K k) {
            increaseWrite();
            return map.delete(getMapTransaction(), encodeKey(k));
        }

        @Override
        public synchronized V get(K k) {
            return decodeValue(map.get(getMapTransaction(), encodeKey(k)));
        }

        private V decodeValue(ByteBuffer byteBuffer) {
            if (byteBuffer == null) {
                return null;
            }

            byte[] bytes = new byte[byteBuffer.limit()];
            byteBuffer.get(bytes);
            return serializer.decodeValue(bytes);
        }

        private K decodeKey(ByteBuffer byteBuffer) {
            if (byteBuffer == null) {
                return null;
            }

            byte[] bytes = new byte[byteBuffer.limit()];
            byteBuffer.get(bytes);
            return serializer.decodeKey(bytes);
        }

        @Override
        public Stream<Map.Entry<K, V>> readOnlyEntryStream(boolean ascending) {
            Iterator<Map.Entry<K, V>> iterator = new TemporaryIterator(ascending);
            return StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(
                            iterator,
                            Spliterator.ORDERED),
                    false);
        }

        private class TemporaryIterator implements Iterator<Map.Entry<K, V>> {
            private final Iterator<CursorIterable.KeyVal<ByteBuffer>> iterator;
            private final CursorIterable<ByteBuffer> iterable;

            public TemporaryIterator(boolean ascending) {
                iterable = map.iterate(txn, ascending ? KeyRange.all() : KeyRange.allBackward());
                iterator = iterable.iterator();
            }

            @Override
            public boolean hasNext() {
                boolean ret = iterator.hasNext();
                if (!ret) {
                    iterable.close();
                }
                return ret;
            }

            @Override
            public Map.Entry<K, V> next() {
                CursorIterable.KeyVal<ByteBuffer> entry = iterator.next();
                return Map.entry(decodeKey(entry.key()), decodeValue(entry.val()));
            }
        }
    }

    private static class NonMemoryMappedTemporaryMap<K, V> extends TemporaryLmdbMap<K, V> {
        public NonMemoryMappedTemporaryMap(LmdbMetadataRepositoryStorage storage, MapSerializer<K, V> serializer) throws IOException {
            super(storage, serializer);
        }

        @Override
        protected void commitAndGrowIfNeeded() {
            super.commit();
        }
    }

    public static class NonMemoryMapped extends LmdbMetadataRepositoryStorage {
        private static final long MAP_SIZE = 512L * 1024 * 1024 * 1024;
        private static final String STORAGE_ROOT = "v3";

        public NonMemoryMapped(String dataPath, int revision, boolean alternateBlockTable) {
            super(Paths.get(dataPath, revision == 0 ? STORAGE_ROOT : String.format("%s-%d", STORAGE_ROOT, revision))
                    .toFile(), alternateBlockTable);
        }

        @Override
        protected void checkExpand() {
            super.commitIfNeeded();
        }

        @Override
        protected long openMapSize(File root) {
            return MAP_SIZE;
        }

        @Override
        protected EnvFlags[] createDbFlags(boolean readOnly) {
            if (readOnly)
                return new EnvFlags[]{MDB_RDONLY_ENV};
            else
                return new EnvFlags[]{};
        }

        @Override
        public <K, V> CloseableMap<K, V> temporaryMap(MapSerializer<K, V> serializer) throws IOException {
            return new NonMemoryMappedTemporaryMap<K, V>(this, serializer);
        }
    }

    @RequiredArgsConstructor
    private class TransactionIterator<T> implements Iterator<T>, Closeable {
        private final Dbi<ByteBuffer> map;
        private final Function<CursorIterable.KeyVal<ByteBuffer>, T> decoder;
        private final boolean ascending;
        private byte[] lastValue;
        private CursorIterable<ByteBuffer> iterable;
        private Iterator<CursorIterable.KeyVal<ByteBuffer>> iterator;

        @Override
        public boolean hasNext() {
            try {
                return synchronizeDbAccess(() -> {
                    if (iterable == null) {
                        Txn<ByteBuffer> txn = getReadTransaction();
                        if (lastValue == null) {
                            iterable = map.iterate(txn, ascending ? KeyRange.all() : KeyRange.allBackward());
                        } else {
                            ByteBuffer buffer = ByteBuffer.allocateDirect(lastValue.length);
                            buffer.put(lastValue);
                            buffer.flip();
                            iterable = map.iterate(txn, ascending ? KeyRange.greaterThan(buffer)
                                    : KeyRange.greaterThanBackward(buffer));
                        }
                        preCommitActions.add(() -> {
                            iterable.close();
                            iterable = null;
                        });
                        iterator = iterable.iterator();
                    }

                    return iterator.hasNext();
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public T next() {
            try {
                return synchronizeDbAccess(() -> {
                    CursorIterable.KeyVal<ByteBuffer> val = iterator.next();
                    lastValue = new byte[val.key().limit()];
                    val.key().get(lastValue);
                    val.key().rewind();
                    return decoder.apply(val);
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() {
            try {
                synchronizeDbAccess(() -> {
                    if (iterable != null) {
                        iterable.close();
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
