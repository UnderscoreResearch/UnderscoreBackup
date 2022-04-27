package com.underscoreresearch.backup.cli.commands;

import java.io.IOException;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.cli.CommandPlugin;

@Slf4j
@CommandPlugin(value = "version", description = "Display version of application",
        needPrivateKey = false, needConfiguration = false)
public class VersionCommand extends SimpleCommand {
    public static String getVersion() {


        Properties prop = new Properties();
        try {
            prop.load(VersionCommand.class.getClassLoader().getResourceAsStream("version.properties"));

            String version = prop.getProperty("version");
            if (version != null) {
                return version;
            }
        }
        catch (IOException exc) {
            log.warn("Failed to read version", exc);
        }

        return "DEVELOPMENT";
    }

    public void executeCommand() {
        System.out.println("Version " + getVersion());
    }
}
