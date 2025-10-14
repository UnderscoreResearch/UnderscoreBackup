package com.underscoreresearch.backup.ui.web.methods;

import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.google.inject.ProvisionException;
import com.underscoreresearch.backup.ui.commands.ChangePasswordCommand;
import com.underscoreresearch.backup.ui.commands.InteractiveCommand;
import com.underscoreresearch.backup.ui.commands.RebuildRepositoryCommand;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupManifest;
import com.underscoreresearch.backup.service.api.model.SharePrivateKeys;
import com.underscoreresearch.backup.service.api.model.ShareResponse;
import com.underscoreresearch.backup.service.api.model.SourceResponse;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import com.underscoreresearch.backup.ui.web.PrivateKeyRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.ParseException;
import org.takes.HttpException;
import org.takes.Request;
import org.takes.Response;
import org.takes.misc.Href;
import org.takes.rq.RqHref;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static com.underscoreresearch.backup.ui.commands.DownloadConfigCommand.storeKeyData;
import static com.underscoreresearch.backup.ui.commands.RebuildRepositoryCommand.downloadRemoteConfiguration;
import static com.underscoreresearch.backup.ui.commands.RebuildRepositoryCommand.unpackConfigData;
import static com.underscoreresearch.backup.ui.web.methods.ConfigurationPost.validateDestinations;
import static com.underscoreresearch.backup.ui.web.PrivateKeyRequest.decodePrivateKeyRequest;
import static com.underscoreresearch.backup.ui.web.methods.service.SourcesPut.destinationDecode;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE_CONFIG;
import static com.underscoreresearch.backup.configuration.CommandLineModule.expandSourceManifestDestination;
import static com.underscoreresearch.backup.configuration.CommandLineModule.getSourceConfigLocation;
import static com.underscoreresearch.backup.encryption.encryptors.PQCEncryptor.PQC_ENCRYPTION;
import static com.underscoreresearch.backup.io.IOUtils.createDirectory;
import static com.underscoreresearch.backup.io.implementation.UnderscoreBackupProvider.UB_TYPE;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_DESTINATION_READER;

/**
 * Handles HTTP POST requests for selecting backup sources in the web UI.
 * This class manages the process of selecting, validating, and switching between
 * different backup sources, including local sources, service-managed sources, and shared sources.
 */
@Slf4j
public class SourceSelectPost extends BaseWrap {
    /**
     * Creates a new SourceSelectPost instance with the specified base URL.
     *
     * @param base The base URL for the API endpoint
     */
    public SourceSelectPost(String base) {
        super(new Implementation(base));
    }

        /**
     * Retrieves the encryption key for a shared backup source.
     * Attempts to decrypt the shared private key using the user's password and encryption identity.
     *
     * @param encryptionIdentity The user's encryption identity
     * @param password The user's password for decryption
     * @param share The share response containing encrypted keys
     * @return The identity keys for the share, or null if no matching key was found
     * @throws IOException If there's an error reading or writing data
     * @throws GeneralSecurityException If there's an error with encryption/decryption
     */
    private static IdentityKeys getShareEncryptionKey(EncryptionIdentity encryptionIdentity,
                                                      String password, ShareResponse share) throws IOException, GeneralSecurityException {
        EncryptionIdentity.PrivateIdentity privateKey = encryptionIdentity.getPrivateIdentity(password);
        IdentityKeys usedPrivateKey = null;
        for (SharePrivateKeys keys : share.getPrivateKeys()) {
            try {
                IdentityKeys sharePrivateKey = encryptionIdentity.getIdentityKeysForPublicIdentity(IdentityKeys.fromString(keys.getPublicKey(), null));

                Encryptor encryptor = EncryptorFactory.getEncryptor(PQC_ENCRYPTION);

                byte[] decryptedKey = encryptor.decodeBlock(null, BaseEncoding.base64Url().decode(keys.getEncryptedKey()),
                        sharePrivateKey.getPrivateKeys(privateKey));
                if (decryptedKey.length != 32) { // 32 is the length of the x25519 private key of old shares.
                    try (ByteArrayInputStream inputStream = new ByteArrayInputStream(decryptedKey)) {
                        try (GZIPInputStream gzipStream = new GZIPInputStream(inputStream)) {
                            decryptedKey = gzipStream.readAllBytes();
                        }
                    }
                    usedPrivateKey = IdentityKeys.fromString(new String(decryptedKey, StandardCharsets.UTF_8), privateKey);
                } else {
                    usedPrivateKey = IdentityKeys.fromString("=" + Hash.encodeBytes(decryptedKey), privateKey);
                }
                try {
                    encryptionIdentity.getIdentityKeysForPublicIdentity(usedPrivateKey);
                } catch (IndexOutOfBoundsException exc) {
                    encryptionIdentity.getAdditionalKeys().add(usedPrivateKey);
                    InstanceFactory.getInstance(ManifestManager.class).updateKeyData(encryptionIdentity);
                }
                break;
            } catch (IndexOutOfBoundsException ignored) {
                // Normal case where the key did not exist.
            } catch (GeneralSecurityException e) {
                log.warn("Failed to process key", e);
            }
        }
        return usedPrivateKey;
    }

