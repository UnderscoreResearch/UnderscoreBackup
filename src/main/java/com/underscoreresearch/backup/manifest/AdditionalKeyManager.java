package com.underscoreresearch.backup.manifest;

import java.io.IOException;

import com.underscoreresearch.backup.encryption.EncryptionKey;

public interface AdditionalKeyManager {
    EncryptionKey generateNewKey(ManifestManager manifestManager)
            throws IOException;

    boolean addNewKey(EncryptionKey newKey, ManifestManager manifestManager) throws IOException;

    EncryptionKey findMatchingPrivateKey(EncryptionKey publicKey);

    EncryptionKey[] getKeys();

    void writeAdditionalKeys(EncryptionKey.PrivateKey otherKey) throws IOException;
}
