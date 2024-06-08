package com.underscoreresearch.backup.encryption.encryptors;

import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptionFormatTypes.NON_PADDED_GCM;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptionFormatTypes.PADDED_GCM;

import javax.crypto.spec.GCMParameterSpec;
import java.security.spec.AlgorithmParameterSpec;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AesEncryptorGcm extends AesEncryptorFormat {
    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";

    @Override
    protected String getKeyAlgorithm() {
        return ENCRYPTION_ALGORITHM;
    }

    @Override
    protected int getIvSize() {
        return 12;
    }

    @Override
    protected byte paddingFormat(int estimatedSize) {
        return estimatedSize % 4 != 3 ? NON_PADDED_GCM : PADDED_GCM;
    }

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

    @Override
    protected AlgorithmParameterSpec createAlgorithmParameterSpec(byte[] iv) {
        return new GCMParameterSpec(iv.length * 8, iv);
    }
}
