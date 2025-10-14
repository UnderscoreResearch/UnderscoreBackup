package com.underscoreresearch.backup.manifest;

import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOProvider;

import java.io.IOException;
import java.util.Map;

/**
 * Interface for managing backup manifests, extending BaseManifestManager with additional functionality.
 * Provides methods for log replay, repository repair, and share management.
 */
public interface ManifestManager extends BaseManifestManager {

    /**
     * Replay the log using the provided consumer and password.
     * 
     * @param consumer The log consumer to process log entries
     * @param password The password for decryption
     * @throws IOException If there's an error replaying the log
     */
    void replayLog(LogConsumer consumer, String password) throws IOException;

    /**
     * Set whether the repository is being repaired.
     * 
     * @param repairingRepository True if the repository is being repaired, false otherwise
     */
    void setRepairingRepository(boolean repairingRepository);

    /**
     * Repair the repository by replaying logs.
     * 
     * @param logConsumer The log consumer to process log entries
     * @param password The password for decryption
     * @throws IOException If there's an error repairing the repository
     */
    void repairRepository(LogConsumer logConsumer, String password) throws IOException;

    /**
     * Optimize the log by consolidating entries.
     * 
     * @param existingRepository The existing metadata repository
     * @param logConsumer The log consumer to process log entries
     * @param force Whether to force optimization even if not necessary
     * @return True if optimization was performed, false otherwise
     * @throws IOException If there's an error optimizing the log
     */
    boolean optimizeLog(MetadataRepository existingRepository, LogConsumer logConsumer, boolean force) throws IOException;

    /**
     * Get the IO provider associated with this manifest manager.
     * 
     * @return The IO provider
     */
    IOProvider getIoProvider();

    /**
     * Set whether flushing is disabled.
     * 
     * @param disabledFlushing True if flushing should be disabled, false otherwise
     */
    void setDisabledFlushing(boolean disabledFlushing);

    /**
     * Get access to backup contents at a specific timestamp.
     * 
     * @param timestamp The timestamp to view contents at, or null for latest
     * @param includeDeleted Whether to include deleted files
     * @return A BackupContentsAccess instance
     * @throws IOException If there's an error accessing backup contents
     */
    BackupContentsAccess backupContents(Long timestamp, boolean includeDeleted) throws IOException;

    /**
     * Get access to search backup contents at a specific timestamp.
     * 
     * @param timestamp The timestamp to search at, or null for latest
     * @param includeDeleted Whether to include deleted files
     * @return A BackupSearchAccess instance
     * @throws IOException If there's an error accessing search functionality
     */
    BackupSearchAccess backupSearch(Long timestamp, boolean includeDeleted) throws IOException;

    /**
     * Activate shares using the provided private key.
     * 
     * @param repository The log consumer for the repository
     * @param privateKey The private key for activation
     * @throws IOException If there's an error activating shares
     */
    void activateShares(LogConsumer repository, EncryptionIdentity.PrivateIdentity privateKey) throws IOException;

    /**
     * Check if the manifest manager is currently busy.
     * 
     * @return True if busy, false otherwise
     */
    boolean isBusy();

    /**
     * Check if the repository is ready for use.
     * 
     * @return True if ready, false otherwise
     */
    boolean isRepositoryReady();

    /**
     * Update key data with the provided encryption identity.
     * 
     * @param key The encryption identity
     * @throws IOException If there's an error updating key data
     */
    void updateKeyData(EncryptionIdentity key) throws IOException;

    /**
     * Update service source data with the provided encryption key.
     * 
     * @param encryptionKey The encryption key
     * @throws IOException If there's an error updating service source data
     */
    void updateServiceSourceData(EncryptionIdentity encryptionKey) throws IOException;

    /**
     * Get a map of activated shares.
     * 
     * @return A map of share identifiers to ShareManifestManager instances
     */
    Map<String, ShareManifestManager> getActivatedShares();

    /**
     * Update share encryption with the provided private key.
     * 
     * @param privateKey The private key for encryption
     * @throws IOException If there's an error updating share encryption
     */
    void updateShareEncryption(EncryptionIdentity.PrivateIdentity privateKey) throws IOException;

    /**
     * Set a dependent manifest manager.
     * 
     * @param dependentManifestManager The dependent manifest manager
     */
    void setDependentManager(ManifestManager dependentManifestManager);
}
