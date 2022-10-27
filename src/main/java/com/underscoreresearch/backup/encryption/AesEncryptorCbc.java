package com.underscoreresearch.backup.encryption;

import javax.crypto.spec.IvParameterSpec;
import java.security.spec.AlgorithmParameterSpec;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AesEncryptorCbc extends AesEncryptorFormat {
    public static final byte CBC = 0;
    private static final String ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding";

    protected String getKeyAlgorithm() {
        return ENCRYPTION_ALGORITHM;
    }

    @Override
    protected int getIvSize() {
        return 16;
    }

    @Override
    protected byte paddingFormat(int length) {
        return CBC;
    }

    @Override
    protected AlgorithmParameterSpec createAlgorithmParameterSpec(byte[] iv) {
        return new IvParameterSpec(iv);
    }
}
