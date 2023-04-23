package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.cli.commands.ConfigureCommand.getConfigurationUrl;
import static com.underscoreresearch.backup.cli.web.ResetDelete.deleteContents;
import static com.underscoreresearch.backup.configuration.CommandLineModule.CONFIG_DATA;
import static com.underscoreresearch.backup.manifest.implementation.ShareManifestManagerImpl.SHARE_CONFIG_FILE;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.LogUtil.readableEta;
import static com.underscoreresearch.backup.utils.LogUtil.readableNumber;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_ACTIVATED_SHARE_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_DESTINATION_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_WRITER;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.channels.Channels;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

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
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
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
import com.underscoreresearch.backup.model.BackupActivatedShare;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockAdditional;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupShare;
import com.underscoreresearch.backup.service.api.model.ShareResponse;
import com.underscoreresearch.backup.service.api.model.SourceRequest;
import com.underscoreresearch.backup.utils.AccessLock;
import com.underscoreresearch.backup.utils.NonClosingInputStream;
import com.underscoreresearch.backup.utils.StatusLine;
import com.underscoreresearch.backup.utils.StatusLogger;

@Slf4j
public class ManifestManagerImpl extends BaseManifestManagerImpl implements ManifestManager, StatusLogger {
    private final String source;
    private Map<String, ShareManifestManager> activeShares;

    private Object operationLock = new Object();
    private String operation;
    private AtomicLong totalFiles;
    private AtomicLong totalOperations;
    private AtomicLong processedFiles;
    private AtomicLong processedOperations;
    private Stopwatch operationDuration;

