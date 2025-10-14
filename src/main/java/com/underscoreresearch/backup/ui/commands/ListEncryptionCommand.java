package com.underscoreresearch.backup.ui.commands;

import com.underscoreresearch.backup.encryption.EncryptorFactory;

/**
 * Command that lists all supported encryption types for backup data.
 * This command displays the available encryption algorithms that can be used
 * to secure backup data.
 */
@CommandPlugin(value = "list-encryption", description = "Display the list of supported encryption types",
        needPrivateKey = false, needConfiguration = false)
public class ListEncryptionCommand extends SimpleCommand {
    /**
     * Executes the command to list all supported encryption types.
     * 
     * @throws Exception If an error occurs while retrieving the encryption types
     */
    public void executeCommand() throws Exception {
        System.out.println("Supported encryption types: " + String.join(", ", EncryptorFactory.supportedEncryptions()));
    }
}
