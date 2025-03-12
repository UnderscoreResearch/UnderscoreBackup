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

public class RestoreModule extends AbstractModule {
    public static final String DOWNLOAD_THREADS = "DOWNLOAD_THREADS";
    private static final int DEFAULT_DOWNLOAD_THREADS = 4;

    public static int getGlobalDownloadThreads(BackupConfiguration configuration) {
        int threads;
        if (configuration.getLimits() == null || configuration.getLimits().getMaximumDownloadThreads() == null)
            threads = DEFAULT_DOWNLOAD_THREADS;
        else
            threads = configuration.getLimits().getMaximumDownloadThreads();
        return threads;
    }

    @Named(DOWNLOAD_THREADS)
    @Provides
    public int getDownloadThreads(BackupConfiguration configuration) {
        return getGlobalDownloadThreads(configuration);
    }

    @Singleton
    @Provides
    public DownloadSchedulerImpl downloadSchedulerImpl(@Named(DOWNLOAD_THREADS) int threads,
                                                       MetadataRepository repository,
                                                       FileDownloader fileDownloader) {
        return new DownloadSchedulerImpl(threads, repository, fileDownloader);
    }

    @Singleton
    @Provides
    public DownloadScheduler downloadScheduler(DownloadSchedulerImpl downloadScheduler) {
        return downloadScheduler;
    }

    @Singleton
    @Provides
    public FileDownloader fileDownloader(FileDownloaderImpl fileDownloader) {
        return fileDownloader;
    }

    @Provides
    @Singleton
    public BlockDownloaderImpl blockDownloader(@Named(SOURCE_CONFIG) BackupConfiguration configuration,
                                               RateLimitController rateLimitController,
                                               MetadataRepository metadataRepository,
                                               EncryptionIdentity identity,
                                               @Named(DOWNLOAD_THREADS) int threads) {
        return new BlockDownloaderImpl(configuration, rateLimitController, metadataRepository, identity, threads);
    }

    @Provides
    @Singleton
    public BlockDownloader blockDownloader(BlockDownloaderImpl blockDownloader) {
        return blockDownloader;
    }

    @Singleton
    @Provides
    public FileDownloaderImpl fileDownloader(MetadataRepository repository,
                                             FileSystemAccess fileSystemAccess) {
        return new FileDownloaderImpl(repository, fileSystemAccess);
    }
}
