package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.configuration.CommandLineModule.BIND_ADDRESS;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang.SystemUtils;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.facets.auth.Identity;
import org.takes.facets.auth.Pass;
import org.takes.facets.auth.PsBasic;
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
import org.takes.tk.TkWithType;

import com.underscoreresearch.backup.cli.web.service.BestRegionGet;
import com.underscoreresearch.backup.cli.web.service.CreateSecretPut;
import com.underscoreresearch.backup.cli.web.service.GenerateTokenPost;
import com.underscoreresearch.backup.cli.web.service.GetSecretPost;
import com.underscoreresearch.backup.cli.web.service.SharesGet;
import com.underscoreresearch.backup.cli.web.service.SourcesGet;
import com.underscoreresearch.backup.cli.web.service.SourcesPost;
import com.underscoreresearch.backup.cli.web.service.SourcesPut;
import com.underscoreresearch.backup.cli.web.service.TokenDelete;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.model.BackupConfiguration;

@Slf4j
public class WebServer {
    private static final Opt<Identity> AUTHENTICATED = new Opt.Single<Identity>(new Identity.Simple("urn:users:root"));
    private static WebServer server;
    private ServerSocket socket;
    private String base;

    public static synchronized WebServer getInstance() {
        if (server == null) {
            server = new WebServer();
        }
        return server;
    }

    public void start(boolean developerMode) {
        if (socket == null) {
            InetAddress address = getInetAddress();

            if (developerMode) {
                try {
                    socket = new ServerSocket(12345, 10, address);
                } catch (IOException e) {
                    throw new RuntimeException("Can't find local host address", e);
                }

                base = "/fixed";
            } else {
                SecureRandom random = new SecureRandom();

                try {
                    socket = new ServerSocket(0, 10, address);
                } catch (IOException e) {
                    throw new RuntimeException("Can't find local host address", e);
                }

                byte[] bytes = new byte[32];
                random.nextBytes(bytes);
                base = "/" + Hash.encodeBytes(bytes);
            }

            Take serviceTake = new TkForward(
                    new TkAuth(
                            new TkFork(
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
                                            new FkMethods("GET", new ListBackupFilesGet(base))
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
                                    new FkRegex(base + "/api/ping", new TkFork(
                                            new FkMethods("GET", new PingGet()),
                                            new FkMethods("OPTIONS", new PingOptions())
                                    )),
                                    new FkRegex(base + "/api/restore", new TkFork(
                                            new FkMethods("POST", new RestorePost()))),
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
                                            new FkMethods("PUT", new CreateSecretPut()))),

                                    createIndexPath(base),
                                    createIndexPath(base + "/destinations"),
                                    createIndexPath(base + "/restore"),
                                    createIndexPath(base + "/sets"),
                                    createIndexPath(base + "/status"),
                                    createIndexPath(base + "/settings"),
                                    createIndexPath(base + "/share"),
                                    createIndexPath(base + "/sources"),
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
                                    new PsNoAuthConfigured(),
                                    new PsBasic("backup", (user, pwd) -> {
                                        BackupConfiguration config = InstanceFactory.getInstance(BackupConfiguration.class);
                                        String configUser = config.getManifest().getConfigUser();
                                        String configPassword = config.getManifest().getConfigPassword();
                                        if (!configUser.equals(user) || !configPassword.equals(pwd)) {
                                            return new Opt.Empty<Identity>();
                                        }
                                        return AUTHENTICATED;
                                    })
                            )
                    )
            );

            Thread thread = new Thread(() -> {
                FtBasic basic = new FtBasic(new BkParallel(new BkSafe(new BkBasic(serviceTake)), 2), socket);
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
                urlFile.getParentFile().mkdirs();
                urlFile.deleteOnExit();
                try (FileWriter writer = new FileWriter(urlFile, StandardCharsets.UTF_8)) {
                    writer.write(configUrl.toString());
                    writer.write("\n");
                }
                ConfigurationPost.setReadOnlyFilePermissions(urlFile);
            } catch (Exception exc) {
                log.warn("Failed to write configuration location to disk", exc);
            }
        }
    }

    private static InetAddress getInetAddress() {
        CommandLine commandLine = InstanceFactory.getInstance(CommandLine.class);

        if (commandLine.hasOption(BIND_ADDRESS)) {
            try {
                return InetAddress.getByName(commandLine.getOptionValue(BIND_ADDRESS));
            } catch (UnknownHostException e) {
                throw new RuntimeException(String.format("Failed to resolve %s",
                        commandLine.getOptionValue(BIND_ADDRESS)), e);
            }
        } else
            return InetAddress.getLoopbackAddress();
    }

    private Fork createIndexPath(String base) {
        return new FkRegex(base, new TkFork(
                new FkMethods("GET", new TkWithType(new StrippedPrefixClasspath(base, "/web/index.html"),
                        "text/html"))
        ));
    }

    public void launchPage() {
        try {
            URI uri = getConfigurationUrl();
            if (SystemUtils.IS_OS_MAC_OSX) {
                Runtime.getRuntime().exec(new String[]{"open", uri.toString()});
            } else {
                Desktop.getDesktop().browse(uri);
            }
        } catch (IOException | HeadlessException e) {
            log.error("Can't launch browser", e);
        }
    }

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

    private Fork createFiletypePath(String extension, String contentType) {
        return new FkRegex(base + "/[^\\/].+\\." + extension,
                new TkFork(
                        new FkMethods("GET",
                                new TkWithType(new StrippedPrefixClasspath(base, "/web"), contentType))
                ));
    }

    private static class PsNoAuthConfigured implements Pass {

        @Override
        public Opt<Identity> enter(Request request) throws Exception {
            try {
                if (InstanceFactory.hasConfiguration(false)) {
                    BackupConfiguration config = InstanceFactory.getInstance(BackupConfiguration.class);
                    String configUser = config.getManifest().getConfigUser();
                    String configPassword = config.getManifest().getConfigPassword();
                    if (configUser != null && configPassword != null) {
                        return new Opt.Empty<Identity>();
                    }
                }
            } catch (Exception exc) {
            }
            return AUTHENTICATED;
        }

        @Override
        public Response exit(Response response, Identity identity) throws Exception {
            return response;
        }
    }
}
