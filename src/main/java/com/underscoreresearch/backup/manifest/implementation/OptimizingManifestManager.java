package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.cli.commands.ConfigureCommand.getConfigurationUrl;
import static com.underscoreresearch.backup.cli.web.ResetDelete.deleteContents;
import static com.underscoreresearch.backup.configuration.CommandLineModule.CONFIG_DATA;
import static com.underscoreresearch.backup.manifest.implementation.ShareManifestManagerImpl.SHARE_CONFIG_FILE;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_ACTIVATED_SHARE_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_DESTINATION_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_WRITER;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.underscoreresearch.backup.cli.UIManager;
import com.underscoreresearch.backup.cli.commands.VersionCommand;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.file.implementation.BackupStatsLogger;
import com.underscoreresearch.backup.file.implementation.NullRepository;
import com.underscoreresearch.backup.file.implementation.ScannerSchedulerImpl;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.manifest.BackupSearchAccess;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.LoggingMetadataRepository;
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
import com.underscoreresearch.backup.utils.NonClosingInputStream;

@Slf4j
public class OptimizingManifestManager extends BaseManifestManagerImpl implements ManifestManager {
    public final static String CONFIGURATION_FILENAME = "configuration.json";
    private static final String UPLOAD_PENDING = "Upload pending";

    @Getter(AccessLevel.PROTECTED)
    private final String source;
    private final BackupStatsLogger statsLogger;
    @Getter(AccessLevel.PROTECTED)
    private final AdditionalManifestManager additionalManifestManager;
    @Getter(AccessLevel.PROTECTED)
    private final Object operationLock = new Object();
    private Map<String, ShareManifestManager> activeShares;
    private boolean repositoryReady = true;
    @Getter(AccessLevel.PROTECTED)
    private String operation;
    @Getter(AccessLevel.PROTECTED)
    private AtomicLong totalFiles;
    @Getter(AccessLevel.PROTECTED)
    private AtomicLong totalOperations;
    @Getter(AccessLevel.PROTECTED)
    private AtomicLong processedFiles;
    @Getter(AccessLevel.PROTECTED)
    private AtomicLong processedOperations;
    @Getter(AccessLevel.PROTECTED)
    private Stopwatch operationDuration;

    public OptimizingManifestManager(BackupConfiguration configuration,
                                     String manifestLocation,
                                     IOProvider provider,
                                     Encryptor encryptor,
                                     RateLimitController rateLimitController,
                                     ServiceManager serviceManager,
                                     String installationIdentity,
                                     String source,
                                     boolean forceIdentity,
                                     EncryptionKey publicKey,
                                     BackupStatsLogger statsLogger,
                                     AdditionalManifestManager additionalManifestManager)
            throws IOException {
        super(configuration,
                configuration.getDestinations().get(configuration.getManifest().getDestination()),
                manifestLocation,
                provider,
                encryptor,
                rateLimitController,
                serviceManager,
                installationIdentity,
                forceIdentity,
                publicKey);
        this.source = source;
        this.statsLogger = statsLogger;
        this.additionalManifestManager = additionalManifestManager;
    }

