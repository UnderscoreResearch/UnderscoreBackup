package com.underscoreresearch.backup.encryption;

import javax.crypto.spec.GCMParameterSpec;
import java.security.spec.AlgorithmParameterSpec;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AesEncryptorGcm extends AesEncryptorFormat {
    public static final byte PADDED_GCM = 2;
    public static final byte NON_PADDED_GCM = 1;
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
            case NON_PADDED_GCM:
                return estimatedSize;
            case PADDED_GCM:
                return estimatedSize + 1;
        }
        throw new IllegalArgumentException("Unknown AES padding format");
    }

    @Override
    protected int adjustDecodeLength(byte paddingFormat, int payloadLength) {
        switch (paddingFormat) {
            case NON_PADDED_GCM:
                return payloadLength;
            case PADDED_GCM:
                return payloadLength - 1;
        }
        throw new IllegalArgumentException("Unknown AES padding format");
    }

    @Override
    protected AlgorithmParameterSpec createAlgorithmParameterSpec(byte[] iv) {
        return new GCMParameterSpec(iv.length * 8, iv);
    }
}
