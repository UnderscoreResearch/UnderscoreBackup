package com.underscoreresearch.backup.encryption;

import com.underscoreresearch.backup.model.BackupBlockStorage;

public interface Encryptor {
    byte[] encryptBlock(BackupBlockStorage storage, byte[] data);

    byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData);

    default void backfillEncryption(BackupBlockStorage storage, byte[] encryptedBlob) {
    }

    default boolean requireStorage() {
        return false;
    }

    ;

    default boolean validStorage(BackupBlockStorage storage) {
        return true;
    }

    ;
}
