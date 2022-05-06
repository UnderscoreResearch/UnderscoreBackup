package com.underscoreresearch.backup.encryption;

import com.underscoreresearch.backup.model.BackupBlockStorage;

@EncryptorPlugin(value = "NONE")
public class NoneEncryptor implements Encryptor {
    @Override
    public byte[] encryptBlock(BackupBlockStorage storage, byte[] data) {
        return data;
    }

    @Override
    public byte[] decodeBlock(BackupBlockStorage storage, byte[] encryptedData) {
        return encryptedData;
    }
}
