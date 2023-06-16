package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.manifest.implementation.OptimizingManifestManager.CONFIGURATION_FILENAME;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_ACTIVATED_SHARE_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.ByteArrayInputStream;
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
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.manifest.ShareManifestManager;
import com.underscoreresearch.backup.model.BackupActivatedShare;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.utils.AccessLock;
import com.underscoreresearch.backup.utils.NonClosingInputStream;

@Slf4j
public class ShareManifestManagerImpl extends BaseManifestManagerImpl implements ShareManifestManager {
    public static final String SHARE_CONFIG_FILE = "share.json";
    @Getter
    private final BackupActivatedShare activatedShare;
    private boolean activated;

    public ShareManifestManagerImpl(BackupConfiguration configuration,
                                    BackupDestination manifestDestination,
                                    String manifestLocation,
                                    IOProvider provider,
                                    Encryptor encryptor,
                                    RateLimitController rateLimitController,
                                    ServiceManager serviceManager,
                                    String installationIdentity,
                                    boolean forceIdentity,
                                    EncryptionKey publicKey,
                                    boolean activated,
                                    BackupActivatedShare activatedShare) {
        super(configuration, manifestDestination, manifestLocation, provider, encryptor,
                rateLimitController, serviceManager, installationIdentity, forceIdentity, publicKey);
        this.activated = activated;
        this.activatedShare = activatedShare;
    }

    private boolean serviceShare() {
        return activatedShare.getShare().getDestination().isServiceDestination();
    }

    @Override
    public void validateIdentity() {
        // Service shares are validated by their parent.
        if (!serviceShare()) {
            super.validateIdentity();
        }
    }

    @Override
    protected String getShare() {
        return getPublicKey().getPublicKey();
    }

    @Override
    public void storeIdentity() {
        // Service shares are validated by their parent.
        if (!serviceShare()) {
            super.storeIdentity();
        }
    }

    protected void uploadPending(LogConsumer logConsumer) throws IOException {
        syncDestinationKey();

        if (activated) {
            uploadConfigurationFile();
        }

        List<File> files = existingLogFiles();

        if (files.size() > 0) {
            for (File file : files) {
                try (AccessLock lock = new AccessLock(file.getAbsolutePath())) {
                    if (lock.tryLock(true)) {
                        InputStream stream = new NonClosingInputStream(Channels
                                .newInputStream(lock.getLockedChannel()));
                        uploadLogFile(file.getAbsolutePath(), stream);
                    } else {
                        log.warn("Log file {} locked by other process", file.getAbsolutePath());
                    }
                }

                file.delete();
            }
        }
    }

    private void uploadConfigurationFile() throws IOException {
        uploadConfigData(CONFIGURATION_FILENAME,
                new ByteArrayInputStream(getConfigurationData().getBytes(StandardCharsets.UTF_8)),
                true);
    }

    private String getConfigurationData() throws JsonProcessingException {
        BackupConfiguration strippedConfiguration = BackupConfiguration.builder()
                .destinations(getConfiguration().getDestinations().entrySet()
                        .stream()
                        .filter(entry -> activatedShare.getUsedDestinations().contains(entry.getKey()))
                        .map(entry -> Map.entry(entry.getKey(), entry.getValue().strippedDestination(getServiceManager().getSourceId(),
                                getPublicKey().getPublicKey())))
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
        File configLocation = Paths.get(InstanceFactory.getInstance(MANIFEST_LOCATION), "shares", getPublicKey().getPublicKey(),
                SHARE_CONFIG_FILE).toFile();
        configLocation.getParentFile().mkdirs();
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
    public void updateEncryptionKeys(EncryptionKey.PrivateKey privateKey) throws IOException {
        if (getServiceManager().getToken() != null && getServiceManager().getSourceId() != null) {
            activatedShare.setUpdatedEncryption(getServiceManager().updateShareEncryption(privateKey,
                    getPublicKey().getPublicKey(),
                    activatedShare.getShare()));
        } else {
            activatedShare.setUpdatedEncryption(true);
        }
        saveShareFile();
    }
}
