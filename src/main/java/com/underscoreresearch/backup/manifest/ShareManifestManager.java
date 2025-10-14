package com.underscoreresearch.backup.manifest;

import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.model.BackupActivatedShare;

import java.io.IOException;

/**
 * Interface for managing share manifests, extending BaseManifestManager with share-specific functionality.
 * Provides methods for share activation and management.
 */
public interface ShareManifestManager extends BaseManifestManager {

    /**
     * Complete the activation process for a share.
     * 
     * @throws IOException If there's an error completing activation
     */
    void completeActivation() throws IOException;

    /**
     * Add a destination to the list of used destinations for this share.
     * 
     * @param destination The destination identifier
     * @throws IOException If there's an error adding the destination
     */
    void addUsedDestinations(String destination) throws IOException;

    /**
     * Get the activated share information.
     * 
     * @return The activated share
     */
    BackupActivatedShare getActivatedShare();

    /**
     * Update encryption keys for the share using the provided private key.
     * 
     * @param privateKey The private key for encryption
     * @throws IOException If there's an error updating encryption keys
     */
    void updateEncryptionKeys(EncryptionIdentity.PrivateIdentity privateKey) throws IOException;
}
