package com.underscoreresearch.backup.ui.web;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.ui.commands.ConfigureCommand;
import com.underscoreresearch.backup.ui.desktop.UIHandler;
import com.underscoreresearch.backup.ui.web.methods.ActivateSharesPost;
import com.underscoreresearch.backup.ui.web.methods.ActiveSharesGet;
import com.underscoreresearch.backup.ui.web.methods.ActivityGet;
import com.underscoreresearch.backup.ui.web.methods.AdditionalKeyPut;
import com.underscoreresearch.backup.ui.web.methods.AdditionalKeysPost;
import com.underscoreresearch.backup.ui.web.methods.AuthEndpointGet;
import com.underscoreresearch.backup.ui.web.methods.AuthPost;
import com.underscoreresearch.backup.ui.web.methods.BackupDownloadPost;
import com.underscoreresearch.backup.ui.web.methods.BackupFilesDelete;
import com.underscoreresearch.backup.ui.web.methods.BackupPauseGet;
import com.underscoreresearch.backup.ui.web.methods.ConfigurationGet;
import com.underscoreresearch.backup.ui.web.methods.ConfigurationPost;
import com.underscoreresearch.backup.ui.web.methods.DefragPost;
import com.underscoreresearch.backup.ui.web.methods.GenerateKeyPut;
import com.underscoreresearch.backup.ui.web.methods.KeyChangePost;
import com.underscoreresearch.backup.ui.web.methods.KeyPost;
import com.underscoreresearch.backup.ui.web.methods.ListBackupFilesGet;
import com.underscoreresearch.backup.ui.web.methods.ListBackupVersionsGet;
import com.underscoreresearch.backup.ui.web.methods.ListLocalFilesGet;
import com.underscoreresearch.backup.ui.web.methods.OptimizePost;
import com.underscoreresearch.backup.ui.web.methods.PingGet;
import com.underscoreresearch.backup.ui.web.methods.PingOptions;
import com.underscoreresearch.backup.ui.web.methods.PingPost;
import com.underscoreresearch.backup.ui.web.methods.RebuildAvailableGet;
import com.underscoreresearch.backup.ui.web.methods.RemoteConfigurationGet;
import com.underscoreresearch.backup.ui.web.methods.RemoteRestorePost;
import com.underscoreresearch.backup.ui.web.methods.RepairPost;
import com.underscoreresearch.backup.ui.web.methods.ResetDelete;
import com.underscoreresearch.backup.ui.web.methods.RestartSetsPost;
import com.underscoreresearch.backup.ui.web.methods.RestorePost;
import com.underscoreresearch.backup.ui.web.methods.SearchBackupFilesGet;
import com.underscoreresearch.backup.ui.web.methods.ShutdownGet;
import com.underscoreresearch.backup.ui.web.methods.SourceSelectPost;
import com.underscoreresearch.backup.ui.web.methods.StateGet;
import com.underscoreresearch.backup.ui.web.methods.TrimPost;
import com.underscoreresearch.backup.ui.web.methods.ValidateBlocksPost;
import com.underscoreresearch.backup.ui.web.methods.service.BestRegionGet;
import com.underscoreresearch.backup.ui.web.methods.service.CreateSecretPut;
import com.underscoreresearch.backup.ui.web.methods.service.DeleteSecretPost;
import com.underscoreresearch.backup.ui.web.methods.service.GenerateTokenPost;
import com.underscoreresearch.backup.ui.web.methods.service.GetSecretPost;
import com.underscoreresearch.backup.ui.web.methods.service.SharesGet;
import com.underscoreresearch.backup.ui.web.methods.service.SourcesGet;
import com.underscoreresearch.backup.ui.web.methods.service.SourcesPost;
import com.underscoreresearch.backup.ui.web.methods.service.SourcesPut;
import com.underscoreresearch.backup.ui.web.methods.service.SupportBundlePost;
import com.underscoreresearch.backup.ui.web.methods.service.TokenDelete;
import com.underscoreresearch.backup.ui.web.methods.service.VersionCheckGet;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.Hash;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.facets.auth.Identity;
import org.takes.facets.auth.Pass;
import org.takes.facets.auth.PsChain;
import org.takes.facets.auth.TkAuth;
import org.takes.facets.fork.FkMethods;
import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.Fork;
import org.takes.facets.fork.TkFork;
import org.takes.facets.forward.TkForward;
import org.takes.http.BkBasic;
import org.takes.http.BkParallel;
import org.takes.http.BkSafe;
import org.takes.http.FtBasic;
import org.takes.misc.Opt;
import org.takes.rq.RqHref;
import org.takes.rq.RqMethod;
import org.takes.tk.TkWithType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import static com.underscoreresearch.backup.configuration.CommandLineModule.BIND_ADDRESS;
import static com.underscoreresearch.backup.encryption.EncryptionIdentity.RANDOM;
import static com.underscoreresearch.backup.io.IOUtils.createDirectory;

