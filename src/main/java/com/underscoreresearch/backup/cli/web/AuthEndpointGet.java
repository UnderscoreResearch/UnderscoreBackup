package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;

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
import org.takes.rs.RsText;
import org.takes.tk.TkWrap;

import com.fasterxml.jackson.databind.ObjectWriter;

@Slf4j
public class AuthEndpointGet extends JsonWrap {

    private static final ObjectWriter WRITER = MAPPER.writerFor(EndpointResponse.class);

    public AuthEndpointGet(InetAddress address, URI baseApi) {
        super(new Implementation(address, baseApi));
    }

    @AllArgsConstructor
    @Data
    public static class EndpointResponse {
        private String endpoint;
    }

    private static class Implementation extends ExclusiveImplementation {
        private static final TemporalAmount MAX_OPEN_DURATION = Duration.ofMinutes(10);
        private static String existingAddress;
        private static Instant lastRequested;
        private final InetAddress address;
        private final URI baseApi;

        public Implementation(InetAddress address, URI baseApi) {
            this.address = address;
            this.baseApi = baseApi;
        }

        @Override
        public Response actualAct(Request req) throws Exception {
            try {
                if (!address.isLoopbackAddress()) {
                    return messageJson(409, "OAuth only supported with loopback interface");
                }

                synchronized (AuthEndpointGet.class) {
                    if (existingAddress != null) {
                        lastRequested = Instant.now();
                        return new RsText(WRITER.writeValueAsString(new EndpointResponse(existingAddress)));
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
                return new RsText(WRITER.writeValueAsString(new EndpointResponse(existingAddress)));
            } catch (Exception exc) {
                log.error("Failed to get auth endpoint", exc);
            }
            return messageJson(404, "Failed to get auth endpoint");
        }

        @Override
        protected String getBusyMessage() {
            return "Creating auth endpoint";
        }

        private static class AuthRedirect extends TkWrap {
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
