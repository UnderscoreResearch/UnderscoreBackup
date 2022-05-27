package com.underscoreresearch.backup.configuration;

import static com.underscoreresearch.backup.configuration.CommandLineModule.KEY_FILE_NAME;
import static com.underscoreresearch.backup.configuration.CommandLineModule.NEED_PRIVATE_KEY;
import static com.underscoreresearch.backup.configuration.CommandLineModule.PRIVATE_KEY_SEED;
import static com.underscoreresearch.backup.configuration.CommandLineModule.PUBLIC_KEY_DATA;
import static com.underscoreresearch.backup.configuration.CommandLineModule.getDefaultUserManifestLocation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.commons.lang.SystemUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.underscoreresearch.backup.cli.PassphraseReader;
import com.underscoreresearch.backup.encryption.PublicKeyEncrypion;
import com.underscoreresearch.backup.io.IOUtils;

public class EncryptionModule extends AbstractModule {
    private static final String SYSTEM_DEFAULT_KEY_FILE = "/etc/underscorebackup/key";
    public static final String[] DEFAULT_KEY_FILES;

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

    @Provides
    @Singleton
    public PublicKeyEncrypion publicKeyEncrypion(@Named(NEED_PRIVATE_KEY) boolean needPrivateKey,
                                                 @Named(PUBLIC_KEY_DATA) String publicKeyData,
                                                 @Named(KEY_FILE_NAME) String keyFile,
                                                 @Named(PRIVATE_KEY_SEED) String privateKeySeed) throws IOException {
        PublicKeyEncrypion publicKeyEncrypion;
        if (!Strings.isNullOrEmpty(publicKeyData)) {
            publicKeyEncrypion = new ObjectMapper().readValue(publicKeyData, PublicKeyEncrypion.class);
        } else {
            try (FileInputStream inputStream = new FileInputStream(keyFile)) {
                publicKeyEncrypion = new ObjectMapper().readValue(IOUtils.readAllBytes(inputStream), PublicKeyEncrypion.class);
            }
        }

        if (needPrivateKey || !Strings.isNullOrEmpty(privateKeySeed)) {
            String key = privateKeySeed;
            if (Strings.isNullOrEmpty(key))
                key = PassphraseReader.readPassphrase("Enter passphrase for private key: ");
            if (key == null) {
                System.exit(1);
            }
            PublicKeyEncrypion ret = PublicKeyEncrypion.generateKeyWithSeed(key, publicKeyEncrypion.getSalt());
            if (!publicKeyEncrypion.getPublicKey().equals(ret.getPublicKey())) {
                throw new IllegalArgumentException("Private key does not match public key");
            }
            return ret;
        }

        return publicKeyEncrypion;
    }
}
