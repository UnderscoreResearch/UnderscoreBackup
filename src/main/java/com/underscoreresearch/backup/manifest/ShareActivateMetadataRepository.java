package com.underscoreresearch.backup.manifest;

import java.io.IOException;
import java.util.Map;

import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.implementation.NullRepository;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockAdditional;
import com.underscoreresearch.backup.model.BackupShare;

public class ShareActivateMetadataRepository extends LoggingMetadataRepository {

    public ShareActivateMetadataRepository(MetadataRepository repository,
                                           ManifestManager manifestManager,
                                           Map<String, BackupShare> shares,
                                           Map<String, ShareManifestManager> shareManagers) {
        super(new NullRepository() {
            @Override
            public BackupBlock block(String hash) throws IOException {
                return repository.block(hash);
            }

            @Override
            public BackupBlockAdditional additionalBlock(String publicKey, String blockHash) throws IOException {
                return repository.additionalBlock(publicKey, blockHash);
            }

            @Override
            public void addAdditionalBlock(BackupBlockAdditional block) throws IOException {
                repository.addAdditionalBlock(block);
            }
        }, manifestManager, shares, shareManagers, false);
    }

    @Override
    protected synchronized void writeLogEntry(BaseManifestManager logger, String type, Object obj) {
        if (logger != getManifestManager()) {
            super.writeLogEntry(logger, type, obj);
        }
    }
}
