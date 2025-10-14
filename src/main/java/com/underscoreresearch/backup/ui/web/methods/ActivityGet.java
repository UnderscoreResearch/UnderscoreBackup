package com.underscoreresearch.backup.ui.web.methods;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.inject.ProvisionException;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.utils.log.StateLogger;
import com.underscoreresearch.backup.utils.log.StatusLine;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.misc.Href;
import org.takes.rq.RqHref;

import java.util.ArrayList;
import java.util.List;

import static com.underscoreresearch.backup.ui.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Handles GET requests to retrieve activity information.
 * This class provides status and activity information about the backup system.
 */
@Slf4j
public class ActivityGet extends BaseWrap {

    private static final ObjectWriter WRITER = MAPPER
            .writerFor(StatusResponse.class);

    /**
     * Creates a new ActivityGet instance.
     */
    public ActivityGet() {
        super(new Implementation());
    }

    /**
     * Data class representing the status response.
     */
    @AllArgsConstructor
    @Data
    public static class StatusResponse {
        private List<StatusLine> status;
    }

    /**
     * Implementation class that handles retrieving activity information.
     */
    private static class Implementation implements Take {
        /**
         * Processes the request to get activity information.
         * Retrieves status lines from the state logger based on the request parameters.
         *
         * @param req The HTTP request
         * @return The HTTP response containing activity information
         * @throws Exception If an error occurs during processing
         */
        @Override
        public Response act(Request req) throws Exception {
            try {
                Href href = new RqHref.Base(req).href();
                boolean temporal = "true".equals(href.param("temporal").iterator().hasNext() ? href.param("temporal").iterator().next() : "false");
                List<StatusLine> statusLines;
                if (InstanceFactory.hasConfiguration(false) && hasKey()) {
                    statusLines = InstanceFactory.getInstance(StateLogger.class).logData(
                            temporal ? (type -> type == StateLogger.Type.LOG) : type -> type != StateLogger.Type.LOG);
                } else {
                    statusLines = new ArrayList<>();
                }
                return encryptResponse(req, WRITER.writeValueAsString(new StatusResponse(statusLines)));
            } catch (Throwable exc) {
                log.error("Failed to fetch current activity", exc);
            }
            return messageJson(404, "Failed to fetch current activity");
        }

        /**
         * Checks if an encryption key is available.
         *
         * @return True if an encryption key is available, false otherwise
         */
        private boolean hasKey() {
            try {
                InstanceFactory.getInstance(EncryptionIdentity.class);
                return true;
            } catch (ProvisionException exc) {
                return false;
            }
        }
    }
}
