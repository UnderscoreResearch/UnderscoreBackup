package com.underscoreresearch.backup.ui.web.methods.service;

import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.io.BaseEncoding;
import com.underscoreresearch.backup.ui.commands.ChangePasswordCommand;
import com.underscoreresearch.backup.ui.commands.RebuildRepositoryCommand;
import com.underscoreresearch.backup.ui.commands.VersionCommand;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import com.underscoreresearch.backup.ui.web.PrivateKeyRequest;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.IOProviderUtil;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.service.api.BackupApi;
import com.underscoreresearch.backup.service.api.invoker.ApiException;
import com.underscoreresearch.backup.service.api.model.SourceRequest;
import com.underscoreresearch.backup.service.api.model.SourceResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.takes.Request;
import org.takes.Response;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Objects;

import static com.underscoreresearch.backup.ui.commands.ConfigureCommand.getConfigurationUrl;
import static com.underscoreresearch.backup.ui.commands.GenerateKeyCommand.getDefaultEncryptionFileName;
import static com.underscoreresearch.backup.ui.commands.RebuildRepositoryCommand.downloadRemoteConfiguration;
import static com.underscoreresearch.backup.ui.commands.RebuildRepositoryCommand.unpackConfigData;
import static com.underscoreresearch.backup.ui.web.methods.ConfigurationPost.updateConfiguration;
import static com.underscoreresearch.backup.ui.web.PsAuthedContent.decodeRequestBody;
import static com.underscoreresearch.backup.ui.web.methods.service.CreateSecretPut.encryptionIdentity;
import static com.underscoreresearch.backup.io.IOUtils.deleteFile;
import static com.underscoreresearch.backup.manifest.implementation.BaseManifestManagerImpl.PUBLICKEY_FILENAME;
import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.sendApiFailureOn;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_DESTINATION_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Endpoint for updating backup sources in the service.
 * This class handles the updating of existing backup sources in the backup service.
 */
@Slf4j
public class SourcesPut extends BaseWrap {
    private static final ObjectReader READER = MAPPER.readerFor(UpdateSourceRequest.class);

    /**
     * Constructor for the SourcesPut endpoint.
     */
    public SourcesPut() {
        super(new Implementation());
    }

    /**
     * Decodes the destination data from a source definition.
     * 
     * @param sourceDefinition The source definition containing the destination
     * @param privateKey The private key to decrypt the destination data
     * @return The decoded backup destination
     * @throws IOException If there's an error decoding the destination
     */
    public static BackupDestination decodeDestination(SourceResponse sourceDefinition, IdentityKeys.PrivateKeys privateKey) throws IOException {
        return BACKUP_DESTINATION_READER.readValue(unpackConfigData(
                sourceDefinition.getEncryptionMode(), privateKey,
                destinationDecode(sourceDefinition.getDestination())));
    }

    /**
     * Retrieves the encryption key from a source.
     * 
     * @param serviceManager The service manager instance
     * @param sourceDefinition The source definition
     * @param privateKey The private key to access the source
     * @return The encryption identity from the source
     * @throws IOException If there's an error retrieving the key
     */
    public static EncryptionIdentity getEncryptionKey(ServiceManager serviceManager,
                                                      SourceResponse sourceDefinition,
                                                      IdentityKeys.PrivateKeys privateKey) throws IOException {
        String oldSourceId = serviceManager.getSourceId();
        try {
            serviceManager.setSourceId(sourceDefinition.getSourceId());
            IOProvider provider = IOProviderFactory.getProvider(decodeDestination(sourceDefinition, privateKey));
            byte[] keyData = IOProviderUtil.download(provider, PUBLICKEY_FILENAME);
            try {
                return EncryptionIdentity.restoreFromString(new String(keyData, StandardCharsets.UTF_8));
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            }
        } finally {
            serviceManager.setSourceId(oldSourceId);
        }
    }

    /**
     * Restores a backup from a source.
     * 
     * @param sourceName The name of the source
     * @param password The password to decrypt the source
     * @param serviceManager The service manager instance
     * @param sourceDefinition The source definition
     * @param identity The identity of the current installation
     * @param privateIdentity The private identity to use for restoration
     * @return True if the restore was successful, false otherwise
     * @throws IOException If there's an error during restoration
     */
    public static boolean restoreFromSource(String sourceName,
                                            String password,
                                            ServiceManager serviceManager,
                                            SourceResponse sourceDefinition,
                                            String identity,
                                            EncryptionIdentity.PrivateIdentity privateIdentity) throws IOException {
        IdentityKeys.PrivateKeys privateKeys = privateIdentity.getEncryptionIdentity().getPrimaryKeys().getPrivateKeys(privateIdentity);
        BackupDestination destination = decodeDestination(sourceDefinition, privateKeys);

        File privateKeyFile = getDefaultEncryptionFileName(InstanceFactory
                .getInstance(CommandLine.class));
        try {
            // Kind of ugly but we need to do this to be able to download config from service.
            String oldSourceId = serviceManager.getSourceId();
            String config;
            try {
                serviceManager.setSourceId(sourceDefinition.getSourceId());

                // Need to download the full public key from the source since the service key does not contain
                // additional keys.

                EncryptionIdentity.PrivateIdentity fullPrivateIdentity;

                if (privateIdentity.getEncryptionIdentity().getAdditionalKeys() != null) {
                    fullPrivateIdentity = privateIdentity;
                } else {
                    EncryptionIdentity fullEncryptionKey = getEncryptionKey(serviceManager, sourceDefinition, privateKeys);
                    fullPrivateIdentity = fullEncryptionKey.getPrivateIdentity(password);
                }

                config = downloadRemoteConfiguration(destination, privateKeys);

                ChangePasswordCommand.saveKeyFile(privateKeyFile, fullPrivateIdentity.getEncryptionIdentity());

                updateConfiguration(config, true, true, true);
            } catch (Exception exc) {
                serviceManager.setSourceId(oldSourceId);
                throw exc;
            }
            updateSource(sourceName, serviceManager, sourceDefinition, identity);

            InstanceFactory.reloadConfiguration(
                    () -> RebuildRepositoryCommand.rebuildFromLog(password, true));

            return true;
        } catch (Exception exc) {
            log.error("Failed to download configuration", exc);
            deleteFile(privateKeyFile);
            return false;
        }
    }

