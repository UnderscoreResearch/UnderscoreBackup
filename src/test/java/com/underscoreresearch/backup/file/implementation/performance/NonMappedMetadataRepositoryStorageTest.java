package com.underscoreresearch.backup.file.implementation.performance;

import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.SystemUtils;

import com.underscoreresearch.backup.file.MetadataRepositoryStorage;
import com.underscoreresearch.backup.file.implementation.LmdbMetadataRepositoryStorage;

@Slf4j
public class NonMappedMetadataRepositoryStorageTest extends MetadataRepositoryStoragePerformance {
    @Override
    protected MetadataRepositoryStorage createStorageEngine(Path directory) {
        if (SystemUtils.IS_OS_LINUX) {
            return new LmdbMetadataRepositoryStorage.NonMemoryMapped(directory.toString(), 0, false);
        }
        return null;
    }
}