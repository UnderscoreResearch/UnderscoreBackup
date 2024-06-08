package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.cli.commands.DownloadConfigCommand.storeKeyData;
import static com.underscoreresearch.backup.cli.commands.RebuildRepositoryCommand.downloadRemoteConfiguration;
import static com.underscoreresearch.backup.cli.commands.RebuildRepositoryCommand.unpackConfigData;
import static com.underscoreresearch.backup.cli.web.ConfigurationPost.validateDestinations;
import static com.underscoreresearch.backup.cli.web.PrivateKeyRequest.decodePrivateKeyRequest;
import static com.underscoreresearch.backup.cli.web.service.SourcesPut.destinationDecode;
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.ParseException;
import org.takes.HttpException;
import org.takes.Request;
import org.takes.Response;
import org.takes.misc.Href;
import org.takes.rq.RqHref;

import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.google.inject.ProvisionException;
import com.underscoreresearch.backup.cli.commands.ChangePasswordCommand;
import com.underscoreresearch.backup.cli.commands.InteractiveCommand;
import com.underscoreresearch.backup.cli.commands.RebuildRepositoryCommand;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
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

@Slf4j
public class SourceSelectPost extends BaseWrap {
    public SourceSelectPost(String base) {
        super(new Implementation(base));
    }

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
                if (decryptedKey.length > 53) { // 53 is the length of the string base32 encoded private key prepended by =
                    try (ByteArrayInputStream inputStream = new ByteArrayInputStream(decryptedKey)) {
                        try (GZIPInputStream gzipStream = new GZIPInputStream(inputStream)) {
                            decryptedKey = gzipStream.readAllBytes();
                        }
                    }
                }
                usedPrivateKey = IdentityKeys.fromString(new String(decryptedKey, StandardCharsets.UTF_8), privateKey);
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

    private static void writeSourceKey(String share, EncryptionIdentity identity) throws IOException {
        File keyFile = new File(CommandLineModule.getKeyFileName(share));
        createDirectory(keyFile.getParentFile(), true);
        ChangePasswordCommand.saveKeyFile(keyFile, identity);
    }

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

    public static EncryptionIdentity.PrivateIdentity validatePrivateKey(SourceResponse sourceDefinition, String password) {
        try {
            EncryptionIdentity identity = EncryptionIdentity.restoreFromString(sourceDefinition.getKey());

            return identity.getPrivateIdentity(password);
        } catch (Exception exc) {
            return null;
        }
    }

    private static class Implementation extends ExclusiveImplementation {
        private final String base;

        private Implementation(String base) {
            this.base = base + "/api/sources/";
        }

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
