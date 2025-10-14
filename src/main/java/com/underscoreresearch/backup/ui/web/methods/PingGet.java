package com.underscoreresearch.backup.ui.web.methods;

import com.underscoreresearch.backup.ui.web.BaseImplementation;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;
import org.takes.rq.RqHeaders;
import org.takes.rs.RsWithHeaders;

import java.io.IOException;
import java.util.Iterator;

/**
 * Handles HTTP GET requests for the ping endpoint.
 * This endpoint is used to check if the service is running and to handle CORS requests.
 */
@Slf4j
public class PingGet extends BaseWrap {
    /**
     * Creates a new PingGet instance with the Implementation handler.
     */
    public PingGet() {
        super(new Implementation());
    }

    /**
     * Gets the CORS headers for a request.
     *
     * @param req The HTTP request
     * @return An array of CORS header strings
     * @throws IOException If there's an error processing the request
     */
    public static String[] getCorsHeaders(Request req) throws IOException {
        return new String[]{
                "Access-Control-Allow-Origin: " + getSiteUrl(req),
                "Access-Control-Allow-Methods: GET, OPTIONS",
        };
    }

    /**
     * Gets the site URL from the request's origin header or from environment variables.
     *
     * @param req The HTTP request
     * @return The site URL to use for CORS
     * @throws IOException If there's an error processing the request
     */
    public static String getSiteUrl(Request req) throws IOException {
        if (req != null) {
            final Iterator<String> headers = new RqHeaders.Smart(req)
                    .header("origin").iterator();
            if (headers.hasNext()) {
                final String origin = headers.next();
                if ("https://www.underscorebackup.com".equals(origin)) {
                    return origin;
                }
            }
        }

        return ("true".equals(System.getenv("BACKUP_DEV")) ? "https://dev.underscorebackup.com" : "https://underscorebackup.com");
    }

    /**
     * Implementation of the ping endpoint that returns a 200 OK response.
     */
    private static class Implementation extends BaseImplementation {
        /**
         * Handles the ping request by returning a 200 OK response with CORS headers.
         *
         * @param req The HTTP request
         * @return A response with 200 OK status and CORS headers
         * @throws Exception If there's an error processing the request
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            return new RsWithHeaders(messageJson(200, "Ok"), getCorsHeaders(req));
        }
    }
}
