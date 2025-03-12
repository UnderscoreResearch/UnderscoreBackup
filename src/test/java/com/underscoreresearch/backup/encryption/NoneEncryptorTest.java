package com.underscoreresearch.backup.encryption;

import com.underscoreresearch.backup.encryption.encryptors.NoneEncryptor;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import static org.hamcrest.MatcherAssert.assertThat;

class NoneEncryptorTest {
    private NoneEncryptor encryptor;

    @BeforeEach
    public void setup() {
        encryptor = new NoneEncryptor();
    }

    @Test
    public void basic() throws GeneralSecurityException {
        SecureRandom random = new SecureRandom();
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            byte[] encryptedData = encryptor.encryptBlock(null, data, null);
            byte[] decryptedData = encryptor.decodeBlock(null, encryptedData, null);

            assertThat(decryptedData, Is.is(data));
            assertThat(decryptedData, Is.is(encryptedData));
        }
    }
}