package com.underscoreresearch.backup.cli.commands;

import com.underscoreresearch.backup.cli.CommandPlugin;

@CommandPlugin(value = "version", description = "Display version of application",
        needPrivateKey = false, needConfiguration = false)
public class VersionCommand extends SimpleCommand {
    private static final String VERSION = "0.2.2";

    public void executeCommand() {
        System.out.println("Version " + VERSION);
    }
}
