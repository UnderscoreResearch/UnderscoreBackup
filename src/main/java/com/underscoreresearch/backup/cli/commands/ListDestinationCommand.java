package com.underscoreresearch.backup.cli.commands;

import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.io.IOProviderFactory;

@CommandPlugin(value = "list-destination", description = "Display the list of supported destination types",
        needPrivateKey = false, needConfiguration = false)
public class ListDestinationCommand extends SimpleCommand {
    public void executeCommand() throws Exception {
        System.out.println("Supported destination types: " + String.join(", ", IOProviderFactory.supportedProviders()));
    }
}
