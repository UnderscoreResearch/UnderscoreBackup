package com.underscoreresearch.backup.ui.web.methods;

import com.underscoreresearch.backup.ui.web.BaseImplementation;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;
import org.takes.rs.RsWithHeaders;
import org.takes.rs.RsWithStatus;

import static com.underscoreresearch.backup.ui.web.methods.PingGet.getCorsHeaders;

/**
 * Handles HTTP OPTIONS requests for the ping endpoint.
 * This endpoint is used to support CORS preflight requests from web browsers.
 */
@Slf4j
public class PingOptions extends BaseWrap {
    /**
     * Creates a new PingOptions instance with the Implementation handler.
     */
    public PingOptions() {
        super(new Implementation());
    }

    /**
     * Implementation of the ping OPTIONS endpoint that returns a 200 OK response with CORS headers.
     */
    private static class Implementation extends BaseImplementation {
        /**
         * Handles the OPTIONS request by returning a 200 OK response with CORS headers.
         *
         * @param req The HTTP request
         * @return A response with 200 OK status and CORS headers
         * @throws Exception If there's an error processing the request
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            return new RsWithHeaders(new RsWithStatus(200), getCorsHeaders(req));
        }
    }
}
