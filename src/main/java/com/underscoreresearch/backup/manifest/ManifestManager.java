package com.underscoreresearch.backup.manifest;

import java.io.IOException;
import java.util.Map;

import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.file.MetadataRepository;

public interface ManifestManager extends BaseManifestManager {

    void replayLog(LogConsumer consumer, String password) throws IOException;

    void repairRepository(LogConsumer logConsumer, String password) throws IOException;

    void optimizeLog(MetadataRepository existingRepository, LogConsumer logConsumer, boolean force) throws IOException;


    void setDisabledFlushing(boolean disabledFlushing);

    BackupContentsAccess backupContents(Long timestamp, boolean includeDeleted) throws IOException;

    BackupSearchAccess backupSearch(Long timestamp, boolean includeDeleted) throws IOException;

    void activateShares(LogConsumer repository, EncryptionKey.PrivateKey privateKey) throws IOException;

    boolean isBusy();

    boolean isRepositoryReady();

    void updateKeyData(EncryptionKey key) throws IOException;

    void updateServiceSourceData(EncryptionKey encryptionKey) throws IOException;

    Map<String, ShareManifestManager> getActivatedShares();

    void updateShareEncryption(EncryptionKey.PrivateKey privateKey) throws IOException;
}