    /**
     * Downloads the configuration for a shared backup source.
     * Uses the provided private keys to decrypt and retrieve the configuration.
     *
     * @param share The share response containing destination information
     * @param usedPrivateKey The private keys to use for decryption
     * @return The configuration as a JSON string, or null if download failed
     */
        /**
     * Downloads the configuration for a shared backup source.
     * Uses the provided private keys to decrypt and retrieve the configuration.
     *
     * @param share The share response containing destination information
     * @param usedPrivateKey The private keys to use for decryption
     * @return The configuration as a JSON string, or null if download failed
     */
    private static String downloadShareConfig(ShareResponse share, IdentityKeys.PrivateKeys usedPrivateKey) {
        try {
            Encryptor encryptor = EncryptorFactory.getEncryptor(PQC_ENCRYPTION);
            BackupDestination destination = BACKUP_DESTINATION_READER.readValue(encryptor.decodeBlock(null,
                    destinationDecode(share.getDestination()),
                    usedPrivateKey));

            String sourceSharePath = share.getSourceId() + "." + share.getShareId();
            if (!UB_TYPE.equals(destination.getType())) {
                File file = new File(getSourceConfigLocation(InstanceFactory.getInstance(MANIFEST_LOCATION), sourceSharePath));
                if (file.exists()) {
                    BackupConfiguration sourceConfig = BACKUP_CONFIGURATION_READER.readValue(file);
                    destination = sourceConfig.getDestinations().get(sourceConfig.getManifest().getDestination());
                }
            }

            try {
                IOProviderFactory.getProvider(destination).checkCredentials(false);
            } catch (Exception exc) {
                writeSourceKey(share.getSourceId() + "." + share.getShareId(),
                        usedPrivateKey.getIdentity().toPublicEncryptionIdentity());

                BackupConfiguration partialConfig = BackupConfiguration.builder()
                        .destinations(Map.of("manifest", destination))
                        .manifest(BackupManifest.builder()
                                .destination("manifest")
                                .build())
                        .build();

                return BACKUP_CONFIGURATION_WRITER.writeValueAsString(partialConfig);
            }

            String config = downloadRemoteConfiguration(destination, usedPrivateKey);

            writeSourceKey(sourceSharePath, usedPrivateKey.getIdentity().toPublicEncryptionIdentity());

            return BACKUP_CONFIGURATION_WRITER.writeValueAsString(
                    expandSourceManifestDestination(BACKUP_CONFIGURATION_READER.readValue(config),
                            destination));
        } catch (Exception exc) {
            log.warn("Could not download share configuration", exc);
            return null;
        }
    }

