package com.underscoreresearch.backup.manifest.implementation;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.IOProviderUtil;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.io.implementation.SchedulerImpl;
import com.underscoreresearch.backup.manifest.BaseManifestManager;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.service.SubscriptionLackingException;
import com.underscoreresearch.backup.utils.AccessLock;
import com.underscoreresearch.backup.utils.SingleTaskScheduler;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.ParseException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.io.IOUtils.createDirectory;
import static com.underscoreresearch.backup.io.IOUtils.deleteFile;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

@Slf4j
public abstract class BaseManifestManagerImpl implements BaseManifestManager {
    public static final String LOG_ROOT = "logs";
    public static final String IDENTITY_MANIFEST_LOCATION = "identity";
    public static final String PUBLICKEY_FILENAME = "publickey.json";
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
    private final Encryptor encryptor;
    @Getter(AccessLevel.PROTECTED)
    private final String installationIdentity;
    @Getter(AccessLevel.PROTECTED)
    private final boolean forceIdentity;
    @Getter(AccessLevel.PROTECTED)
    private final ServiceManager serviceManager;
    @Getter(AccessLevel.PROTECTED)
    private final Object lock = new Object();
    private final ExecutorService uploadExecutor;
    private final AtomicInteger uploadCount = new AtomicInteger();
    private final AtomicInteger uploadSubmissionCount = new AtomicInteger();
    private final AtomicBoolean currentlyClosingLog = new AtomicBoolean();
    private final SingleTaskScheduler executor = new SingleTaskScheduler(getClass().getSimpleName());
    @Getter(AccessLevel.PROTECTED)
    private final EncryptionIdentity encryptionIdentity;
    @Getter(AccessLevel.PROTECTED)
    private final UploadScheduler uploadScheduler;
    @Getter(AccessLevel.PUBLIC)
    private final IOProvider ioProvider;
    @Getter(AccessLevel.PROTECTED)
    private final IdentityKeys identityKeys;
    @Getter
    private boolean shutdown;
    @Getter
    @Setter
    private boolean disabledFlushing;
    private AccessLock currentLogLock;
    private String lastLogFilename;
    private long currentLogLength;
    @Getter(AccessLevel.PROTECTED)
    private LogConsumer logConsumer;
    @Getter(AccessLevel.PROTECTED)
    private boolean initialized;
    private LogFileType logFileType = LogFileType.DEFAULT;

