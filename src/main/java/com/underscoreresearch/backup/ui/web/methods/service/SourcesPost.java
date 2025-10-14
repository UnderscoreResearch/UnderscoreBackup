package com.underscoreresearch.backup.ui.web.methods.service;

import com.fasterxml.jackson.databind.ObjectReader;
import com.underscoreresearch.backup.ui.commands.VersionCommand;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.service.api.model.SourceRequest;
import com.underscoreresearch.backup.service.api.model.SourceResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.takes.Request;
import org.takes.Response;

import java.io.IOException;

import static com.underscoreresearch.backup.ui.web.PsAuthedContent.decodeRequestBody;
import static com.underscoreresearch.backup.ui.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.ui.web.methods.service.CreateSecretPut.encryptionIdentity;
import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.sendApiFailureOn;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Endpoint for creating backup sources in the service.
 * This class handles the creation of new backup sources in the backup service.
 */
public class SourcesPost extends BaseWrap {
    private static final ObjectReader READER = MAPPER.readerFor(CreateSourceRequest.class);

    /**
     * Constructor for the SourcesPost endpoint.
     */
    public SourcesPost() {
        super(new Implementation());
    }

    /**
     * Data class for the create source request.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateSourceRequest {
        private String name;
    }

    /**
     * Data class for the create source response.
     */
    @Data
    @AllArgsConstructor
    public static class CreateSourceResponse {
        private String sourceId;
    }

    /**
     * Implementation of the exclusive implementation that handles the HTTP request.
     */
    private static class Implementation extends ExclusiveImplementation {
        
        /**
         * Processes the HTTP request to create a source.
         * 
         * @param req The HTTP request
         * @return Response containing the created source ID or an error message
         * @throws Exception If there's an error processing the request
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            String config = decodeRequestBody(req);
            CreateSourceRequest request = READER.readValue(config);
            try {
                ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                serviceManager.setSourceId(null);
                serviceManager.setSourceName(request.getName());
                EncryptionIdentity key = encryptionIdentity();
                if (InstanceFactory.hasConfiguration(false) && key != null) {
                    InstanceFactory.getInstance(ManifestManager.class).updateServiceSourceData(key);
                } else {
                    String identity = InstanceFactory.getInstance(CommandLineModule.INSTALLATION_IDENTITY);
                    SourceResponse ret = serviceManager.call(null, (api) -> api.createSource(new SourceRequest()
                            .name(serviceManager.getSourceName())
                            .version(VersionCommand.getVersionEdition())
                            .identity(identity)));
                    serviceManager.setSourceId(ret.getSourceId());
                }
                return encryptResponse(req, MAPPER.writeValueAsString(new CreateSourceResponse(serviceManager.getSourceId())));
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
            return "Creating source";
        }
    }
}
