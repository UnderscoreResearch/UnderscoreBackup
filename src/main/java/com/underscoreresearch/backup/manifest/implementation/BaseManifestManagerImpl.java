package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;
import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_WRITER;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
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
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.ParseException;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.manifest.BaseManifestManager;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.utils.AccessLock;

@Slf4j
public abstract class BaseManifestManagerImpl implements BaseManifestManager {
    public static final String LOG_ROOT = "logs";
    public static final String IDENTITY_MANIFEST_LOCATION = "identity";
    private final static DateTimeFormatter LOG_FILE_FORMATTER
            = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss.nnnnnnnnn").withZone(ZoneId.of("UTC"));
    @Getter(AccessLevel.PROTECTED)
    private final BackupConfiguration configuration;
    @Getter(AccessLevel.PROTECTED)
    private final RateLimitController rateLimitController;
    @Getter(AccessLevel.PROTECTED)
    private final String manifestLocation;
    private final BackupDestination manifestDestination;
    @Getter(AccessLevel.PROTECTED)
    private final IOProvider provider;
    @Getter(AccessLevel.PROTECTED)
    private final Encryptor encryptor;
    @Getter(AccessLevel.PROTECTED)
    private final String installationIdentity;
    @Getter(AccessLevel.PROTECTED)
    private final boolean forceIdentity;
    @Getter(AccessLevel.PROTECTED)
    private final ServiceManager serviceManager;
    protected Closeable operationTask;
    @Getter
    private boolean shutdown;

    @Getter
    @Setter
    private boolean disabledFlushing;

