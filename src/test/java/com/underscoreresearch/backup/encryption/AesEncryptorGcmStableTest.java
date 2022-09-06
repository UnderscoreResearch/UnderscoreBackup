package com.underscoreresearch.backup.encryption;

import com.underscoreresearch.backup.model.BackupBlockStorage;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesEncryptorGcmStableTest {
    private PublicKeyEncrypion key;
    private PublicKeyEncrypion otherKey;
    private PublicKeyEncrypion publicKey;
    private PublicKeyEncrypion otherPublicKey;
    private AesEncryptorFormat encryptor;
    private AesEncryptorFormat decryptor;
    private AesEncryptor validEncryptor;

    @BeforeEach
    public void setup() {
        key = PublicKeyEncrypion.generateKeyWithPassphrase("Seed", null);
        otherKey = PublicKeyEncrypion.generateKeyWithPassphrase("OtherSeed", null);
        publicKey = key.publicOnly();
        otherPublicKey = otherKey.publicOnly();

        encryptor = new AesEncryptorGcmStable(publicKey);
        decryptor = new AesEncryptorGcmStable(key);
        validEncryptor = new AesEncryptor(publicKey);
    }

    @Test
    public void basic() {
        assertThrows(IllegalArgumentException.class, () -> decryptor.decodeBlock(null, new byte[10], 1));
        assertThrows(IllegalArgumentException.class, () -> encryptor.encryptBlock(null, new byte[10]));
    }

    @Test
    public void withStorage() {
        SecureRandom random = new SecureRandom();
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            BackupBlockStorage storage = new BackupBlockStorage();
            assertFalse(validEncryptor.validStorage(storage));
            byte[] encryptedData = encryptor.encryptBlock(storage, data);
            assertThrows(IllegalStateException.class, () -> encryptor.decodeBlock(storage, encryptedData, 1));
            byte[] decryptedData = decryptor.decodeBlock(storage, encryptedData, 1);
            assertNotNull(storage.getProperties().get("p"));
            assertNotNull(storage.getProperties().get("k"));

            assertTrue(validEncryptor.validStorage(storage));

            assertThat(decryptedData, Is.is(data));

            assertThat(new AesEncryptorGcmStable(otherPublicKey).encryptBlock(
                    new BackupBlockStorage(),
                    data), Is.is(encryptedData));
            assertThat(new AesEncryptorGcmStable(otherKey).decodeBlock(
                    validEncryptor.reKeyStorage(storage, key, otherPublicKey),
                    encryptedData, 1), Is.is(data));
        }
    }
}