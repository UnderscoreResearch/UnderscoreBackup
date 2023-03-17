package com.underscoreresearch.backup.cli.web.service;

import static com.underscoreresearch.backup.cli.commands.GenerateKeyCommand.getDefaultEncryptionFileName;
import static com.underscoreresearch.backup.cli.commands.RebuildRepositoryCommand.downloadRemoteConfiguration;
import static com.underscoreresearch.backup.cli.commands.RebuildRepositoryCommand.unpackConfigData;
import static com.underscoreresearch.backup.cli.web.ConfigurationPost.updateConfiguration;
import static com.underscoreresearch.backup.cli.web.service.SourcesPost.encryptionKey;
import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.sendApiFailureOn;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_DESTINATION_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_READER;
import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_WRITER;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;
import org.takes.Request;
import org.takes.Response;
import org.takes.rq.RqPrint;

import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.io.BaseEncoding;
import com.underscoreresearch.backup.cli.commands.RebuildRepositoryCommand;
import com.underscoreresearch.backup.cli.commands.VersionCommand;
import com.underscoreresearch.backup.cli.web.ExclusiveImplementation;
import com.underscoreresearch.backup.cli.web.JsonWrap;
import com.underscoreresearch.backup.cli.web.PrivateKeyRequest;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.service.api.invoker.ApiException;
import com.underscoreresearch.backup.service.api.model.SourceRequest;
import com.underscoreresearch.backup.service.api.model.SourceResponse;

@Slf4j
public class SourcesPut extends JsonWrap {
    private static final ObjectReader READER = MAPPER.readerFor(UpdateSourceRequest.class);

    public SourcesPut() {
        super(new Implementation());
    }

    public static boolean restoreFromSource(String sourceName,
                                            String password,
                                            ServiceManager serviceManager,
                                            SourceResponse sourceDefinition,
                                            String identity,
                                            EncryptionKey.PrivateKey privateKey) throws IOException {
        BackupDestination destination = BACKUP_DESTINATION_READER.readValue(unpackConfigData(
                sourceDefinition.getEncryptionMode(), privateKey,
                BaseEncoding.base64().decode(sourceDefinition.getDestination())));

        File privateKeyFile = getDefaultEncryptionFileName(InstanceFactory
                .getInstance(CommandLine.class));
        try {
            // Kind of ugly but we need to do this to be able to download config from service.
            String oldSourceId = serviceManager.getSourceId();
            String config;
            try {
                serviceManager.setSourceId(sourceDefinition.getSourceId());
                config = downloadRemoteConfiguration(destination, privateKey);

                try (FileOutputStream writer = new FileOutputStream(privateKeyFile)) {
                    writer.write(ENCRYPTION_KEY_WRITER.writeValueAsBytes(privateKey.getParent().publicOnly()));
                }

                updateConfiguration(config, true, true);
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
            privateKeyFile.delete();
            return false;
        }
    }

    private static void updateSource(String sourceName, ServiceManager serviceManager,
                                     SourceResponse sourceDefinition, String identity) throws IOException {
        if (!Objects.equals(serviceManager.getSourceName(), sourceName)
                || !Objects.equals(identity, sourceDefinition.getIdentity())) {
            serviceManager.call(null, (api) -> api.updateSource(sourceDefinition.getSourceId(), new SourceRequest()
                    .name(sourceName)
                    .identity(identity)
                    .encryptionMode(sourceDefinition.getEncryptionMode())
                    .destination(sourceDefinition.getDestination())
                    .sharingKey(sourceDefinition.getSharingKey())
                    .version(VersionCommand.getVersion() + VersionCommand.getEdition())
                    .key(sourceDefinition.getKey())));
        }
        serviceManager.setSourceId(sourceDefinition.getSourceId());
        serviceManager.setSourceName(sourceName);
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class UpdateSourceRequest extends PrivateKeyRequest {
        private String sourceId;
        private String name;
    }

    private static class Implementation extends ExclusiveImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            String requestBody = new RqPrint(req).printBody();
            UpdateSourceRequest request = READER.readValue(requestBody);
            try {
                ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                if (request.getSourceId() == null) {
                    request.setSourceId(serviceManager.getSourceId());
                }
                if (request.getSourceId() == null) {
                    throw new ApiException(400, "Bad request");
                }
                EncryptionKey existingKey = encryptionKey();

                SourceResponse sourceDefinition = serviceManager.call(null, (api) -> api
                        .getSource(request.getSourceId()));

                String identity = InstanceFactory.getInstance(CommandLineModule.INSTALLATION_IDENTITY);

                if (InstanceFactory.hasConfiguration(false) && existingKey != null) {
                    if (!Objects.equals(sourceDefinition.getIdentity(), identity)) {
                        return messageJson(400, "Trying to adopt a source with existing config");
                    }

                    updateSource(request.getName(), serviceManager, sourceDefinition, identity);
                    return messageJson(200, "Source updated");
                } else {
                    if (sourceDefinition.getDestination() != null && sourceDefinition.getEncryptionMode() != null
                            && sourceDefinition.getKey() != null) {
                        EncryptionKey encryptionKey = ENCRYPTION_KEY_READER.readValue(sourceDefinition.getKey());
                        EncryptionKey.PrivateKey privateKey;
                        try {
                            privateKey = encryptionKey.getPrivateKey(request.getPassword());
                        } catch (Exception exc) {
                            return messageJson(403, "Invalid password provided");
                        }

                        if (restoreFromSource(request.getName(), request.getPassword(), serviceManager,
                                sourceDefinition, identity, privateKey)) {
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

        @Override
        protected String getBusyMessage() {
            return "Updating source";
        }
    }
}
