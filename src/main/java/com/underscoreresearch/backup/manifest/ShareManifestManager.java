package com.underscoreresearch.backup.manifest;

import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.model.BackupActivatedShare;

import java.io.IOException;

public interface ShareManifestManager extends BaseManifestManager {

    void completeActivation() throws IOException;

    void addUsedDestinations(String destination) throws IOException;

    BackupActivatedShare getActivatedShare();

    void updateEncryptionKeys(EncryptionIdentity.PrivateIdentity privateKey) throws IOException;
}