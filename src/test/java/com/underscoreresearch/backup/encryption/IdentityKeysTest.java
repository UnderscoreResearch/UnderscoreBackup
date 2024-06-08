package com.underscoreresearch.backup.encryption;

import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Set;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.underscoreresearch.backup.encryption.encryptors.PQCEncryptor;
import com.underscoreresearch.backup.model.BackupBlockStorage;

class IdentityKeysTest {
    private static final String PUBLIC_LEGACY_KEY = "{\"publicKeyHash\":\"1P5DKWWUH40vQHGxcqo6A23GsmqTm57uW7_0fexMMXw\",\"publicKey\":\"G2I6TQWOX4KK7WRURDBEN3TDLGUFKFOV2YKVFSANCHGCJ7Z6FBVQ\",\"sharingPublicKey\":\"FOMVEHP3Z2KWBSRD35EFHKSMMEKVVMUXWBSUXWVP2AMV46IPJBBA\",\"salt\":\"FEU2RAGIUSPLFNNGL3WZRRRDNGAPRNN7BOEEAIZWR2OWJR7GHKUA\",\"algorithm\":\"ARGON2\",\"encryptedAdditionalKeys\":\"Aa1_f-mof648Ad-N38ggPvsBqBfVuuHlaeFq0smedirIqo-Mo7vEu1ESDH14TEDk5XyLrhrR6rvwGWlrxeo2TPpv1WKZnDjnb0uj4v5WCtGg1dtSKFZK6pp23Y8rfIgfFI1WFxHZbyJrgGnJSDFZgA77\",\"keyData\":\"GLASWN73FACEE3IDESLITPSTQNZUNIOK7HAWUXQEBUNCHO5OC3WA\"}";
    private static final String UPLOAD_LEGACY_KEY = "{\"publicKeyHash\":\"1P5DKWWUH40vQHGxcqo6A23GsmqTm57uW7_0fexMMXw\",\"sharingPublicKey\":\"FOMVEHP3Z2KWBSRD35EFHKSMMEKVVMUXWBSUXWVP2AMV46IPJBBA\",\"salt\":\"FEU2RAGIUSPLFNNGL3WZRRRDNGAPRNN7BOEEAIZWR2OWJR7GHKUA\",\"algorithm\":\"ARGON2\",\"encryptedAdditionalKeys\":\"Aa1_f-mof648Ad-N38ggPvsBqBfVuuHlaeFq0smedirIqo-Mo7vEu1ESDH14TEDk5XyLrhrR6rvwGWlrxeo2TPpv1WKZnDjnb0uj4v5WCtGg1dtSKFZK6pp23Y8rfIgfFI1WFxHZbyJrgGnJSDFZgA77\",\"keyData\":\"GLASWN73FACEE3IDESLITPSTQNZUNIOK7HAWUXQEBUNCHO5OC3WA\"}";
    private static final String SERVICE_LEGACY_KEY = "{\"publicKeyHash\":\"1P5DKWWUH40vQHGxcqo6A23GsmqTm57uW7_0fexMMXw\",\"salt\":\"FEU2RAGIUSPLFNNGL3WZRRRDNGAPRNN7BOEEAIZWR2OWJR7GHKUA\",\"algorithm\":\"ARGON2\",\"keyData\":\"GLASWN73FACEE3IDESLITPSTQNZUNIOK7HAWUXQEBUNCHO5OC3WA\"}";
    private static final String NORMAL_AES_ENCRYPTION = "ATe26chkGI3SpEIenPw7GsEzfocRVhRywD4YRr7lkkloHDPbVeiKBqhGN9R0MCkOBD1USHZ3pV_-a_AMqg";
    private static final String STABLE_AES_ENCRYPTION = "A3sbRSF7irUDZBLDNz_N7Fc";
    private static final String STABLE_AES_STORAGE = "{\"props\":{\"p\":\"MRNSQDJT5SVT22UPEOHGH5AE2I4GXA4GVL5Q7UYR75RHMK7CYYAA\",\"k\":\"T3J3HJYKSDZPE3ZVFOEZBDI6W7SPS7XBGTKGAAYVMSYPI6GNWNIA\"}}";
    private static final String BARE_LEGACY_KEY = "{\"publicKeyHash\":\"1V0ceEuTj1kzXjHv15iPd05Fv7hARStU7D3MnFodzf8\",\"publicKey\":\"HHRCNLV4XI2A6UFVWSQAOLAVZXPF5AHMHSRYU5SQAFWQKRR4ZQ5Q\",\"privateKey\":\"Q6BJEG4Q3LPHRTOKF7GJ7EO7IVKOC34VDIG2GBMBYKYIVUHRK2RA\"}";
    private static final String BARE_AES_ENCRYPTED = "AeaCSdRf-zGsNNI6VIGUNKFiWMgeUbNdYKGdaKdmSLsj8pvlA_r58MTKGUgbzZ4VHVfT7POlL-L-CxXaTg";

