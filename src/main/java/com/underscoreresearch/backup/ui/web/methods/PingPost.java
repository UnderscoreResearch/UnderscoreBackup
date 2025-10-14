package com.underscoreresearch.backup.ui.web.methods;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.ui.commands.InteractiveCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.ui.web.BaseImplementation;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;
import org.takes.rs.RsWithHeaders;

import static com.underscoreresearch.backup.ui.web.methods.PingGet.getCorsHeaders;

/**
 * Handles HTTP POST requests for the ping endpoint.
 * This endpoint is used to reload the configuration and potentially start the backup process.
 */
@Slf4j
public class PingPost extends BaseWrap {
    /**
     * Creates a new PingPost instance with the Implementation handler.
     */
    public PingPost() {
        super(new Implementation());
    }

    /**
     * Implementation of the ping POST endpoint that reloads configuration and returns a 200 OK response.
     */
    private static class Implementation extends BaseImplementation {
        /**
         * Handles the POST request by reloading the configuration and returning a 200 OK response with CORS headers.
         * If no additional source is specified, it will also start the backup process if available.
         *
         * @param req The HTTP request
         * @return A response with 200 OK status and CORS headers
         * @throws Exception If there's an error processing the request
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            if (Strings.isNullOrEmpty(InstanceFactory.getAdditionalSource()))
                InstanceFactory.reloadConfiguration(InteractiveCommand::startBackupIfAvailable);
            else
                InstanceFactory.reloadConfigurationWithSource();

            return new RsWithHeaders(messageJson(200, "Ok"), getCorsHeaders(req));
        }
    }
}