    /**
     * Writes the encryption identity for a source to a key file.
     *
     * @param share The source or share identifier
     * @param identity The encryption identity to write
     * @throws IOException If there's an error writing the key file
     */
        /**
     * Writes the encryption identity for a source to a key file.
     *
     * @param share The source or share identifier
     * @param identity The encryption identity to write
     * @throws IOException If there's an error writing the key file
     */
    private static void writeSourceKey(String share, EncryptionIdentity identity) throws IOException {
        File keyFile = new File(CommandLineModule.getKeyFileName(share));
        createDirectory(keyFile.getParentFile(), true);
        ChangePasswordCommand.saveKeyFile(keyFile, identity);
    }

    /**
     * Downloads the configuration for a backup source.
     * Uses the provided private identity to decrypt and retrieve the configuration.
     *
     * @param source The source identifier
     * @param sourceDefinition The source response containing destination information
     * @param privateIdentity The private identity to use for decryption
     * @return The configuration as a JSON string, or null if download failed
     */
        /**
     * Downloads the configuration for a backup source.
     * Uses the provided private identity to decrypt and retrieve the configuration.
     *
     * @param source The source identifier
     * @param sourceDefinition The source response containing destination information
     * @param privateIdentity The private identity to use for decryption
     * @return The configuration as a JSON string, or null if download failed
     */
    public static String downloadSourceConfig(String source, SourceResponse sourceDefinition, EncryptionIdentity.PrivateIdentity privateIdentity) {
        try {
            IdentityKeys.PrivateKeys privateKeys = privateIdentity.getEncryptionIdentity().getPrimaryKeys().getPrivateKeys(privateIdentity);
            BackupDestination destination = BACKUP_DESTINATION_READER.readValue(unpackConfigData(
                    sourceDefinition.getEncryptionMode(), privateKeys,
                    destinationDecode(sourceDefinition.getDestination())));

            String config = downloadRemoteConfiguration(destination.sourceShareDestination(source, null), privateKeys);

            writeSourceKey(source, privateIdentity.getEncryptionIdentity());

            return BACKUP_CONFIGURATION_WRITER.writeValueAsString(
                    expandSourceManifestDestination(BACKUP_CONFIGURATION_READER.readValue(config),
                            destination));
        } catch (Exception exc) {
            log.warn("Could not download source configuration", exc);
            return null;
        }
    }

    /**
     * Validates that the provided password can decrypt the source's private key.
     *
     * @param sourceDefinition The source response containing the encrypted key
     * @param password The password to validate
     * @return The private identity if valid, or null if invalid
     */
        /**
     * Validates that the provided password can decrypt the source's private key.
     *
     * @param sourceDefinition The source response containing the encrypted key
     * @param password The password to validate
     * @return The private identity if valid, or null if invalid
     */
    public static EncryptionIdentity.PrivateIdentity validatePrivateKey(SourceResponse sourceDefinition, String password) {
        try {
            EncryptionIdentity identity = EncryptionIdentity.restoreFromString(sourceDefinition.getKey());

            return identity.getPrivateIdentity(password);
        } catch (Exception exc) {
            return null;
        }
    }

    /**
     * Implementation of the source selection functionality.
     * Handles the actual processing of source selection requests.
     */
        /**
     * Implementation of the source selection functionality.
     * Handles the actual processing of source selection requests.
     */
    private static class Implementation extends ExclusiveImplementation {
        private final String base;

        private Implementation(String base) {
            this.base = base + "/api/sources/";
        }

        /**
        * Selects a local backup source.
        * Validates the password, loads the configuration, and switches to the source.
        *
        * @param source The source identifier
        * @param password The password for decryption
        * @return The HTTP response
        */
                /**
         * Selects a local backup source.
         * Validates the password, loads the configuration, and switches to the source.
         *
         * @param source The source identifier
         * @param password The password for decryption
         * @return The HTTP response
         */
        private static Response selectLocalSource(String source, String password) {
            String config;
            InstanceFactory.reloadConfiguration(source, null, null);
            try {
                if (new File(CommandLineModule.getKeyFileName(source)).exists()) {
                    InstanceFactory.getInstance(EncryptionIdentity.class);
                } else {
                    throw new IllegalArgumentException();
                }
            } catch (ProvisionException | IllegalArgumentException exc) {
                try {
                    storeKeyData(password, source);
                    InstanceFactory.reloadConfigurationWithSource();
                } catch (ParseException | IOException exc2) {
                    InstanceFactory.reloadConfiguration(null);
                    return messageJson(403, "Invalid password provided");
                }
            }

            try {
                config = downloadRemoteConfiguration(source, password);
            } catch (Exception exc) {
                log.warn("Could not download source configuration", exc);
                InstanceFactory.reloadConfiguration(null);
                return messageJson(403, "Could not download source configuration");
            }

            return processConfig(password, config);
        }

