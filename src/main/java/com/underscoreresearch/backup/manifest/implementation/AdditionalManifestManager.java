package com.underscoreresearch.backup.manifest.implementation;

import static com.underscoreresearch.backup.manifest.implementation.BaseManifestManagerImpl.IDENTITY_MANIFEST_LOCATION;
import static com.underscoreresearch.backup.manifest.implementation.BaseManifestManagerImpl.compressConfigData;
import static com.underscoreresearch.backup.manifest.implementation.OptimizingManifestManager.CONFIGURATION_FILENAME;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_WRITER;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.ParseException;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;

@Slf4j
public class AdditionalManifestManager {
    @Getter
    @RequiredArgsConstructor
    private static class Destination {
        private final BackupDestination destination;
        private final IOIndex provider;
        @Setter
        private List<String> existingFiles;
    }
    private final Map<String, Destination> additionalProviders = new HashMap<>();
    private final RateLimitController rateLimitController;

    public AdditionalManifestManager(BackupConfiguration configuration, RateLimitController rateLimitController) {
        if (configuration.getManifest() != null && configuration.getManifest().getAdditionalDestinations() != null) {
            for (String additionalDestination : configuration.getManifest().getAdditionalDestinations()) {
                BackupDestination destination = configuration.getDestinations().get(additionalDestination);
                additionalProviders.put(additionalDestination,
                        new Destination(destination, (IOIndex) IOProviderFactory.getProvider(destination)));
            }
        }
        this.rateLimitController = rateLimitController;
    }

    public void startOptimizeLog() throws IOException {
        for (Map.Entry<String, Destination> entry : additionalProviders.entrySet()) {
            entry.getValue().setExistingFiles(entry.getValue().getProvider().availableLogs(null));
        }
    }

    public void finishOptimizeLog() throws IOException {
        for (Map.Entry<String, Destination> entry : additionalProviders.entrySet()) {
            List<String> oldFiles = entry.getValue().getExistingFiles();
            if (oldFiles != null) {
                for (String file : oldFiles) {
                    entry.getValue().getProvider().delete(file);
                }
                entry.getValue().setExistingFiles(null);
            }
        }
    }

    public void uploadConfigurationData(String filename, byte[] data, byte[] unencryptedData,
                                        Encryptor encryptor, EncryptionKey encryptionKey) throws IOException {
        for (Map.Entry<String, Destination> entry : additionalProviders.entrySet()) {
            byte[] useData = data;
            if (encryptor != null) {
                Encryptor neededEncryptor = EncryptorFactory.getEncryptor(entry.getValue().getDestination().getEncryption());
                if (neededEncryptor != encryptor) {
                    useData = neededEncryptor.encryptBlock(null, unencryptedData, encryptionKey);
                }
            }
            uploadConfigurationData(filename, useData, entry.getValue());
        }
    }

    private void uploadConfigurationData(String filename, byte[] data, Destination destination) throws IOException {
        destination.getProvider().upload(filename, data);
        rateLimitController.acquireUploadPermits(destination.getDestination(), data.length);
    }

    public void uploadConfiguration(BackupConfiguration configuration, EncryptionKey encryptionKey) throws IOException {
        if (configuration.getManifest() != null && configuration.getManifest().getAdditionalDestinations() != null) {
            BackupConfiguration copy = configuration.toBuilder()
                    .manifest(configuration.getManifest().toBuilder().build())
                    .build();
            List<String> allDestinations = Lists.newArrayList(configuration.getManifest().getAdditionalDestinations());
            allDestinations.add(configuration.getManifest().getDestination());
            for (Map.Entry<String, Destination> entry : additionalProviders.entrySet()) {
                copy.getManifest().setDestination(entry.getKey());
                copy.getManifest().setAdditionalDestinations(allDestinations.stream()
                        .filter(s -> !s.equals(entry.getKey())).collect(Collectors.toList()));
                byte[] rawData = compressConfigData(new ByteArrayInputStream(
                        BACKUP_CONFIGURATION_WRITER.writeValueAsBytes(copy)));
                Encryptor encryptor = EncryptorFactory.getEncryptor(entry.getValue().getDestination().getEncryption());
                byte[] data = encryptor.encryptBlock(null, rawData, encryptionKey);
                uploadConfigurationData(CONFIGURATION_FILENAME, data, rawData, encryptor, encryptionKey);
            }
        }
    }

    public void storeIdentity(String identity) {
        for (Map.Entry<String, Destination> entry : additionalProviders.entrySet()) {
            storeIdentity(entry.getValue(), identity);
        }
    }

    private void storeIdentity(Destination destination, String identity) {
        byte[] data = identity.getBytes(StandardCharsets.UTF_8);
        try {
            destination.getProvider().upload(IDENTITY_MANIFEST_LOCATION, data);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to save identity to target: %s", e.getMessage()), e);
        }
        rateLimitController.acquireUploadPermits(destination.getDestination(), data.length);
    }

    public void validateIdentity(String identity, boolean forceIdentity) {
        for (Map.Entry<String, Destination> entry : additionalProviders.entrySet()) {
            byte[] data;
            try {
                debug(() -> log.debug("Validating manifest installation identity"));
                data = entry.getValue().getProvider().download(IDENTITY_MANIFEST_LOCATION);
                rateLimitController.acquireDownloadPermits(entry.getValue().getDestination(), data.length);
            } catch (Exception exc) {
                storeIdentity(entry.getValue(), identity);
                return;
            }
            String destinationIdentity = new String(data, StandardCharsets.UTF_8);
            if (!destinationIdentity.equals(identity)) {
                if (forceIdentity) {
                    log.error("Another installation of UnderscoreBackup is already writing to this manifest "
                            + "destination. Proceeding anyway because of --force flag on command line. Consider doing "
                            + "a log optimize operation to avoid data corruption");
                    storeIdentity(entry.getValue(), identity);
                } else {
                    throw new RuntimeException(
                            new ParseException("Another installation of UnderscoreBackup is already writing to this manifest "
                                    + "destination. To take over backing up from this installation execute a "
                                    + "rebuild-repository operation or reset the local configuration under settings in the UI"));
                }
            }
        }
    }
}
