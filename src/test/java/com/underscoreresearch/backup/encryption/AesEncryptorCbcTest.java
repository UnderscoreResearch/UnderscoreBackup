package com.underscoreresearch.backup.encryption;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

class AesEncryptorCbcTest {
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

        encryptor = new AesEncryptorCbc(publicKey);
        decryptor = new AesEncryptorCbc(key);
        validEncryptor = new AesEncryptor(publicKey);
    }

    @Test
    public void basic() {
        SecureRandom random = new SecureRandom();
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            byte[] encryptedData = encryptor.encryptBlock(null, data);
            assertThrows(IllegalStateException.class, () -> encryptor.decodeBlock(null, encryptedData, 1));
            byte[] decryptedData = decryptor.decodeBlock(null, encryptedData, 1);

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
            assertFalse(validEncryptor.validStorage(storage));
            byte[] encryptedData = encryptor.encryptBlock(storage, data);
            assertThrows(IllegalStateException.class, () -> encryptor.decodeBlock(storage, encryptedData, 1));
            byte[] decryptedData = decryptor.decodeBlock(storage, encryptedData, 1);
            assertNotNull(storage.getProperties().get("p"));

            BackupBlockStorage otherStorage = new BackupBlockStorage();
            decryptor.backfillEncryption(otherStorage, encryptedData, 1);
            assertEquals(otherStorage, storage);

            assertTrue(validEncryptor.validStorage(storage));

            assertThat(decryptedData, Is.is(data));

            assertThat(new AesEncryptorCbc(otherKey).decodeBlock(
                    validEncryptor.reKeyStorage(storage, key, otherPublicKey),
                    encryptedData, 1), Is.is(data));
        }
    }

    @Test
    public void encryptionBackfill() {
        SecureRandom random = new SecureRandom();
        BackupBlockStorage storage = new BackupBlockStorage();
        byte[] data = new byte[100];
        random.nextBytes(data);
        byte[] encryptedData = encryptor.encryptBlock(null, data);
        assertFalse(validEncryptor.validStorage(storage));
        validEncryptor.backfillEncryption(storage, encryptedData);
        assertTrue(validEncryptor.validStorage(storage));
        assertArrayEquals(data, decryptor.decodeBlock(storage, encryptedData, 1));
    }
}