        /**
        * Selects a service-managed backup source.
        * Retrieves the source definition from the service, validates the password,
        * downloads the configuration, and switches to the source.
        *
        * @param source The source identifier
        * @param password The password for decryption
        * @param serviceManager The service manager for API calls
        * @return The HTTP response
        * @throws IOException If there's an error communicating with the service
        */
                /**
         * Selects a service-managed backup source.
         * Retrieves the source definition from the service, validates the password,
         * downloads the configuration, and switches to the source.
         *
         * @param source The source identifier
         * @param password The password for decryption
         * @param serviceManager The service manager for API calls
         * @return The HTTP response
         * @throws IOException If there's an error communicating with the service
         */
        private static Response selectServiceSource(String source, String password, ServiceManager serviceManager) throws IOException {
            final String finalSource;
            String config;
            finalSource = source;
            SourceResponse sourceDefinition = serviceManager.call(null, (api) -> api.getSource(finalSource));

            if (sourceDefinition == null || sourceDefinition.getDestination() == null
                    || sourceDefinition.getKey() == null || sourceDefinition.getEncryptionMode() == null) {
                return messageJson(404, "Source not found");
            }

            EncryptionIdentity.PrivateIdentity privateKey = validatePrivateKey(sourceDefinition, password);
            if (privateKey == null) {
                return messageJson(403, "Invalid password provided");
            }

            config = downloadSourceConfig(source, sourceDefinition, privateKey);
            if (config == null) {
                return messageJson(403, "Could not download source configuration");
            }

            InstanceFactory.reloadConfiguration(sourceDefinition.getSourceId(), sourceDefinition.getName(), null);

            return processConfig(password, config);
        }

                /**
         * Selects a shared backup source.
         * Retrieves the share from the service, validates the password,
         * downloads the configuration, and switches to the source.
         *
         * @param fullSource The full source identifier (sourceId.shareId)
         * @param password The password for decryption
         * @param serviceManager The service manager for API calls
         * @param sourceId The source identifier
         * @param shareId The share identifier
         * @return The HTTP response
         * @throws IOException If there's an error communicating with the service
         * @throws GeneralSecurityException If there's an error with encryption/decryption
         */
        private static Response selectShareSource(String fullSource, String password, ServiceManager serviceManager,
                                                  String sourceId, String shareId)
                throws IOException, GeneralSecurityException {
            String config;
            if (!PrivateKeyRequest.validatePassword(password)) {
                return messageJson(403, "Invalid password provided");
            }

            final ShareResponse share;
            try {
                share = serviceManager.call(null, (api) -> api.getShare(sourceId, shareId));
            } catch (HttpException exc) {
                return messageJson(404, "Share not found");
            }

            EncryptionIdentity encryptionKey = InstanceFactory.getInstance(EncryptionIdentity.class);
            IdentityKeys usedPrivateKey = getShareEncryptionKey(encryptionKey, password, share);
            if (usedPrivateKey == null) {
                return messageJson(403, "No matching private key found");
            }

            config = downloadShareConfig(share, usedPrivateKey.getPrivateKeys(encryptionKey.getPrivateIdentity(password)));
            if (config == null) {
                return messageJson(403, "Could not download share configuration");
            }
            InstanceFactory.reloadConfiguration(fullSource, share.getName(), null);

            return processConfig(password, config);
        }