/**
 * Web server implementation for the Underscore Backup application.
 * Provides a web-based user interface for configuration, monitoring, and management of backup operations.
 * Handles API endpoints for various backup operations, authentication, and service interactions.
 * The server runs on a dynamically assigned port with a secure base path for API access.
 */
@Slf4j
public class WebServer {
    private static final Opt<Identity> AUTHENTICATED = new Opt.Single<Identity>(new Identity.Simple("urn:users:root"));
    private static WebServer server;
    private ServerSocket socket;
    private String base;

    /**
     * Gets the singleton instance of the WebServer.
     * Creates a new instance if one doesn't exist yet.
     *
     * @return The WebServer instance
     */
    public static synchronized WebServer getInstance() {
        if (server == null) {
            server = new WebServer();
        }
        return server;
    }

    /**
     * Gets the InetAddress to bind the server to based on command line options.
     * Uses the loopback address by default, or a specified address if provided.
     *
     * @return The InetAddress to bind the server to
     */
    private static InetAddress getInetAddress() {
        CommandLine commandLine = InstanceFactory.getInstance(CommandLine.class);

        if (commandLine.hasOption(BIND_ADDRESS)) {
            try {
                return InetAddress.getByName(commandLine.getOptionValue(BIND_ADDRESS));
            } catch (UnknownHostException e) {
                throw new RuntimeException(String.format("Failed to resolve \"%s\"",
                        commandLine.getOptionValue(BIND_ADDRESS)), e);
            }
        } else
            return InetAddress.getLoopbackAddress();
    }

