package com.underscoreresearch.backup.encryption;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesEncryptorTest {
    private PublicKeyEncrypion key;
    private PublicKeyEncrypion publicKey;
    private AesEncryptor encryptor;
    private AesEncryptor decryptor;

    @BeforeEach
    public void setup() {
        key = PublicKeyEncrypion.generateKeyWithSeed("Seed", null);
        publicKey = key.publicOnly();

        encryptor = new AesEncryptor(publicKey);
        decryptor = new AesEncryptor(key);
    }

    @Test
    public void basic() {
        SecureRandom random = new SecureRandom();
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            byte[] encryptedData = encryptor.encryptBlock(data);
            assertThrows(IllegalStateException.class, () -> encryptor.decodeBlock(encryptedData));
            byte[] decryptedData = decryptor.decodeBlock(encryptedData);

            assertThat(decryptedData, Is.is(data));
        }
    }
}