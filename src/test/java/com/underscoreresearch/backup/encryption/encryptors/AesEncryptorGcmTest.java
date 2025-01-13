package com.underscoreresearch.backup.encryption.encryptors;

import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptorTest.getFirstAdditionalStorage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HashMap;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.model.BackupBlockStorage;

class AesEncryptorGcmTest {
    private EncryptionIdentity key;
    private EncryptionIdentity otherKey;
    private AesEncryptorFormat encryptor;
    private AesEncryptorFormat decryptor;
    private X25519Encryptor validEncryptor;

    @BeforeEach
    public void setup() throws GeneralSecurityException {
        key = EncryptionIdentity.generateKeyWithPassword("Seed");
        otherKey = EncryptionIdentity.generateKeyWithPassword("OtherSeed");

        encryptor = new AesEncryptorGcm();
        decryptor = new AesEncryptorGcm();
        validEncryptor = new X25519Encryptor();
    }

    @Test
    public void basic() throws GeneralSecurityException {
        SecureRandom random = new SecureRandom();
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            byte[] encryptedData = encryptor.encryptBlock(null, data, key.getPrimaryKeys());
            byte[] decryptedData = decryptor.decodeBlock(null, encryptedData, 1, key.getPrivateKeys("Seed"));

            assertThat(decryptedData, Is.is(data));
        }
    }

    @Test
    public void withStorage() throws GeneralSecurityException {
        SecureRandom random = new SecureRandom();
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            random.nextBytes(data);

            BackupBlockStorage storage = new BackupBlockStorage();
            assertFalse(validEncryptor.validStorage(storage));
            byte[] encryptedData = encryptor.encryptBlock(storage, data, key.getPrimaryKeys());
            byte[] decryptedData = decryptor.decodeBlock(storage, encryptedData, 1, key.getPrivateKeys("Seed"));
            assertNotNull(storage.getProperties().get("p"));

            assertTrue(validEncryptor.validStorage(storage));

            assertThat(decryptedData, Is.is(data));

            assertThat(new AesEncryptorGcm().decodeBlock(
                    validEncryptor.reKeyStorage(storage, key.getPrivateKeys("Seed"), otherKey.getPrimaryKeys()),
                    encryptedData, 1, otherKey.getPrivateKeys("OtherSeed")), Is.is(data));
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
            storage.getAdditionalStorageProperties().put(additionalKey.getSharingKeys(), new HashMap<>());
            byte[] encryptedData = encryptor.encryptBlock(storage, data, key.getPrimaryKeys());
            byte[] decryptedData = decryptor.decodeBlock(storage, encryptedData, 1, key.getPrivateKeys("Seed"));
            assertNotNull(storage.getProperties().get("p"));

            assertThat(decryptedData, Is.is(data));

            decryptedData = new PQCEncryptor().decodeBlock(
                    getFirstAdditionalStorage(storage), encryptedData, additionalKey.getSharingKeys()
                            .getPrivateKeys(additionalKey.getPrivateIdentity("AdditionalSeed")));
            assertThat(decryptedData, Is.is(data));
        }
    }
}