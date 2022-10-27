package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_ACTIVATED_SHARE_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ShareManifestManager;
import com.underscoreresearch.backup.model.BackupActivatedShare;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.utils.AccessLock;
import com.underscoreresearch.backup.utils.NonClosingInputStream;

@Slf4j
public class ShareManifestManagerImpl extends BaseManifestManagerImpl implements ShareManifestManager {
    public static final String SHARE_CONFIG_FILE = "share.json";
    private final BackupActivatedShare activatedShare;
    private boolean activated;

    public ShareManifestManagerImpl(BackupConfiguration configuration,
                                    BackupDestination manifestDestination,
                                    String manifestLocation,
                                    IOProvider provider,
                                    Encryptor encryptor,
                                    RateLimitController rateLimitController,
                                    String installationIdentity,
                                    boolean forceIdentity,
                                    EncryptionKey publicKey,
                                    boolean activated,
                                    BackupActivatedShare activatedShare) {
        super(configuration, manifestDestination, manifestLocation, provider, encryptor,
                rateLimitController, installationIdentity, forceIdentity, publicKey);
        this.activated = activated;
        this.activatedShare = activatedShare;
    }

    protected void uploadPending(LogConsumer logConsumer) throws IOException {
        try {
            EncryptionKey existingPublicKey = ENCRYPTION_KEY_READER.readValue(getProvider().download("publickey.json"));
            if (!getPublicKey().getPublicKey().equals(existingPublicKey.getPublicKey())) {
                throw new IOException("Public key that exist in destination does not match current public key");
            }
        } catch (Exception exc) {
            if (!IOUtils.hasInternet()) {
                throw exc;
            }
            log.info("Public key does not exist");
            uploadKeyData(getPublicKey());
        }

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
        uploadConfigData("configuration.json",
                new ByteArrayInputStream(getConfigurationData().getBytes(Charset.forName("UTF-8"))),
                true);
    }

    private String getConfigurationData() throws JsonProcessingException {
        BackupConfiguration strippedConfiguration = BackupConfiguration.builder()
                .destinations(getConfiguration().getDestinations().entrySet()
                        .stream()
                        .filter(entry -> activatedShare.getUsedDestinations().contains(entry.getKey()))
                        .map(entry -> Map.entry(entry.getKey(), stripDestination(entry.getValue())))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .properties(getConfiguration().getProperties())
                .build();
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(strippedConfiguration);
    }

    private BackupDestination stripDestination(BackupDestination value) {
        return BackupDestination.builder()
                .type(value.getType())
                .endpointUri(value.getEndpointUri())
                .properties(value.getProperties())
                .encryption(value.getEncryption())
                .errorCorrection(value.getErrorCorrection())
                .properties(value.getProperties())
                .build();
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
}
