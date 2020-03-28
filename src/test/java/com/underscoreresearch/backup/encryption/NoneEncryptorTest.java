package com.underscoreresearch.backup.encryption;

import static org.hamcrest.MatcherAssert.assertThat;

import java.security.SecureRandom;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoneEncryptorTest {
    private NoneEncryptor encryptor;

    @BeforeEach
    public void setup() {
        encryptor = new NoneEncryptor();
    }

    @Test
    public void basic() {
        SecureRandom random = new SecureRandom();
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            byte[] encryptedData = encryptor.encryptBlock(data);
            byte[] decryptedData = encryptor.decodeBlock(encryptedData);

            assertThat(decryptedData, Is.is(data));
            assertThat(decryptedData, Is.is(encryptedData));
        }
    }
}