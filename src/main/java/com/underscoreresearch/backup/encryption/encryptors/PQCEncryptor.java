package com.underscoreresearch.backup.encryption.encryptors;

import com.google.inject.Inject;
import com.underscoreresearch.backup.encryption.EncryptorPlugin;
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
import static com.underscoreresearch.backup.encryption.encryptors.AesEncryptorPqcStable.KEY_TYPES_PQC;
import static com.underscoreresearch.backup.encryption.encryptors.PQCEncryptor.PQC_ENCRYPTION;

/**
 * Post-Quantum Cryptography encryptor implementation.
 * This class extends the BaseAesEncryptor to add support for post-quantum
 * cryptographic algorithms for key encapsulation.
 *
 * <p>
 * It is designed to work with the Kyber post-quantum algorithm in addition to the X25519 
 * key exchange algorithm for deriving the AES encryption key, providing a hybrid approach
 * that maintains security even if one of the algorithms is compromised.
 * </p>
 *
 * <p>
 * The class supports both standard and stable (deduplication-friendly) encryption formats
 * using AesEncryptorPqc and AesEncryptorPqcStable implementations.
 * </p>
 */
@EncryptorPlugin(PQC_ENCRYPTION)
@Slf4j
public class PQCEncryptor extends BaseAesEncryptor {
    public static final String PQC_ENCRYPTION = "PQC";

    private static final AesEncryptorFormat stableFormat = new AesEncryptorPqcStable();
    private static final AesEncryptorFormat defaultFormat = new AesEncryptorPqc();

    /**
     * Constructor for PQCEncryptor.
     * Initializes the encryptor with default settings.
     */
    @Inject
    public PQCEncryptor() {
    }

    /**
     * Stores encryption parameters in the storage metadata.
     * This method adds both X25519 and Kyber key encapsulations to the storage properties.
     *
     * @param storage The storage metadata
     * @param ret The encryption parameters to store
     */
    @Override
    protected void storeEncryptionParameters(BackupBlockStorage storage, IdentityKeys.EncryptionParameters ret) {
        storage.getProperties().put(X25519_KEY, Hash.encodeBytes(ret.getKeys().get(X25519_KEY).getEncapsulation()));
        storage.getProperties().put(KYBER_KEY, Hash.encodeBytes(ret.getKeys().get(KYBER_KEY).getEncapsulation()));
    }

    /**
     * Gets the set of encryption key types used by this encryptor.
     * Returns both X25519 and Kyber key types for post-quantum security.
     *
     * @return The set of encryption key types
     */
    @Override
    protected Set<String> getEncryptionKeys() {
        return KEY_TYPES_PQC;
    }

    /**
     * Creates encapsulated keys from storage metadata.
     * This method extracts both X25519 and Kyber public keys from storage properties
     * and creates encapsulated keys. If Kyber key is not available, only X25519 key is used.
     *
     * @param storage The storage metadata containing the public keys
     * @return A map of key types to encapsulated keys
     */
    @Override
    protected Map<String, PublicKeyMethod.EncapsulatedKey> createEncapsulatedKeys(BackupBlockStorage storage) {
        PublicKeyMethod.EncapsulatedKey x25519PK = new PublicKeyMethod.EncapsulatedKey(Hash.decodeBytes(storage.getProperties().get(X25519_KEY)));
        if (storage.getProperties().containsKey(KYBER_KEY)) {
            PublicKeyMethod.EncapsulatedKey kyberPK = new PublicKeyMethod.EncapsulatedKey(Hash.decodeBytes(storage.getProperties().get(KYBER_KEY)));
            return Map.of(
                    X25519_KEY, x25519PK,
                    KYBER_KEY, kyberPK
            );
        }
        return Map.of(
                X25519_KEY, x25519PK
        );
    }

    /**
     * Determines the appropriate encryptor format based on the encrypted data.
     * This method extends the base implementation to handle PQC-specific formats.
     *
     * @param data The encrypted data
     * @return The appropriate AesEncryptorFormat implementation
     * @throws IllegalArgumentException If the encryption format is unknown
     */
    @Override
    protected AesEncryptorFormat getEncryptorFormat(byte[] data) {
        if (data.length % 4 == 0) {
            return super.getEncryptorFormat(data);
        }

        return switch (data[0]) {
            case AesEncryptionFormatTypes.NON_PADDED_PQC,
                 AesEncryptionFormatTypes.PADDED_PQC -> defaultFormat;
            case AesEncryptionFormatTypes.NON_PADDED_GCM_STABLE,
                 AesEncryptionFormatTypes.PADDED_GCM_STABLE -> stableFormat;
            default -> super.getEncryptorFormat(data);
        };
    }

    /**
     * Encrypts a data block using the appropriate encryption format.
     * Uses stable format for deduplication if storage is provided and stableDedupe is enabled.
     * This method uses PQC-specific formats for encryption.
     *
     * @param storage The storage metadata
     * @param data The data to encrypt
     * @param key The identity keys to use for encryption
     * @return The encrypted data
     * @throws GeneralSecurityException If encryption fails
     */
    @Override
    public byte[] encryptBlock(BackupBlockStorage storage, byte[] data, IdentityKeys key) throws GeneralSecurityException {
        if (storage != null && isStableDedupe()) {
            return stableFormat.encryptBlock(storage, data, key);
        }
        return defaultFormat.encryptBlock(storage, data, key);
    }
}