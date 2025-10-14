package com.underscoreresearch.backup.ui.web.methods.service;

import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.manifest.ServiceManager;
import org.takes.Request;
import org.takes.Response;

import java.io.IOException;

import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.sendApiFailureOn;

/**
 * Endpoint for deleting authentication tokens.
 * This class handles the deletion of authentication tokens from the backup service.
 */
public class TokenDelete extends BaseWrap {
    
    /**
     * Constructor for the TokenDelete endpoint.
     */
    public TokenDelete() {
        super(new Implementation());
    }

    /**
     * Implementation of the exclusive implementation that handles the HTTP request.
     */
    private static class Implementation extends ExclusiveImplementation {
        
        /**
         * Processes the HTTP request to delete a token.
         * 
         * @param req The HTTP request
         * @return Response indicating success or failure
         * @throws Exception If there's an error processing the request
         */
        @Override
        public Response actualAct(Request req) throws Exception {

            try {
                ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                serviceManager.deleteToken();
                return messageJson(200, "Token deleted");
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
            return "Deleting service token";
        }
    }
}
