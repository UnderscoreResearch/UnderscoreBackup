package com.underscoreresearch.backup.cli.commands;

import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.cli.ui.UIHandler;
import com.underscoreresearch.backup.cli.web.AuthPost;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import static com.underscoreresearch.backup.cli.commands.InteractiveCommand.suppressedOpen;
import static com.underscoreresearch.backup.configuration.CommandLineModule.URL_LOCATION;
import static com.underscoreresearch.backup.utils.LogUtil.debug;

@CommandPlugin(value = "configure", description = "Open configuration interface",
        needConfiguration = false, needPrivateKey = false)
@Slf4j
public class ConfigureCommand extends SimpleCommand {
    public static String getConfigurationUrl() throws IOException {
        File configFile = new File(InstanceFactory.getInstance(URL_LOCATION));
        if (!configFile.exists()) {
            throw new ConfigurationUrlException("Daemon does not appear to be running");
        }
        if (!configFile.canRead()) {
            throw new ConfigurationUrlException("No permissions to access configuration interface");
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String url = reader.readLine();

            return url.trim();
        }
    }

    public static void validateConfigurationUrl(String configurationUrl) throws ConfigurationUrlException {
        try {
            URL url = new URI(configurationUrl + "api/ping").toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            if (connection.getResponseCode() != 200) {
                throw new IOException("Got response code " + connection.getResponseCode());
            }
        } catch (IOException exc) {
            debug(() -> log.debug("Failed pinging daemon"));
            throw new ConfigurationUrlException("Daemon does not appear to be running");
        } catch (Exception exc) {
            debug(() -> log.debug("Failed pinging daemon", exc));
            throw new ConfigurationUrlException("Failed to ping daemon");
        }
    }

    public static void reloadIfRunning() throws IOException {
        try {
            String configurationUrl = getConfigurationUrl();

            try {
                AuthPost.performAuthenticatedRequest(configurationUrl, "POST", "api/ping", null);
                log.info("Reloaded configuration for background application");
            } catch (IOException exc) {
                log.error("Failed to reload configuration for running backup, restart manually");
            }
        } catch (ConfigurationUrlException ignored) {
        }
    }

    @Override
    protected void executeCommand() throws Exception {
        try {
            String url = getConfigurationUrl();

            validateConfigurationUrl(url);

            System.err.println("Configuration URL is available at:");
            System.err.println();
            System.out.println(url);

            if (!suppressedOpen()) {
                try {
                    UIHandler.setup();
                    UIHandler.openUri(new URI(url.trim()));
                } catch (URISyntaxException ignored) {
                }
            }
        } catch (ConfigurationUrlException exc) {
            log.error(exc.getMessage());
            System.exit(1);
        } catch (UnsupportedOperationException exc) {
            log.error("Encountered issue reading configuration URL", exc);
            System.exit(1);
        }
    }

    public static class ConfigurationUrlException extends IOException {
        public ConfigurationUrlException(String message) {
            super(message);
        }
    }
}
