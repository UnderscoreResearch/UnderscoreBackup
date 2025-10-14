package com.underscoreresearch.backup.ui.web.methods;

import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.DestinationDecoder;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import org.takes.Request;
import org.takes.Response;

/**
 * Web endpoint for checking if a rebuild is available for a destination.
 * This class handles requests to check if a backup repository can be rebuilt from a destination.
 */
public class RebuildAvailableGet extends BaseWrap {
    /**
     * Creates a new RebuildAvailableGet instance.
     */
    public RebuildAvailableGet() {
        super(new Implementation());
    }

    /**
     * Implementation class that handles rebuild availability check requests.
     */
    private static class Implementation extends ExclusiveImplementation {
        /**
         * Processes a request to check if a rebuild is available.
         * Checks if the specified destination supports rebuilding and if a rebuild is available.
         *
         * @param req The HTTP request
         * @return The HTTP response indicating if a rebuild is available
         * @throws Exception If an error occurs during processing
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            DestinationDecoder destination = new DestinationDecoder(req);
            if (destination.getResponse() != null) {
                return destination.getResponse();
            }
            if (!(destination.getProvider() instanceof IOIndex index)) {
                return messageJson(400, "Destination " + destination + " does not support index");
            }

            if (index.rebuildAvailable())
                return messageJson(200, "Rebuild " + destination + " available");
            return messageJson(404, "Rebuild " + destination + " not available");
        }

        /**
         * Gets the message to display when the system is busy.
         *
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Checking rebuild availability";
        }
    }
}
