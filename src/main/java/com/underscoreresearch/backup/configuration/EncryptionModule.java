package com.underscoreresearch.backup.configuration;

import static com.underscoreresearch.backup.configuration.CommandLineModule.ADDITIONAL_KEY;
import static com.underscoreresearch.backup.configuration.CommandLineModule.ADDITIONAL_SOURCE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.ENCRYPTION_KEY_DATA;
import static com.underscoreresearch.backup.configuration.CommandLineModule.KEY_FILE_NAME;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.configuration.CommandLineModule.PRIVATE_KEY_SEED;
import static com.underscoreresearch.backup.configuration.CommandLineModule.getDefaultUserManifestLocation;
import static com.underscoreresearch.backup.utils.SerializationUtils.ENCRYPTION_KEY_READER;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.InvalidKeyException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang.SystemUtils;

import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.underscoreresearch.backup.cli.PasswordReader;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.io.IOUtils;

public class EncryptionModule extends AbstractModule {
    public static final String[] DEFAULT_KEY_FILES;
    public static final String ROOT_KEY = "ROOT_KEY";
    private static final String SYSTEM_DEFAULT_KEY_FILE = "/etc/underscorebackup/key";

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

    @Provides
    @Singleton
    @Named(ROOT_KEY)
    public EncryptionKey rootEncryptionKeyEncryption(@Named(ENCRYPTION_KEY_DATA) String keyData,
                                                     @Named(KEY_FILE_NAME) String rootKeyFile,
                                                     CommandLine commandLine) throws IOException, InvalidKeyException {
        EncryptionKey encryptionKey;
        if (!Strings.isNullOrEmpty(keyData) && !commandLine.hasOption(ADDITIONAL_KEY)) {
            encryptionKey = EncryptionKey.createWithKeyData(keyData);
        } else {

            try (FileInputStream inputStream = new FileInputStream(rootKeyFile)) {
                encryptionKey = ENCRYPTION_KEY_READER.readValue(IOUtils.readAllBytes(inputStream));
            }
        }

        return encryptionKey;
    }

    @Provides
    @Singleton
    public EncryptionKey encryptionKey(@Named(ENCRYPTION_KEY_DATA) String keyData,
                                       @Named(KEY_FILE_NAME) String rootKeyFile,
                                       @Named(MANIFEST_LOCATION) String manifestLocation,
                                       @Named(ADDITIONAL_SOURCE) String source,
                                       CommandLine commandLine) throws IOException, InvalidKeyException {
        EncryptionKey encryptionKey;
        if (!Strings.isNullOrEmpty(keyData) && Strings.isNullOrEmpty(source) && !commandLine.hasOption(ADDITIONAL_KEY)) {
            encryptionKey = EncryptionKey.createWithKeyData(keyData);
        } else {
            String keyFile;
            if (!Strings.isNullOrEmpty(source)) {
                keyFile = Paths.get(manifestLocation, "sources", source, "key").toString();
            } else {
                keyFile = rootKeyFile;
            }

            try (FileInputStream inputStream = new FileInputStream(keyFile)) {
                encryptionKey = ENCRYPTION_KEY_READER.readValue(IOUtils.readAllBytes(inputStream));
            }
        }

        return encryptionKey;
    }
}
