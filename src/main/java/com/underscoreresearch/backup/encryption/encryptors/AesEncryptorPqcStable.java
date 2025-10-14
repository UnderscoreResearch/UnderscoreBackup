package com.underscoreresearch.backup.encryption.encryptors;

import com.google.common.collect.Sets;
import com.underscoreresearch.backup.encryption.Hash;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.encryption.PublicKeyMethod;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import lombok.extern.slf4j.Slf4j;

import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Set;

import static com.underscoreresearch.backup.encryption.IdentityKeys.KYBER_KEY;
import static com.underscoreresearch.backup.encryption.IdentityKeys.X25519_KEY;

/**
 * AES encryptor implementation using Post-Quantum Cryptography with stable output.
 * This class extends the GCM stable encryptor to add support for post-quantum
 * cryptographic algorithms with stable output for deduplication.
 */
@Slf4j
public class AesEncryptorPqcStable extends AesEncryptorGcmStable {
    /**
     * Set of key types used for post-quantum cryptography.
     * Includes both X25519 and Kyber keys for hybrid encryption.
     */
    public static final Set<String> KEY_TYPES_PQC = Sets.newHashSet(X25519_KEY, KYBER_KEY);

    /**
     * Creates the encryption key secret from identity keys and stores it in the storage metadata.
     * Uses both X25519 and Kyber key types for post-quantum security.
     *
     * @param storage The storage metadata
     * @param key The identity keys
     * @return The combined key secret
     * @throws GeneralSecurityException If key creation fails
     */
    protected byte[] createKeySecret(BackupBlockStorage storage, IdentityKeys key) throws GeneralSecurityException {
        IdentityKeys.EncryptionParameters parameters = key.getEncryptionParameters(KEY_TYPES_PQC);
        byte[] combinedKey = parameters.getSecret();

        storage.addProperty(X25519_KEY, Hash.encodeBytes(parameters.getKeys().get(X25519_KEY).getEncapsulation()));
        storage.addProperty(KYBER_KEY, Hash.encodeBytes(parameters.getKeys().get(KYBER_KEY).getEncapsulation()));
        return combinedKey;
    }

    /**
     * Recreates the key secret from the storage metadata and private keys.
     * Handles both cases where only X25519 key is available or both X25519 and Kyber keys are available.
     *
     * @param storage The storage metadata
     * @param key The private keys
     * @return The recreated key secret
     * @throws GeneralSecurityException If key recreation fails
     */
    protected byte[] recreateKeySecret(BackupBlockStorage storage, IdentityKeys.PrivateKeys key) throws GeneralSecurityException {
        if (!storage.getProperties().containsKey(KYBER_KEY)) {
            return key.recreateSecret(Map.of(
                    X25519_KEY, new PublicKeyMethod.EncapsulatedKey(Hash.decodeBytes(storage.getProperties().get(X25519_KEY)))));
        }
        return key.recreateSecret(Map.of(
                X25519_KEY, new PublicKeyMethod.EncapsulatedKey(Hash.decodeBytes(storage.getProperties().get(X25519_KEY))),
                KYBER_KEY, new PublicKeyMethod.EncapsulatedKey(Hash.decodeBytes(storage.getProperties().get(KYBER_KEY)))));
    }

    /**
     * Creates the encryption key secret from identity keys.
     * Uses both X25519 and Kyber key types for post-quantum security.
     *
     * @param key The identity keys
     * @return The encryption parameters
     * @throws GeneralSecurityException If key creation fails
     */
    protected IdentityKeys.EncryptionParameters createKeySecret(IdentityKeys key) throws GeneralSecurityException {
        return key.getEncryptionParameters(KEY_TYPES_PQC);
    }
}
