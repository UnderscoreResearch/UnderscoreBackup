package com.underscoreresearch.backup.cli.commands;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.cli.PasswordReader;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.EncryptionModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.manifest.ManifestManager;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

import static com.underscoreresearch.backup.configuration.CommandLineModule.ADDITIONAL_KEY;
import static com.underscoreresearch.backup.configuration.CommandLineModule.ENCRYPTION_KEY_DATA;
import static com.underscoreresearch.backup.configuration.CommandLineModule.KEY;
import static com.underscoreresearch.backup.io.IOUtils.createDirectory;

@CommandPlugin(value = "generate-key", description = "Generate a new public key and store it to disk",
        needPrivateKey = false, needConfiguration = false)
public class GenerateKeyCommand extends Command {
    public static String generateAndSaveNewKey(CommandLine commandLine, String firstTry) throws IOException, GeneralSecurityException {
        EncryptionIdentity identity = EncryptionIdentity.generateKeyWithPassword(firstTry);

        return ChangePasswordCommand.saveKeyFile(commandLine, identity);
    }

    public static File getDefaultEncryptionFileName(CommandLine commandLine) {
        String file = commandLine.getOptionValue(KEY);
        if (Strings.isNullOrEmpty(file)) {
            file = EncryptionModule.DEFAULT_KEY_FILES[0];
        }

        File keyFile = new File(file);
        createDirectory(keyFile.getParentFile(), true);

        return keyFile;
    }

    public void executeCommand(CommandLine commandLine) throws Exception {
        if (commandLine.getArgList().size() != 1) {
            throw new ParseException("Too many arguments for command");
        }

        String firstTry;
        if (commandLine.hasOption(CommandLineModule.PRIVATE_KEY_SEED)) {
            firstTry = commandLine.getOptionValue(CommandLineModule.PRIVATE_KEY_SEED);
        } else {
            if (commandLine.hasOption(ADDITIONAL_KEY)) {
                firstTry = PasswordReader.readPassword("Enter password for private key: ");
                if (firstTry == null) {
                    System.exit(1);
                }
            } else {
                firstTry = PasswordReader.readPassword("Please enter the password for the private key: ");
                if (firstTry == null) {
                    System.exit(1);
                }
                String secondTry
                        = PasswordReader.readPassword("Reenter the password for the private key: ");
                if (secondTry == null) {
                    System.exit(1);
                }
                if (!firstTry.equals(secondTry)) {
                    System.out.println("Passwords do not match");
                    System.exit(1);
                }
            }
        }
        if (commandLine.hasOption(ADDITIONAL_KEY)) {
            EncryptionIdentity identity = InstanceFactory.getInstance(EncryptionIdentity.class);
            EncryptionIdentity.PrivateIdentity privateIdentity = identity.getPrivateIdentity(firstTry);

            ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
            if (commandLine.hasOption(ENCRYPTION_KEY_DATA)) {
                String keyData = commandLine.getOptionValue(ENCRYPTION_KEY_DATA);
                IdentityKeys newKeys = IdentityKeys.fromString(keyData, privateIdentity);
                identity.getAdditionalKeys().add(newKeys);
                manifestManager.updateKeyData(identity);

                System.err.println("Added new key. Below is the key to share with the source of the sharing if needed:");
                System.out.println(newKeys.getPublicKeyString());
            } else {
                IdentityKeys newKey = IdentityKeys.createIdentityKeys(privateIdentity);
                identity.getAdditionalKeys().add(newKey);
                manifestManager.updateKeyData(identity);
                System.out.println("Generated new key with ID: " + newKey.getKeyIdentifier());
                System.err.println("Below is the public key to share with the source of the sharing:");
                System.out.println(newKey.getPublicKeyString());
                System.out.println();
                System.err.println("If you are sharing then the private key below is what you need to pass to the recipient:");
                System.out.println(newKey.getPrivateKeyString(privateIdentity));
            }
            manifestManager.shutdown();
        } else {
            try {
                InstanceFactory.getInstance(EncryptionIdentity.class);
                if (!commandLine.hasOption(CommandLineModule.FORCE)) {
                    System.out.println("Private key already exists, use --force flag to overwrite");
                    System.exit(1);
                }
                System.out.println("Private key already exists, replacing it because --force flag was used");
            } catch (Exception ignored) {
            }

            String file = generateAndSaveNewKey(commandLine, firstTry);

            System.out.println("Wrote public key to " + file);
        }
    }
}
