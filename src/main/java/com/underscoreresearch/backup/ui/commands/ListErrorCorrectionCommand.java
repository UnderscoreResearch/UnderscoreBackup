package com.underscoreresearch.backup.ui.commands;

import com.underscoreresearch.backup.errorcorrection.ErrorCorrectorFactory;

/**
 * Command that lists all supported error correction types for backup data.
 * This command displays the available error correction algorithms that can be used
 * to ensure data integrity in backup storage.
 */
@CommandPlugin(value = "list-error-correction", description = "Display the list of supported error correction types",
        needPrivateKey = false, needConfiguration = false)
public class ListErrorCorrectionCommand extends SimpleCommand {
    /**
     * Executes the command to list all supported error correction types.
     * 
     * @throws Exception If an error occurs while retrieving the error correction types
     */
    public void executeCommand() throws Exception {
        System.out.println("Supported error correction types: " + String.join(", ",
                ErrorCorrectorFactory.supportedCorrectors()));
    }
}
