package com.underscoreresearch.backup.cli.commands;

import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.encryption.EncryptorFactory;

@CommandPlugin(value = "list-encryption", description = "Display the list of supported encryption types",
        needPrivateKey = false, needConfiguration = false)
public class ListEncryptionCommand extends SimpleCommand {
    public void executeCommand() throws Exception {
        System.out.println("Supported encryption types: " + String.join(", ", EncryptorFactory.supportedEncryptions()));
    }
}
