package com.underscoreresearch.backup.encryption;

public interface Encryptor {
    byte[] encryptBlock(byte[] data);

    byte[] decodeBlock(byte[] encryptedData);
}
