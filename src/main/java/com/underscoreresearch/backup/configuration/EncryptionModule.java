package com.underscoreresearch.backup.configuration;

import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.underscoreresearch.backup.ui.PasswordReader;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.io.IOUtils;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang.SystemUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;

import static com.underscoreresearch.backup.configuration.CommandLineModule.ADDITIONAL_KEY;
import static com.underscoreresearch.backup.configuration.CommandLineModule.ADDITIONAL_SOURCE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.ENCRYPTION_KEY_DATA;
import static com.underscoreresearch.backup.configuration.CommandLineModule.KEY_FILE_NAME;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.configuration.CommandLineModule.PRIVATE_KEY_SEED;
import static com.underscoreresearch.backup.configuration.CommandLineModule.getDefaultUserManifestLocation;

/**
 * Guice module for encryption-related dependencies.
 * This module provides bindings for encryption keys and identity management.
 */
public class EncryptionModule extends AbstractModule {
    /**
     * Default key file locations based on the operating system.
     */
    public static final String[] DEFAULT_KEY_FILES;
    
    /**
     * Named constant for root encryption key.
     */
    public static final String ROOT_KEY = "ROOT_KEY";
    
    /**
     * Default system key file location.
     */
    private static final String SYSTEM_DEFAULT_KEY_FILE = "/etc/underscorebackup/key";

    /*
     * Static initializer to set up default key file locations based on the operating system.
     */
    static {
        if (SystemUtils.IS_OS_WINDOWS) {
            DEFAULT_KEY_FILES = new String[]{
                    getDefaultUserManifestLocation() + "\\key"
            };
        } else {
            if (Strings.isNullOrEmpty(System.getProperty("user.home"))) {
                DEFAULT_KEY_FILES = new String[]{
                        SYSTEM_DEFAULT_KEY_FILE
                };
            } else {
                File systemFile = new File(SYSTEM_DEFAULT_KEY_FILE);
                if (systemFile.canRead() || systemFile.getParentFile().canWrite()) {
                    DEFAULT_KEY_FILES = new String[]{
                            SYSTEM_DEFAULT_KEY_FILE,
                            getDefaultUserManifestLocation() + "/key"
                    };
                } else {
                    DEFAULT_KEY_FILES = new String[]{
                            getDefaultUserManifestLocation() + "/key",
                            "/etc/underscorebackup/key"
                    };
                }
            }
        }
    }

    /**
     * Gets the password for the private key, either from command line or by prompting the user.
     *
     * @return The password for the private key
     * @throws IOException If there's an error reading the password
     */
    public static String getPassword() throws IOException {
        CommandLine commandLine = InstanceFactory.getInstance(CommandLine.class);
        String key;
        if (!commandLine.hasOption(PRIVATE_KEY_SEED)) {
            key = PasswordReader.readPassword("Enter password for private key: ");
            if (key == null) {
                System.exit(1);
            }
        } else
            key = commandLine.getOptionValue(PRIVATE_KEY_SEED);

        return key;
    }

    /**
     * Provides the root encryption identity.
     *
     * @param keyData The encryption key data
     * @param rootKeyFile The root key file path
     * @param commandLine The command line
     * @return The root encryption identity
     * @throws IOException If there's an error reading the key file
     * @throws GeneralSecurityException If there's an error with the encryption
     */
    @Provides
    @Singleton
    @Named(ROOT_KEY)
    public EncryptionIdentity rootEncryptionKeyEncryption(@Named(ENCRYPTION_KEY_DATA) String keyData,
                                                          @Named(KEY_FILE_NAME) String rootKeyFile,
                                                          CommandLine commandLine) throws IOException, GeneralSecurityException {
        EncryptionIdentity encryptionKey;
        if (!Strings.isNullOrEmpty(keyData) && !commandLine.hasOption(ADDITIONAL_KEY)) {
            encryptionKey = EncryptionIdentity.restoreFromString(keyData);
        } else {

            try (FileInputStream inputStream = new FileInputStream(rootKeyFile)) {
                encryptionKey = EncryptionIdentity.restoreFromString(
                        new String(IOUtils.readAllBytes(inputStream), StandardCharsets.UTF_8));
            }
        }

        return encryptionKey;
    }

    /**
     * Provides the encryption identity for the current operation.
     * This may be the root key or a source-specific key.
     *
     * @param keyData The encryption key data
     * @param rootKeyFile The root key file path
     * @param manifestLocation The manifest location
     * @param source The additional source
     * @param rootEncryptionKey The root encryption key
     * @param commandLine The command line
     * @return The encryption identity
     * @throws IOException If there's an error reading the key file
     * @throws GeneralSecurityException If there's an error with the encryption
     */
    @Provides
    @Singleton
    public EncryptionIdentity encryptionKey(@Named(ENCRYPTION_KEY_DATA) String keyData,
                                            @Named(KEY_FILE_NAME) String rootKeyFile,
                                            @Named(MANIFEST_LOCATION) String manifestLocation,
                                            @Named(ADDITIONAL_SOURCE) String source,
                                            @Named(ROOT_KEY) EncryptionIdentity rootEncryptionKey,
                                            CommandLine commandLine) throws IOException, GeneralSecurityException {
        EncryptionIdentity encryptionKey;
        if (!Strings.isNullOrEmpty(keyData) && Strings.isNullOrEmpty(source) && !commandLine.hasOption(ADDITIONAL_KEY)) {
            encryptionKey = EncryptionIdentity.restoreFromString(keyData);
        } else {
            String keyFile;
            if (!Strings.isNullOrEmpty(source)) {
                keyFile = Paths.get(manifestLocation, "sources", source, "key").toString();
            } else {
                keyFile = rootKeyFile;
            }

            try (FileInputStream inputStream = new FileInputStream(keyFile)) {
                encryptionKey = EncryptionIdentity.restoreFromString(
                        new String(IOUtils.readAllBytes(inputStream), StandardCharsets.UTF_8));
            }

            if (encryptionKey.getSalt() == null) {
                encryptionKey = rootEncryptionKey.copyWithPublicPrimaryKey(encryptionKey.getPrimaryKeys());
            }
        }

        return encryptionKey;
    }
}
