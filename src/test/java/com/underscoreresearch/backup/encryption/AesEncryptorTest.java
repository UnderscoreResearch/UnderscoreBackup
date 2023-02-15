package com.underscoreresearch.backup.encryption;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupBlockStorage;

class AesEncryptorTest {
    private EncryptionKey key;
    private AesEncryptor encryptor;
    private AesEncryptor decryptor;

    public static synchronized BackupBlockStorage getFirstAdditionalStorage(BackupBlockStorage root) {
        return root.getAdditionalStorageProperties().entrySet().stream().map(e -> {
            Map<String, String> additionalProperties = new HashMap<>(root.getProperties());
            additionalProperties.putAll(e.getValue());
            BackupBlockStorage storage = root.toBuilder().properties(additionalProperties).build();
            return storage;
        }).findFirst().get();
    }

    @BeforeEach
    public void setup() {
        InstanceFactory.initialize(new String[]{"--no-log", "--config-data", "{}"}, null, null);
        key = EncryptionKey.generateKeyWithPassword("Seed");

        encryptor = new AesEncryptor();
        decryptor = new AesEncryptor();
    }

    @Test
    public void basic() {
        SecureRandom random = new SecureRandom();
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            byte[] encryptedData = encryptor.encryptBlock(null, data, key);
            assertThrows(IllegalStateException.class, () -> encryptor.decodeBlock(null, encryptedData, new EncryptionKey.PrivateKey(null, null, null)));
            byte[] decryptedData = decryptor.decodeBlock(null, encryptedData, key.getPrivateKey("Seed"));

            assertThat(decryptedData, Is.is(data));
        }
    }

    @Test
    public void legacyExplicit() {
        AesEncryptorFormat encryptorFormat = new AesEncryptorCbc();

        SecureRandom random = new SecureRandom();
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            byte[] encryptedData = encryptorFormat.encryptBlock(null, data, key);
            assertThrows(IllegalStateException.class, () -> encryptor.decodeBlock(null, encryptedData, new EncryptionKey.PrivateKey(null, null, null)));
            byte[] decryptedData = decryptor.decodeBlock(null, encryptedData, key.getPrivateKey("Seed"));

            assertThat(decryptedData, Is.is(data));
        }
    }

    @Test
    public void legacyImplicit() {
        AesEncryptorFormat encryptorFormat = new AesEncryptorCbc();

        SecureRandom random = new SecureRandom();
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            byte[] encryptedData = encryptorFormat.encryptBlock(null, data, key);
            byte[] strippedFirstByte = new byte[encryptedData.length - 1];
            System.arraycopy(encryptedData, 1, strippedFirstByte, 0, strippedFirstByte.length);
            assertThrows(IllegalStateException.class, () -> encryptor.decodeBlock(null, strippedFirstByte, new EncryptionKey.PrivateKey(null, null, null)));
            byte[] decryptedData = decryptor.decodeBlock(null, strippedFirstByte, key.getPrivateKey("Seed"));

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
            byte[] encryptedData = encryptor.encryptBlock(storage, data, key);
            assertThrows(IllegalStateException.class, () -> encryptor.decodeBlock(storage, encryptedData, new EncryptionKey.PrivateKey(null, null, null)));
            byte[] decryptedData = decryptor.decodeBlock(storage, encryptedData, key.getPrivateKey("Seed"));
            assertNotNull(storage.getProperties().get("p"));

            assertTrue(encryptor.validStorage(storage));

            assertThat(decryptedData, Is.is(data));

            EncryptionKey otherKey = EncryptionKey.generateKeys();
            byte[] otherData = new AesEncryptor().encryptBlock(new BackupBlockStorage(),
                    data, otherKey.publicOnly());
            assertThat(encryptedData, Is.is(otherData));
        }
    }

    @Test
    public void withAdditionalStorage() {
        SecureRandom random = new SecureRandom();
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            BackupBlockStorage storage = new BackupBlockStorage();
            assertFalse(encryptor.validStorage(storage));
            EncryptionKey additionalKey = EncryptionKey.generateKeys();
            storage.getAdditionalStorageProperties().put(additionalKey.shareableKey(), new HashMap<>());
            byte[] encryptedData = encryptor.encryptBlock(storage, data, key);
            assertThrows(IllegalStateException.class, () -> encryptor.decodeBlock(storage, encryptedData, new EncryptionKey.PrivateKey(null, null, null)));
            byte[] decryptedData = decryptor.decodeBlock(storage, encryptedData, key.getPrivateKey("Seed"));
            assertNotNull(storage.getProperties().get("p"));

            assertTrue(encryptor.validStorage(storage));

            assertThat(decryptedData, Is.is(data));

            decryptedData = new AesEncryptor().decodeBlock(
                    getFirstAdditionalStorage(storage), encryptedData, additionalKey.getPrivateKey(null));
            assertThat(decryptedData, Is.is(data));
        }
    }

}