    public ManifestManagerImpl(BackupConfiguration configuration,
                               String manifestLocation,
                               IOProvider provider,
                               Encryptor encryptor,
                               RateLimitController rateLimitController,
                               ServiceManager serviceManager,
                               String installationIdentity,
                               String source,
                               boolean forceIdentity,
                               EncryptionKey publicKey)
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
    }

    protected void uploadPending(LogConsumer logConsumer) throws IOException {
        EncryptionKey encryptionKey = InstanceFactory.getInstance(EncryptionKey.class);
        startOperation("Upload pending");
        try {
            processedFiles = new AtomicLong(0);
            totalFiles = new AtomicLong(2);
            try {
                EncryptionKey existingPublicKey = ENCRYPTION_KEY_READER
                        .readValue(getProvider().download("publickey.json"));
                if (!encryptionKey.getSalt().equals(existingPublicKey.getSalt())
                        || !encryptionKey.getPublicKey().equals(existingPublicKey.getPublicKey())) {
                    throw new IOException("Public key that exist in destination does not match current public key");
                }
            } catch (Exception exc) {
                if (!IOUtils.hasInternet()) {
                    throw exc;
                }
                log.info("Public key does not exist");
                uploadConfigData("publickey.json",
                        new ByteArrayInputStream(ENCRYPTION_KEY_WRITER.writeValueAsBytes(encryptionKey)),
                        false);
            } finally {
                processedFiles.incrementAndGet();
            }

            uploadConfigData("configuration.json",
                    new ByteArrayInputStream(InstanceFactory.getInstance(CONFIG_DATA).getBytes(Charset.forName("UTF-8"))),
                    true);
            processedFiles.incrementAndGet();

            updateServiceSourceData(encryptionKey);

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
            String keyData = ENCRYPTION_KEY_WRITER.writeValueAsString(encryptionKey.publicOnly());
            getServiceManager().call(null, (api) -> api.updateSource(getServiceManager().getSourceId(),
                    new SourceRequest()
                            .identity(getInstallationIdentity())
                            .name(getServiceManager().getSourceName())
                            .destination(destinationData)
                            .applicationUrl(getApplicationUrl())
                            .encryptionMode(destination.getEncryption())
                            .key(keyData)
                            .version(VersionCommand.getVersionEdition())
                            .sharingKey(encryptionKey.getSharingPublicKey())));
        }
    }

    private String getApplicationUrl() {
        try {
            return getConfigurationUrl();
        } catch (Exception exc) {
            return null;
        }
    }

    public void validateIdentity() {
        if (Strings.isNullOrEmpty(source)) {
            super.validateIdentity();
        }
    }

    public void storeIdentity() {
        if (Strings.isNullOrEmpty(source)) {
            super.storeIdentity();
        }
    }

    @Override
    public void replayLog(LogConsumer consumer, String password) throws IOException {
        storeIdentity();

        startOperation("Replay log");

        if (consumer.lastSyncedLogFile() != null) {
            log.info("Continuing rebuild from after file {}", getLogConsumer().lastSyncedLogFile());
        }

        try {
            IOIndex index = (IOIndex) getProvider();
            List<String> files = index.availableLogs(consumer.lastSyncedLogFile());

            totalFiles = new AtomicLong(files.size());
            processedFiles = new AtomicLong(0L);
            processedOperations = new AtomicLong(0L);

            for (String file : files) {
                byte[] data = getProvider().download(file);

                try {
                    log.info("Processing log file {}", file);
                    byte[] unencryptedData = getEncryptor().decodeBlock(null, data,
                            InstanceFactory.getInstance(EncryptionKey.class).getPrivateKey(password));
                    try (ByteArrayInputStream inputStream = new ByteArrayInputStream(unencryptedData)) {
                        try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                            processLogInputStream(consumer, gzipInputStream);
                        }
                    }
                    consumer.setLastSyncedLogFile(file);
                } catch (Exception exc) {
                    log.error("Failed to read log file " + file, exc);
                }
                if (!Strings.isNullOrEmpty(source) && (isShutdown() || InstanceFactory.isShutdown())) {
                    return;
                }
                processedFiles.incrementAndGet();
            }

            if (processedOperations.get() > 1000000L) {
                log.info("Optimizing repository metadata (This could take a while)");
            } else {
                log.info("Optimizing repository metadata");
            }
            flushLogging();
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

    @NotNull
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

        setDisabledFlushing(true);

        try (CloseableLock ignored = existingRepository.acquireLock()) {
            LoggingMetadataRepository copyRepository = new LoggingMetadataRepository(new NullRepository(), this, false);

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
            existingRepository.allBlocks().forEach((block) -> {
                processedOperations.incrementAndGet();
                try {
                    copyRepository.addBlock(block);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            log.info("Processing files");
            existingRepository.allFiles(true).forEach((file) -> {
                processedOperations.incrementAndGet();
                try {
                    copyRepository.addFile(file);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            log.info("Processing directory contents");
            existingRepository.allDirectories(true).forEach((dir) -> {
                processedOperations.incrementAndGet();
                try {
                    copyRepository.addDirectory(dir);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
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
        } finally {
            resetStatus();

            setDisabledFlushing(false);
        }
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
        uploadKeyData(key);
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

    public boolean isBusy() {
        return operation != null;
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
            Map<EncryptionKey, ShareManifestManager> pendingShareManagers = new HashMap<>();
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
                    for (Map.Entry<EncryptionKey, ShareManifestManager> entry : pendingShareManagers.entrySet()) {
                        entry.getValue().validateIdentity();
                        existingLogs.put(entry.getKey().getPublicKey(), entry.getValue().getExistingLogs());
                    }

                    ShareActivateMetadataRepository copyRepository = new ShareActivateMetadataRepository(repository,
                            this, pendingShares,
                            pendingShareManagers.entrySet().stream()
                                    .map(entry -> Map.entry(entry.getKey().getPublicKey(), entry.getValue()))
                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                    copyRepository.clear();

                    log.info("Calculating new block storage keys if needed");

                    repository.allBlocks().forEach((block) -> {
                        if (isShutdown()) {
                            throw new CancellationException();
                        }
                        for (Map.Entry<EncryptionKey, ShareManifestManager> entry : pendingShareManagers.entrySet()) {
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
                                    blockAdditional.getProperties().add(newStorage
                                            .get(i)
                                            .getProperties()
                                            .entrySet()
                                            .stream()
                                            .filter((check) -> !check.getValue().equals(oldProperties.get(check.getKey())))
                                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                                }
                                try {
                                    repository.addAdditionalBlock(blockAdditional);
                                } catch (IOException e) {
                                    throw new RuntimeException("Failed to create share", e);
                                }
                            }
                        }
                    });

                    log.info("Writing files to shares");

                    repository.allFiles(true).forEach(file -> {
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

                    log.info("Writing directories to shares");

                    repository.allDirectories(true).forEach(dir -> {
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

                    log.info("Deleting any existing log files");

                    totalFiles = new AtomicLong(existingLogs.values().stream().map(List::size).reduce(0, Integer::sum));
                    processedFiles = new AtomicLong();
                    for (Map.Entry<EncryptionKey, ShareManifestManager> entry : pendingShareManagers.entrySet()) {
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
    }

    @Override
    public void updateShareEncryption(EncryptionKey.PrivateKey privateKey) throws IOException {
        for (Map.Entry<String, ShareManifestManager> entry : activeShares.entrySet()) {
            if (!Strings.isNullOrEmpty(entry.getValue().getActivatedShare().getShare().getTargetEmail())) {
                entry.getValue().updateEncryptionKeys(privateKey);
            }
        }
    }

    @Override
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

    public void flushLog() throws IOException {
        synchronized (getLock()) {
            super.flushLog();

            if (activeShares != null) {
                for (ShareManifestManager others : activeShares.values())
                    others.flushLog();
            }
        }
    }

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

    @Override
    public boolean temporal() {
        return false;
    }

    @Override
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
                            readableNumber(processedFiles.get()) + " / " + readableNumber(totalFiles.get())));
                }
                if (processedOperations != null) {
                    if (totalOperations != null) {
                        if (operationDuration != null) {
                            ret.add(new StatusLine(getClass(), code + "_PROCESSED_OPERATIONS", operation + " processed operations",
                                    processedOperations.get(), totalOperations.get(),
                                    readableNumber(processedOperations.get()) + " / " + readableNumber(totalOperations.get())
                                            + readableEta(processedOperations.get(), totalOperations.get(),
                                            operationDuration.elapsed())));
                        } else {
                            ret.add(new StatusLine(getClass(), code + "_PROCESSED_OPERATIONS", operation + " processed operations",
                                    processedOperations.get(), totalOperations.get(),
                                    readableNumber(processedOperations.get()) + " / " + readableNumber(totalOperations.get())));
                        }
                    } else {
                        ret.add(new StatusLine(getClass(), code + "_PROCESSED_OPERATIONS", operation + " processed operations",
                                processedOperations.get()));
                    }
                }

                if (processedOperations != null && operationDuration != null) {
                    int elapsedMilliseconds = (int) operationDuration.elapsed(TimeUnit.MILLISECONDS);
                    if (elapsedMilliseconds > 0) {
                        long throughput = 1000 * processedOperations.get() / elapsedMilliseconds;
                        ret.add(new StatusLine(getClass(), code + "_THROUGHPUT", operation + " throughput",
                                throughput, readableNumber(throughput) + " operations/s"));
                    }
                }
            }
        }
        return ret;
    }
}
