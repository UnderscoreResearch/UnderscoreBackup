package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.cli.commands.ConfigureCommand.getConfigurationUrl;
import static com.underscoreresearch.backup.configuration.CommandLineModule.CONFIG_DATA;
import static com.underscoreresearch.backup.io.IOUtils.deleteContents;
import static com.underscoreresearch.backup.io.IOUtils.deleteFile;
import static com.underscoreresearch.backup.manifest.implementation.ShareManifestManagerImpl.SHARE_CONFIG_FILE;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableEta;
import static com.underscoreresearch.backup.utils.LogUtil.readableNumber;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_ACTIVATED_SHARE_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_DESTINATION_WRITER;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import com.underscoreresearch.backup.file.LogFileRepository;
import com.underscoreresearch.backup.file.implementation.LogFileRepositoryImpl;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.underscoreresearch.backup.cli.commands.ChangePasswordCommand;
import com.underscoreresearch.backup.cli.commands.VersionCommand;
import com.underscoreresearch.backup.cli.ui.UIHandler;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.MetadataRepositoryStorage;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.file.RepositoryOpenMode;
import com.underscoreresearch.backup.file.implementation.BackupStatsLogger;
import com.underscoreresearch.backup.file.implementation.NullRepository;
import com.underscoreresearch.backup.file.implementation.ScannerSchedulerImpl;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.manifest.BackupSearchAccess;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.manifest.ShareActivateMetadataRepository;
import com.underscoreresearch.backup.manifest.ShareManifestManager;
import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupActivatedShare;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockAdditional;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupShare;
import com.underscoreresearch.backup.service.api.BackupApi;
import com.underscoreresearch.backup.service.api.invoker.ApiException;
import com.underscoreresearch.backup.service.api.model.MessageResponse;
import com.underscoreresearch.backup.service.api.model.ShareResponse;
import com.underscoreresearch.backup.service.api.model.SourceRequest;
import com.underscoreresearch.backup.utils.AccessLock;
import com.underscoreresearch.backup.utils.ManualStatusLogger;
import com.underscoreresearch.backup.utils.StateLogger;
import com.underscoreresearch.backup.utils.StatusLine;

@Slf4j
public class ManifestManagerImpl extends BaseManifestManagerImpl implements ManualStatusLogger, ManifestManager {
    public final static String CONFIGURATION_FILENAME = "configuration.json";
    public static final String OPTIMIZING_LOG_OPERATION = "Optimizing log";
    public static final String REPAIRING_REPOSITORY_OPERATION = "Repairing repository";
    private static final String UPLOAD_PENDING = "Upload pending";
    public static final long EVENTUAL_CONSISTENCY_TIMEOUT_MS = 20 * 1000;
    private final String source;
    private final BackupStatsLogger statsLogger;
    private final AdditionalManifestManager additionalManifestManager;
    private final Object operationLock = new Object();
    private final boolean noDelete;
    protected Closeable operationTask;
    private Map<String, ShareManifestManager> activeShares;
    private boolean repositoryReady = true;
    @Getter(AccessLevel.PROTECTED)
    private String operation;
    private AtomicLong totalFiles;
    private AtomicLong totalOperations;
    private AtomicLong processedFiles;
    private AtomicLong processedOperations;
    private Stopwatch operationDuration;
    @Getter
    @Setter
    private ManifestManager dependentManager;
    private boolean repairingRepository;

    public ManifestManagerImpl(BackupConfiguration configuration,
                               String manifestLocation,
                               RateLimitController rateLimitController,
                               ServiceManager serviceManager,
                               String installationIdentity,
                               String source,
                               boolean forceIdentity,
                               boolean noDelete,
                               EncryptionIdentity encryptionIdentity,
                               IdentityKeys identityKeys,
                               BackupStatsLogger statsLogger,
                               AdditionalManifestManager additionalManifestManager,
                               UploadScheduler uploadScheduler) {
        super(configuration,
                configuration.getDestinations().get(configuration.getManifest().getDestination()),
                manifestLocation,
                rateLimitController,
                serviceManager,
                installationIdentity,
                forceIdentity,
                encryptionIdentity,
                identityKeys,
                uploadScheduler);
        this.source = source;
        this.statsLogger = statsLogger;
        this.noDelete = noDelete;
        this.additionalManifestManager = additionalManifestManager;
        StateLogger.addLogger(this);
    }

