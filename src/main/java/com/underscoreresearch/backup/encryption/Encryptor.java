package com.underscoreresearch.backup.encryption;

import com.underscoreresearch.backup.model.BackupBlockStorage;

import java.security.GeneralSecurityException;

public interface Encryptor {
    byte[] encryptBlock(BackupBlockStorage storage, byte[] data, IdentityKeys keys) throws GeneralSecurityException;

    byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData, IdentityKeys.PrivateKeys keys) throws GeneralSecurityException;

    default boolean validStorage(BackupBlockStorage storage) {
        return true;
    }

    BackupBlockStorage reKeyStorage(BackupBlockStorage storage,
                                    IdentityKeys.PrivateKeys oldPrivateKey,
                                    IdentityKeys newPublicKey) throws GeneralSecurityException;
}
