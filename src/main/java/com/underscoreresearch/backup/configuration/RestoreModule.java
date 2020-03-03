package com.underscoreresearch.backup.configuration;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.underscoreresearch.backup.block.FileDownloader;
import com.underscoreresearch.backup.block.implementation.FileDownloaderImpl;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.DownloadScheduler;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.io.implementation.DownloadSchedulerImpl;
import com.underscoreresearch.backup.model.BackupConfiguration;

public class RestoreModule extends AbstractModule {
    private static final int DEFAULT_DOWNLOAD_THREADS = 4;

    @Singleton
    @Provides
    public DownloadSchedulerImpl downloadSchedulerImpl(FileDownloader fileDownloader,
                                                       BackupConfiguration configuration) {
        int threads;
        if (configuration.getLimits() == null || configuration.getLimits().getMaximumDownloadThreads() == null)
            threads = DEFAULT_DOWNLOAD_THREADS;
        else
            threads = configuration.getLimits().getMaximumDownloadThreads();

        return new DownloadSchedulerImpl(threads, fileDownloader);
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

    @Singleton
    @Provides
    public FileDownloaderImpl fileDownloader(MetadataRepository repository,
                                             RateLimitController rateLimitController,
                                             FileSystemAccess fileSystemAccess,
                                             BackupConfiguration backupConfiguration) {
        return new FileDownloaderImpl(repository, fileSystemAccess, rateLimitController, backupConfiguration);
    }
}
