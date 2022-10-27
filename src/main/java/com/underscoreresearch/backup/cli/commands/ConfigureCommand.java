package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.configuration.CommandLineModule.URL_LOCATION;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.SystemUtils;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;

@CommandPlugin(value = "configure", description = "Open configuration interface",
        needConfiguration = false, needPrivateKey = false)
@Slf4j
public class ConfigureCommand extends Command {
    public void executeCommand(CommandLine commandLine) throws Exception {
        if (commandLine.getArgList().size() > 1) {
            throw new ParseException("Too many arguments for command");
        }

        try {
            File configFile = new File(InstanceFactory.getInstance(URL_LOCATION));
            if (!configFile.exists()) {
                System.err.println("Daemon does not appear to be running");
                System.exit(1);
            }
            if (!configFile.canRead()) {
                System.err.println("No permissions to access configuration interface");
                System.exit(1);
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                String url = reader.readLine();

                System.err.println("Configuration URL is available at:");
                System.err.println();
                System.out.println(url);

                try {
                    if (SystemUtils.IS_OS_MAC_OSX) {
                        Runtime.getRuntime().exec(new String[]{"open", url.trim()});
                    } else {
                        Desktop.getDesktop().browse(new URI(url.trim()));
                    }
                } catch (IOException | UnsupportedOperationException e) {
                }
            }
        } catch (Exception exc) {
            log.error("Encountered issue reading configuration URL", exc);
            System.exit(1);
        }
    }
}
