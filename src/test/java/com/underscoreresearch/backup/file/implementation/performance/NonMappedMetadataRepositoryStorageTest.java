package com.underscoreresearch.backup.file.implementation.performance;

import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.file.MetadataRepositoryStorage;
import com.underscoreresearch.backup.file.implementation.LmdbMetadataRepositoryStorage;
import com.underscoreresearch.backup.file.implementation.LockingMetadataRepository;

@Slf4j
public class NonMappedMetadataRepositoryStorageTest extends MetadataRepositoryStoragePerformance {
    @Override
    protected MetadataRepositoryStorage createStorageEngine(Path directory) {
        if (LockingMetadataRepository.getDefaultVersion() >= LockingMetadataRepository.LMDB_NON_MAPPING_STORAGE) {
            return new LmdbMetadataRepositoryStorage.NonMemoryMapped(directory.toString(), false);
        }
        return null;
    }
}