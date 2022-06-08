package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.cli.commands.GenerateKeyCommand.getDefaultEncryptionFileName;

import java.io.File;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.cli.PassphraseReader;
import com.underscoreresearch.backup.cli.web.ConfigurationPost;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.PublicKeyEncrypion;

@CommandPlugin(value = "change-passphrase", description = "Change the passphrase of an existing key",
        needConfiguration = false)
public class ChangePassphraseCommand extends Command {
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
        String file = generateAndSaveNewKey(commandLine, firstTry);

        System.out.println("Wrote public key to " + file);
    }

    public static String generateAndSaveNewKey(CommandLine commandLine, String firstTry) throws IOException {
        PublicKeyEncrypion key = InstanceFactory.getInstance(PublicKeyEncrypion.class);
        PublicKeyEncrypion publicKeyEncrypion = PublicKeyEncrypion.changeEncryptionPassphrase(firstTry, key);

        File keyFile = getDefaultEncryptionFileName(commandLine);

        new ObjectMapper().writeValue(keyFile,
                publicKeyEncrypion.publicOnly());

        ConfigurationPost.setReadOnlyFilePermissions(keyFile);

        return keyFile.getAbsolutePath();
    }
}
