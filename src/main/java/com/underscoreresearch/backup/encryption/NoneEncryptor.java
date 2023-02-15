package com.underscoreresearch.backup.encryption;

import static com.underscoreresearch.backup.encryption.NoneEncryptor.NONE_ENCRYPTION;

import com.underscoreresearch.backup.model.BackupBlockStorage;

@EncryptorPlugin(value = NONE_ENCRYPTION)
public class NoneEncryptor implements Encryptor {
    public static final String NONE_ENCRYPTION = "NONE";

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
