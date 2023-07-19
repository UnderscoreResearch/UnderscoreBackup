package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.cli.commands.ConfigureCommand.getConfigurationUrl;

import java.net.HttpURLConnection;
import java.net.URL;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.cli.CommandPlugin;

@CommandPlugin(value = "shutdown", description = "Shutdown the daemon",
        needConfiguration = false, needPrivateKey = false)
@Slf4j
public class ShutdownCommand extends SimpleCommand {
    @Override
    protected void executeCommand() throws Exception {
        try {
            String configurationUrl = getConfigurationUrl();

            String shutdownUrl = configurationUrl + "api/shutdown";
            URL url = new URL(shutdownUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();
            if (connection.getResponseCode() != 200) {
                log.error("Failed to shut down process: {}", connection.getResponseCode());
                System.exit(2);
            }

            // Wait for 10 seconds for the process to shut down 100ms at a time.
            for (int i = 0; i < 100; i++) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException exc) {
                    log.error("Interrupted while waiting for shutdown", exc);
                }
                try {
                    getConfigurationUrl();
                } catch (ConfigureCommand.ConfigurationUrlException exc) {
                    // When the config file disappears the shutdown should be done.
                    System.exit(0);
                }
            }
            log.error("Failed to shut down process");
            System.exit(1);
        } catch (ConfigureCommand.ConfigurationUrlException exc) {
            log.error(exc.getMessage());
            System.exit(0);
        } catch (Exception exc) {
            log.error("Encountered issue reading configuration URL", exc);
            System.exit(1);
        }
    }
}
