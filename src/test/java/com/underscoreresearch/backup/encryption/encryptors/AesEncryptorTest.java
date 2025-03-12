package com.underscoreresearch.backup.encryption.encryptors;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesEncryptorTest {
    private EncryptionIdentity key;
    private X25519Encryptor encryptor;
    private X25519Encryptor decryptor;

    public static synchronized BackupBlockStorage getFirstAdditionalStorage(BackupBlockStorage root) {
        return root.getAdditionalStorageProperties().entrySet().stream().map(e -> {
            Map<String, String> additionalProperties = new HashMap<>(root.getProperties());
            additionalProperties.putAll(e.getValue());
            BackupBlockStorage storage = root.toBuilder().properties(additionalProperties).build();
            return storage;
        }).findFirst().get();
    }

    @BeforeEach
    public void setup() throws GeneralSecurityException {
        InstanceFactory.initialize(new String[]{"--no-log", "--config-data", "{}"}, null, null);
        key = EncryptionIdentity.generateKeyWithPassword("Seed");

        encryptor = new X25519Encryptor();
        decryptor = new X25519Encryptor();
    }

    @Test
    public void basic() throws GeneralSecurityException {
        SecureRandom random = new SecureRandom();
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            byte[] encryptedData = encryptor.encryptBlock(null, data, key.getPrimaryKeys());
            byte[] decryptedData = decryptor.decodeBlock(null, encryptedData, key.getPrivateKeys("Seed"));

            assertThat(decryptedData, Is.is(data));
        }
    }

    @Test
    public void legacyExplicit() throws GeneralSecurityException {
        AesEncryptorFormat encryptorFormat = new AesEncryptorCbc();

        SecureRandom random = new SecureRandom();
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            byte[] encryptedData = encryptorFormat.encryptBlock(null, data, key.getPrimaryKeys());
            byte[] decryptedData = decryptor.decodeBlock(null, encryptedData, key.getPrivateKeys("Seed"));

            assertThat(decryptedData, Is.is(data));
        }
    }

    @Test
    public void legacyImplicit() throws GeneralSecurityException {
        AesEncryptorFormat encryptorFormat = new AesEncryptorCbc();

        SecureRandom random = new SecureRandom();
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            byte[] encryptedData = encryptorFormat.encryptBlock(null, data, key.getPrimaryKeys());
            byte[] strippedFirstByte = new byte[encryptedData.length - 1];
            System.arraycopy(encryptedData, 1, strippedFirstByte, 0, strippedFirstByte.length);
            byte[] decryptedData = decryptor.decodeBlock(null, strippedFirstByte, key.getPrivateKeys("Seed"));

            assertThat(decryptedData, Is.is(data));
        }
    }

    @Test
    public void withStorage() throws GeneralSecurityException {
        SecureRandom random = new SecureRandom();
        EncryptionIdentity additionalKey = EncryptionIdentity.generateKeyWithPassword("AdditionalSeed");
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            BackupBlockStorage storage = new BackupBlockStorage();
            assertFalse(encryptor.validStorage(storage));
            byte[] encryptedData = encryptor.encryptBlock(storage, data, key.getPrimaryKeys());
            byte[] decryptedData = decryptor.decodeBlock(storage, encryptedData, key.getPrivateKeys("Seed"));
            assertNotNull(storage.getProperties().get("p"));

            assertTrue(encryptor.validStorage(storage));

            assertThat(decryptedData, Is.is(data));

            byte[] otherData = new PQCEncryptor().encryptBlock(new BackupBlockStorage(),
                    data, additionalKey.getPrimaryKeys());
            assertThat(encryptedData, Is.is(otherData));
        }
    }

    @Test
    public void withAdditionalStorage() throws GeneralSecurityException {
        SecureRandom random = new SecureRandom();
        EncryptionIdentity additionalKey = EncryptionIdentity.generateKeyWithPassword("AdditionalSeed");
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            BackupBlockStorage storage = new BackupBlockStorage();
            assertFalse(encryptor.validStorage(storage));
            storage.getAdditionalStorageProperties().put(additionalKey.getSharingKeys(), new HashMap<>());
            byte[] encryptedData = encryptor.encryptBlock(storage, data, key.getPrimaryKeys());
            byte[] decryptedData = decryptor.decodeBlock(storage, encryptedData, key.getPrivateKeys("Seed"));
            assertNotNull(storage.getProperties().get("p"));

            assertTrue(encryptor.validStorage(storage));

            assertThat(decryptedData, Is.is(data));

            decryptedData = new PQCEncryptor().decodeBlock(
                    getFirstAdditionalStorage(storage), encryptedData, additionalKey.getSharingKeys()
                            .getPrivateKeys(additionalKey.getPrivateIdentity("AdditionalSeed")));
            assertThat(decryptedData, Is.is(data));
        }
    }

}