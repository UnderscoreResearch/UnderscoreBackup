package com.underscoreresearch.backup.encryption;

import com.underscoreresearch.backup.model.BackupBlockStorage;

@EncryptorPlugin(value = "NONE")
public class NoneEncryptor implements Encryptor {
    @Override
    public byte[] encryptBlock(BackupBlockStorage storage, byte[] data, EncryptionKey key) {
        return data;
    }

    @Override
    public byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData, EncryptionKey.PrivateKey key) {
        return encryptedData;
    }

    @Override
    public BackupBlockStorage reKeyStorage(BackupBlockStorage storage,
                                           EncryptionKey.PrivateKey oldPrivateKey,
                                           EncryptionKey newPublicKey) {
        return storage;
    }
}
