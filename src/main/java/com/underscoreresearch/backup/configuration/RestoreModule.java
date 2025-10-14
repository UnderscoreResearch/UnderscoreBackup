package com.underscoreresearch.backup.configuration;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.block.FileDownloader;
import com.underscoreresearch.backup.block.implementation.BlockDownloaderImpl;
import com.underscoreresearch.backup.block.implementation.FileDownloaderImpl;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.DownloadScheduler;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.io.implementation.DownloadSchedulerImpl;
import com.underscoreresearch.backup.model.BackupConfiguration;

import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE_CONFIG;

/**
 * Guice module for restore-related dependencies.
 * This module provides bindings for components used in the restore process,
 * including file and block downloaders.
 */
public class RestoreModule extends AbstractModule {
    /**
     * Named constant for download threads.
     */
    public static final String DOWNLOAD_THREADS = "DOWNLOAD_THREADS";
    
    /**
     * Default number of download threads.
     */
    private static final int DEFAULT_DOWNLOAD_THREADS = 4;

    /**
     * Gets the global download thread count from configuration.
     *
     * @param configuration The backup configuration
     * @return The number of download threads to use
     */
    public static int getGlobalDownloadThreads(BackupConfiguration configuration) {
        int threads;
        if (configuration.getLimits() == null || configuration.getLimits().getMaximumDownloadThreads() == null)
            threads = DEFAULT_DOWNLOAD_THREADS;
        else
            threads = configuration.getLimits().getMaximumDownloadThreads();
        return threads;
    }

    /**
     * Provides the download thread count.
     *
     * @param configuration The backup configuration
     * @return The number of download threads to use
     */
    @Named(DOWNLOAD_THREADS)
    @Provides
    public int getDownloadThreads(BackupConfiguration configuration) {
        return getGlobalDownloadThreads(configuration);
    }

    /**
     * Provides a singleton DownloadSchedulerImpl instance.
     *
     * @param threads The number of download threads
     * @param repository The metadata repository
     * @param fileDownloader The file downloader
     * @return A configured DownloadSchedulerImpl instance
     */
    @Singleton
    @Provides
    public DownloadSchedulerImpl downloadSchedulerImpl(@Named(DOWNLOAD_THREADS) int threads,
                                                       MetadataRepository repository,
                                                       FileDownloader fileDownloader) {
        return new DownloadSchedulerImpl(threads, repository, fileDownloader);
    }

    /**
     * Provides a singleton DownloadScheduler instance.
     *
     * @param downloadScheduler The DownloadSchedulerImpl instance
     * @return The DownloadScheduler interface implementation
     */
    @Singleton
    @Provides
    public DownloadScheduler downloadScheduler(DownloadSchedulerImpl downloadScheduler) {
        return downloadScheduler;
    }

    /**
     * Provides a singleton FileDownloader instance.
     *
     * @param fileDownloader The FileDownloaderImpl instance
     * @return The FileDownloader interface implementation
     */
    @Singleton
    @Provides
    public FileDownloader fileDownloader(FileDownloaderImpl fileDownloader) {
        return fileDownloader;
    }

    /**
     * Provides a singleton BlockDownloaderImpl instance.
     *
     * @param configuration The backup configuration
     * @param rateLimitController The rate limit controller
     * @param metadataRepository The metadata repository
     * @param identity The encryption identity
     * @param threads The number of download threads
     * @return A configured BlockDownloaderImpl instance
     */
    @Provides
    @Singleton
    public BlockDownloaderImpl blockDownloader(@Named(SOURCE_CONFIG) BackupConfiguration configuration,
                                               RateLimitController rateLimitController,
                                               MetadataRepository metadataRepository,
                                               EncryptionIdentity identity,
                                               @Named(DOWNLOAD_THREADS) int threads) {
        return new BlockDownloaderImpl(configuration, rateLimitController, metadataRepository, identity, threads);
    }

    /**
     * Provides a singleton BlockDownloader instance.
     *
     * @param blockDownloader The BlockDownloaderImpl instance
     * @return The BlockDownloader interface implementation
     */
    @Provides
    @Singleton
    public BlockDownloader blockDownloader(BlockDownloaderImpl blockDownloader) {
        return blockDownloader;
    }

    /**
     * Provides a singleton FileDownloaderImpl instance.
     *
     * @param repository The metadata repository
     * @param fileSystemAccess The file system access
     * @return A configured FileDownloaderImpl instance
     */
    @Singleton
    @Provides
    public FileDownloaderImpl fileDownloader(MetadataRepository repository,
                                             FileSystemAccess fileSystemAccess) {
        return new FileDownloaderImpl(repository, fileSystemAccess);
    }
}
