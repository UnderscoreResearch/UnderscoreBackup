package com.underscoreresearch.backup.encryption;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.underscoreresearch.backup.model.BackupBlockStorage;

class AesEncryptorTest {
    private PublicKeyEncrypion key;
    private PublicKeyEncrypion publicKey;
    private AesEncryptor encryptor;
    private AesEncryptor decryptor;

    @BeforeEach
    public void setup() {
        key = PublicKeyEncrypion.generateKeyWithPassphrase("Seed", null);
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

            byte[] encryptedData = encryptor.encryptBlock(null, data);
            assertThrows(IllegalStateException.class, () -> encryptor.decodeBlock(null, encryptedData));
            byte[] decryptedData = decryptor.decodeBlock(null, encryptedData);

            assertThat(decryptedData, Is.is(data));
        }
    }

    @Test
    public void legacyExplicit() {
        AesEncryptorFormat encryptorFormat = new AesEncryptorCbc(publicKey);

        SecureRandom random = new SecureRandom();
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            byte[] encryptedData = encryptorFormat.encryptBlock(null, data);
            assertThrows(IllegalStateException.class, () -> encryptor.decodeBlock(null, encryptedData));
            byte[] decryptedData = decryptor.decodeBlock(null, encryptedData);

            assertThat(decryptedData, Is.is(data));
        }
    }

    @Test
    public void legacyImplicit() {
        AesEncryptorFormat encryptorFormat = new AesEncryptorCbc(publicKey);

        SecureRandom random = new SecureRandom();
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            byte[] encryptedData = encryptorFormat.encryptBlock(null, data);
            byte[] strippedFirstByte = new byte[encryptedData.length - 1];
            System.arraycopy(encryptedData, 1, strippedFirstByte, 0, strippedFirstByte.length);
            assertThrows(IllegalStateException.class, () -> encryptor.decodeBlock(null, strippedFirstByte));
            byte[] decryptedData = decryptor.decodeBlock(null, strippedFirstByte);

            assertThat(decryptedData, Is.is(data));
        }
    }

    @Test
    public void withStorage() {
        SecureRandom random = new SecureRandom();
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            BackupBlockStorage storage = new BackupBlockStorage();
            assertFalse(encryptor.validStorage(storage));
            byte[] encryptedData = encryptor.encryptBlock(storage, data);
            assertThrows(IllegalStateException.class, () -> encryptor.decodeBlock(storage, encryptedData));
            byte[] decryptedData = decryptor.decodeBlock(storage, encryptedData);
            assertNotNull(storage.getProperties().get("p"));

            assertTrue(encryptor.validStorage(storage));

            assertThat(decryptedData, Is.is(data));

            PublicKeyEncrypion otherKey = PublicKeyEncrypion.generateKeys();
            byte[] otherData = new AesEncryptor(otherKey.publicOnly()).encryptBlock(new BackupBlockStorage(), data);
            assertThat(encryptedData, Is.is(otherData));
        }
    }
}