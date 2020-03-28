package com.underscoreresearch.backup.configuration;

import static com.underscoreresearch.backup.configuration.CommandLineModule.KEY_FILE_NAME;
import static com.underscoreresearch.backup.configuration.CommandLineModule.NEED_PRIVATE_KEY;
import static com.underscoreresearch.backup.configuration.CommandLineModule.PRIVATE_KEY_SEED;
import static com.underscoreresearch.backup.configuration.CommandLineModule.PUBLIC_KEY_DATA;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;

import org.apache.commons.lang.SystemUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.underscoreresearch.backup.cli.PasswordReader;
import com.underscoreresearch.backup.encryption.PublicKeyEncrypion;
import com.underscoreresearch.backup.io.IOUtils;

public class EncryptionModule extends AbstractModule {
    public static final String[] DEFAULT_KEY_FILES;

    static {
        if (SystemUtils.IS_OS_WINDOWS) {
            DEFAULT_KEY_FILES = new String[]{
                    "C:\\UnderscoreBackup\\key"
            };
        } else {
            DEFAULT_KEY_FILES = new String[]{
                    Paths.get(System.getProperty("user.home"), ".underscorebackup.key").toString(),
                    "/etc/underscorebackup/key",
            };
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

        if (needPrivateKey) {
            String key = privateKeySeed;
            if (Strings.isNullOrEmpty(key))
                key = PasswordReader.readPassword("Enter seed for private key: ");
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
