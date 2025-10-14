package com.underscoreresearch.backup.encryption.encryptors;

import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.encryption.EncryptorPlugin;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.model.BackupBlockStorage;

import java.security.GeneralSecurityException;

import static com.underscoreresearch.backup.encryption.encryptors.NoneEncryptor.NONE_ENCRYPTION;

/**
 * A no-op encryptor that passes data through without encryption.
 * This encryptor is used for testing or when encryption is not required.
 */
@EncryptorPlugin(value = NONE_ENCRYPTION)
public class NoneEncryptor implements Encryptor {
    /**
     * Identifier for the none encryption type.
     */
    public static final String NONE_ENCRYPTION = "NONE";

    /**
     * Returns the input data without encryption.
     *
     * @param storage The storage metadata (ignored)
     * @param data The data to "encrypt"
     * @param keys The identity keys (ignored)
     * @return The original data
     * @throws GeneralSecurityException Never thrown
     */
    @Override
    public byte[] encryptBlock(BackupBlockStorage storage, byte[] data, IdentityKeys keys) throws GeneralSecurityException {
        return data;
    }

    /**
     * Returns the input data without decryption.
     *
     * @param storage The storage metadata (ignored)
     * @param encryptedData The data to "decrypt"
     * @param keys The private keys (ignored)
     * @return The original data
     * @throws GeneralSecurityException Never thrown
     */
    @Override
    public byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData, IdentityKeys.PrivateKeys keys) throws GeneralSecurityException {
        return encryptedData;
    }

    /**
     * Returns the original storage without modification.
     *
     * @param storage The storage metadata
     * @param oldPrivateKey The old private keys (ignored)
     * @param newPublicKey The new public keys (ignored)
     * @return The original storage metadata
     * @throws GeneralSecurityException Never thrown
     */
    @Override
    public BackupBlockStorage reKeyStorage(BackupBlockStorage storage, IdentityKeys.PrivateKeys oldPrivateKey, IdentityKeys newPublicKey) throws GeneralSecurityException {
        return storage;
    }
}
