package com.underscoreresearch.backup.encryption.encryptors;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.spec.GCMParameterSpec;
import java.security.spec.AlgorithmParameterSpec;

import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptionFormatTypes.NON_PADDED_GCM;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptionFormatTypes.PADDED_GCM;

/**
 * AES encryptor implementation using GCM mode.
 * This class provides encryption and decryption using the AES/GCM/NoPadding algorithm
 * with support for both padded and non-padded formats.
 */
@Slf4j
public class AesEncryptorGcm extends AesEncryptorFormat {
    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";

    /**
     * Gets the encryption algorithm name.
     *
     * @return The encryption algorithm name
     */
    @Override
    protected String getKeyAlgorithm() {
        return ENCRYPTION_ALGORITHM;
    }

    /**
     * Gets the initialization vector size in bytes.
     *
     * @return The IV size (12 bytes for GCM)
     */
    @Override
    protected int getIvSize() {
        return 12;
    }

    /**
     * Gets the padding format identifier based on the estimated size.
     * Uses different formats depending on whether padding is needed.
     *
     * @param estimatedSize The estimated size of the encrypted data
     * @return The padding format identifier
     */
    @Override
    protected byte paddingFormat(int estimatedSize) {
        return estimatedSize % 4 != 3 ? NON_PADDED_GCM : PADDED_GCM;
    }

    /**
     * Adjusts the estimated size based on the padding format.
     *
     * @param paddingFormat The padding format
     * @param estimatedSize The estimated size
     * @return The adjusted size
     * @throws IllegalArgumentException If the padding format is unknown
     */
    @Override
    protected int adjustEstimatedSize(byte paddingFormat, int estimatedSize) {
        switch (paddingFormat) {
            case NON_PADDED_GCM -> {
                return estimatedSize;
            }
            case PADDED_GCM -> {
                return estimatedSize + 1;
            }
        }
        throw new IllegalArgumentException("Unknown AES padding format");
    }

    /**
     * Adjusts the decode length based on the padding format.
     *
     * @param paddingFormat The padding format
     * @param payloadLength The payload length
     * @return The adjusted length
     * @throws IllegalArgumentException If the padding format is unknown
     */
    @Override
    protected int adjustDecodeLength(byte paddingFormat, int payloadLength) {
        switch (paddingFormat) {
            case NON_PADDED_GCM -> {
                return payloadLength;
            }
            case PADDED_GCM -> {
                return payloadLength - 1;
            }
        }
        throw new IllegalArgumentException("Unknown AES padding format");
    }

    /**
     * Creates the algorithm parameter specification for the cipher.
     *
     * @param iv The initialization vector
     * @return The algorithm parameter specification
     */
    @Override
    protected AlgorithmParameterSpec createAlgorithmParameterSpec(byte[] iv) {
        return new GCMParameterSpec(iv.length * 8, iv);
    }
}