    /**
     * Starts the web server.
     * Initializes a server socket on a dynamic or fixed port based on the developer mode setting.
     * Sets up all API endpoints and routes for the web interface.
     * Writes the configuration URL to disk for later access.
     *
     * @param developerMode If true, uses a fixed port (12345) and path (/fixed) for development purposes
     */
    public void start(boolean developerMode) {
        if (socket == null) {
            InetAddress address = getInetAddress();

            if (developerMode) {
                try {
                    socket = new ServerSocket(12345, 100, address);
                } catch (IOException e) {
                    throw new RuntimeException("Can't find local host address", e);
                }

                base = "/fixed";
            } else {
                try {
                    URI existingConfig = URI.create(ConfigureCommand.getConfigurationUrl());

                    base = existingConfig.getPath();
                    if (base.endsWith("/"))
                        base = base.substring(0, base.length() - 1);

                    if (base.equals("/fixed"))
                        throw new IOException("Don't bind to fixed");

                    try {
                        socket = new ServerSocket(existingConfig.getPort(), 10, address);
                    } catch (IOException e) {
                        throw new RuntimeException("Can't find local host address", e);
                    }

                } catch (Exception exc) {
                    try {
                        socket = new ServerSocket(0, 10, address);
                    } catch (IOException e) {
                        throw new RuntimeException("Can't find local host address", e);
                    }

                    byte[] bytes = new byte[32];
                    RANDOM.nextBytes(bytes);
                    base = "/" + Hash.encodeBytes(bytes);
                }
            }

            Take serviceTake = new TkForward(
                    new LoggingTake(
                            new TkAuth(
                                    new TkFork(
                                            new FkRegex(base + "/api/ping", new TkFork(
                                                    new FkMethods("GET", new PingGet()),
                                                    new FkMethods("OPTIONS", new PingOptions()),
                                                    new FkMethods("POST", new PingPost())
                                            )),
                                            new FkRegex(base + "/api/auth", new TkFork(
                                                    new FkMethods("POST", new AuthPost())
                                            )),

                                            new FkRegex(base + "/api", new TkFork(
                                                    new FkMethods("DELETE", new ResetDelete()))),
                                            new FkRegex(base + "/api/activity", new TkFork(
                                                    new FkMethods("GET", new ActivityGet())
                                            )),
                                            new FkRegex(base + "/api/auth-endpoint", new TkFork(
                                                    new FkMethods("GET", new AuthEndpointGet(address, getConfigurationUrl()))
                                            )),
                                            new FkRegex(base + "/api/backup/pause", new TkFork(
                                                    new FkMethods("GET", new BackupPauseGet()))),
                                            new FkRegex(base + "/api/backup-download/.*", new TkFork(
                                                    new FkMethods("POST", new BackupDownloadPost(base)))),
                                            new FkRegex(base + "/api/backup-files(/.*)?", new TkFork(
                                                    new FkMethods("GET", new ListBackupFilesGet(base)),
                                                    new FkMethods("DELETE", new BackupFilesDelete(base))
                                            )),
                                            new FkRegex(base + "/api/backup-versions(/.*)?", new TkFork(
                                                    new FkMethods("GET", new ListBackupVersionsGet(base))
                                            )),
                                            new FkRegex(base + "/api/configuration", new TkFork(
                                                    new FkMethods("GET", new ConfigurationGet()),
                                                    new FkMethods("POST", new ConfigurationPost()))),
                                            new FkRegex(base + "/api/state", new TkFork(
                                                    new FkMethods("GET", new StateGet())
                                            )),
                                            new FkRegex(base + "/api/encryption-key", new TkFork(
                                                    new FkMethods("POST", new KeyPost()),
                                                    new FkMethods("PUT", new GenerateKeyPut()))),
                                            new FkRegex(base + "/api/encryption-key/additional", new TkFork(
                                                    new FkMethods("PUT", new AdditionalKeyPut()),
                                                    new FkMethods("POST", new AdditionalKeysPost()))),
                                            new FkRegex(base + "/api/encryption-key/change", new TkFork(
                                                    new FkMethods("POST", new KeyChangePost()))),
                                            new FkRegex(base + "/api/local-files(/.*)?", new TkFork(
                                                    new FkMethods("GET", new ListLocalFilesGet(base))
                                            )),
                                            new FkRegex(base + "/api/rebuild-available", new TkFork(
                                                    new FkMethods("GET", new RebuildAvailableGet())
                                            )),
                                            new FkRegex(base + "/api/remote-configuration/rebuild", new TkFork(
                                                    new FkMethods("POST", new RemoteRestorePost()))),
                                            new FkRegex(base + "/api/remote-configuration", new TkFork(
                                                    new FkMethods("GET", new RemoteConfigurationGet())
                                            )),
                                            new FkRegex(base + "/api/restore", new TkFork(
                                                    new FkMethods("POST", new RestorePost()))),
                                            new FkRegex(base + "/api/trim", new TkFork(
                                                    new FkMethods("POST", new TrimPost()))),
                                            new FkRegex(base + "/api/optimize", new TkFork(
                                                    new FkMethods("POST", new OptimizePost()))),
                                            new FkRegex(base + "/api/validate-blocks", new TkFork(
                                                    new FkMethods("POST", new ValidateBlocksPost()))),
                                            new FkRegex(base + "/api/defrag", new TkFork(
                                                    new FkMethods("POST", new DefragPost()))),
                                            new FkRegex(base + "/api/repair", new TkFork(
                                                    new FkMethods("POST", new RepairPost()))),
                                            new FkRegex(base + "/api/search-backup", new TkFork(
                                                    new FkMethods("GET", new SearchBackupFilesGet(base))
                                            )),
                                            new FkRegex(base + "/api/sets/restart", new TkFork(
                                                    new FkMethods("POST", new RestartSetsPost()))),
                                            new FkRegex(base + "/api/shares", new TkFork(
                                                    new FkMethods("GET", new ActiveSharesGet()),
                                                    new FkMethods("POST", new ActivateSharesPost()))),
                                            new FkRegex(base + "/api/shutdown", new TkFork(
                                                    new FkMethods("GET", new ShutdownGet())
                                            )),
                                            new FkRegex(base + "/api/sources/[^\\/]*", new TkFork(
                                                    new FkMethods("POST", new SourceSelectPost(base))
                                            )),

                                            new FkRegex(base + "/api/service/best-region", new TkFork(
                                                    new FkMethods("GET", new BestRegionGet()))),
                                            new FkRegex(base + "/api/service/token", new TkFork(
                                                    new FkMethods("POST", new GenerateTokenPost()),
                                                    new FkMethods("DELETE", new TokenDelete()))),
                                            new FkRegex(base + "/api/service/sources", new TkFork(
                                                    new FkMethods("GET", new SourcesGet()),
                                                    new FkMethods("POST", new SourcesPost()),
                                                    new FkMethods("PUT", new SourcesPut()))),
                                            new FkRegex(base + "/api/service/shares", new TkFork(
                                                    new FkMethods("GET", new SharesGet()))),
                                            new FkRegex(base + "/api/service/secrets", new TkFork(
                                                    new FkMethods("POST", new GetSecretPost()),
                                                    new FkMethods("DELETE", new DeleteSecretPost()),
                                                    new FkMethods("PUT", new CreateSecretPut()))),
                                            new FkRegex(base + "/api/service/support", new TkFork(
                                                    new FkMethods("POST", new SupportBundlePost()))),
                                            new FkRegex(base + "/api/service/version", new TkFork(
                                                    new FkMethods("GET", new VersionCheckGet()))),

                                            createIndexPath(base),

                                            createIndexPath(base + "/destinations"),
                                            createIndexPath(base + "/restore"),
                                            createIndexPath(base + "/sets"),
                                            createIndexPath(base + "/status"),
                                            createIndexPath(base + "/settings"),
                                            createIndexPath(base + "/share"),
                                            createIndexPath(base + "/sources"),

                                            createIndexPath(base + "/connect"),
                                            createIndexPath(base + "/source"),
                                            createIndexPath(base + "/destination"),
                                            createIndexPath(base + "/security"),
                                            createIndexPath(base + "/contents"),

                                            createIndexPath(base + "/authorizeaccept"),

                                            createFiletypePath("css", "text/css"),
                                            createFiletypePath("html", "text/html"),
                                            createFiletypePath("js", "text/javascript"),
                                            createFiletypePath("woff", "font/woff"),
                                            createFiletypePath("woff2", "font/woff2"),
                                            createFiletypePath("ttf", "font/ttf"),
                                            createFiletypePath("ico", "image/x-icon"),
                                            createFiletypePath("webmanifest", "application/manifest+json"),

                                            new FkRegex("/favicon.+\\.ico",
                                                    new TkFork(
                                                            new FkMethods("GET",
                                                                    new TkWithType(new StrippedPrefixClasspath("", "/web"), "image/x-icon"))
                                                    )),
                                            new FkRegex("/manifest\\.webmanifest",
                                                    new TkFork(
                                                            new FkMethods("GET",
                                                                    new TkWithType(new StrippedPrefixClasspath("", "/web"), "application/manifest+json"))
                                                    ))
                                    ),
                                    new PsChain(
                                            new PsUnauthedContent(base),
                                            new PsAuthedContent()
                                    )
                            )
                    )
            );

            Thread thread = new Thread(() -> {
                FtBasic basic = new FtBasic(new BkParallel(new BkSafe(new BkBasic(serviceTake)), 6), socket);
                try {
                    basic.start(() -> false);
                } catch (IOException e) {
                    log.error("Web server error", e);
                }
            }, "Webserver");
            thread.setDaemon(true);
            thread.start();

            URI configUrl = getConfigurationUrl();

            log.info("URL for configuration: " + configUrl);

            try {
                File urlFile = new File(InstanceFactory.getInstance(CommandLineModule.URL_LOCATION));
                createDirectory(urlFile.getParentFile(), false);
                try (FileWriter writer = new FileWriter(urlFile, StandardCharsets.UTF_8)) {
                    writer.write(configUrl.toString());
                    writer.write("\n");
                }
            } catch (Exception exc) {
                log.warn("Failed to write configuration location to disk", exc);
            }
        }
    }

