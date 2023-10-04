package com.underscoreresearch.backup.file.implementation;

import java.io.File;

import org.apache.commons.lang3.SystemUtils;

public class NonMappedMetadataRepositoryTest extends LockingMetadataRepositoryTest {
    @Override
    protected LockingMetadataRepository createRepository(File tempDir) {
        if (SystemUtils.IS_OS_LINUX) {
            return new LockingMetadataRepository(tempDir.getPath(), false,
                    LockingMetadataRepository.LMDB_NON_MAPPING_STORAGE);
        }
        return null;
    }
}
