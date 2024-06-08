package com.underscoreresearch.backup.cli.commands;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.IdentityKeys;

@CommandPlugin(value = "list-keys", description = "List additional generated keys",
        needPrivateKey = true, needConfiguration = true)
public class ListKeysCommand extends Command {
    public void executeCommand(CommandLine commandLine) throws Exception {
        if (commandLine.getArgList().size() != 1) {
            throw new ParseException("Too many arguments for command");
        }

        EncryptionIdentity identity = InstanceFactory.getInstance(EncryptionIdentity.class);
        EncryptionIdentity.PrivateIdentity privateIdentity = identity.getPrivateIdentity(getPassword());

        int i = 1;
        for (IdentityKeys key : identity.getAdditionalKeys()) {
            System.out.printf("Public key %d : %s%n", i, key.getPublicKeyString());
            if (key.hasPrivateKey()) {
                System.out.printf("Private key %d: %s%n%n", i, key.getPrivateKeyString(privateIdentity));
            }
            i++;
        }
    }
}
