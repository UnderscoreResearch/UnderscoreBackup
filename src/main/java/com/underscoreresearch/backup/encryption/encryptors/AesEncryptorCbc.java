package com.underscoreresearch.backup.encryption.encryptors;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.spec.IvParameterSpec;
import java.security.spec.AlgorithmParameterSpec;

import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptionFormatTypes.CBC;

/**
 * AES encryptor implementation using CBC mode with PKCS5 padding.
 * This class provides encryption and decryption using the AES/CBC/PKCS5Padding algorithm.
 */
@Slf4j
public class AesEncryptorCbc extends AesEncryptorFormat {
    private static final String ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding";

    /**
     * Gets the encryption algorithm name.
     *
     * @return The encryption algorithm name
     */
    protected String getKeyAlgorithm() {
        return ENCRYPTION_ALGORITHM;
    }

    /**
     * Gets the initialization vector size in bytes.
     *
     * @return The IV size (16 bytes for CBC)
     */
    @Override
    protected int getIvSize() {
        return 16;
    }

    /**
     * Gets the padding format identifier.
     *
     * @param length The length of the data
     * @return The padding format identifier
     */
    @Override
    protected byte paddingFormat(int length) {
        return CBC;
    }

    /**
     * Creates the algorithm parameter specification for the cipher.
     *
     * @param iv The initialization vector
     * @return The algorithm parameter specification
     */
    @Override
    protected AlgorithmParameterSpec createAlgorithmParameterSpec(byte[] iv) {
        return new IvParameterSpec(iv);
    }
}
