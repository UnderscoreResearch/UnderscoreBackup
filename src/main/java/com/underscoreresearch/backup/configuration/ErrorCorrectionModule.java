package com.underscoreresearch.backup.configuration;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.underscoreresearch.backup.errorcorrection.implementation.NoneErrorCorrector;
import com.underscoreresearch.backup.errorcorrection.implementation.ReedSolomonErrorCorrector;
import com.underscoreresearch.backup.model.BackupConfiguration;

import java.io.IOException;

public class ErrorCorrectionModule extends AbstractModule {
    private static final int DEFAULT_MAXIMUM_FILE_SIZE = 16 * 1024 * 1024;
    private static final int DEFAULT_DATA_SLICES = 17;
    private static final int DEFAULT_PARITY_SLICES = 3;

    @Provides
    @Singleton
    public ReedSolomonErrorCorrector reedSolomonErrorCorrector(BackupConfiguration configuration) throws IOException {
        return new ReedSolomonErrorCorrector(
                configuration.getProperty("reedSolomon.dataSlices", DEFAULT_DATA_SLICES),
                configuration.getProperty("reedSolomon.paritySlices", DEFAULT_PARITY_SLICES));
    }

    @Provides
    @Singleton
    public NoneErrorCorrector noneErrorCorrector(BackupConfiguration configuration) throws IOException {
        return new NoneErrorCorrector(
                configuration.getProperty("noneErrorCorrection.maximumFileSize", DEFAULT_MAXIMUM_FILE_SIZE)
        );
    }
}
