package com.underscoreresearch.backup.manifest;

import java.io.IOException;

import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.model.BackupActivatedShare;

public interface ShareManifestManager extends BaseManifestManager {

    void completeActivation() throws IOException;

    void addUsedDestinations(String destination) throws IOException;

    BackupActivatedShare getActivatedShare();

    void updateEncryptionKeys(EncryptionKey.PrivateKey privateKey) throws IOException;
}