    /**
     * Decodes a destination string from base64.
     * 
     * @param destination The base64 encoded destination string
     * @return The decoded bytes
     */
    public static byte[] destinationDecode(String destination) {
        try {
            return BaseEncoding.base64Url().decode(destination);
        } catch (IllegalArgumentException exc) {
            return BaseEncoding.base64().decode(destination);
        }
    }

    /**
     * Updates a source in the service.
     * 
     * @param sourceName The name of the source
     * @param serviceManager The service manager instance
     * @param sourceDefinition The source definition
     * @param identity The identity of the current installation
     * @throws IOException If there's an error updating the source
     */
    private static void updateSource(String sourceName, ServiceManager serviceManager,
                                     SourceResponse sourceDefinition, String identity) throws IOException {
        if (!Objects.equals(serviceManager.getSourceName(), sourceName)
                || !Objects.equals(identity, sourceDefinition.getIdentity())) {
            String configurationUrl = safeGetConfigUrl();
            serviceManager.call(null, (api) -> api.updateSource(sourceDefinition.getSourceId(), new SourceRequest()
                    .name(sourceName)
                    .identity(identity)
                    .encryptionMode(sourceDefinition.getEncryptionMode())
                    .destination(sourceDefinition.getDestination())
                    .sharingKey(sourceDefinition.getSharingKey())
                    .applicationUrl(configurationUrl)
                    .version(VersionCommand.getVersionEdition())
                    .key(sourceDefinition.getKey())));
        }
        serviceManager.setSourceId(sourceDefinition.getSourceId());
        serviceManager.setSourceName(sourceName);
    }

    /**
     * Safely gets the configuration URL, returning null if there's an error.
     * 
     * @return The configuration URL or null
     */
    private static String safeGetConfigUrl() {
        String configurationUrl;
        try {
            configurationUrl = getConfigurationUrl();
        } catch (IOException exc) {
            configurationUrl = null;
        }
        return configurationUrl;
    }

    /**
     * Data class for the update source request.
     */
    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class UpdateSourceRequest extends PrivateKeyRequest {
        private String sourceId;
        private String name;
        private boolean force;
    }

    /**
     * Implementation of the exclusive implementation that handles the HTTP request.
     */
    private static class Implementation extends ExclusiveImplementation {
        
        /**
         * Processes the HTTP request to update a source.
         * 
         * @param req The HTTP request
         * @return Response indicating success or failure
         * @throws Exception If there's an error processing the request
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            String requestBody = decodeRequestBody(req);
            UpdateSourceRequest request = READER.readValue(requestBody);
            try {
                ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                if (request.getSourceId() == null) {
                    request.setSourceId(serviceManager.getSourceId());
                }
                if (request.getSourceId() == null) {
                    throw new ApiException(400, "Bad request");
                }
                EncryptionIdentity existingKey = encryptionIdentity();

                SourceResponse sourceDefinition = serviceManager.call(null, new ServiceManager.ApiFunction<SourceResponse>() {
                    @Override
                    public SourceResponse call(BackupApi api) throws ApiException {
                        return api.getSource(request.getSourceId());
                    }

                    @Override
                    public boolean shouldRetryMissing(String region, ApiException apiException) {
                        return true;
                    }
                });

                String identity = InstanceFactory.getInstance(CommandLineModule.INSTALLATION_IDENTITY);

                if (!request.force && InstanceFactory.hasConfiguration(false) && existingKey != null) {
                    if (!Objects.equals(sourceDefinition.getIdentity(), identity)) {
                        return messageJson(400, "Trying to adopt a source with existing config");
                    }

                    updateSource(request.getName(), serviceManager, sourceDefinition, identity);
                    return messageJson(200, "Source updated");
                } else {
                    if (sourceDefinition.getDestination() != null && sourceDefinition.getEncryptionMode() != null
                            && sourceDefinition.getKey() != null) {
                        EncryptionIdentity encryptionKey = EncryptionIdentity.restoreFromString(sourceDefinition.getKey());
                        EncryptionIdentity.PrivateIdentity privateIdentity;
                        try {
                            privateIdentity = encryptionKey.getPrivateIdentity(request.getPassword());
                        } catch (Exception exc) {
                            return messageJson(403, "Invalid password provided");
                        }

                        if (restoreFromSource(request.getName(), request.getPassword(), serviceManager,
                                sourceDefinition, identity, privateIdentity)) {
                            return messageJson(200, "Started rebuild");
                        } else {
                            return messageJson(500, "Failed to download configuration");
                        }
                    }

                    updateSource(request.getName(), serviceManager, sourceDefinition, identity);
                    return messageJson(200, "Updated source");
                }

            } catch (IOException exc) {
                return sendApiFailureOn(exc);
            }
        }

        /**
         * Returns the message to display when the system is busy.
         * 
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Updating source";
        }
    }
}
