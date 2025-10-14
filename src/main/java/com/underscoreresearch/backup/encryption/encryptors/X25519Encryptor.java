package com.underscoreresearch.backup.encryption.encryptors;

import com.google.inject.Inject;
import com.underscoreresearch.backup.encryption.EncryptorPlugin;
import lombok.extern.slf4j.Slf4j;

import static com.underscoreresearch.backup.encryption.encryptors.X25519Encryptor.AES_ENCRYPTION;

/**
 * X25519Encryptor is an implementation of the AES encryption algorithm.
 * The class extends BaseAesEncryptor to provide AES encryption functionality.
 * It is designed to work with the X25519 key exchange algorithm for deriving the AES encryption key.
 * This class is annotated with @EncryptorPlugin to indicate that it is a plugin for encryption.
 */
@EncryptorPlugin(AES_ENCRYPTION)
@Slf4j
public class X25519Encryptor extends BaseAesEncryptor {
    public static final String AES_ENCRYPTION = "AES256";

    /**
     * The padding format identifier for GCM mode.
     */
    @Inject
    public X25519Encryptor() {
    }
}