    protected void uploadPending(LogConsumer logConsumer) throws IOException {
        startOperation(UPLOAD_PENDING);
        try {
            processedFiles = new AtomicLong(0);
            totalFiles = new AtomicLong(2);

            syncDestinationKey();

            processedFiles.incrementAndGet();

            uploadConfigData(CONFIGURATION_FILENAME,
                    InstanceFactory.getInstance(CONFIG_DATA).getBytes(StandardCharsets.UTF_8),
                    true, null);
            processedFiles.incrementAndGet();

            updateServiceSourceData(getEncryptionIdentity());

            List<File> files = existingLogFiles();

            if (!files.isEmpty()) {
                totalFiles.addAndGet(files.size());

                logConsumer.setRecoveryMode(true);
                try {
                    for (File file : files) {
                        byte[] data = null;
                        try (AccessLock lock = new AccessLock(file.getAbsolutePath())) {
                            if (lock.tryLock(true)) {
                                try (InputStream stream = Channels
                                        .newInputStream(lock.getLockedChannel())) {
                                    data = IOUtils.readAllBytes(stream);
                                }
                            } else {
                                log.warn("Log file \"{}\" locked by other process", file.getAbsolutePath());
                            }
                        }
                        if (data != null) {
                            if (!repairingRepository) {
                                processLogInputStream(logConsumer, new ByteArrayInputStream(data));
                            }
                            uploadLogFile(file.getAbsolutePath(), data);
                        }
                        processedFiles.incrementAndGet();
                    }
                } finally {
                    logConsumer.setRecoveryMode(false);
                }
            }
        } finally {
            resetStatus();
        }
    }

    @Override
    protected void uploadConfigData(String filename, byte[] inputData, boolean encrypt, String deleteFilename) throws IOException {
        byte[] unencryptedData;
        byte[] data;
        validateIdentity();

        if (encrypt) {
            unencryptedData = compressConfigData(inputData);
            try {
                data = getEncryptor().encryptBlock(null, unencryptedData, getIdentityKeys());
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            }
        } else {
            data = unencryptedData = inputData;
        }
        log.info("Uploading \"{}\" ({})", filename, readableSize(data.length));

        AtomicInteger successNeeded = new AtomicInteger(1 + additionalManifestManager.count());
        Runnable success;
        if (deleteFilename != null) {
            success = () -> {
                if (successNeeded.decrementAndGet() == 0) {
                    deleteFile(new File(deleteFilename));
                }
            };
        } else {
            success = null;
        }
        uploadData(filename, data, success);

        if (filename.equals(CONFIGURATION_FILENAME)) {
            additionalManifestManager.uploadConfiguration(getConfiguration(), getIdentityKeys());
        } else {
            if (encrypt)
                additionalManifestManager.uploadConfigurationData(filename, data, unencryptedData, getEncryptor(),
                        getIdentityKeys(), success);
            else
                additionalManifestManager.uploadConfigurationData(filename, data, null, null,
                        null, success);
        }
    }

    @Override
    public void updateServiceSourceData(EncryptionIdentity encryptionKey) throws IOException {
        if (getServiceManager().getToken() != null
                && getServiceManager().getSourceName() != null
                && getServiceManager().getSourceId() != null) {
            BackupDestination destination;
            String destinationData;
            try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
                destination = getConfiguration().getDestinations().get(getConfiguration().getManifest().getDestination());
                BACKUP_DESTINATION_WRITER.writeValue(stream, destination);
                destinationData = BaseEncoding.base64Url().encode(encryptConfigData(stream.toByteArray())).replace("=", "");
            }
            String keyData;
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                encryptionKey.writeKey(EncryptionIdentity.KeyFormat.SERVICE, output);
                keyData = output.toString(StandardCharsets.UTF_8);
            }

