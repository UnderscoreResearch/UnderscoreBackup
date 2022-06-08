package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.configuration.CommandLineModule.KEY;

import java.io.File;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.cli.PassphraseReader;
import com.underscoreresearch.backup.cli.web.ConfigurationPost;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.EncryptionModule;
import com.underscoreresearch.backup.encryption.PublicKeyEncrypion;

@CommandPlugin(value = "generate-key", description = "Generate a new public key and store it to disk",
        needPrivateKey = false, needConfiguration = false)
public class GenerateKeyCommand extends Command {
    public void executeCommand(CommandLine commandLine) throws Exception {
        if (commandLine.getArgList().size() != 1) {
            throw new ParseException("Too many arguments for command");
        }

        String firstTry;
        if (commandLine.hasOption(CommandLineModule.PRIVATE_KEY_SEED)) {
            firstTry = commandLine.getOptionValue(CommandLineModule.PRIVATE_KEY_SEED);
        } else {
            firstTry = PassphraseReader.readPassphrase("Please enter the passphrase for the private key: ");
            if (firstTry == null) {
                System.exit(1);
            }
            String secondTry
                    = PassphraseReader.readPassphrase("Reenter the passphrase for the private key: ");
            if (secondTry == null) {
                System.exit(1);
            }
            if (!firstTry.equals(secondTry)) {
                System.out.println("Passphrases do not match");
                System.exit(1);
            }
        }
        String file = generateAndSaveNewKey(commandLine, firstTry);

        System.out.println("Wrote public key to " + file);
    }

    public static String generateAndSaveNewKey(CommandLine commandLine, String firstTry) throws IOException {
        PublicKeyEncrypion publicKeyEncrypion = PublicKeyEncrypion.generateKeyWithPassphrase(firstTry, null);

        File keyFile = getDefaultEncryptionFileName(commandLine);

        new ObjectMapper().writeValue(keyFile,
                publicKeyEncrypion.publicOnly());

        ConfigurationPost.setReadOnlyFilePermissions(keyFile);

        return keyFile.getAbsolutePath();
    }

    public static File getDefaultEncryptionFileName(CommandLine commandLine) {
        String file = commandLine.getOptionValue(KEY);
        if (Strings.isNullOrEmpty(file)) {
            file = EncryptionModule.DEFAULT_KEY_FILES[0];
        }

        File keyFile = new File(file);
        if (!keyFile.getParentFile().isDirectory())
            keyFile.getParentFile().mkdirs();

        return keyFile;
    }
}
