package com.underscoreresearch.backup.cli.commands;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;

@CommandPlugin(value = "list-keys", description = "List additional generated keys",
        needPrivateKey = true, needConfiguration = true)
public class ListKeysCommand extends Command {
    public void executeCommand(CommandLine commandLine) throws Exception {
        if (commandLine.getArgList().size() != 1) {
            throw new ParseException("Too many arguments for command");
        }

        EncryptionKey encryptionKey = InstanceFactory.getInstance(EncryptionKey.class);

        System.out.println("Public key (Used by sharer)                          Private key (Used by consumers of share)");
        System.out.println("---------------------------------------------------- -----------------------------------------------------");
        for (EncryptionKey key : encryptionKey.getPrivateKey(getPassword()).getAdditionalKeyManager().getKeys()) {
            System.out.println(String.format("%s %s", key.getPublicKey(), key.getPrivateKey(null).getDisplayPrivateKey()));
        }
    }
}
