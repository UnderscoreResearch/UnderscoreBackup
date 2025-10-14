package com.underscoreresearch.backup.ui.commands;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Properties;

/**
 * Command that displays the version information of the application.
 * This command reads version information from a properties file and
 * displays it to the user.
 */
@Slf4j
@CommandPlugin(value = "version", description = "Display version of application",
        needPrivateKey = false, needConfiguration = false)
public class VersionCommand extends SimpleCommand {
    private static String version;
    private static String edition;

    /**
     * Loads version properties from the version.properties resource file.
     * Sets default values if the properties cannot be loaded so it can run in a dev build from an IDE.
     */
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

    /**
     * Gets the version of the application.
     * Loads the properties if they haven't been loaded yet.
     *
     * @return The version string
     */
    public static String getVersion() {
        if (version == null) {
            loadProperties();
        }

        return version;
    }

    /**
     * Gets the edition of the application.
     * Loads the properties if they haven't been loaded yet.
     *
     * @return The edition string
     */
    public static String getEdition() {
        if (edition == null) {
            loadProperties();
        }
        return edition;
    }

    /**
     * Gets the combined version and edition string.
     * If there is no edition, returns just the version.
     *
     * @return The combined version and edition string
     */
    public static String getVersionEdition() {
        String edition = getEdition();
        if (edition.isEmpty())
            return getVersion();
        else
            return getVersion() + "-" + edition;
    }

    /**
     * Executes the command to display the version information.
     */
    public void executeCommand() {
        System.out.println("Version " + getVersionEdition());
    }
}
