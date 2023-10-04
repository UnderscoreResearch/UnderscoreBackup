package com.underscoreresearch.backup.file.implementation.performance;

import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.SystemUtils;

import com.underscoreresearch.backup.file.MetadataRepositoryStorage;
import com.underscoreresearch.backup.file.implementation.LmdbMetadataRepositoryStorage;

@Slf4j
public class LmdbMetadataRepositoryStorageTest extends MetadataRepositoryStoragePerformance {
    @Override
    protected MetadataRepositoryStorage createStorageEngine(Path directory) {
        if (SystemUtils.IS_OS_WINDOWS) {
            return new LmdbMetadataRepositoryStorage(directory.toString(), 0, false);
        }
        return null;
    }
}