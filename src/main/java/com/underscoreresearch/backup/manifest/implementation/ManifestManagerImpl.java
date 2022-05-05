package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.configuration.CommandLineModule.CONFIG_DATA;
import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.underscoreresearch.backup.utils.NonClosingInputStream;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.encryption.PublicKeyEncrypion;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.implementation.NullRepository;
import com.underscoreresearch.backup.file.implementation.ScannerSchedulerImpl;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.LoggingMetadataRepository;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.utils.AccessLock;
import com.underscoreresearch.backup.utils.StatusLine;
import com.underscoreresearch.backup.utils.StatusLogger;

@Slf4j
public class ManifestManagerImpl implements ManifestManager, StatusLogger {
    private final static DateTimeFormatter LOG_FILE_FORMATTER
            = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss.n").withZone(ZoneId.of("UTC"));
    private static final String LOG_ROOT = "logs";

    private final BackupConfiguration configuration;
    private final RateLimitController rateLimitController;
    private final String localRoot;
    private final BackupDestination manifestDestination;
    private final IOProvider provider;
    private final Encryptor encryptor;

    private AccessLock currentLogLock;
    private long currentLogLength;
    private Object lock = new Object();
    private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder().setNameFormat(getClass().getSimpleName() + "-%d").build());
    private LogConsumer initializeLogConsumer;
    private AtomicBoolean currentlyClosingLog = new AtomicBoolean();

    public ManifestManagerImpl(BackupConfiguration configuration,
                               IOProvider provider,
                               Encryptor encryptor,
                               RateLimitController rateLimitController)
            throws IOException {
        this.configuration = configuration;
        this.rateLimitController = rateLimitController;
        this.localRoot = configuration.getManifest().getLocalLocation();

        manifestDestination = configuration.getDestinations().get(configuration.getManifest().getDestination());
        if (manifestDestination == null) {
            throw new IllegalArgumentException("Can't find destination for manifest");
        }

        this.provider = provider;
        if (!(provider instanceof IOIndex)) {
            throw new IllegalArgumentException("Manifest destination must be able to support listing files");
        }
        this.encryptor = encryptor;
    }

    private void uploadPending(LogConsumer logConsumer) throws IOException {
        PublicKeyEncrypion publicKeyEncrypion = InstanceFactory.getInstance(PublicKeyEncrypion.class);
        startOperation("Upload pending");
        try {
            processedFiles = new AtomicLong(0);
            totalFiles = new AtomicLong(2);
            try {
                PublicKeyEncrypion existingPublicKey = new ObjectMapper().readValue(provider.download("publickey.json"),
                        PublicKeyEncrypion.class);
                if (!publicKeyEncrypion.getSalt().equals(existingPublicKey.getSalt())
                        || !publicKeyEncrypion.getPublicKey().equals(existingPublicKey.getPublicKey())) {
                    throw new IOException("Public key that exist in destination does not match current public key");
                }
            } catch (Exception exc) {
                if (!IOUtils.hasInternet()) {
                    throw exc;
                }
                log.info("Public key does not exist");
                uploadConfigData("publickey.json",
                        new ByteArrayInputStream(new ObjectMapper().writeValueAsBytes(publicKeyEncrypion)),
                        false);
            } finally {
                processedFiles.incrementAndGet();
            }

            uploadConfigData("configuration.json",
                    new ByteArrayInputStream(InstanceFactory.getInstance(CONFIG_DATA).getBytes(Charset.forName("UTF-8"))),
                    true);
            processedFiles.incrementAndGet();

            List<File> files = existingLogFiles();

            if (files.size() > 0) {
                totalFiles.addAndGet(files.size());

                for (File file : files) {
                    try (AccessLock lock = new AccessLock(file.getAbsolutePath())) {
                        if (lock.tryLock(true)) {
                            InputStream stream = new NonClosingInputStream(Channels
                                    .newInputStream(lock.getLockedChannel()));
                            processLogInputStream(logConsumer, stream);

                            lock.getLockedChannel().position(0);

                            uploadConfigData(transformLogFilename(file.getAbsolutePath()), stream, true);
                        } else {
                            log.warn("Log file {} locked by other process", file.getAbsolutePath());
                        }
                    }

                    file.delete();
                    processedFiles.incrementAndGet();
                }
            }
        } finally {
            resetStatus();
        }
    }

    private List<File> existingLogFiles() throws IOException {
        File parent = Paths.get(configuration.getManifest().getLocalLocation(), "logs").toFile();
        if (parent.isDirectory()) {
            return Arrays.stream(parent.list()).map(file -> new File(parent, file)).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    @Override
    public void initialize(LogConsumer logConsumer) {
        synchronized (lock) {
            initializeLogConsumer = logConsumer;
        }
    }

    private void internalInitialize() {
        synchronized (lock) {
            if (initializeLogConsumer != null) {
                try {
                    uploadPending(initializeLogConsumer);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to initialize metadata system for writing", e);
                } finally {
                    initializeLogConsumer = null;
                }
            }
        }
    }

    private void uploadConfigData(String filename, InputStream inputStream, boolean encrypt) throws IOException {
        byte[] data;
        if (encrypt) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipStream = new GZIPOutputStream(outputStream)) {
                IOUtils.copyStream(inputStream, gzipStream);
            }
            data = encryptor.encryptBlock(outputStream.toByteArray());
        } else {
            data = IOUtils.readAllBytes(inputStream);
        }
        log.info("Uploading {} ({})", filename, readableSize(data.length));
        rateLimitController.acquireUploadPermits(manifestDestination, data.length);
        provider.upload(filename, data);
    }

    private String transformLogFilename(String path) {
        String filename = Paths.get(path).getFileName().toString();
        return "logs" + PATH_SEPARATOR + filename.replaceAll("(\\d{4}-\\d{2}-\\d{2})-", "$1" + PATH_SEPARATOR) + ".gz";
    }

    @Override
    public void addLogEntry(String type, String jsonDefinition) {
        boolean flush = false;

        synchronized (lock) {
            internalInitialize();

            try {
                if (currentLogLock == null) {
                    createNewLogFile();
                }
                byte[] data = (type + ":" + jsonDefinition + "\n").getBytes(Charset.forName("UTF-8"));
                currentLogLock.getLockedChannel().write(ByteBuffer.wrap(data));
                currentLogLock.getLockedChannel().force(false);
                currentLogLength += data.length;

                if (!currentlyClosingLog.get()
                        && currentLogLength > configuration.getManifest().getMaximumUnsyncedSize()) {
                    flush = true;
                }
            } catch (IOException exc) {
                log.error("Failed to save log entry: " + type + ": " + jsonDefinition, exc);

                try {
                    flushLogging();
                } catch (IOException exc2) {
                    log.error("Start new log file", exc2);
                    try {
                        currentLogLock.close();
                    } catch (IOException e) {
                        log.error("Failed to close lock", e);
                    }
                    currentLogLock = null;
                }
            }
        }

        if (flush) {
            try {
                flushLogging();
            } catch (IOException exc) {
                log.error("Failed to flush log");
            }
        }
    }

    private void flushLogging() throws IOException {
        boolean performFlush = false;
        synchronized (lock) {
            if (!currentlyClosingLog.get()) {
                currentlyClosingLog.set(true);
                performFlush = true;
            }
        }
        if (performFlush) {
            try {
                InstanceFactory.getInstance(MetadataRepository.class).flushLogging();
            } catch (Exception exc) {
                log.error("Failed to flush repository before starting new log file", exc);
            }
            synchronized (lock) {
                try {
                    closeLogFile();
                } catch (Exception exc) {
                    log.error("Failed to close log file", exc);
                } finally {
                    currentlyClosingLog.set(false);
                }
            }
        }
    }

    private void createNewLogFile() throws IOException {
        closeLogFile();
        currentLogLength = 0;
        String filename = Paths.get(localRoot, LOG_ROOT, LOG_FILE_FORMATTER.format(Instant.now())).toString();
        new File(filename).getParentFile().mkdirs();
        currentLogLock = new AccessLock(filename);
        currentLogLock.lock(true);

        File dir = new File(filename).getParentFile();
        if (!dir.isDirectory())
            dir.mkdirs();

        if (configuration.getManifest().getMaximumUnsyncedSeconds() != null) {
            String logName = filename;
            executor.schedule(() -> {
                        synchronized (lock) {
                            if (currentLogLock != null && logName.equals(currentLogLock.getFilename())) {
                                try {
                                    createNewLogFile();
                                } catch (IOException exc) {
                                    log.error("Failed to create new log file", exc);
                                }
                            }
                        }
                    },
                    configuration.getManifest().getMaximumUnsyncedSeconds(),
                    TimeUnit.SECONDS);
        }
    }

    private void closeLogFile() throws IOException {
        if (currentLogLock != null) {
            currentLogLock.getLockedChannel().position(0);
            String filename = currentLogLock.getFilename();
            try (InputStream stream = Channels.newInputStream(currentLogLock.getLockedChannel())) {
                uploadConfigData(transformLogFilename(filename), stream, true);
            }
            currentLogLock.close();
            currentLogLength = 0;
            currentLogLock = null;
            new File(filename).delete();
        }
    }

    public void flushLog() throws IOException {
        synchronized (lock) {
            closeLogFile();
        }
    }

    @Override
    public void replayLog(LogConsumer consumer) throws IOException {
        IOIndex index = (IOIndex) provider;
        List<String> days = index.availableKeys(LOG_ROOT);
        startOperation("Replay");
        try {
            totalFiles = new AtomicLong(days.size());
            processedFiles = new AtomicLong();
            processedOperations = new AtomicLong();

            for (String day : days) {
                List<String> files = index.availableKeys(LOG_ROOT + PATH_SEPARATOR + day);
                totalFiles.addAndGet(files.size());

                for (String file : files) {
                    String path = LOG_ROOT + PATH_SEPARATOR + day;
                    if (!path.endsWith(PATH_SEPARATOR)) {
                        path += PATH_SEPARATOR;
                    }
                    path += file;
                    byte[] data = provider.download(path);

                    try {
                        log.info("Processing log file {}", path);
                        byte[] unencryptedData = encryptor.decodeBlock(data);
                        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(unencryptedData)) {
                            try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                                processLogInputStream(consumer, gzipInputStream);
                            }
                        }
                    } catch (Exception exc) {
                        log.error("Failed to read log file " + file, exc);
                    }
                    processedFiles.incrementAndGet();
                }
                processedFiles.incrementAndGet();
            }
        } finally {
            log.info("Completed reprocessing logs");
            resetStatus();
        }
    }

    private void processLogInputStream(LogConsumer consumer, InputStream inputStream) throws IOException {
        try (InputStreamReader inputStreamReader
                     = new InputStreamReader(inputStream)) {
            try (BufferedReader reader = new BufferedReader(inputStreamReader)) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    int ind = line.indexOf(':');
                    try {
                        consumer.replayLogEntry(line.substring(0, ind),
                                line.substring(ind + 1));
                    } catch (Exception exc) {
                        log.error("Failed processing log line: " + line, exc);
                    }
                }
            }
        }
    }

    @Override
    public void optimizeLog(MetadataRepository existingRepository, LogConsumer logConsumer) throws IOException {
        initialize(logConsumer);
        internalInitialize();
        flushLogging();

        if (existingLogFiles().size() > 0) {
            log.warn("Still having pending log files, can't optimize");
            return;
        }

        IOIndex index = (IOIndex) provider;
        List<String> existingLogs = new ArrayList<>();
        for (String day : index.availableKeys(LOG_ROOT)) {
            for (String file : index.availableKeys(LOG_ROOT + PATH_SEPARATOR + day)) {
                String path = LOG_ROOT + PATH_SEPARATOR + day;
                if (!path.endsWith(PATH_SEPARATOR)) {
                    path += PATH_SEPARATOR;
                }
                existingLogs.add(path + file);
            }
        }

        try (CloseableLock ignored = existingRepository.acquireLock()) {
            LoggingMetadataRepository copyRepository = new LoggingMetadataRepository(new NullRepository(), this);

            internalInitialize();

            startOperation("Optimizing log");
            processedOperations = new AtomicLong();

            log.info("Processing files");
            existingRepository.allFiles().forEach((file) -> {
                processedOperations.incrementAndGet();
                try {
                    copyRepository.addFile(file);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            log.info("Processing blocks");
            existingRepository.allBlocks().forEach((block) -> {
                processedOperations.incrementAndGet();
                try {
                    copyRepository.addBlock(block);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            log.info("Processing directory contents");
            existingRepository.allDirectories().forEach((dir) -> {
                processedOperations.incrementAndGet();
                try {
                    copyRepository.addDirectory(dir);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            log.info("Processing active paths");
            existingRepository.getActivePaths(null).forEach((path, dir) -> {
                processedOperations.incrementAndGet();
                for (String setId : dir.getSetIds()) {
                    try {
                        copyRepository.pushActivePath(setId, path, dir);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });

            log.info("Processing pending sets");
            existingRepository.getPendingSets().forEach((pendingSet) -> {
                processedOperations.incrementAndGet();
                try {
                    if (pendingSet.getSetId() != "") {
                        copyRepository.addPendingSets(pendingSet);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            ScannerSchedulerImpl.updateOptimizeSchedule(copyRepository,
                    configuration.getManifest().getOptimizeSchedule());

            copyRepository.close();

            log.info("Deleting old log files");
            totalFiles = new AtomicLong(existingLogs.size());
            processedFiles = new AtomicLong();
            for (String oldFile : existingLogs) {
                provider.delete(oldFile);
                debug(() -> log.debug("Deleted {}", oldFile));
                processedFiles.incrementAndGet();
            }
        } finally {
            resetStatus();
        }
    }

    @Override
    public BackupContentsAccess backupContents(Long timestamp) throws IOException {
        return new BackupContentsAccessImpl(InstanceFactory.getInstance(MetadataRepository.class),
                timestamp);
    }

    @Override
    public void shutdown() throws IOException {
        synchronized (lock) {
            executor.shutdownNow();
            closeLogFile();
        }
    }

    private String operation;
    private AtomicLong totalFiles;
    private AtomicLong processedFiles;
    private AtomicLong processedOperations;
    private Stopwatch operationDuration;

    private void startOperation(String operation) {
        this.operation = operation;
        operationDuration = Stopwatch.createStarted();
    }

    @Override
    public void resetStatus() {
        operation = null;
        totalFiles = null;
        processedFiles = null;
        processedOperations = null;
        operationDuration = null;
    }

    @Override
    public boolean temporal() {
        return false;
    }

    @Override
    public List<StatusLine> status() {
        List<StatusLine> ret = new ArrayList<>();
        if (operation != null) {
            String code = operation.toUpperCase().replace(" ", "_");
            if (processedFiles != null) {
                ret.add(new StatusLine(getClass(), code + "_PROCESSED_FILES", operation + " processed files", processedFiles.get()));
            }
            if (totalFiles != null) {
                ret.add(new StatusLine(getClass(), code + "_TOTAL_FILES", operation + " total files", totalFiles.get()));
            }
            if (processedOperations != null) {
                ret.add(new StatusLine(getClass(), code + "_PROCESSED_OPERATIONS", operation + " processed operations", processedOperations.get()));
            }

            if (processedOperations != null && operationDuration != null) {
                int elapsedMilliseconds = (int) operationDuration.elapsed(TimeUnit.MILLISECONDS);
                if (elapsedMilliseconds > 0) {
                    long throughput = 1000 * processedOperations.get() / elapsedMilliseconds;
                    ret.add(new StatusLine(getClass(), code + "_THROUGHPUT", operation + " throughput",
                            throughput, throughput + " operations/s"));
                }
            }
        }
        return ret;
    }
}