        /**
        * Processes the downloaded configuration.
        * Validates the configuration, updates the local configuration if needed,
        * and rebuilds the repository.
        *
        * @param password The password for decryption
        * @param config The configuration as a JSON string
        * @return The HTTP response
        */
                /**
         * Processes the downloaded configuration.
         * Validates the configuration, updates the local configuration if needed,
         * and rebuilds the repository.
         *
         * @param password The password for decryption
         * @param config The configuration as a JSON string
         * @return The HTTP response
         */
        private static Response processConfig(String password, String config) {
            BackupConfiguration sourceConfig;
            try {
                sourceConfig = InstanceFactory.getInstance(SOURCE_CONFIG, BackupConfiguration.class);

                BackupConfiguration newConfig = BACKUP_CONFIGURATION_READER.readValue(config);
                if (newConfig.getDestinations() != null) {
                    boolean anyFound = false;
                    for (Map.Entry<String, BackupDestination> entry : newConfig.getDestinations().entrySet()) {
                        if (!sourceConfig.getDestinations().containsKey(entry.getKey())) {
                            log.warn("Found new destination \"{}\" in share", entry.getKey());
                            sourceConfig.getDestinations().put(entry.getKey(), entry.getValue());
                            anyFound = true;
                        }
                    }
                    if (anyFound) {
                        ConfigurationPost.updateSourceConfiguration(BACKUP_CONFIGURATION_WRITER.writeValueAsString(sourceConfig),
                                false);
                    }
                }
            } catch (Exception exc) {
                try {
                    ConfigurationPost.updateSourceConfiguration(config, false);
                    InstanceFactory.reloadConfigurationWithSource();
                    sourceConfig = InstanceFactory.getInstance(SOURCE_CONFIG, BackupConfiguration.class);
                } catch (Exception exc2) {
                    log.error("Failed to validate source configuration file", exc);
                    InstanceFactory.reloadConfiguration(null);
                    return messageJson(403, "Source configuration not supported");
                }
            }

            try {
                validateDestinations(sourceConfig);
            } catch (Exception exc) {
                return messageJson(406, "Destinations in source are missing credentials");
            }
            InstanceFactory.reloadConfiguration(InstanceFactory.getAdditionalSource(),
                    InstanceFactory.getAdditionalSourceName(),
                    () -> RebuildRepositoryCommand.rebuildFromLog(password, true));
            return messageJson(200, "Ok");
        }

        @Override
        public Response actualAct(Request req) throws Exception {
            Href href = new RqHref.Base(req).href();
            String source = URLDecoder.decode(href.path().substring(base.length()), StandardCharsets.UTF_8);
            if ("-".equals(source)) {
                source = null;
            }
            BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);

            if (Strings.isNullOrEmpty(source)) {
                String password;
                try {
                    password = decodePrivateKeyRequest(req);
                    InstanceFactory.reloadConfiguration(null);
                    if (password != null && !PrivateKeyRequest.validatePassword(password)) {
                        return messageJson(403, "Invalid password provided");
                    }
                } catch (HttpException exc) {
                    // Intentionally ignored
                }
                InstanceFactory.reloadConfiguration(InteractiveCommand::startBackupIfAvailable);
                return messageJson(200, "Ok");
            } else {
                String password = decodePrivateKeyRequest(req);

                if (configuration.getAdditionalSources() == null || configuration.getAdditionalSources().get(source) == null) {
                    ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                    if (serviceManager.getToken() == null) {
                        return messageJson(404, "Source not found");
                    }

                    final String finalSource;
                    int shareSplitter = source.indexOf('.');
                    if (shareSplitter > 0) {
                        finalSource = source.substring(0, shareSplitter);
                        final String shareId = source.substring(shareSplitter + 1);
                        InstanceFactory.reloadConfiguration(null);
                        return selectShareSource(source, password, serviceManager, finalSource, shareId);
                    } else {
                        return selectServiceSource(source, password, serviceManager);
                    }
                } else {
                    return selectLocalSource(source, password);
                }
            }
        }

        @Override
        protected String getBusyMessage() {
            return "Switching source";
        }
    }
}
