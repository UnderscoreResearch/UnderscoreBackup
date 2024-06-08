package com.underscoreresearch.backup.manifest;

import java.io.IOException;

import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.model.BackupActivatedShare;

public interface ShareManifestManager extends BaseManifestManager {

    void completeActivation() throws IOException;

    void addUsedDestinations(String destination) throws IOException;

    BackupActivatedShare getActivatedShare();

    void updateEncryptionKeys(EncryptionIdentity.PrivateIdentity privateKey) throws IOException;
}