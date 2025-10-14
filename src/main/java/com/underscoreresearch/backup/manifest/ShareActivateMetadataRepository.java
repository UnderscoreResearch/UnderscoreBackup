package com.underscoreresearch.backup.manifest;

import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.implementation.NullRepository;
import com.underscoreresearch.backup.manifest.implementation.LoggingMetadataRepository;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockAdditional;
import com.underscoreresearch.backup.model.BackupShare;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.Map;

/**
 * Special metadata repository implementation for share activation.
 * Extends LoggingMetadataRepository to provide specific behavior for share activation.
 */
public class ShareActivateMetadataRepository extends LoggingMetadataRepository {
    /**
     * Constructor for ShareActivateMetadataRepository.
     * 
     * @param repository The underlying metadata repository
     * @param manifestManager The manifest manager
     * @param shares Map of share identifiers to BackupShare objects
     * @param shareManagers Map of share identifiers to ShareManifestManager objects
     */
    public ShareActivateMetadataRepository(MetadataRepository repository,
                                           ManifestManager manifestManager,
                                           Map<String, BackupShare> shares,
                                           Map<String, ShareManifestManager> shareManagers) {
        super(new NullShareRepository(repository), manifestManager, shares, shareManagers, false);
    }

    /**
     * Override to control which log entries are written.
     * Only writes log entries that don't come from the main manifest manager.
     * 
     * @param logger The manifest manager writing the log
     * @param type The type of log entry
     * @param obj The object to log
     */
    @Override
    protected synchronized void writeLogEntry(BaseManifestManager logger, String type, Object obj) {
        if (logger != getManifestManager()) {
            super.writeLogEntry(logger, type, obj);
        }
    }

    /**
     * Inner class that provides a null repository implementation that passes through
     * block-related operations to the underlying repository.
     */
    @RequiredArgsConstructor
    private static class NullShareRepository extends NullRepository {
        private final MetadataRepository repository;

        /**
         * Get a block by its hash.
         * 
         * @param hash The hash of the block
         * @return The block, or null if not found
         * @throws IOException If there's an error retrieving the block
         */
        @Override
        public BackupBlock block(String hash) throws IOException {
            return repository.block(hash);
        }

        /**
         * Get additional block information for a specific public key and block hash.
         * 
         * @param publicKey The public key
         * @param blockHash The block hash
         * @return The additional block information
         * @throws IOException If there's an error retrieving the information
         */
        @Override
        public BackupBlockAdditional additionalBlock(String publicKey, String blockHash) throws IOException {
            return repository.additionalBlock(publicKey, blockHash);
        }

        /**
         * Add additional block information.
         * 
         * @param block The additional block information
         * @throws IOException If there's an error adding the information
         */
        @Override
        public void addAdditionalBlock(BackupBlockAdditional block) throws IOException {
            repository.addAdditionalBlock(block);
        }
    }
}
