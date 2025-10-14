package com.underscoreresearch.backup.encryption;

import com.underscoreresearch.backup.model.BackupBlockStorage;

import java.security.GeneralSecurityException;

/**
 * Interface for encryption and decryption of backup blocks.
 * Implementations provide specific encryption algorithms and methods
 * for securing backup data.
 */
public interface Encryptor {
    /**
     * Encrypts a data block using the specified identity keys.
     *
     * @param storage The storage metadata for the block
     * @param data The data to encrypt
     * @param keys The identity keys to use for encryption
     * @return The encrypted data
     * @throws GeneralSecurityException If encryption fails
     */
    byte[] encryptBlock(BackupBlockStorage storage, byte[] data, IdentityKeys keys) throws GeneralSecurityException;

    /**
     * Decodes (decrypts) a block using the specified private keys.
     *
     * @param storage The storage metadata for the block
     * @param encryptedData The encrypted data to decode
     * @param keys The private keys to use for decryption
     * @return The decrypted data
     * @throws GeneralSecurityException If decryption fails
     */
    byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData, IdentityKeys.PrivateKeys keys) throws GeneralSecurityException;

    /**
     * Checks if the storage configuration is valid for this encryptor.
     *
     * @param storage The storage metadata to validate
     * @return True if the storage is valid for this encryptor, false otherwise
     */
    default boolean validStorage(BackupBlockStorage storage) {
        return true;
    }

    /**
     * Re-encrypts storage metadata with new keys.
     *
     * @param storage The storage metadata to re-key
     * @param oldPrivateKey The old private keys
     * @param newPublicKey The new public keys
     * @return The updated storage metadata
     * @throws GeneralSecurityException If re-keying fails
     */
    BackupBlockStorage reKeyStorage(BackupBlockStorage storage,
                                    IdentityKeys.PrivateKeys oldPrivateKey,
                                    IdentityKeys newPublicKey) throws GeneralSecurityException;
}
