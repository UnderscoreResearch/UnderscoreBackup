package com.underscoreresearch.backup.ui.web.methods.service;

import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.service.api.model.SourceResponse;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;

import java.io.IOException;

import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.sendApiFailureOn;

/**
 * Endpoint for deleting secrets from the service.
 * This class handles the deletion of encryption key secrets stored in the backup service.
 */
@Slf4j
public class DeleteSecretPost extends BaseWrap {
    
    /**
     * Constructor for the DeleteSecretPost endpoint.
     */
    public DeleteSecretPost() {
        super(new Implementation());
    }

    /**
     * Implementation of the exclusive implementation that handles the HTTP request.
     */
    private static class Implementation extends ExclusiveImplementation {
        
        /**
         * Processes the HTTP request to delete a secret.
         * 
         * @param req The HTTP request
         * @return Response indicating success or failure
         * @throws Exception If there's an error processing the request
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            try {
                ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                if (serviceManager.getSourceId() == null) {
                    return messageJson(400, "No source selected");
                }

                SourceResponse source = serviceManager.call(null, (api) -> api.getSource(serviceManager.getSourceId()));
                if (source.getSecretRegion() != null) {
                    serviceManager.call(source.getSecretRegion(), (api) -> api.deleteSecret(serviceManager.getSourceId()));
                }
                return messageJson(200, "Secret deleted");
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
            return "Restoring private key from service";
        }
    }
}