    @Test
    public void testAll() throws GeneralSecurityException {
        EncryptionIdentity encryptionIdentity = EncryptionIdentity.generateKeyWithPassword("password");
        EncryptionIdentity.PrivateIdentity privateIdentity = encryptionIdentity.getPrivateIdentity("password");
        IdentityKeys identityKeys = IdentityKeys.fromString(
                IdentityKeys.createIdentityKeys(privateIdentity).toString(),
                privateIdentity);
        IdentityKeys.EncryptionParameters secret = identityKeys.getEncryptionParameters(
                Set.of(IdentityKeys.X25519_KEY, IdentityKeys.KYBER_KEY));

        byte[] recreatedSecret = identityKeys.getPrivateKeys(privateIdentity).recreateSecret(secret.getKeys());
        assertThat(recreatedSecret, Is.is(secret.getSecret()));
    }

    @Test
    public void testX25519Only() throws GeneralSecurityException {
        EncryptionIdentity encryptionIdentity = EncryptionIdentity.generateKeyWithPassword("password");
        EncryptionIdentity.PrivateIdentity privateIdentity = encryptionIdentity.getPrivateIdentity("password");
        IdentityKeys identityKeys = IdentityKeys.createIdentityKeys(privateIdentity);
        IdentityKeys.EncryptionParameters secret = identityKeys.getEncryptionParameters(
                Set.of(IdentityKeys.X25519_KEY));

        byte[] recreatedSecret = identityKeys.getPrivateKeys(privateIdentity).recreateSecret(secret.getKeys());
        assertThat(recreatedSecret, Is.is(secret.getSecret()));
    }

    @Test
    public void testMigrationPublic() throws GeneralSecurityException {
        EncryptionIdentity encryptionIdentity = EncryptionIdentity.restoreFromString(PUBLIC_LEGACY_KEY);

        PQCEncryptor encryptor = new PQCEncryptor();
        IdentityKeys.PrivateKeys privateKey = encryptionIdentity.getPrivateKeys("password");
        byte[] data = encryptor.decodeBlock(null, Hash.decodeBytes64(NORMAL_AES_ENCRYPTION), privateKey);

        assertTrue(Arrays.equals(data, new byte[]{0x01, 0x02, 0x03, 0x04}));
    }

    @Test
    public void testMigrationUpload() throws GeneralSecurityException {
        EncryptionIdentity encryptionIdentity = EncryptionIdentity.restoreFromString(UPLOAD_LEGACY_KEY);

        PQCEncryptor encryptor = new PQCEncryptor();
        IdentityKeys.PrivateKeys privateKey = encryptionIdentity.getPrivateKeys("password");
        byte[] data = encryptor.decodeBlock(null, Hash.decodeBytes64(NORMAL_AES_ENCRYPTION), privateKey);

        assertTrue(Arrays.equals(data, new byte[]{0x01, 0x02, 0x03, 0x04}));
    }

    @Test
    public void testMigrationService() throws GeneralSecurityException {
        EncryptionIdentity encryptionIdentity = EncryptionIdentity.restoreFromString(SERVICE_LEGACY_KEY);

        PQCEncryptor encryptor = new PQCEncryptor();
        IdentityKeys.PrivateKeys privateKey = encryptionIdentity.getPrivateKeys("password");
        byte[] data = encryptor.decodeBlock(null, Hash.decodeBytes64(NORMAL_AES_ENCRYPTION), privateKey);

        assertTrue(Arrays.equals(data, new byte[]{0x01, 0x02, 0x03, 0x04}));
    }

    @Test
    public void testMigrationStable() throws GeneralSecurityException, JsonProcessingException {
        EncryptionIdentity encryptionIdentity = EncryptionIdentity.restoreFromString(PUBLIC_LEGACY_KEY);

        PQCEncryptor encryptor = new PQCEncryptor();
        IdentityKeys.PrivateKeys privateKey = encryptionIdentity.getPrivateKeys("password");
        BackupBlockStorage storage = MAPPER.readValue(STABLE_AES_STORAGE, BackupBlockStorage.class);
        byte[] data = encryptor.decodeBlock(storage, Hash.decodeBytes64(STABLE_AES_ENCRYPTION), privateKey);

        assertTrue(Arrays.equals(data, new byte[]{0x01, 0x02, 0x03, 0x04}));
    }

    @Test
    public void testMigrationBare() throws GeneralSecurityException, JsonProcessingException {
        EncryptionIdentity encryptionIdentity = EncryptionIdentity.generateKeyWithPassword("password");
        IdentityKeys identityKeys = IdentityKeys.fromString(BARE_LEGACY_KEY, encryptionIdentity.getPrivateIdentity("password"));

        PQCEncryptor encryptor = new PQCEncryptor();
        byte[] data = encryptor.decodeBlock(null, Hash.decodeBytes64(BARE_AES_ENCRYPTED),
                identityKeys.getPrivateKeys(encryptionIdentity.getPrivateIdentity("password")));

        assertTrue(Arrays.equals(data, new byte[]{0x01, 0x02, 0x03, 0x04}));
    }
}