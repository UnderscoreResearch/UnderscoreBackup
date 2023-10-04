package com.underscoreresearch.backup.file.implementation;

import java.io.File;

import org.apache.commons.lang3.SystemUtils;

public class LmdbMetadataRepositoryTest extends LockingMetadataRepositoryTest {
    @Override
    protected LockingMetadataRepository createRepository(File tempDir) {
        if (SystemUtils.IS_OS_WINDOWS) {
            return new LockingMetadataRepository(tempDir.getPath(), false, LockingMetadataRepository.LMDB_STORAGE);
        }
        return null;
    }
}
