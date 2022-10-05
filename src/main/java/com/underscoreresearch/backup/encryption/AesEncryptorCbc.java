package com.underscoreresearch.backup.encryption;

import javax.crypto.spec.IvParameterSpec;
import javax.inject.Inject;
import java.security.spec.AlgorithmParameterSpec;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AesEncryptorCbc extends AesEncryptorFormat {
    private static final String ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding";
    public static final byte CBC = 0;

    @Inject
    public AesEncryptorCbc(PublicKeyEncrypion key) {
        super(key);
    }

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
