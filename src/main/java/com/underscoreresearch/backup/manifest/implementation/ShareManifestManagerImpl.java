package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.io.IOUtils.createDirectory;
import static com.underscoreresearch.backup.manifest.implementation.ManifestManagerImpl.CONFIGURATION_FILENAME;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_ACTIVATED_SHARE_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.manifest.ShareManifestManager;
import com.underscoreresearch.backup.model.BackupActivatedShare;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.utils.AccessLock;

@Slf4j
public class ShareManifestManagerImpl extends BaseManifestManagerImpl implements ShareManifestManager {
    public static final String SHARE_CONFIG_FILE = "share.json";
    @Getter
    private final BackupActivatedShare activatedShare;
    private boolean activated;

    public ShareManifestManagerImpl(BackupConfiguration configuration,
                                    BackupDestination manifestDestination,
                                    String manifestLocation,
                                    RateLimitController rateLimitController,
                                    ServiceManager serviceManager,
                                    String installationIdentity,
                                    boolean forceIdentity,
                                    EncryptionIdentity encryptionIdentity,
                                    IdentityKeys keys,
                                    boolean activated,
                                    BackupActivatedShare activatedShare,
                                    UploadScheduler uploadScheduler) {
        super(configuration, manifestDestination, manifestLocation, rateLimitController, serviceManager,
                installationIdentity, forceIdentity, encryptionIdentity, keys, uploadScheduler);
        this.activated = activated;
        this.activatedShare = activatedShare;
    }

    private boolean isNotServiceShare() {
        return !activatedShare.getShare().getDestination().isServiceDestination();
    }

    @Override
    public void validateIdentity() {
        // Service shares are validated by their parent.
        if (isNotServiceShare()) {
            super.validateIdentity();
        }
    }

    @Override
    protected String getShare() {
        return getIdentityKeys().getKeyIdentifier();
    }

    @Override
    public void storeIdentity() {
        // Service shares are validated by their parent.
        if (isNotServiceShare()) {
            super.storeIdentity();
        }
    }

    protected void uploadPending(LogConsumer logConsumer) throws IOException {
        syncDestinationKey();

        if (activated) {
            uploadConfigurationFile();
        }

        List<File> files = existingLogFiles();

        if (!files.isEmpty()) {
            getLogConsumer().setRecoveryMode(true);
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
                        uploadLogFile(file.getAbsolutePath(), data);
                    }
                }
            } finally {
                getLogConsumer().setRecoveryMode(false);
            }
        }
    }

    @Override
    protected void addLogFile(String remoteFile) throws IOException {
        // Share log files should not be added to the manifest.
    }

    private void uploadConfigurationFile() throws IOException {
        uploadConfigData(CONFIGURATION_FILENAME,
                getConfigurationData().getBytes(StandardCharsets.UTF_8),
                true, null);
    }

    private String getConfigurationData() throws JsonProcessingException {
        BackupConfiguration strippedConfiguration = BackupConfiguration.builder()
                .destinations(getConfiguration().getDestinations().entrySet()
                        .stream()
                        .filter(entry -> activatedShare.getUsedDestinations().contains(entry.getKey()))
                        .map(entry -> Map.entry(entry.getKey(), entry.getValue().strippedDestination(getServiceManager().getSourceId(),
                                getShare())))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .properties(getConfiguration().getProperties())
                .build();
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(strippedConfiguration);
    }

    @Override
    public void completeActivation() throws IOException {
        if (!activated) {
            activated = true;
            saveShareFile();
        }
    }

    private void saveShareFile() throws IOException {
        File configLocation = Paths.get(InstanceFactory.getInstance(MANIFEST_LOCATION), "shares", getShare(),
                SHARE_CONFIG_FILE).toFile();
        createDirectory(configLocation.getParentFile(), true);
        BACKUP_ACTIVATED_SHARE_WRITER.writeValue(
                configLocation,
                activatedShare);
    }

    @Override
    public void addUsedDestinations(String destination) throws IOException {
        if (activatedShare.getUsedDestinations().add(destination)) {
            uploadConfigurationFile();
            if (activated) {
                saveShareFile();
            }
        }
    }

    @Override
    public void updateEncryptionKeys(EncryptionIdentity.PrivateIdentity privateKey) throws IOException {
        if (getServiceManager().getToken() != null && getServiceManager().getSourceId() != null && activatedShare.getShare().getTargetEmail() != null) {
            activatedShare.setUpdatedEncryption(getServiceManager().updateShareEncryption(privateKey,
                    getShare(),
                    activatedShare.getShare()));
        } else {
            activatedShare.setUpdatedEncryption(true);
        }
        saveShareFile();
    }
}
