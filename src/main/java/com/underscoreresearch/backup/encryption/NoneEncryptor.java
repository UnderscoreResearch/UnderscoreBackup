package com.underscoreresearch.backup.encryption;

@EncryptorPlugin(value = "NONE")
public class NoneEncryptor implements Encryptor {
    @Override
    public byte[] encryptBlock(byte[] data) {
        return data;
    }

    @Override
    public byte[] decodeBlock(byte[] encryptedData) {
        return encryptedData;
    }
}