            getServiceManager().call(null, new ServiceManager.ApiFunction<>() {
                @Override
                public boolean shouldRetryMissing(String region) {
                    return region != null && !region.equals("us-west");
                }

                @Override
                public MessageResponse call(BackupApi api) throws ApiException {
                    return api.updateSource(getServiceManager().getSourceId(),
                            new SourceRequest()
                                    .identity(getInstallationIdentity())
                                    .name(getServiceManager().getSourceName())
                                    .destination(destinationData)
                                    .applicationUrl(getApplicationUrl())
                                    .encryptionMode(destination.getEncryption())
                                    .key(keyData)
                                    .version(VersionCommand.getVersionEdition())
                                    .sharingKey(encryptionKey.getSharingKeys() != null ?
                                            encryptionKey.getSharingKeys().getPublicKeyString() :
                                            null));
                }
            });
        }
    }

    private String getApplicationUrl() {
        try {
            return getConfigurationUrl();
        } catch (Exception exc) {
            return null;
        }
    }

    @Override
    public void validateIdentity() {
        if (Strings.isNullOrEmpty(source)) {
            super.validateIdentity();

            additionalManifestManager.validateIdentity(getInstallationIdentity(), isForceIdentity());
        }
    }

    @Override
    public void storeIdentity() {
        if (Strings.isNullOrEmpty(source)) {
            super.storeIdentity();

            additionalManifestManager.storeIdentity(getInstallationIdentity());
        }
    }

    @Override
    public void replayLog(LogConsumer consumer, String password) throws IOException {
        replayLog(true, consumer, password, null, true);
    }

    public boolean replayLog(boolean claimIdentity, LogConsumer consumer, String password,
                             String lastKnownExistingFile, boolean allowFailures) throws IOException {
        repositoryReady = false;

        MetadataRepository repository = getMetadataRepository(true);
        if (claimIdentity) {
            try {
                repository.upgradeStorage();
            } catch (Exception exc) {
                log.error("Failed upgrading storage", exc);
            }

            startOperation("Replay log");

            storeIdentity();
        } else {
            startOperation(REPAIRING_REPOSITORY_OPERATION);
            validateIdentity();
        }

        if (consumer.lastSyncedLogFile(getShare()) != null) {
            log.info("Continuing rebuild from after file \"{}\"", getLogConsumer().lastSyncedLogFile(getShare()));
        }

        try {
            IOIndex index = (IOIndex) getIoProvider();
            List<String> files = index.availableLogs(consumer.lastSyncedLogFile(getShare()), true);

            files = trimFiles(files);

            // If we know this file should be in there, but it is not we wait for eventual consistency
            // and try again fetching the log files.
            if (lastKnownExistingFile != null) {
                if (!files.contains(lastKnownExistingFile)) {
                    awaitEventualConsistency();
                    files = index.availableLogs(consumer.lastSyncedLogFile(getShare()), true);
                    if (!files.contains(lastKnownExistingFile)) {
                        log.warn("Expected log file \"{}\" to exist, but it does not", lastKnownExistingFile);
                    }
                }
            }

            totalFiles = new AtomicLong(files.size());
            processedFiles = new AtomicLong(0L);
            processedOperations = new AtomicLong(0L);

            LogPrefetcher logPrefetcher = null;
            try {
                logPrefetcher = new LogPrefetcher(files,
                        InstanceFactory.getInstance(BackupConfiguration.class),
                        this::downloadData, getEncryptor(),
                        getIdentityKeys().getPrivateKeys(getEncryptionIdentity().getPrivateIdentity(password)));
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            }
            logPrefetcher.start();
            try (CloseableLock ignored = repository.exclusiveLock()) {
                for (String file : files) {
                    try {
                        log.info("Processing log file \"{}\"", file);
                        byte[] data = logPrefetcher.getLog(file);
                        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
                            try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                                processLogInputStream(consumer, gzipInputStream);
                            }
                        }
                        consumer.setLastSyncedLogFile(getShare(), file);
                        if (claimIdentity) {
                            getMetadataRepository(true).getLogFileRepository().addFile(file);
                        }
                    } catch (Exception exc) {
                        log.error("Failed to read log file \"{}\"", file, exc);
                        if (!allowFailures) {
                            logPrefetcher.stop();
                            return false;
                        }
                    }
                    if (!Strings.isNullOrEmpty(source) && (isShutdown() || InstanceFactory.isShutdown())) {
                        logPrefetcher.stop();
                        return false;
                    }
                    processedFiles.incrementAndGet();
                }
            } finally {
                logPrefetcher.shutdown();
            }

            if (activeShares != null && !activeShares.isEmpty()) {
                log.info("Creating additional blocks for {} shares", activeShares.size());

                EncryptionIdentity encryptionIdentity = getEncryptionIdentity();

                Set<IdentityKeys> shareKeys = activeShares.keySet().stream()
                        .map(encryptionIdentity::getIdentityKeyForHash)
                        .collect(Collectors.toSet());

                startOperation("Activating shares");

                synchronized (operationLock) {
                    processedOperations = new AtomicLong();
                    totalOperations = new AtomicLong(repository.getBlockCount());
                    totalFiles = null;
                    processedFiles = null;
                }

                try {
                    createAdditionalBlocks(encryptionIdentity.getPrivateIdentity(password), repository, shareKeys);
                } catch (GeneralSecurityException e) {
                    throw new IOException(e);
                }
            }

            if (processedOperations.get() > 1000000L) {
                log.info("Optimizing repository metadata (This could take a while)");
            } else {
                log.info("Optimizing repository metadata");
            }
            flushRepositoryLogging(true);
            repositoryReady = true;

            return true;
        } finally {
            log.info("Completed reprocessing logs");
            resetStatus();
        }
    }

    private List<String> trimFiles(List<String> files) {
        // Optimize log files start with an initial "-i.gz" file,
        // followed by "-c.gz" files once done. If only one file was needed to optimize the log the ending
        // is "-ic.gz". With this we remove all files up to the last file with an "-i*.gz" ending that
        // has an accompanying "-*c.gz" file.

        if (!noDelete) {
            return LogFileRepositoryImpl.trimLogFiles(files);
        }
        return files;
    }

    @Override
    public void setRepairingRepository(boolean repairingRepository) {
        this.repairingRepository = repairingRepository;
    }

    protected void processLogInputStream(LogConsumer consumer, InputStream inputStream) throws IOException {
        try (InputStreamReader inputStreamReader
                     = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            try (BufferedReader reader = new BufferedReader(inputStreamReader)) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    int ind = line.indexOf(':');
                    try {
                        if (processedOperations != null) {
                            processedOperations.incrementAndGet();
                        }
                        consumer.replayLogEntry(line.substring(0, ind),
                                line.substring(ind + 1));
                    } catch (Exception exc) {
                        log.error("Failed processing log line: \"" + line + "\"", exc);
                    }
                }
            }
        }
    }

    @Override
    protected void additionalInitialization() {
        activeShares = new HashMap<>();
        if (getConfiguration().getShares() != null) {
            Map<String, BackupActivatedShare> existingShares = new HashMap<>();
            File sharesDirectory = new File(getManifestLocation(), "shares");
            if (sharesDirectory.isDirectory()) {
                File[] files = sharesDirectory.listFiles();
                if (files != null) {
                    for (File shareFile : files) {
                        if (shareFile.isDirectory()) {
                            File configFile = new File(shareFile, SHARE_CONFIG_FILE);
                            if (configFile.exists()) {
                                try {
                                    BackupActivatedShare share = BACKUP_ACTIVATED_SHARE_READER.readValue(configFile);
                                    existingShares.put(shareFile.getName(), share);
                                } catch (IOException e) {
                                    log.error("Failed to read share definition for \"{}\"", shareFile.getName(), e);
                                }
                            }
                        }
                    }
                }
            }

            Map<String, ShareResponse> remainingServiceShares = new HashMap<>();

            if (getServiceManager().getToken() != null && getServiceManager().getSourceId() != null) {
                try {
                    remainingServiceShares.putAll(getServiceManager().getSourceShares().stream()
                            .collect(Collectors.toMap(ShareResponse::getShareId, (share) -> share)));
                } catch (IOException e) {
                    log.warn("Failed to check active shares from service", e);
                }
            }

            for (Map.Entry<String, BackupShare> entry : getConfiguration().getShares().entrySet()) {
                BackupActivatedShare existingShare = existingShares.get(entry.getKey());
                if (existingShare == null) {
                    log.warn("Encountered share \"{}\" that is not activated", entry.getValue().getName());
                } else if (existingShare.getShare().equals(entry.getValue().activatedShare(getServiceManager().getSourceId(), entry.getKey()).getShare())) {
                    if (!Strings.isNullOrEmpty(entry.getValue().getTargetEmail()) && !remainingServiceShares.containsKey(entry.getKey())) {
                        log.warn("Removing share \"{}\" that no longer exist in service", existingShare.getShare().getName());
                    } else {
                        try {
                            ShareManifestManager manager = createShareManager(entry.getKey(), existingShare, true);
                            activeShares.put(entry.getKey(), manager);
                            manager.initialize(getLogConsumer(), true);
                            remainingServiceShares.remove(entry.getKey());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        existingShares.remove(entry.getKey());
                    }
                }
            }

            remainingServiceShares.values().forEach((share) -> {
                if (!activeShares.containsKey(share.getShareId())) {
                    log.info("Removing service share \"{}\" that no longer exist in configuration", share.getName());
                    try {
                        getServiceManager().deleteShare(share.getShareId());
                    } catch (IOException e) {
                        log.error("Failed to delete share \"{}\" from service", share.getName(), e);
                    }
                }
            });

            if (!existingShares.isEmpty()) {
                startOperation("Deactivating shares");
                processedOperations = new AtomicLong(0);
                totalOperations = new AtomicLong(existingShares.size());
                try {
                    MetadataRepository metadataRepository = getMetadataRepository(true);
                    for (String share : existingShares.keySet()) {
                        log.info("Deleting unused share \"{}\"", existingShares.get(share).getShare().getName());
                        try {
                            metadataRepository.deleteAdditionalBlock(share, null);
                        } catch (IOException e) {
                            log.error("Failed to delete share \"{}\"", existingShares.get(share).getShare().getName(), e);
                        }

                        File shareDir = new File(sharesDirectory, share);
                        deleteContents(shareDir);
                        deleteFile(shareDir);
                        processedOperations.incrementAndGet();
                    }
                } finally {
                    resetStatus();
                    log.info("Completed deleting shares");
                }
            }

            try {
                updateShareEncryption(null);
            } catch (IOException e) {
                log.error("Failed to update share encryption", e);
            }
        }
    }

    private ShareManifestManagerImpl createShareManager(String shareId, BackupActivatedShare share, boolean activated) throws IOException {
        IdentityKeys keys = getEncryptionIdentity().getIdentityKeyForHash(shareId);
        return new ShareManifestManagerImpl(
                getConfiguration(),
                share.getShare().getDestination(),
                Paths.get(getManifestLocation(), "shares", shareId).toString(),
                getRateLimitController(),
                getServiceManager(),
                getInstallationIdentity() + shareId,
                isForceIdentity(),
                EncryptionIdentity.withIdentityKeys(keys),
                keys,
                activated,
                share,
                getUploadScheduler()
        );
    }

    @Override
    public boolean optimizeLog(MetadataRepository existingRepository, LogConsumer logConsumer, boolean force) throws IOException {
        initialize(logConsumer, true);
        flushRepositoryLogging(true);
        getUploadScheduler().waitForCompletion();

        if (!existingLogFiles().isEmpty()) {
            log.warn("Still having pending log files, can't optimize");
            return false;
        }

        String lastLogFile = logConsumer.lastSyncedLogFile(getShare());

        setDisabledFlushing(true);

        try (CloseableLock ignored = existingRepository.acquireLock()) {
            LoggingMetadataRepository copyRepository = new LoggingMetadataRepository(new NullRepository(), this, false);
            logConsumer.setLastSyncedLogFile(getShare(), null);

            internalInitialize();

            TreeMap<String, BackupActivePath> activePaths = existingRepository.getActivePaths(null);

            try {
                startOptimizeOperation();
                processedOperations = new AtomicLong();
                totalOperations = new AtomicLong(existingRepository.getFileCount()
                        + existingRepository.getBlockCount()
                        + existingRepository.getDirectoryCount()
                        + activePaths.size()
                        + getConfiguration().getSets().size() + 1);

                log.info("Processing blocks");
                try (CloseableStream<BackupBlock> blocks = existingRepository.allBlocks()) {
                    blocks.setReportErrorsAsNull(!force);
                    blocks.stream().forEach((block) -> {
                        if (block == null)
                            throw new InvalidEntryException();

                        if (isShutdown())
                            throw new CancellationException();

                        processedOperations.incrementAndGet();
                        try {
                            copyRepository.addBlock(optimizeBlock(block));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }

                log.info("Processing files");
                try (CloseableStream<BackupFile> files = existingRepository.allFiles(true)) {
                    files.setReportErrorsAsNull(!force);
                    files.stream().forEach((file) -> {
                        if (file == null)
                            throw new InvalidEntryException();

                        if (isShutdown())
                            throw new CancellationException();

                        processedOperations.incrementAndGet();
                        try {
                            copyRepository.addFile(file);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }

                log.info("Processing directory contents");
                try (CloseableStream<BackupDirectory> dirs = existingRepository.allDirectories(true)) {
                    dirs.setReportErrorsAsNull(!force);
                    dirs.stream().forEach((dir) -> {
                        if (dir == null)
                            throw new InvalidEntryException();

                        if (isShutdown())
                            throw new CancellationException();

                        processedOperations.incrementAndGet();
                        try {
                            copyRepository.addDirectory(dir);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }

                log.info("Processing active paths");
                activePaths.forEach((path, dir) -> {
                    if (isShutdown())
                        throw new CancellationException();

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
                        if (!"".equals(pendingSet.getSetId())) {
                            copyRepository.addPendingSets(pendingSet);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                copyRepository.close();
                // There is a rare edge case where the final log entry closed the log, but we need to make
                // sure that we have a log file open so we can close it with the completed flag, even if it is empty.
                ensureOpenLogFile();
                flushRepositoryLogging(true);

                ScannerSchedulerImpl.updateOptimizeSchedule(existingRepository,
                        getConfiguration().getManifest().getOptimizeSchedule());
                completeUploads();
                trimRecordedFiles();

                deleteLogFiles(lastLogFile);

                additionalManifestManager.finishOptimizeLog(lastLogFile, totalFiles, processedFiles);
                existingRepository.setErrorsDetected(false);
                log.info("Completed optimizing log");

                return true;
            } catch (CancellationException exc) {
                log.warn("Cancelled optimizing log, reverting changes");
                cancelOptimization(existingRepository, copyRepository, lastLogFile);

                return false;
            } catch (InvalidEntryException exc) {
                log.error("Encountered invalid entry, aborting optimization. Consider repairing local repository");
                existingRepository.setErrorsDetected(true);

                cancelOptimization(existingRepository, copyRepository, lastLogFile);
                return false;
            }
        } finally {
            resetStatus();

            setDisabledFlushing(false);
        }
    }

    private void trimRecordedFiles() throws IOException {
        LogFileRepository logFileRepository = getMetadataRepository(true).getLogFileRepository();
        List<String> files = logFileRepository.getAllFiles();
        logFileRepository.resetFiles(LogFileRepositoryImpl.trimLogFiles(files));

        debug(() -> log.debug("Trimming recorded log files"));
    }

    private void cancelOptimization(MetadataRepository existingRepository,
                                    LoggingMetadataRepository copyRepository,
                                    String lastLogFile) throws IOException {
        copyRepository.close();
        flushRepositoryLogging(true);
        completeUploads();

        awaitEventualConsistency(EVENTUAL_CONSISTENCY_TIMEOUT_MS);

        log.info("Deleting log optimized log files after \"{}\" and keeping old log file", lastLogFile);

        totalFiles = new AtomicLong();
        processedFiles = new AtomicLong();
        deleteNewLogFiles(lastLogFile, (IOIndex) getIoProvider(), totalFiles, processedFiles);
        additionalManifestManager.cancelOptimizeLog(lastLogFile, totalFiles, processedFiles);

        existingRepository.setLastSyncedLogFile(getShare(), lastLogFile);

        log.info("Completed reverting optimizing log");
    }

    protected void startOptimizeOperation() {
        startOperation(OPTIMIZING_LOG_OPERATION);
    }

    @Override
    public void repairRepository(LogConsumer consumer, String password) throws IOException {
        MetadataRepository repository = getMetadataRepository(true);

        MetadataRepositoryStorage newStorage = repository.createStorageRevision();
        repository.open(RepositoryOpenMode.READ_WRITE);
        try {
            String lastKnownFile = repository.lastSyncedLogFile(getShare());
            repository.setLastSyncedLogFile(getShare(), null);

            if (replayLog(false, consumer, password, lastKnownFile, false)) {
                repository.installStorageRevision(newStorage);
            } else {
                repository.cancelStorageRevision(newStorage);
            }
        } catch (Throwable exc) {
            repository.cancelStorageRevision(newStorage);
            log.error("Failed repository upgrade", exc);
        }
    }

    protected void awaitEventualConsistency() {
        if (operationDuration != null) {
            long milliseconds = EVENTUAL_CONSISTENCY_TIMEOUT_MS - operationDuration.elapsed(TimeUnit.MILLISECONDS);
            if (milliseconds > 0) {
                awaitEventualConsistency(milliseconds);
            }
        }
    }

    protected void awaitEventualConsistency(long milliseconds) {
        try {
            if (!((IOIndex) getIoProvider()).hasConsistentWrites()) {
                log.info("Waiting {} seconds for log file eventual consistency", milliseconds / 1000);
                Thread.sleep(milliseconds);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Failed to wait for eventual consistency", e);
        }
    }

    protected BackupBlock optimizeBlock(BackupBlock block) throws IOException {
        return block;
    }

    @Override
    public void deleteLogFiles(String lastLogFile) throws IOException {
        awaitEventualConsistency();

        log.info("Deleting old log files before \"{}\"", lastLogFile);

        totalFiles = new AtomicLong();
        processedFiles = new AtomicLong();
        deleteLogFiles(lastLogFile, (IOIndex) getIoProvider(), totalFiles, processedFiles);
    }

    @Override
    public BackupContentsAccess backupContents(Long timestamp, boolean includeDeleted) throws IOException {
        return new BackupContentsAccessImpl(getMetadataRepository(true),
                timestamp, includeDeleted);
    }

    @Override
    public BackupSearchAccess backupSearch(Long timestamp, boolean includeDeleted) throws IOException {
        return new BackupSearchAccessImpl(getMetadataRepository(true),
                backupContents(timestamp, includeDeleted),
                timestamp,
                includeDeleted);
    }

    @Override
    public void updateKeyData(EncryptionIdentity key) throws IOException {
        ChangePasswordCommand.saveKeyFile(new File(InstanceFactory.getInstance(CommandLineModule.KEY_FILE_NAME)), key);

        uploadKeyData(key);
        updateServiceSourceData(key);
    }

    protected void startOperation(String operation) {
        log.info(operation);
        synchronized (operationLock) {
            this.operation = operation;
            if (this.operationTask != null) {
                try {
                    this.operationTask.close();
                } catch (IOException e) {
                    log.error("Failed to close active task", e);
                }
            }
            this.operationTask = UIHandler.registerTask(operation, true);
            operationDuration = Stopwatch.createStarted();
        }
    }

    @Override
    public boolean isBusy() {
        return operation != null && !operation.equals(UPLOAD_PENDING);
    }

    @Override
    public boolean isRepositoryReady() {
        return repositoryReady;
    }

    @Override
    public Map<String, ShareManifestManager> getActivatedShares() {
        if (activeShares != null) {
            return activeShares;
        }
        return new HashMap<>();
    }

    @Override
    public void activateShares(LogConsumer consumer, EncryptionIdentity.PrivateIdentity privateIdentity) throws IOException {
        initialize(consumer, true);

        MetadataRepository repository = getMetadataRepository(true);

        if (getConfiguration().getShares() != null) {
            Map<IdentityKeys, ShareManifestManagerImpl> pendingShareManagers = new HashMap<>();
            Map<String, BackupShare> pendingShares = new HashMap<>();

            for (Map.Entry<String, BackupShare> entry : getConfiguration().getShares().entrySet()) {
                if (!activeShares.containsKey(entry.getKey())) {
                    pendingShareManagers.put(privateIdentity.getEncryptionIdentity().getIdentityKeyForHash(entry.getKey()),
                            createShareManager(entry.getKey(), entry.getValue().activatedShare(getServiceManager().getSourceId(), entry.getKey()), false));
                    pendingShares.put(entry.getKey(), entry.getValue());
                    if (getServiceManager().getToken() != null && getServiceManager().getSourceId() != null
                            && !Strings.isNullOrEmpty(entry.getValue().getTargetEmail())) {
                        getServiceManager().createShare(getEncryptionIdentity(), entry.getKey(), entry.getValue());
                    }
                }
            }

            if (!pendingShareManagers.isEmpty()) {
                startOperation("Activating shares");
                processedOperations = new AtomicLong();

                try (CloseableLock ignored = repository.acquireLock()) {
                    totalOperations = new AtomicLong(repository.getBlockCount() + repository.getFileCount()
                            + repository.getDirectoryCount());
                    log.info("Fetching existing logs");

                    Map<String, String> existingLogs = new HashMap<>();
                    for (Map.Entry<IdentityKeys, ShareManifestManagerImpl> entry : pendingShareManagers.entrySet()) {
                        entry.getValue().validateIdentity();
                        existingLogs.put(entry.getKey().getKeyIdentifier(), repository.lastSyncedLogFile(entry.getKey().getKeyIdentifier()));
                        repository.setLastSyncedLogFile(entry.getKey().getKeyIdentifier(), null);
                        entry.getValue().initialize(consumer, false);
                    }

                    ShareActivateMetadataRepository copyRepository = new ShareActivateMetadataRepository(repository,
                            this, pendingShares,
                            pendingShareManagers.entrySet().stream()
                                    .map(entry -> Map.entry(entry.getKey().getKeyIdentifier(), entry.getValue()))
                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                    copyRepository.clear();

                    log.info("Calculating new block storage keys if needed");

                    createAdditionalBlocks(privateIdentity, repository, pendingShareManagers.keySet());

                    log.info("Writing files to shares");

                    try (CloseableStream<BackupFile> files = repository.allFiles(true)) {
                        files.stream().forEach(file -> {
                            if (isShutdown()) {
                                throw new CancellationException();
                            }
                            try {
                                processedOperations.incrementAndGet();
                                copyRepository.addFile(file);
                            } catch (IOException e) {
                                log.error("Failed to write file \"{}\"", file.getPath(), e);
                            }
                        });
                    }

                    log.info("Writing directories to shares");

                    try (CloseableStream<BackupDirectory> dirs = repository.allDirectories(true)) {
                        dirs.stream().forEach(dir -> {
                            if (isShutdown()) {
                                throw new CancellationException();
                            }
                            try {
                                processedOperations.incrementAndGet();
                                copyRepository.addDirectory(dir);
                            } catch (IOException e) {
                                log.error("Failed to write file \"{}\"", PathNormalizer.physicalPath(dir.getPath()), e);
                            }
                        });
                    }

                    log.info("Deleting any existing log files");

                    for (Map.Entry<IdentityKeys, ShareManifestManagerImpl> entry : pendingShareManagers.entrySet()) {
                        entry.getValue().syncLog();
                        entry.getValue().deleteLogFiles(existingLogs.get(entry.getKey().getKeyIdentifier()));
                        entry.getValue().completeActivation();

                        activeShares.put(entry.getKey().getKeyIdentifier(), entry.getValue());
                    }

                } catch (CancellationException exc) {
                    log.warn("Cancelled share activation");
                } finally {
                    resetStatus();
                }
            }
        }

        updateShareEncryption(privateIdentity);
        if (statsLogger != null) {
            statsLogger.setNeedsActivation(false);
        }
    }

    private void createAdditionalBlocks(EncryptionIdentity.PrivateIdentity privateIdentity,
                                        MetadataRepository repository,
                                        Set<IdentityKeys> shareEncryptionKeys) throws IOException {
        try (CloseableStream<BackupBlock> blocks = repository.allBlocks()) {
            blocks.stream().forEach((block) -> {
                if (isShutdown()) {
                    throw new CancellationException();
                }
                processedOperations.incrementAndGet();
                for (IdentityKeys entry : shareEncryptionKeys) {
                    processedOperations.incrementAndGet();

                    IdentityKeys.PrivateKeys privateKey = privateIdentity.getEncryptionIdentity().getPrimaryKeys()
                            .getPrivateKeys(privateIdentity);

                    if (!BackupBlock.isSuperBlock(block.getHash())) {
                        List<BackupBlockStorage> newStorage =
                                new ArrayList<>();
                        for (BackupBlockStorage storage : block.getStorage()) {
                            try {
                                newStorage.add(EncryptorFactory.getEncryptor(storage.getEncryption())
                                        .reKeyStorage(storage, privateKey, entry));
                            } catch (GeneralSecurityException e) {
                                log.error("Failed to reencrypt block {} for share {}", block.getHash(), entry.getKeyIdentifier());
                            }
                        }
                        BackupBlockAdditional blockAdditional = BackupBlockAdditional.builder()
                                .publicKey(entry.getKeyIdentifier())
                                .used(false)
                                .hash(block.getHash())
                                .properties(new ArrayList<>())
                                .build();
                        for (int i = 0; i < newStorage.size(); i++) {
                            Map<String, String> oldProperties = block.getStorage().get(i).getProperties();
                            if (newStorage.get(i).getProperties() != null) {
                                blockAdditional.getProperties().add(newStorage
                                        .get(i)
                                        .getProperties()
                                        .entrySet()
                                        .stream()
                                        .filter((check) -> !check.getValue().equals(oldProperties.get(check.getKey())))
                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                            } else
                                blockAdditional.getProperties().add(null);
                        }
                        try {
                            repository.addAdditionalBlock(blockAdditional);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to create share", e);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void updateShareEncryption(EncryptionIdentity.PrivateIdentity privateIdentity) throws IOException {
        boolean needActivation = false;
        Set<String> definedShares = new HashSet<>(getConfiguration().getShares().keySet());
        for (Map.Entry<String, ShareManifestManager> entry : activeShares.entrySet()) {
            definedShares.remove(entry.getKey());
            entry.getValue().updateEncryptionKeys(privateIdentity);
            if (!entry.getValue().getActivatedShare().isUpdatedEncryption())
                needActivation = true;
        }
        if (!definedShares.isEmpty()) {
            needActivation = true;
        }
        if (needActivation && statsLogger != null) {
            statsLogger.setNeedsActivation(true);
        }
    }

    public void resetStatus() {
        synchronized (getLock()) {
            synchronized (operationLock) {
                operation = null;
                totalFiles = null;
                processedFiles = null;
                processedOperations = null;
                operationDuration = null;
                if (operationTask != null) {
                    try {
                        operationTask.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } finally {
                        operationTask = null;
                    }
                }
                getLock().notifyAll();
            }
        }
    }

    @Override
    public void syncLog() throws IOException {
        synchronized (getLock()) {
            super.syncLog();

            if (activeShares != null) {
                for (ShareManifestManager others : activeShares.values())
                    others.syncLog();
            }
        }
    }

    @Override
    public void waitUploads() {
        super.waitUploads();

        if (activeShares != null) {
            for (ShareManifestManager others : activeShares.values())
                others.waitUploads();
        }

        additionalManifestManager.waitUploads();
    }

    @Override
    protected void waitCompletedOperation() {
        synchronized (getLock()) {
            while (operation != null) {
                try {
                    getLock().wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Failed to wait", e);
                }
            }
        }
    }

    @Override
    public void shutdown() throws IOException {
        if (dependentManager != null) {
            dependentManager.shutdown();
        }
        super.shutdown();

        synchronized (getLock()) {
            if (activeShares != null) {
                for (ShareManifestManager others : activeShares.values())
                    others.shutdown();
            }

            additionalManifestManager.shutdown();
        }

        StateLogger.removeLogger(this);
    }

    public List<StatusLine> status() {
        List<StatusLine> ret = new ArrayList<>();
        if (!Strings.isNullOrEmpty(source)) {
            ret.add(new StatusLine(getClass(), "SOURCE", "Browsing source", null, InstanceFactory.getAdditionalSourceName()));
        }
        synchronized (operationLock) {
            if (operation != null) {
                String code = operation.toUpperCase().replace(" ", "_");
                if (processedFiles != null && totalFiles != null) {
                    ret.add(new StatusLine(getClass(), code + "_PROCESSED_FILES", operation + " processed files",
                            processedFiles.get(), totalFiles.get(),
                            readableNumber(processedFiles.get()) + " / " + readableNumber(totalFiles.get())
                                    + (totalOperations != null ? "" :
                                    readableEta(processedFiles.get(), totalFiles.get(),
                                            operationDuration.elapsed()))));
                }
                if (processedOperations != null) {
                    if (totalOperations != null) {
                        if (operationDuration != null) {
                            ret.add(new StatusLine(getClass(), code + "_PROCESSED_STEPS", operation,
                                    processedOperations.get(), totalOperations.get(),
                                    readableNumber(processedOperations.get()) + " / " + readableNumber(totalOperations.get()) + " steps"
                                            + readableEta(processedOperations.get(), totalOperations.get(),
                                            operationDuration.elapsed())));
                        } else {
                            ret.add(new StatusLine(getClass(), code + "_PROCESSED_STEPS", operation + " processed steps",
                                    processedOperations.get(), totalOperations.get(),
                                    readableNumber(processedOperations.get()) + " / " + readableNumber(totalOperations.get())));
                        }
                    } else {
                        ret.add(new StatusLine(getClass(), code + "_PROCESSED_STEPS", operation + " processed steps",
                                processedOperations.get()));
                    }
                }

                if (processedOperations != null && operationDuration != null) {
                    int elapsedMilliseconds = (int) operationDuration.elapsed(TimeUnit.MILLISECONDS);
                    if (elapsedMilliseconds > 0) {
                        long throughput = 1000 * processedOperations.get() / elapsedMilliseconds;
                        ret.add(new StatusLine(getClass(), code + "_THROUGHPUT", operation + " throughput",
                                throughput, readableNumber(throughput) + " steps/s"));
                    }
                }
            }
        }
        return ret;
    }

    private static class InvalidEntryException extends RuntimeException {
    }
}