    public BaseManifestManagerImpl(BackupConfiguration configuration,
                                   BackupDestination manifestDestination,
                                   String manifestLocation,
                                   RateLimitController rateLimitController,
                                   ServiceManager serviceManager,
                                   String installationIdentity,
                                   boolean forceIdentity,
                                   EncryptionIdentity encryptionIdentity,
                                   IdentityKeys identityKeys,
                                   UploadScheduler uploadScheduler) {
        this.configuration = configuration;
        this.rateLimitController = rateLimitController;
        this.manifestLocation = manifestLocation;
        this.installationIdentity = installationIdentity;
        this.forceIdentity = forceIdentity;
        this.encryptionIdentity = encryptionIdentity;
        this.serviceManager = serviceManager;
        this.identityKeys = identityKeys;

        this.manifestDestination = manifestDestination;
        if (manifestDestination == null) {
            throw new IllegalArgumentException("Can't find destination for manifest");
        }

        this.encryptor = EncryptorFactory.getEncryptor(manifestDestination.getEncryption());
        this.ioProvider = IOProviderFactory.getProvider(manifestDestination);
        if (!(this.ioProvider instanceof IOIndex)) {
            throw new IllegalArgumentException("Manifest destination must be an index");
        }
        this.uploadScheduler = uploadScheduler;

        uploadExecutor = Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder().setNameFormat("Manifest-Upload").build());
    }

    public static byte[] compressConfigData(byte[] data) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipStream = new GZIPOutputStream(outputStream)) {
            gzipStream.write(data);
        }
        return outputStream.toByteArray();
    }

    public static void deleteLogFiles(String lastLogFile, IOIndex provider,
                                      AtomicLong totalFiles, AtomicLong processedFiles) throws IOException {
        if (lastLogFile != null) {
            DeletionScheduler scheduler = new DeletionScheduler(10);
            try {
                while (true) {
                    List<String> fetchedFiles = provider.availableLogs(null, false);
                    List<String> files = fetchedFiles.stream().filter((file) -> file.compareTo(lastLogFile) <= 0)
                            .toList();
                    totalFiles.addAndGet(files.size());
                    for (String file : files) {
                        if (file.compareTo(lastLogFile) <= 0) {
                            scheduler.delete(provider, file, processedFiles);
                        }
                    }
                    if (fetchedFiles.size() != files.size() || files.isEmpty()) {
                        break;
                    }
                }
            } finally {
                scheduler.waitForCompletion();
                scheduler.shutdown();
            }
        }
    }

    public static void deleteNewLogFiles(String lastLogFile, IOIndex provider,
                                         AtomicLong totalFiles, AtomicLong processedFiles) throws IOException {
        List<String> files = provider.availableLogs(lastLogFile, true);
        if (!files.isEmpty()) {
            DeletionScheduler scheduler = new DeletionScheduler(10);
            try {
                totalFiles.addAndGet(files.size());
                for (String file : files) {
                    scheduler.delete(provider, file, processedFiles);
                }
            } finally {
                scheduler.waitForCompletion();
                scheduler.shutdown();
            }
        }
    }

    protected void uploadKeyData(EncryptionIdentity key) throws IOException {
        uploadConfigData(PUBLICKEY_FILENAME,
                encryptionKeyForUpload(key),
                false, null);
    }

    protected abstract void uploadPending(LogConsumer logConsumer) throws IOException;

    protected String getShare() {
        return null;
    }

    private void uploadLogFile(String localFile, String remoteFile, byte[] data) throws IOException {
        uploadConfigData(remoteFile, data, true, localFile);
        addLogFile(remoteFile);
    }

    protected void addLogFile(String remoteFile) throws IOException {
        getMetadataRepository(true).getLogFileRepository().addFile(remoteFile);
    }

    protected void uploadLogFile(String file, byte[] data) throws IOException {
        String uploadFilename = transformLogFilename(file, null);
        updateNextLogFilename(uploadFilename);
        uploadConfigData(uploadFilename, data, true, file);
        addLogFile(uploadFilename);
    }

    private void updateNextLogFilename(String uploadFilename) throws IOException {
        if (logConsumer != null) {
            if (logConsumer.lastSyncedLogFile(getShare()) != null && logConsumer.lastSyncedLogFile(getShare()).compareTo(uploadFilename) > 0) {
                log.warn("Uploaded log file \"{}\" out of order, already uploaded \"{}\"", uploadFilename,
                        logConsumer.lastSyncedLogFile(getShare()));
            } else {
                logConsumer.setLastSyncedLogFile(getShare(), uploadFilename);
            }
        }
    }

    protected List<File> existingLogFiles() throws IOException {
        File parent = Paths.get(manifestLocation, LOG_ROOT).toFile();
        if (parent.isDirectory()) {
            String[] files = parent.list();
            if (files != null) {
                return Arrays.stream(files).map(file -> new File(parent, file)).collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }

    public void initialize(LogConsumer logConsumer, boolean immediate) {
        synchronized (lock) {
            if (this.logConsumer == null) {
                this.logConsumer = logConsumer;
            }

            if (immediate) {
                internalInitialize();
            }
        }
    }

    protected byte[] downloadData(String file) throws IOException {
        byte[] data = IOProviderUtil.download(getIoProvider(), file);
        if (data != null) {
            rateLimitController.acquireDownloadPermits(manifestDestination, data.length);
        }
        return data;
    }

    protected void uploadData(String file, byte[] data, Runnable success) {
        uploadCount.incrementAndGet();
        uploadSubmissionCount.incrementAndGet();
        uploadExecutor.submit(() -> {
            try {
                uploadScheduler.scheduleUpload(manifestDestination, file, data, key -> {
                    try {
                        if (success != null && key != null) {
                            success.run();
                        }
                    } finally {
                        synchronized (uploadCount) {
                            uploadCount.decrementAndGet();
                            uploadCount.notifyAll();
                        }
                    }
                });
            } finally {
                synchronized (uploadSubmissionCount) {
                    uploadSubmissionCount.decrementAndGet();
                    uploadSubmissionCount.notifyAll();
                }
            }
        });
    }

    public void validateIdentity() {
        byte[] data;
        try {
            debug(() -> log.debug("Validating manifest installation identity"));
            data = downloadData(IDENTITY_MANIFEST_LOCATION);
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
            rateLimitController.acquireUploadPermits(manifestDestination, data.length);
            IOProviderUtil.upload(getIoProvider(), IDENTITY_MANIFEST_LOCATION, data);
        } catch (SubscriptionLackingException e) {
            throw new RuntimeException(e.getMessage(), e);
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

    protected List<String> getExistingLogs() throws IOException {
        IOIndex index = (IOIndex) getIoProvider();
        return index.availableLogs(null, true);
    }

    protected byte[] encryptionKeyForUpload(EncryptionIdentity encryptionKey) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            encryptionKey.writeKey(EncryptionIdentity.KeyFormat.UPLOAD, output);
            return output.toByteArray();
        }
    }

    protected void syncDestinationKey() throws IOException {
        byte[] keyData = null;
        try {
            keyData = downloadData(PUBLICKEY_FILENAME);
        } catch (Exception exc) {
            try {
                getIoProvider().checkCredentials(false);
            } catch (IOException e) {
                if (!IOUtils.hasInternet()) {
                    throw exc;
                }
            }
            log.info("Public key does not exist");
            uploadPublicKey(getEncryptionIdentity());
        }

        if (keyData != null) {
            EncryptionIdentity existingPublicKey = null;
            try {
                existingPublicKey = EncryptionIdentity.restoreFromString(new String(keyData, StandardCharsets.UTF_8));
            } catch (GeneralSecurityException exc) {
                log.error("Failed to read destination public key, replacing with local copy", exc);
                uploadPublicKey(getEncryptionIdentity());
            }

            if (existingPublicKey != null) {
                if (!getEncryptionIdentity().getPrimaryKeys().equals(existingPublicKey.getPrimaryKeys())) {
                    throw new IOException("Public key that exist in destination does not match current public key");
                }
                if (getEncryptionIdentity().getSalt() != null && !getEncryptionIdentity().getSalt().equals(existingPublicKey.getSalt())) {
                    log.info("Public key needs to be updated");
                    uploadPublicKey(getEncryptionIdentity());
                }
            }
        }
    }

    protected void uploadPublicKey(EncryptionIdentity identity) throws IOException {
        uploadConfigData(PUBLICKEY_FILENAME, encryptionKeyForUpload(identity), false, null);
    }

    protected void uploadConfigData(String filename, byte[] unencryptedData,
                                    boolean encrypt,
                                    String deleteFilename) throws IOException {
        byte[] data = unencryptedData;
        if (encrypt) {
            data = encryptConfigData(unencryptedData);
        }

        log.info("Uploading \"{}\" ({})", filename, readableSize(data.length));

        Runnable success;
        if (deleteFilename != null) {
            success = () -> {
                deleteFile(new File(deleteFilename));
            };
        } else {
            success = null;
        }

        uploadData(filename, data, success);
    }

    public byte[] encryptConfigData(byte[] data) throws IOException {
        try {
            return encryptor.encryptBlock(null, compressConfigData(data), getIdentityKeys());
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private String transformLogFilename(String path, String additional) {
        String filename = Paths.get(path).getFileName().toString();
        String end = ".gz";
        if (additional != null) {
            end = "-" + additional + end;
        }
        return "logs" + PATH_SEPARATOR + filename.replaceAll("(\\d{4}-\\d{2}-\\d{2})-", "$1" + PATH_SEPARATOR) + end;
    }

    protected void ensureOpenLogFile() throws IOException {
        synchronized (lock) {
            if (currentLogLock == null) {
                createNewLogFile();
            }
        }
    }

    public void addLogEntry(String type, String jsonDefinition) {
        boolean flush = false;

        synchronized (lock) {
            internalInitialize();

            try {
                ensureOpenLogFile();
                writeLogEntry(type, jsonDefinition);

                if (!currentlyClosingLog.get()
                        && currentLogLength > configuration.getManifest().getMaximumUnsyncedSize()) {
                    flush = true;
                }
            } catch (IOException exc) {
                log.error("Failed to save log entry: " + type + ": \u200E" + jsonDefinition + "\u200E", exc);

                try {
                    flushRepositoryLogging(false);
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
                flushRepositoryLogging(false);
            } catch (IOException exc) {
                log.error("Failed to flush log");
            }
        }
    }

    private void writeLogEntry(String type, String jsonDefinition) throws IOException {
        byte[] data = (type + ":" + jsonDefinition + "\n").getBytes(StandardCharsets.UTF_8);
        if (currentLogLock.getLockedChannel().write(ByteBuffer.wrap(data)) != data.length) {
            log.error("Failed to write log entry");
        }
        if (!disabledFlushing) {
            currentLogLock.getLockedChannel().force(false);
        }
        currentLogLength += data.length;
    }

    protected MetadataRepository getMetadataRepository(boolean required) {
        if (logConsumer != null) {
            MetadataRepository repository = logConsumer.getMetadataRepository();
            if (repository != null) {
                return repository;
            }
        }
        if (required) {
            throw new RuntimeException("Metadata repository not ready");
        }
        return null;
    }

    protected void flushRepositoryLogging(boolean wait) throws IOException {
        boolean performFlush = false;
        synchronized (lock) {
            if (!currentlyClosingLog.get()) {
                currentlyClosingLog.set(true);
                performFlush = true;
            }
        }
        if (performFlush) {
            try {
                MetadataRepository repository = getMetadataRepository(false);
                if (repository != null) {
                    repository.flushLogging();
                }
            } catch (Exception exc) {
                log.error("Failed to flush repository before starting new log file", exc);
            }
            synchronized (lock) {
                try {
                    if (wait) {
                        logFileType = switch (logFileType) {
                            case INITIAL -> LogFileType.INITIAL_COMPLETED;
                            case DEFAULT -> LogFileType.COMPLETED;
                            default -> logFileType;
                        };
                    }
                    closeLogFile();
                } catch (Exception exc) {
                    log.error("Failed to close log file", exc);
                } finally {
                    currentlyClosingLog.set(false);
                }
            }
            if (wait)
                waitUploadSubmissions();
        }
    }

    private void createNewLogFile() throws IOException {
        closeLogFile();
        currentLogLength = 0;
        String filename;
        filename = createLogFilename();

        File parentFile = new File(filename).getParentFile();
        createDirectory(parentFile, true);
        try {
            IOUtils.setOwnerOnlyPermissions(parentFile);
        } catch (IOException e) {
            log.warn("Failed to set owner only permissions on logs directory", e);
        }

        currentLogLock = new AccessLock(filename);
        lastLogFilename = filename;
        currentLogLock.lock(true);

        String lastUploadFile = logConsumer.lastSyncedLogFile(getShare());
        if (lastUploadFile != null) {
            writeLogEntry("previousFile", MAPPER.writeValueAsString(lastUploadFile));
            debug(() -> log.debug("Registering previous log file \"{}\" for \"{}\"", lastUploadFile, lastLogFilename));
        } else {
            logFileType = LogFileType.INITIAL;
        }

        if (configuration.getManifest().getMaximumUnsyncedSeconds() != null) {
            executor.schedule(() -> {
                        LogClosing shouldClose = null;
                        synchronized (lock) {
                            if (currentLogLock != null && currentLogLength > 0 && filename.equals(currentLogLock.getFilename())) {
                                try {
                                    shouldClose = synchronizedLogClosing();
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                        if (shouldClose != null) {
                            try {
                                asyncCloseLogFile(shouldClose);
                            } catch (IOException exc) {
                                log.error("Failed to create new log file", exc);
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
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            debug(() -> log.warn("Had to wait a bit to get a unique filename for log"));
            filename = Paths.get(manifestLocation, LOG_ROOT, LOG_FILE_FORMATTER.format(Instant.now())).toString();
        }
        return filename;
    }

    private void closeLogFile() throws IOException {
        LogClosing result = synchronizedLogClosing();

        asyncCloseLogFile(result);
    }

    private void asyncCloseLogFile(LogClosing result) throws IOException {
        if (result != null) {
            result.logLockToClose.getLockedChannel().position(0);
            String filename = result.logLockToClose.getFilename();
            byte[] data;
            try (InputStream stream = Channels.newInputStream(result.logLockToClose.getLockedChannel())) {
                data = IOUtils.readAllBytes(stream);
            } finally {
                try {
                    result.logLockToClose().close();
                } catch (IOException exc) {
                    log.error("Failed to close log file lock \"{}\"", filename, exc);
                }
            }
            try {
                uploadLogFile(filename, result.uploadFilename, data);
            } catch (IOException exc) {
                throw new IOException(String.format("Failed to upload log file \"%s\"", filename), exc);
            }
        }
    }

    private LogClosing synchronizedLogClosing() throws IOException {
        synchronized (lock) {
            if (currentLogLock != null) {
                String uploadFilename = transformLogFilename(currentLogLock.getFilename(), switch (logFileType) {
                    case INITIAL -> "i";
                    case INITIAL_COMPLETED -> "ic";
                    case COMPLETED -> "c";
                    default -> null;
                });
                logFileType = LogFileType.DEFAULT;
                updateNextLogFilename(uploadFilename);
                try {
                    return new LogClosing(currentLogLock, uploadFilename);
                } finally {
                    currentLogLength = 0;
                    currentLogLock = null;
                }
            }
            return null;
        }
    }

    public void deleteLogFiles(String lastLogFile) throws IOException {
        deleteLogFiles(lastLogFile, (IOIndex) getIoProvider(), new AtomicLong(), new AtomicLong());
    }

    public void syncLog() throws IOException {
        synchronized (lock) {
            closeLogFile();
        }
        waitUploads();
    }

    protected void completeUploads() throws IOException {
        synchronized (lock) {
            closeLogFile();
        }
        waitUploads();
    }

    protected void waitUploadSubmissions() {
        synchronized (uploadSubmissionCount) {
            while (uploadSubmissionCount.get() > 0) {
                try {
                    uploadSubmissionCount.wait();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void waitUploads() {
        synchronized (uploadCount) {
            while (uploadCount.get() > 0) {
                try {
                    uploadCount.wait();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void shutdown() throws IOException {
        synchronized (lock) {
            shutdown = true;
        }

        waitCompletedOperation();

        uploadScheduler.waitForCompletion();
        flushRepositoryLogging(true);

        synchronized (lock) {
            completeUploads();
            uploadExecutor.shutdown();
            executor.shutdownNow();
        }
    }

    protected void waitCompletedOperation() {
    }

    private enum LogFileType {
        INITIAL,
        DEFAULT,
        COMPLETED,
        INITIAL_COMPLETED
    }

    private record LogClosing(AccessLock logLockToClose, String uploadFilename) {
    }

    protected static class DeletionScheduler extends SchedulerImpl {
        public DeletionScheduler(int maximumConcurrency) {
            super(maximumConcurrency);
        }

        public void delete(IOProvider provider, String file, AtomicLong counter) {
            schedule(() -> {
                try {
                    provider.delete(file);
                    counter.incrementAndGet();
                    debug(() -> log.debug("Deleted \"{}\"", file));
                } catch (IOException e) {
                    log.error("Failed to delete \"{}\"", file, e);
                }
            });
        }
    }
}
