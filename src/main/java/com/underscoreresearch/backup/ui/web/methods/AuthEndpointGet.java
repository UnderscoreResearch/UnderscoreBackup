package com.underscoreresearch.backup.ui.web.methods;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import com.underscoreresearch.backup.ui.web.ExclusiveImplementation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;
import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.TkFork;
import org.takes.http.BkBasic;
import org.takes.http.BkSafe;
import org.takes.http.FtBasic;
import org.takes.rq.RqRequestLine;
import org.takes.rs.RsRedirect;
import org.takes.tk.TkWrap;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;

import static com.underscoreresearch.backup.ui.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Web endpoint for retrieving authentication endpoints.
 * This class creates and manages a temporary web server for OAuth authentication redirects.
 */
@Slf4j
public class AuthEndpointGet extends BaseWrap {

    private static final ObjectWriter WRITER = MAPPER.writerFor(EndpointResponse.class);

    /**
     * Creates a new AuthEndpointGet instance.
     *
     * @param address The address to bind the authentication server to
     * @param baseApi The base URI for the API
     */
    public AuthEndpointGet(InetAddress address, URI baseApi) {
        super(new Implementation(address, baseApi));
    }

    /**
     * Data class representing the endpoint response.
     */
    @AllArgsConstructor
    @Data
    public static class EndpointResponse {
        private String endpoint;
    }

    /**
     * Implementation class that handles creating and managing authentication endpoints.
     */
    private static class Implementation extends ExclusiveImplementation {
        private static final TemporalAmount MAX_OPEN_DURATION = Duration.ofMinutes(10);
        private static String existingAddress;
        private static Instant lastRequested;
        private final InetAddress address;
        private final URI baseApi;

        /**
         * Creates a new Implementation instance.
         *
         * @param address The address to bind the authentication server to
         * @param baseApi The base URI for the API
         */
        public Implementation(InetAddress address, URI baseApi) {
            this.address = address;
            this.baseApi = baseApi;
        }

        /**
         * Processes the request to get an authentication endpoint.
         * Creates a temporary web server for OAuth authentication if one doesn't exist.
         *
         * @param req The HTTP request
         * @return The HTTP response containing the authentication endpoint
         * @throws Exception If an error occurs during processing
         */
        @Override
        public Response actualAct(Request req) throws Exception {
            try {
                if (!address.isLoopbackAddress()) {
                    return messageJson(409, "OAuth only supported with loopback interface");
                }

                synchronized (AuthEndpointGet.class) {
                    if (existingAddress != null) {
                        lastRequested = Instant.now();
                        return encryptResponse(req, WRITER.writeValueAsString(new EndpointResponse(existingAddress)));
                    }

                    int port = 12321;
                    ServerSocket searchSocket;
                    while (true) {
                        try {
                            searchSocket = new ServerSocket(port, 10, address);
                            break;
                        } catch (IOException e) {
                            if (port >= 12325) {
                                log.error("Can't find port for auth service", e);
                                return messageJson(409, "Can't find available port");
                            }

                            port++;
                        }
                    }

                    final ServerSocket socket = searchSocket;

                    Thread thread = new Thread(() -> {
                        FtBasic basic = new FtBasic(
                                new BkSafe(
                                        new BkBasic(
                                                new TkFork(
                                                        new FkRegex("/auth-redirect",
                                                                new AuthRedirect(baseApi.toString()
                                                                        + "destinations")))
                                        )
                                ),
                                socket);
                        try {
                            basic.start(() -> {
                                synchronized (AuthEndpointGet.class) {
                                    if (lastRequested.plus(MAX_OPEN_DURATION).isBefore(Instant.now())) {
                                        lastRequested = null;
                                        existingAddress = null;
                                        return true;
                                    }
                                }
                                return false;
                            });
                        } catch (IOException e) {
                            log.error("Web server error", e);
                        }
                    }, "Auth Webserver");
                    thread.setDaemon(true);
                    thread.start();

                    lastRequested = Instant.now();
                    existingAddress = String.format("http://localhost:%d/auth-redirect", port);
                }
                return encryptResponse(req, WRITER.writeValueAsString(new EndpointResponse(existingAddress)));
            } catch (Exception exc) {
                log.error("Failed to get auth endpoint", exc);
            }
            return messageJson(404, "Failed to get auth endpoint");
        }

        /**
         * Gets the message to display when the system is busy.
         *
         * @return The busy message
         */
        @Override
        protected String getBusyMessage() {
            return "Creating auth endpoint";
        }

        /**
         * Wrapper class for handling authentication redirects.
         */
        private static class AuthRedirect extends TkWrap {
            /**
             * Creates a new AuthRedirect instance.
             *
             * @param redirectUrl The URL to redirect to after authentication
             */
            public AuthRedirect(String redirectUrl) {
                super(req -> {
                    final String uri = new RqRequestLine.Base(req).uri();
                    String newLocation = redirectUrl;
                    int ind = uri.indexOf("?");
                    if (ind >= 0)
                        newLocation += uri.substring(ind);
                    return new RsRedirect(newLocation, 302);
                });
            }
        }
    }
}
