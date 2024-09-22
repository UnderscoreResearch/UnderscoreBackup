package com.underscoreresearch.backup.manifest;

import java.io.IOException;
import java.util.Map;

import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.file.MetadataRepository;

public interface ManifestManager extends BaseManifestManager {

    void replayLog(LogConsumer consumer, String password) throws IOException;

    void setRepairingRepository(boolean repairingRepository);

    void repairRepository(LogConsumer logConsumer, String password) throws IOException;

    boolean optimizeLog(MetadataRepository existingRepository, LogConsumer logConsumer, boolean force) throws IOException;

    void setDisabledFlushing(boolean disabledFlushing);

    BackupContentsAccess backupContents(Long timestamp, boolean includeDeleted) throws IOException;

    BackupSearchAccess backupSearch(Long timestamp, boolean includeDeleted) throws IOException;

    void activateShares(LogConsumer repository, EncryptionIdentity.PrivateIdentity privateKey) throws IOException;

    boolean isBusy();

    boolean isRepositoryReady();

    void updateKeyData(EncryptionIdentity key) throws IOException;

    void updateServiceSourceData(EncryptionIdentity encryptionKey) throws IOException;

    Map<String, ShareManifestManager> getActivatedShares();

    void updateShareEncryption(EncryptionIdentity.PrivateIdentity privateKey) throws IOException;

    void setDependentManager(ManifestManager dependentManifestManager);
}
