package com.underscoreresearch.backup.encryption.encryptors;

import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HashMap;

import static com.underscoreresearch.backup.encryption.EncryptionIdentity.RANDOM;
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptorTest.getFirstAdditionalStorage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesEncryptorGcmStableTest {
    private EncryptionIdentity key;
    private EncryptionIdentity otherKey;
    private AesEncryptorFormat encryptor;
    private AesEncryptorFormat decryptor;
    private X25519Encryptor validEncryptor;

    @BeforeEach
    public void setup() throws GeneralSecurityException {
        key = EncryptionIdentity.generateKeyWithPassword("Seed");
        otherKey = EncryptionIdentity.generateKeyWithPassword("OtherSeed");

        encryptor = new AesEncryptorGcmStable();
        decryptor = new AesEncryptorGcmStable();
        validEncryptor = new X25519Encryptor();
    }

    @Test
    public void basic() {
        assertThrows(IllegalArgumentException.class, () -> decryptor.decodeBlock(null, new byte[10], 1, key.getPrivateKeys("Seed")));
        assertThrows(IllegalArgumentException.class, () -> encryptor.encryptBlock(null, new byte[10], key.getPrimaryKeys()));
    }

    @Test
    public void withStorage() throws GeneralSecurityException {
        for (int i = 1; i < 256; i++) {
            byte[] data = new byte[i];
            RANDOM.nextBytes(data);

            BackupBlockStorage storage = new BackupBlockStorage();
            assertFalse(validEncryptor.validStorage(storage));
            byte[] encryptedData = encryptor.encryptBlock(storage, data, key.getPrimaryKeys());
            byte[] decryptedData = decryptor.decodeBlock(storage, encryptedData, 1, key.getPrivateKeys("Seed"));
            assertNotNull(storage.getProperties().get("p"));
            assertNotNull(storage.getProperties().get("k"));

            assertTrue(validEncryptor.validStorage(storage));

            assertThat(decryptedData, Is.is(data));

            assertThat(new AesEncryptorGcmStable().encryptBlock(
                    new BackupBlockStorage(),
                    data, otherKey.getPrimaryKeys()), Is.is(encryptedData));
            assertThat(new AesEncryptorGcmStable().decodeBlock(
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