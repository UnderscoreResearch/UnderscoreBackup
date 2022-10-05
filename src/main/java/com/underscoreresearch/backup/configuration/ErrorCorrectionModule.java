package com.underscoreresearch.backup.configuration;

import java.io.IOException;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.underscoreresearch.backup.errorcorrection.implementation.NoneErrorCorrector;
import com.underscoreresearch.backup.errorcorrection.implementation.ReedSolomonErrorCorrector;
import com.underscoreresearch.backup.model.BackupConfiguration;

public class ErrorCorrectionModule extends AbstractModule {
    private static final int DEFAULT_MAXIMUM_FILE_SIZE = 16 * 1024 * 1024;
    private static final int DEFAULT_DATA_SLICES = 17;
    private static final int DEFAULT_PARITY_SLICES = 3;

    @Provides
    @Singleton
    public ReedSolomonErrorCorrector reedSolomonErrorCorrector(
            @Named(CommandLineModule.SOURCE_CONFIG) BackupConfiguration configuration) throws IOException {
        return new ReedSolomonErrorCorrector(
                configuration.getProperty("reedSolomon.dataSlices", DEFAULT_DATA_SLICES),
                configuration.getProperty("reedSolomon.paritySlices", DEFAULT_PARITY_SLICES));
    }

    @Provides
    @Singleton
    public NoneErrorCorrector noneErrorCorrector(
            @Named(CommandLineModule.SOURCE_CONFIG) BackupConfiguration configuration) throws IOException {
        return new NoneErrorCorrector(
                configuration.getProperty("noneErrorCorrection.maximumFileSize", DEFAULT_MAXIMUM_FILE_SIZE)
        );
    }
}