    /**
     * Creates a fork for handling requests to an index path.
     * Maps the path to the web/index.html resource with the appropriate content type.
     *
     * @param base The base path to create the index path for
     * @return A Fork that handles requests to the specified path
     */
    private Fork createIndexPath(String base) {
        return new FkRegex(base, new TkFork(
                new FkMethods("GET", new TkWithType(new StrippedPrefixClasspath(base, "/web/index.html"),
                        "text/html"))
        ));
    }

    /**
     * Launches the web interface in the default browser.
     * Opens the configuration URL in the system's default web browser.
     */
    public void launchPage() {
        UIHandler.openUri(getConfigurationUrl());
    }

    /**
     * Gets the URL for accessing the web interface.
     * Constructs a URI using the server's hostname, port, and base path.
     *
     * @return The URI for accessing the web interface
     */
    private URI getConfigurationUrl() {
        InetAddress address = getInetAddress();
        String hostname = address.getHostName();
        if (address.isAnyLocalAddress()) {
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException ignored) {
            }
        }
        try {
            return new URI("http://" + hostname + ":"
                    + socket.getLocalPort() + base + "/");
        } catch (URISyntaxException e) {
            throw new RuntimeException("Internal exception, failed to create website URI", e);
        }
    }

    /**
     * Creates a fork for handling requests to files with a specific extension.
     * Maps the path pattern to the appropriate resource with the specified content type.
     *
     * @param extension The file extension to handle
     * @param contentType The content type to use for the response
     * @return A Fork that handles requests to files with the specified extension
     */
    private Fork createFiletypePath(String extension, String contentType) {
        return new FkRegex(base + "/[^\\/].+\\." + extension,
                new TkFork(
                        new FkMethods("GET",
                                new TkWithType(new StrippedPrefixClasspath(base, "/web"), contentType))
                ));
    }

    private static class PsUnauthedContent implements Pass {

        private final String base;
        private final java.util.List<String> allowedPathMethods;

        /**
         * Creates a new PsUnauthedContent instance.
         * This Pass implementation allows unauthenticated access to specific API endpoints.
         *
         * @param base The base path for API endpoints
         */
        public PsUnauthedContent(String base) {
            this.base = base + "/api/";
            this.allowedPathMethods = Lists.newArrayList(
                    this.base + "ping GET",
                    this.base + "auth POST"
            );
        }

        /**
         * Determines if the request should be allowed without authentication.
         * Allows access to specific API endpoints without authentication.
         *
         * @param request The incoming request
         * @return An empty Opt if authentication is required, or AUTHENTICATED if the path is allowed without authentication
         * @throws Exception If an error occurs while processing the request
         */
        @Override
        public Opt<Identity> enter(Request request) throws Exception {
            String path = new RqHref.Base(request).href().path();
            String pathMethod = path + " " + new RqMethod.Base(request).method();
            if (path.startsWith(base) && (!allowedPathMethods.contains(pathMethod))) {
                return new Opt.Empty<Identity>();
            }
            return AUTHENTICATED;
        }

        /**
         * Processes the response after authentication.
         *
         * @param response The response to process
         * @param identity The identity of the authenticated user
         * @return The processed response
         * @throws Exception If an error occurs while processing the response
         */
        @Override
        public Response exit(Response response, Identity identity) throws Exception {
            return response;
        }
    }
}