    private AccessLock currentLogLock;
    private String lastLogFilename;
    private long currentLogLength;
    @Getter(AccessLevel.PROTECTED)
    private Object lock = new Object();
    private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder().setNameFormat(getClass().getSimpleName() + "-%d").build());
    @Getter(AccessLevel.PROTECTED)
    private LogConsumer logConsumer;
    private AtomicBoolean currentlyClosingLog = new AtomicBoolean();
    @Getter(AccessLevel.PROTECTED)
    private boolean initialized;
    @Getter(AccessLevel.PROTECTED)
    private EncryptionKey publicKey;

    public BaseManifestManagerImpl(BackupConfiguration configuration,
                                   BackupDestination manifestDestination,
                                   String manifestLocation,
                                   IOProvider provider,
                                   Encryptor encryptor,
                                   RateLimitController rateLimitController,
                                   ServiceManager serviceManager,
                                   String installationIdentity,
                                   boolean forceIdentity,
                                   EncryptionKey publicKey) {
        this.configuration = configuration;
        this.rateLimitController = rateLimitController;
        this.manifestLocation = manifestLocation;
        this.installationIdentity = installationIdentity;
        this.forceIdentity = forceIdentity;
        this.publicKey = publicKey;
        this.serviceManager = serviceManager;

        this.manifestDestination = manifestDestination;
        if (manifestDestination == null) {
            throw new IllegalArgumentException("Can't find destination for manifest");
        }

        this.provider = provider;
        if (!(provider instanceof IOIndex)) {
            throw new IllegalArgumentException("Manifest destination must be able to support listing files");
        }
        this.encryptor = encryptor;
    }

    protected static List<String> getListOfLogFiles(LogConsumer consumer, IOIndex index, String parent, boolean partial)
            throws IOException {
        final String parentPrefix;
        if (!parent.endsWith(PATH_SEPARATOR)) {
            parentPrefix = parent + PATH_SEPARATOR;
        } else {
            parentPrefix = parent;
        }
        List<String> files = index.availableKeys(parent).stream().map(file -> parentPrefix + file)
                .sorted().collect(Collectors.toList());

        String lastSyncedFile = consumer.lastSyncedLogFile();

        if (lastSyncedFile != null) {
            files = files.stream()
                    .filter(file -> file.compareTo(lastSyncedFile.length() > file.length() ?
                            lastSyncedFile.substring(0, file.length()) :
                            lastSyncedFile) >= (partial ? 0 : 1))
                    .collect(Collectors.toList());
        }
        return files;
    }

    protected void uploadKeyData(EncryptionKey key) throws IOException {
        EncryptionKey publicKey = key.publicOnly();

        uploadConfigData("publickey.json",
                new ByteArrayInputStream(ENCRYPTION_KEY_WRITER.writeValueAsBytes(publicKey)),
                false);
    }

    protected abstract void uploadPending(LogConsumer logConsumer) throws IOException;

    protected void uploadLogFile(String file, InputStream stream) throws IOException {
        String uploadFilename = transformLogFilename(file);
        uploadConfigData(uploadFilename, stream, true);
        if (logConsumer != null) {
            if (logConsumer.lastSyncedLogFile() != null && logConsumer.lastSyncedLogFile().compareTo(uploadFilename) > 0) {
                log.warn("Uploaded log file {} out of order, already uploaded {}", uploadFilename,
                        logConsumer.lastSyncedLogFile());
            } else {
                logConsumer.setLastSyncedLogFile(uploadFilename);
            }
        }
    }

    protected List<File> existingLogFiles() throws IOException {
        File parent = Paths.get(manifestLocation, "logs").toFile();
        if (parent.isDirectory()) {
            return Arrays.stream(parent.list()).map(file -> new File(parent, file)).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    public void initialize(LogConsumer logConsumer, boolean immediate) {
        synchronized (lock) {
            if (logConsumer != null) {
                this.logConsumer = logConsumer;
            }

            if (immediate) {
                internalInitialize();
            }
        }
    }

    public void validateIdentity() {
        byte[] data;
        try {
            debug(() -> log.debug("Validating manifest installation identity"));
            data = provider.download(IDENTITY_MANIFEST_LOCATION);
            rateLimitController.acquireDownloadPermits(manifestDestination, data.length);
        } catch (Exception exc) {
            storeIdentity();
            return;
        }
        String destinationIdentity = new String(data, StandardCharsets.UTF_8);
        if (!destinationIdentity.equals(installationIdentity)) {
            if (forceIdentity) {
                log.error("Another installation of UnderscoreBackup is already writing to this manifest "
                        + "destination. Proceeding anyway because of --force flag on command line. Consider doing "
                        + "a log optimize operation to avoid data corruption");
                storeIdentity();
            } else {
                throw new RuntimeException(
                        new ParseException("Another installation of UnderscoreBackup is already writing to this manifest "
                                + "destination. To take over backing up from this installation execute a "
                                + "rebuild-repository operation or reset the local configuration under settings in the UI"));
            }
        }
    }

    public void storeIdentity() {
        log.info("Updating manifest installation identity");
        try {
            byte[] data = installationIdentity.getBytes(StandardCharsets.UTF_8);
            provider.upload(IDENTITY_MANIFEST_LOCATION, data);
            rateLimitController.acquireUploadPermits(manifestDestination, data.length);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to save identity to target: %s", e.getMessage()), e);
        }
    }

    protected void internalInitialize() {
        synchronized (lock) {
            if (!initialized) {
                validateIdentity();

                try {
                    uploadPending(logConsumer);

                    additionalInitialization();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to initialize metadata system for writing", e);
                } finally {
                    initialized = true;
                }
            }
        }
    }

    protected void additionalInitialization() {
    }

    @Override
    public List<String> getExistingLogs() throws IOException {
        IOIndex index = (IOIndex) getProvider();
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
        return existingLogs;
    }

    protected void uploadConfigData(String filename, InputStream inputStream, boolean encrypt) throws IOException {
        byte[] data;
        if (encrypt) {
            validateIdentity();

            data = encryptConfigData(inputStream);
        } else {
            data = IOUtils.readAllBytes(inputStream);
        }
        log.info("Uploading {} ({})", filename, readableSize(data.length));
        rateLimitController.acquireUploadPermits(manifestDestination, data.length);
        provider.upload(filename, data);
    }

    public byte[] encryptConfigData(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipStream = new GZIPOutputStream(outputStream)) {
            IOUtils.copyStream(inputStream, gzipStream);
        }
        return encryptor.encryptBlock(null, outputStream.toByteArray(), publicKey);
    }

    private String transformLogFilename(String path) {
        String filename = Paths.get(path).getFileName().toString();
        return "logs" + PATH_SEPARATOR + filename.replaceAll("(\\d{4}-\\d{2}-\\d{2})-", "$1" + PATH_SEPARATOR) + ".gz";
    }

    public void addLogEntry(String type, String jsonDefinition) {
        boolean flush = false;

        synchronized (lock) {
            internalInitialize();

            try {
                if (currentLogLock == null) {
                    createNewLogFile();
                }
                byte[] data = (type + ":" + jsonDefinition + "\n").getBytes(StandardCharsets.UTF_8);
                currentLogLock.getLockedChannel().write(ByteBuffer.wrap(data));
                if (!disabledFlushing) {
                    currentLogLock.getLockedChannel().force(false);
                }
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

    protected void flushLogging() throws IOException {
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
        String filename;
        filename = createLogFilename();

        new File(filename).getParentFile().mkdirs();
        currentLogLock = new AccessLock(filename);
        lastLogFilename = filename;
        currentLogLock.lock(true);

        File dir = new File(filename).getParentFile();
        if (!dir.isDirectory())
            dir.mkdirs();

        if (configuration.getManifest().getMaximumUnsyncedSeconds() != null) {
            String logName = filename;
            executor.schedule(() -> {
                        synchronized (lock) {
                            if (currentLogLock != null && currentLogLength > 0 && logName.equals(currentLogLock.getFilename())) {
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

    private String createLogFilename() {
        String filename;
        filename = Paths.get(manifestLocation, LOG_ROOT, LOG_FILE_FORMATTER.format(Instant.now())).toString();
        while (filename.equals(lastLogFilename)) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
            }
            debug(() -> log.warn("Had to wait a bit to get a unique filename for log"));
            filename = Paths.get(manifestLocation, LOG_ROOT, LOG_FILE_FORMATTER.format(Instant.now())).toString();
        }
        return filename;
    }

    private void closeLogFile() throws IOException {
        if (currentLogLock != null) {
            currentLogLock.getLockedChannel().position(0);
            String filename = currentLogLock.getFilename();
            boolean uploaded = false;
            try (InputStream stream = Channels.newInputStream(currentLogLock.getLockedChannel())) {
                uploadLogFile(filename, stream);
                uploaded = true;
            } finally {
                try {
                    currentLogLock.close();
                } catch (IOException exc) {
                    log.error("Failed to close log file lock {}", filename, exc);
                }
                if (uploaded) {
                    if (!(new File(filename).delete())) {
                        log.error("Failed to delete log file {}", filename);
                    }
                }
                currentLogLength = 0;
                currentLogLock = null;
            }
        }
    }

    @Override
    public void deleteLogFiles(List<String> existingLogs) throws IOException {
        for (String oldFile : existingLogs) {
            getProvider().delete(oldFile);
            debug(() -> log.debug("Deleted {}", oldFile));
        }
    }

    public void flushLog() throws IOException {
        synchronized (lock) {
            closeLogFile();
        }
    }

    public void shutdown() throws IOException {
        synchronized (lock) {
            executor.shutdownNow();
            closeLogFile();
            shutdown = true;
        }
    }
}