    protected void uploadPending(LogConsumer logConsumer) throws IOException {
        startOperation(UPLOAD_PENDING);
        try {
            processedFiles = new AtomicLong(0);
            totalFiles = new AtomicLong(2);

            syncDestinationKey();

            processedFiles.incrementAndGet();

            uploadConfigData(CONFIGURATION_FILENAME,
                    new ByteArrayInputStream(InstanceFactory.getInstance(CONFIG_DATA).getBytes(StandardCharsets.UTF_8)),
                    true);
            processedFiles.incrementAndGet();

            updateServiceSourceData(getPublicKey());

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

                            uploadLogFile(file.getAbsolutePath(), stream);
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

    @Override
    protected void uploadConfigData(String filename, InputStream inputStream, boolean encrypt) throws IOException {
        byte[] unencryptedData;
        byte[] data;
        if (encrypt) {
            validateIdentity();

            unencryptedData = compressConfigData(inputStream);
            data = getEncryptor().encryptBlock(null, unencryptedData, getPublicKey());
        } else {
            data = unencryptedData = IOUtils.readAllBytes(inputStream);
        }
        log.info("Uploading {} ({})", filename, readableSize(data.length));
        uploadData(filename, data);

        if (filename.equals(CONFIGURATION_FILENAME)) {
            additionalManifestManager.uploadConfiguration(getConfiguration(), getPublicKey());
        } else {
            if (encrypt)
                additionalManifestManager.uploadConfigurationData(filename, data, unencryptedData, getEncryptor(), getPublicKey());
            else
                additionalManifestManager.uploadConfigurationData(filename, data, null, null, null);
        }
    }

    @Override
    public void updateServiceSourceData(EncryptionKey encryptionKey) throws IOException {
        if (getServiceManager().getToken() != null
                && getServiceManager().getSourceName() != null
                && getServiceManager().getSourceId() != null) {
            BackupDestination destination;
            String destinationData;
            try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
                destination = getConfiguration().getDestinations().get(getConfiguration().getManifest().getDestination());
                BACKUP_DESTINATION_WRITER.writeValue(stream, destination);
                try (InputStream inputStream = new ByteArrayInputStream(stream.toByteArray())) {
                    destinationData = BaseEncoding.base64Url().encode(encryptConfigData(inputStream)).replace("=", "");
                }
            }
            String keyData = ENCRYPTION_KEY_WRITER.writeValueAsString(encryptionKey.serviceOnlyKey());
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
                                    .sharingKey(encryptionKey.getSharingPublicKey()));
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
        repositoryReady = false;

        MetadataRepository repository = (MetadataRepository) consumer;
        repository.upgradeStorage();

        startOperation("Replay log");

        storeIdentity();

        if (consumer.lastSyncedLogFile(getShare()) != null) {
            log.info("Continuing rebuild from after file {}", getLogConsumer().lastSyncedLogFile(getShare()));
        }

        try {
            IOIndex index = (IOIndex) getProvider();
            List<String> files = index.availableLogs(consumer.lastSyncedLogFile(getShare()));

            totalFiles = new AtomicLong(files.size());
            processedFiles = new AtomicLong(0L);
            processedOperations = new AtomicLong(0L);

            LogPrefetcher logPrefetcher = new LogPrefetcher(files,
                    InstanceFactory.getInstance(BackupConfiguration.class),
                    this::downloadData, getEncryptor(),
                    InstanceFactory.getInstance(EncryptionKey.class).getPrivateKey(password));
            logPrefetcher.start();
            try (CloseableLock ignored = repository.exclusiveLock()) {
                for (String file : files) {
                    byte[] data = logPrefetcher.getLog(file);

                    try {
                        log.info("Processing log file {}", file);
                        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
                            try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                                processLogInputStream(consumer, gzipInputStream);
                            }
                        }
                        consumer.setLastSyncedLogFile(getShare(), file);
                    } catch (Exception exc) {
                        log.error("Failed to read log file " + file, exc);
                    }
                    if (!Strings.isNullOrEmpty(source) && (isShutdown() || InstanceFactory.isShutdown())) {
                        logPrefetcher.stop();
                        return;
                    }
                    processedFiles.incrementAndGet();
                }
            } finally {
                logPrefetcher.shutdown();
            }

            if (processedOperations.get() > 1000000L) {
                log.info("Optimizing repository metadata (This could take a while)");
            } else {
                log.info("Optimizing repository metadata");
            }
            flushLogging();
            repositoryReady = true;
        } finally {
            log.info("Completed reprocessing logs");
            resetStatus();
        }
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
                        log.error("Failed processing log line: " + line, exc);
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
                for (File shareFile : sharesDirectory.listFiles()) {
                    if (shareFile.isDirectory()) {
                        File configFile = new File(shareFile, SHARE_CONFIG_FILE);
                        if (configFile.exists()) {
                            try {
                                BackupActivatedShare share = BACKUP_ACTIVATED_SHARE_READER.readValue(configFile);
                                existingShares.put(shareFile.getName(), share);
                            } catch (IOException e) {
                                log.error("Failed to read share definition for {}", shareFile.getName(), e);
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

            existingShares = existingShares.entrySet().stream().filter((entry) -> {
                if (!Strings.isNullOrEmpty(entry.getValue().getShare().getTargetEmail())) {
                    if (!remainingServiceShares.containsKey(entry.getKey())) {
                        log.warn("Removing share {} that no longer exist in service", entry.getValue().getShare().getName());
                        return false;
                    }
                }
                return true;
            }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            for (Map.Entry<String, BackupShare> entry : getConfiguration().getShares().entrySet()) {
                BackupActivatedShare existingShare = existingShares.get(entry.getKey());
                if (existingShare == null) {
                    log.warn("Encountered share {} that is not activated", entry.getValue().getName());
                } else if (existingShare.getShare().equals(entry.getValue().activatedShare(getServiceManager().getSourceId(), entry.getKey()).getShare())) {
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

            remainingServiceShares.values().forEach((share) -> {
                if (!activeShares.containsKey(share.getShareId())) {
                    log.info("Removing service share {} that no longer exist in configuration", share.getName());
                    try {
                        getServiceManager().deleteShare(share.getShareId());
                    } catch (IOException e) {
                        log.error("Failed to delete share {} from service", share.getName(), e);
                    }
                }
            });

            if (existingShares.size() > 0 && getLogConsumer() instanceof MetadataRepository) {
                startOperation("Deactivating shares");
                processedOperations = new AtomicLong(0);
                totalOperations = new AtomicLong(existingShares.size());
                try {
                    MetadataRepository metadataRepository = (MetadataRepository) getLogConsumer();
                    for (String share : existingShares.keySet()) {
                        log.info("Deleting unused share {}", existingShares.get(share).getShare().getName());
                        try {
                            metadataRepository.deleteAdditionalBlock(share, null);
                        } catch (IOException e) {
                            log.error("Failed to delete share {}", existingShares.get(share).getShare().getName(), e);
                        }

                        File shareDir = new File(sharesDirectory, share);
                        deleteContents(shareDir);
                        shareDir.delete();
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

    private ShareManifestManagerImpl createShareManager(String publicKey, BackupActivatedShare share, boolean activated) throws IOException {
        return new ShareManifestManagerImpl(
                getConfiguration(),
                share.getShare().getDestination(),
                Paths.get(getManifestLocation(), "shares", publicKey).toString(),
                IOProviderFactory.getProvider(share.getShare().getDestination()),
                EncryptorFactory.getEncryptor(share.getShare().getDestination().getEncryption()),
                getRateLimitController(),
                getServiceManager(),
                getInstallationIdentity() + publicKey,
                isForceIdentity(),
                EncryptionKey.createWithPublicKey(publicKey),
                activated,
                share
        );
    }

    @Override
    public void optimizeLog(MetadataRepository existingRepository, LogConsumer logConsumer) throws IOException {
        initialize(logConsumer, true);
        flushLogging();

        if (existingLogFiles().size() > 0) {
            log.warn("Still having pending log files, can't optimize");
            return;
        }

        List<String> existingLogs = getExistingLogs();
        additionalManifestManager.startOptimizeLog();

        setDisabledFlushing(true);

        try (CloseableLock ignored = existingRepository.acquireLock()) {
            LoggingMetadataRepository copyRepository = new LoggingMetadataRepository(new NullRepository(), this, false);
            logConsumer.setLastSyncedLogFile(getShare(), null);

            internalInitialize();

            TreeMap<String, BackupActivePath> activePaths = existingRepository.getActivePaths(null);

            startOperation("Optimizing log");
            processedOperations = new AtomicLong();
            totalOperations = new AtomicLong(existingRepository.getFileCount()
                    + existingRepository.getBlockCount()
                    + existingRepository.getDirectoryCount()
                    + activePaths.size()
                    + getConfiguration().getSets().size() + 1);

            log.info("Processing blocks");
            try (CloseableStream<BackupBlock> blocks = existingRepository.allBlocks()) {
                blocks.stream().forEach((block) -> {
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
                files.stream().forEach((file) -> {
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
                dirs.stream().forEach((dir) -> {
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

            copyRepository.close();
            flushLogging();

            ScannerSchedulerImpl.updateOptimizeSchedule(existingRepository,
                    getConfiguration().getManifest().getOptimizeSchedule());

            log.info("Deleting old log files ({})", existingLogs.size());
            deleteLogFiles(existingLogs);
            additionalManifestManager.finishOptimizeLog();
        } finally {
            resetStatus();

            setDisabledFlushing(false);
        }
    }

    protected BackupBlock optimizeBlock(BackupBlock block) throws IOException {
        return block;
    }

    @Override
    public void deleteLogFiles(List<String> existingLogs) throws IOException {
        totalFiles = new AtomicLong(existingLogs.size());
        processedFiles = new AtomicLong();
        for (String oldFile : existingLogs) {
            getProvider().delete(oldFile);
            debug(() -> log.debug("Deleted {}", oldFile));
            processedFiles.incrementAndGet();
        }
    }

    @Override
    public BackupContentsAccess backupContents(Long timestamp, boolean includeDeleted) throws IOException {
        return new BackupContentsAccessImpl(InstanceFactory.getInstance(MetadataRepository.class),
                timestamp, includeDeleted);
    }

    @Override
    public BackupSearchAccess backupSearch(Long timestamp, boolean includeDeleted) throws IOException {
        return new BackupSearchAccessImpl(InstanceFactory.getInstance(MetadataRepository.class),
                backupContents(timestamp, includeDeleted),
                timestamp,
                includeDeleted);
    }

    @Override
    public void updateKeyData(EncryptionKey key) throws IOException {
        EncryptionKey publicKey = key.publicOnly();

        ENCRYPTION_KEY_WRITER.writeValue(new File(InstanceFactory.getInstance(CommandLineModule.KEY_FILE_NAME)),
                publicKey);
        uploadKeyData(publicKey);
        updateServiceSourceData(key);
    }

    private void startOperation(String operation) {
        log.info(operation);
        synchronized (operationLock) {
            this.operation = operation;
            this.operationTask = UIManager.registerTask(operation);
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
    public void activateShares(LogConsumer consumer, EncryptionKey.PrivateKey privateKey) throws IOException {
        initialize(consumer, true);

        MetadataRepository repository = (MetadataRepository) consumer;

        if (getConfiguration().getShares() != null) {
            Map<EncryptionKey, ShareManifestManagerImpl> pendingShareManagers = new HashMap<>();
            Map<String, BackupShare> pendingShares = new HashMap<>();

            for (Map.Entry<String, BackupShare> entry : getConfiguration().getShares().entrySet()) {
                if (!activeShares.containsKey(entry.getKey())) {
                    pendingShareManagers.put(EncryptionKey.createWithPublicKey(entry.getKey()),
                            createShareManager(entry.getKey(), entry.getValue().activatedShare(getServiceManager().getSourceId(), entry.getKey()), false));
                    pendingShares.put(entry.getKey(), entry.getValue());
                    if (getServiceManager().getToken() != null && getServiceManager().getSourceId() != null
                            && !Strings.isNullOrEmpty(entry.getValue().getTargetEmail())) {
                        getServiceManager().createShare(entry.getKey(), entry.getValue());
                    }
                }
            }

            if (pendingShareManagers.size() > 0) {
                startOperation("Activating shares");
                processedOperations = new AtomicLong();

                try (CloseableLock ignored = repository.acquireLock()) {
                    totalOperations = new AtomicLong(repository.getBlockCount() + repository.getFileCount()
                            + repository.getDirectoryCount());
                    log.info("Fetching existing logs");

                    Map<String, List<String>> existingLogs = new HashMap<>();
                    for (Map.Entry<EncryptionKey, ShareManifestManagerImpl> entry : pendingShareManagers.entrySet()) {
                        entry.getValue().validateIdentity();
                        existingLogs.put(entry.getKey().getPublicKey(), entry.getValue().getExistingLogs());
                        repository.setLastSyncedLogFile(entry.getKey().getPublicKey(), null);
                        entry.getValue().initialize(consumer, false);
                    }

                    ShareActivateMetadataRepository copyRepository = new ShareActivateMetadataRepository(repository,
                            this, pendingShares,
                            pendingShareManagers.entrySet().stream()
                                    .map(entry -> Map.entry(entry.getKey().getPublicKey(), entry.getValue()))
                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                    copyRepository.clear();

                    log.info("Calculating new block storage keys if needed");

                    try (CloseableStream<BackupBlock> blocks = repository.allBlocks()) {
                        blocks.stream().forEach((block) -> {
                            if (isShutdown()) {
                                throw new CancellationException();
                            }
                            for (Map.Entry<EncryptionKey, ShareManifestManagerImpl> entry : pendingShareManagers.entrySet()) {
                                processedOperations.incrementAndGet();
                                if (!BackupBlock.isSuperBlock(block.getHash())) {
                                    List<BackupBlockStorage> newStorage =
                                            block.getStorage().stream()
                                                    .map((storage) -> EncryptorFactory.getEncryptor(storage.getEncryption())
                                                            .reKeyStorage(storage, privateKey, entry.getKey()))
                                                    .toList();
                                    BackupBlockAdditional blockAdditional = BackupBlockAdditional.builder()
                                            .publicKey(entry.getKey().getPublicKey())
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
                                log.error("Failed to write file {}", file.getPath(), e);
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
                                log.error("Failed to write file {}", PathNormalizer.physicalPath(dir.getPath()), e);
                            }
                        });
                    }

                    log.info("Deleting any existing log files");

                    totalFiles = new AtomicLong(existingLogs.values().stream().map(List::size).reduce(0, Integer::sum));
                    processedFiles = new AtomicLong();
                    for (Map.Entry<EncryptionKey, ShareManifestManagerImpl> entry : pendingShareManagers.entrySet()) {
                        entry.getValue().flushLog();
                        entry.getValue().deleteLogFiles(existingLogs.get(entry.getKey().getPublicKey()));
                        processedFiles.addAndGet(existingLogs.get(entry.getKey().getPublicKey()).size());
                        entry.getValue().completeActivation();

                        activeShares.put(entry.getKey().getPublicKey(), entry.getValue());
                    }

                } catch (CancellationException exc) {
                    log.warn("Cancelled share activation");
                } finally {
                    resetStatus();
                }
            }
        }

        updateShareEncryption(privateKey);
        if (statsLogger != null) {
            statsLogger.setNeedsActivation(false);
        }
    }

    @Override
    public void updateShareEncryption(EncryptionKey.PrivateKey privateKey) throws IOException {
        boolean needActivation = false;
        Set<String> definedShares = new HashSet<>(getConfiguration().getShares().keySet());
        for (Map.Entry<String, ShareManifestManager> entry : activeShares.entrySet()) {
            definedShares.remove(entry.getKey());
            entry.getValue().updateEncryptionKeys(privateKey);
            if (!entry.getValue().getActivatedShare().isUpdatedEncryption())
                needActivation = true;
        }
        if (definedShares.size() > 0) {
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
    public void flushLog() throws IOException {
        synchronized (getLock()) {
            super.flushLog();

            if (activeShares != null) {
                for (ShareManifestManager others : activeShares.values())
                    others.flushLog();
            }
        }
    }

    @Override
    public void shutdown() throws IOException {
        synchronized (getLock()) {
            super.shutdown();
            if (activeShares != null) {
                for (ShareManifestManager others : activeShares.values())
                    others.shutdown();
            }
            while (operation != null) {
                try {
                    getLock().wait();
                } catch (InterruptedException e) {
                    log.warn("Failed to wait", e);
                }
            }
        }
    }
}
