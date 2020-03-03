package com.underscoreresearch.backup.cli.commands;

import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.errorcorrection.ErrorCorrectorFactory;

@CommandPlugin(value = "list-error-correction", description = "Display the list of supported error correction types",
        needPrivateKey = false, needConfiguration = false)
public class ListErrorCorrectionCommand extends SimpleCommand {
    public void executeCommand() throws Exception {
        System.out.println("Supported error correction types: " + String.join(", ",
                ErrorCorrectorFactory.supportedCorrectors()));
    }
}
