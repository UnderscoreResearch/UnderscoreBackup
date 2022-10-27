package com.underscoreresearch.backup.encryption;

import static com.underscoreresearch.backup.encryption.AesEncryptorTest.getFirstAdditionalStorage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.HashMap;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.underscoreresearch.backup.model.BackupBlockStorage;

class AesEncryptorCbcTest {
    private EncryptionKey key;
    private EncryptionKey otherKey;
    private AesEncryptorFormat encryptor;
    private AesEncryptorFormat decryptor;
    private AesEncryptor validEncryptor;

    @BeforeEach
    public void setup() {
        key = EncryptionKey.generateKeyWithPassphrase("Seed");
        otherKey = EncryptionKey.generateKeyWithPassphrase("OtherSeed");

        encryptor = new AesEncryptorCbc();
        decryptor = new AesEncryptorCbc();
        validEncryptor = new AesEncryptor();
    }

    @Test
    public void basic() {
        SecureRandom random = new SecureRandom();
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            byte[] encryptedData = encryptor.encryptBlock(null, data, key);
            assertThrows(IllegalStateException.class, () -> encryptor.decodeBlock(null, encryptedData, 1, new EncryptionKey.PrivateKey(null, null, null)));
            byte[] decryptedData = decryptor.decodeBlock(null, encryptedData, 1, key.getPrivateKey("Seed"));

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
            byte[] encryptedData = encryptor.encryptBlock(storage, data, key);
            assertThrows(IllegalStateException.class, () -> encryptor.decodeBlock(storage, encryptedData, 1, new EncryptionKey.PrivateKey(null, null, null)));
            byte[] decryptedData = decryptor.decodeBlock(storage, encryptedData, 1, key.getPrivateKey("Seed"));
            assertNotNull(storage.getProperties().get("p"));

            BackupBlockStorage otherStorage = new BackupBlockStorage();
            decryptor.backfillEncryption(otherStorage, encryptedData, 1);
            assertEquals(otherStorage, storage);

            assertTrue(validEncryptor.validStorage(storage));

            assertThat(decryptedData, Is.is(data));

            assertThat(new AesEncryptorCbc().decodeBlock(
                    validEncryptor.reKeyStorage(storage, key.getPrivateKey("Seed"), otherKey),
                    encryptedData, 1, otherKey.getPrivateKey("OtherSeed")), Is.is(data));
        }
    }

    @Test
    public void encryptionBackfill() {
        SecureRandom random = new SecureRandom();
        BackupBlockStorage storage = new BackupBlockStorage();
        byte[] data = new byte[100];
        random.nextBytes(data);
        byte[] encryptedData = encryptor.encryptBlock(null, data, key);
        assertFalse(validEncryptor.validStorage(storage));
        validEncryptor.backfillEncryption(storage, encryptedData);
        assertTrue(validEncryptor.validStorage(storage));
        assertArrayEquals(data, decryptor.decodeBlock(storage, encryptedData, 1, key.getPrivateKey("Seed")));
    }

    @Test
    public void withAdditionalStorage() {
        SecureRandom random = new SecureRandom();
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            BackupBlockStorage storage = new BackupBlockStorage();
            EncryptionKey additionalKey = EncryptionKey.generateKeys();
            storage.getAdditionalStorageProperties().put(additionalKey.shareableKey(), new HashMap<>());
            byte[] encryptedData = encryptor.encryptBlock(storage, data, key);
            assertThrows(IllegalStateException.class, () -> encryptor.decodeBlock(storage, encryptedData, 1, new EncryptionKey.PrivateKey(null, null, null)));
            byte[] decryptedData = decryptor.decodeBlock(storage, encryptedData, 1, key.getPrivateKey("Seed"));
            assertNotNull(storage.getProperties().get("p"));

            assertThat(decryptedData, Is.is(data));

            decryptedData = new AesEncryptor().decodeBlock(
                    getFirstAdditionalStorage(storage), encryptedData, additionalKey.getPrivateKey(null));
            assertThat(decryptedData, Is.is(data));
        }
    }
}