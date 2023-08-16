package com.underscoreresearch.backup.file.implementation;

import java.io.File;

public class NonMappedMetadataRepositoryTest extends LockingMetadataRepositoryTest {
    @Override
    protected LockingMetadataRepository createRepository(File tempDir) {
        if (LockingMetadataRepository.getDefaultVersion() >= LockingMetadataRepository.LMDB_NON_MAPPING_STORAGE) {
            return new LockingMetadataRepository(tempDir.getPath(), false, 3);
        }
        return null;
    }
}
