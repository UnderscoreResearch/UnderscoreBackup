package com.underscoreresearch.backup.manifest.implementation;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.IOProviderUtil;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.manifest.implementation.BaseManifestManagerImpl.IDENTITY_MANIFEST_LOCATION;
import static com.underscoreresearch.backup.manifest.implementation.BaseManifestManagerImpl.compressConfigData;
import static com.underscoreresearch.backup.manifest.implementation.ManifestManagerImpl.CONFIGURATION_FILENAME;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_WRITER;

@Slf4j
public class AdditionalManifestManager {
    private final Map<String, Destination> additionalProviders = new HashMap<>();
    private final RateLimitController rateLimitController;
    private final UploadScheduler uploadScheduler;
    private final AtomicInteger uploadCount = new AtomicInteger();
    private final ExecutorService uploadExecutor;

    public AdditionalManifestManager(BackupConfiguration configuration,
                                     RateLimitController rateLimitController,
                                     UploadScheduler uploadScheduler) {
        if (configuration.getManifest() != null && configuration.getManifest().getAdditionalDestinations() != null) {
            for (String additionalDestination : configuration.getManifest().getAdditionalDestinations()) {
                BackupDestination destination = configuration.getDestinations().get(additionalDestination);
                additionalProviders.put(additionalDestination,
                        new Destination(destination, (IOIndex) IOProviderFactory.getProvider(destination)));
            }
        }
        this.rateLimitController = rateLimitController;
        this.uploadScheduler = uploadScheduler;

        uploadExecutor = Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder().setNameFormat("AdditionalManifest-Upload").build());
    }

    public void finishOptimizeLog(String lastExistingLog, AtomicLong totalFiles, AtomicLong processedFiles) throws IOException {
        for (Map.Entry<String, Destination> entry : additionalProviders.entrySet()) {
            BaseManifestManagerImpl.deleteLogFiles(lastExistingLog, entry.getValue().getProvider(), totalFiles, processedFiles);
        }
    }

    public void cancelOptimizeLog(String lastExistingLog, AtomicLong totalFiles, AtomicLong processedFiles) throws IOException {
        for (Map.Entry<String, Destination> entry : additionalProviders.entrySet()) {
            BaseManifestManagerImpl.deleteNewLogFiles(lastExistingLog, entry.getValue().getProvider(), totalFiles, processedFiles);
        }
    }

    public void uploadConfigurationData(String filename, byte[] data, byte[] unencryptedData,
                                        Encryptor encryptor, IdentityKeys encryptionKey,
                                        Runnable success) throws IOException {
        for (Map.Entry<String, Destination> entry : additionalProviders.entrySet()) {
            byte[] useData = data;
            if (encryptor != null) {
                Encryptor neededEncryptor = EncryptorFactory.getEncryptor(entry.getValue().getDestination().getEncryption());
                if (neededEncryptor != encryptor) {
                    try {
                        useData = neededEncryptor.encryptBlock(null, unencryptedData, encryptionKey);
                    } catch (GeneralSecurityException e) {
                        throw new IOException(e);
                    }
                }
            }
            uploadConfigurationData(filename, useData, entry.getValue(), success);
        }
    }

    private void uploadConfigurationData(String filename, byte[] data, Destination destination,
                                         Runnable success) {
        uploadCount.incrementAndGet();
        uploadExecutor.submit(() -> uploadScheduler.scheduleUpload(destination.getDestination(), filename, data, (key) -> {
            if (key != null && success != null)
                success.run();
            synchronized (uploadCount) {
                uploadCount.decrementAndGet();
                uploadCount.notifyAll();
            }
        }));
    }

    public void uploadConfiguration(BackupConfiguration configuration, IdentityKeys identityKeys) throws IOException {
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
                byte[] rawData = compressConfigData(BACKUP_CONFIGURATION_WRITER.writeValueAsBytes(copy));
                Encryptor encryptor = EncryptorFactory.getEncryptor(entry.getValue().getDestination().getEncryption());
                byte[] data = new byte[0];
                try {
                    data = encryptor.encryptBlock(null, rawData, identityKeys);
                } catch (GeneralSecurityException e) {
                    throw new IOException(e);
                }
                uploadConfigurationData(CONFIGURATION_FILENAME, data, rawData, encryptor, identityKeys, null);
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
            IOProviderUtil.upload(destination.getProvider(), IDENTITY_MANIFEST_LOCATION, data);
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
                data = IOProviderUtil.download(entry.getValue().getProvider(), IDENTITY_MANIFEST_LOCATION);
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

    public int count() {
        return additionalProviders.size();
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

    public void shutdown() {
        uploadExecutor.shutdown();
        waitUploads();
    }

    @Getter
    @RequiredArgsConstructor
    private static class Destination {
        private final BackupDestination destination;
        private final IOIndex provider;
    }
}
