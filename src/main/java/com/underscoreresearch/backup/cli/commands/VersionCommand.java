package com.underscoreresearch.backup.cli.commands;

import java.io.IOException;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.cli.CommandPlugin;

@Slf4j
@CommandPlugin(value = "version", description = "Display version of application",
        needPrivateKey = false, needConfiguration = false)
public class VersionCommand extends SimpleCommand {
    private static String version;
    private static String edition;

    private static synchronized void loadProperties() {
        Properties prop = new Properties();
        try {
            prop.load(VersionCommand.class.getClassLoader().getResourceAsStream("version.properties"));

            version = prop.getProperty("version");
            edition = prop.getProperty("edition");
            if (version == null) {
                version = "DEVELOPMENT";
            }
            if (edition == null) {
                edition = "";
            }
        } catch (IOException exc) {
            log.warn("Failed to read version", exc);

            version = "DEVELOPMENT";
            edition = "";
        }
    }

    public static String getVersion() {
        if (version == null) {
            loadProperties();
        }

        return version;
    }

    public static String getEdition() {
        if (edition == null) {
            loadProperties();
        }
        return edition;
    }

    public static String getVersionEdition() {
        String edition = getEdition();
        if (edition.isEmpty())
            return getVersion();
        else
            return getVersion() + "-" + edition;
    }

    public void executeCommand() {
        System.out.println("Version " + getVersionEdition());
    }
}
