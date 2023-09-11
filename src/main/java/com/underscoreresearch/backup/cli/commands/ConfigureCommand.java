package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.configuration.CommandLineModule.URL_LOCATION;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.SystemUtils;

import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.cli.web.AuthPost;
import com.underscoreresearch.backup.configuration.InstanceFactory;

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

            System.err.println("Configuration URL is available at:");
            System.err.println();
            System.out.println(url);

            try {
                if (SystemUtils.IS_OS_MAC_OSX) {
                    Runtime.getRuntime().exec(new String[]{"open", url.trim()});
                } else {
                    Desktop.getDesktop().browse(new URI(url.trim()));
                }
            } catch (IOException | UnsupportedOperationException ignored) {
            }
        } catch (ConfigurationUrlException exc) {
            log.error(exc.getMessage());
            System.exit(1);
        } catch (Exception exc) {
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
