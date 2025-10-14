package com.underscoreresearch.backup.configuration;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.underscoreresearch.backup.errorcorrection.implementation.NoneErrorCorrector;
import com.underscoreresearch.backup.errorcorrection.implementation.ReedSolomonErrorCorrector;
import com.underscoreresearch.backup.model.BackupConfiguration;

/**
 * Guice module for error correction implementations.
 * This module provides bindings for different error correction strategies.
 */
public class ErrorCorrectionModule extends AbstractModule {
    /**
     * Default maximum file size for error correction in bytes.
     */
    private static final int DEFAULT_MAXIMUM_FILE_SIZE = 16 * 1024 * 1024;
    
    /**
     * Default number of data slices for Reed-Solomon error correction.
     */
    private static final int DEFAULT_DATA_SLICES = 17;
    
    /**
     * Default number of parity slices for Reed-Solomon error correction.
     */
    private static final int DEFAULT_PARITY_SLICES = 3;

    /**
     * Provides a singleton ReedSolomonErrorCorrector instance.
     *
     * @param configuration The backup configuration
     * @return A configured ReedSolomonErrorCorrector instance
     */
    @Provides
    @Singleton
    public ReedSolomonErrorCorrector reedSolomonErrorCorrector(
            @Named(CommandLineModule.SOURCE_CONFIG) BackupConfiguration configuration) {
        return new ReedSolomonErrorCorrector(
                configuration.getProperty("reedSolomon.dataSlices", DEFAULT_DATA_SLICES),
                configuration.getProperty("reedSolomon.paritySlices", DEFAULT_PARITY_SLICES));
    }

    /**
     * Provides a singleton NoneErrorCorrector instance.
     *
     * @param configuration The backup configuration
     * @return A configured NoneErrorCorrector instance
     */
    @Provides
    @Singleton
    public NoneErrorCorrector noneErrorCorrector(
            @Named(CommandLineModule.SOURCE_CONFIG) BackupConfiguration configuration) {
        return new NoneErrorCorrector(
                configuration.getProperty("noneErrorCorrection.maximumFileSize", DEFAULT_MAXIMUM_FILE_SIZE)
        );
    }
}
