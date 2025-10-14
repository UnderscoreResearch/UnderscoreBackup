package com.underscoreresearch.backup.ui.commands;

import com.underscoreresearch.backup.io.IOProviderFactory;

/**
 * Command that lists all supported destination types for backup storage.
 * This command displays the available destination types that can be used
 * in the backup configuration.
 */
@CommandPlugin(value = "list-destination", description = "Display the list of supported destination types",
        needPrivateKey = false, needConfiguration = false)
public class ListDestinationCommand extends SimpleCommand {
    /**
     * Executes the command to list all supported destination types.
     * 
     * @throws Exception If an error occurs while retrieving the destination types
     */
    public void executeCommand() throws Exception {
        System.out.println("Supported destination types: " + String.join(", ", IOProviderFactory.supportedProviders()));
    }
}
