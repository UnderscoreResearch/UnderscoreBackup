package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.cli.commands.GenerateKeyCommand.getDefaultEncryptionFileName;
import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_WRITER;

import java.io.File;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.cli.PassphraseReader;
import com.underscoreresearch.backup.cli.web.ConfigurationPost;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;

@CommandPlugin(value = "change-passphrase", description = "Change the passphrase of an existing key",
        needConfiguration = false)
public class ChangePassphraseCommand extends Command {
    public static String generateAndSaveNewKey(CommandLine commandLine, String oldPassword, String newPassword) throws IOException {
        EncryptionKey key = InstanceFactory.getInstance(EncryptionKey.class);

        EncryptionKey encryptionKey = EncryptionKey.changeEncryptionPassphrase(oldPassword, newPassword, key);

        File keyFile = getDefaultEncryptionFileName(commandLine);

        ENCRYPTION_KEY_WRITER.writeValue(keyFile,
                encryptionKey.publicOnly());

        ConfigurationPost.setReadOnlyFilePermissions(keyFile);

        return keyFile.getAbsolutePath();
    }

    public void executeCommand(CommandLine commandLine) throws Exception {
        if (commandLine.getArgList().size() != 1) {
            throw new ParseException("Too many arguments for command");
        }

        String firstTry = PassphraseReader.readPassphrase("Please enter the new passphrase for the private key: ");
        if (firstTry == null) {
            System.exit(1);
        }
        String secondTry
                = PassphraseReader.readPassphrase("Reenter the new passphrase for the private key: ");
        if (secondTry == null) {
            System.exit(1);
        }
        if (!firstTry.equals(secondTry)) {
            System.out.println("Passphrases do not match");
            System.exit(1);
        }
        String file = generateAndSaveNewKey(commandLine, getPassphrase(), firstTry);

        System.out.println("Wrote public key to " + file);
    }
}
