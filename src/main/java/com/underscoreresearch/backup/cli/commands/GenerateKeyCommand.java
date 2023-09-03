package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.configuration.CommandLineModule.ADDITIONAL_KEY;
import static com.underscoreresearch.backup.configuration.CommandLineModule.ENCRYPTION_KEY_DATA;
import static com.underscoreresearch.backup.configuration.CommandLineModule.KEY;
import static com.underscoreresearch.backup.io.IOUtils.createDirectory;
import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_WRITER;

import java.io.File;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.cli.PasswordReader;
import com.underscoreresearch.backup.cli.web.ConfigurationPost;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.EncryptionModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.manifest.ManifestManager;

@CommandPlugin(value = "generate-key", description = "Generate a new public key and store it to disk",
        needPrivateKey = false, needConfiguration = false)
public class GenerateKeyCommand extends Command {
    public static String generateAndSaveNewKey(CommandLine commandLine, String firstTry) throws IOException {
        EncryptionKey encryptionKey = EncryptionKey.generateKeyWithPassword(firstTry);
        encryptionKey.generateBlockHashSalt();

        File keyFile = getDefaultEncryptionFileName(commandLine);

        ENCRYPTION_KEY_WRITER.writeValue(keyFile,
                encryptionKey.publicOnly());

        ConfigurationPost.setOwnerOnlyPermissions(keyFile);

        return keyFile.getAbsolutePath();
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
            EncryptionKey publicKey = InstanceFactory.getInstance(EncryptionKey.class);
            EncryptionKey.PrivateKey privateMasterKey = publicKey.getPrivateKey(firstTry);

            ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
            if (commandLine.hasOption(ENCRYPTION_KEY_DATA)) {
                String keyData = commandLine.getOptionValue(ENCRYPTION_KEY_DATA);
                EncryptionKey newKey = EncryptionKey.createWithPrivateKey(keyData);
                privateMasterKey.getAdditionalKeyManager().addNewKey(newKey, manifestManager);

                System.err.println("Added new key. Below is the key to share with the source of the sharing if needed:");
                System.out.println(newKey.getPublicKey());
            } else {
                EncryptionKey newKey = privateMasterKey.getAdditionalKeyManager().generateNewKey(manifestManager);
                System.err.println("Generated new key. Below is the public key to share with the source of the sharing:");
                System.out.println(newKey.getPublicKey());
                System.out.println();
                System.err.println("If you are sharing then the private key below is what you need to pass to the recipient:");
                System.out.println(newKey.getPrivateKey(null).getDisplayPrivateKey());
            }
            manifestManager.shutdown();
        } else {
            try {
                InstanceFactory.getInstance(EncryptionKey.class);
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
