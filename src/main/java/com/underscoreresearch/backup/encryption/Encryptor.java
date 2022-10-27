package com.underscoreresearch.backup.encryption;

import com.underscoreresearch.backup.model.BackupBlockStorage;

public interface Encryptor {
    byte[] encryptBlock(BackupBlockStorage storage, byte[] data, EncryptionKey key);

    byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData, EncryptionKey.PrivateKey key);

    default void backfillEncryption(BackupBlockStorage storage, byte[] encryptedBlob) {
    }

    default boolean validStorage(BackupBlockStorage storage) {
        return true;
    }

    BackupBlockStorage reKeyStorage(BackupBlockStorage storage,
                                    EncryptionKey.PrivateKey oldPrivateKey,
                                    EncryptionKey newPublicKey);
}
