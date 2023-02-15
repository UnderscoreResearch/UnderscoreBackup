package com.underscoreresearch.backup.encryption;

import static com.underscoreresearch.backup.encryption.AesEncryptorTest.getFirstAdditionalStorage;
import static org.hamcrest.MatcherAssert.assertThat;
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

class AesEncryptorGcmStableTest {
    private EncryptionKey key;
    private EncryptionKey otherKey;
    private AesEncryptorFormat encryptor;
    private AesEncryptorFormat decryptor;
    private AesEncryptor validEncryptor;

    @BeforeEach
    public void setup() {
        key = EncryptionKey.generateKeyWithPassword("Seed");
        otherKey = EncryptionKey.generateKeyWithPassword("OtherSeed");

        encryptor = new AesEncryptorGcmStable();
        decryptor = new AesEncryptorGcmStable();
        validEncryptor = new AesEncryptor();
    }

    @Test
    public void basic() {
        assertThrows(IllegalArgumentException.class, () -> decryptor.decodeBlock(null, new byte[10], 1, key.getPrivateKey("Seed")));
        assertThrows(IllegalArgumentException.class, () -> encryptor.encryptBlock(null, new byte[10], key));
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
            assertNotNull(storage.getProperties().get("k"));

            assertTrue(validEncryptor.validStorage(storage));

            assertThat(decryptedData, Is.is(data));

            assertThat(new AesEncryptorGcmStable().encryptBlock(
                    new BackupBlockStorage(),
                    data, otherKey), Is.is(encryptedData));
            assertThat(new AesEncryptorGcmStable().decodeBlock(
                    validEncryptor.reKeyStorage(storage, key.getPrivateKey("Seed"), otherKey),
                    encryptedData, 1, otherKey.getPrivateKey("OtherSeed")), Is.is(data));
        }
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