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

    public static String getVersion() {
        Properties prop = new Properties();
        try {
            prop.load(VersionCommand.class.getClassLoader().getResourceAsStream("version.properties"));

            if (version == null)
                version = prop.getProperty("version");
            if (version != null) {
                return version;
            }
        } catch (IOException exc) {
            log.warn("Failed to read version", exc);
        }

        return "DEVELOPMENT";
    }

    public static String getEdition() {


        Properties prop = new Properties();
        try {
            prop.load(VersionCommand.class.getClassLoader().getResourceAsStream("version.properties"));

            if (edition == null)
                edition = prop.getProperty("edition");
            if (edition != null) {
                return edition;
            }
        } catch (IOException exc) {
            log.warn("Failed to read edition", exc);
        }

        return "DEVELOPMENT";
    }

    public void executeCommand() {
        System.out.println("Version " + getVersion() + getEdition());
    }
}
