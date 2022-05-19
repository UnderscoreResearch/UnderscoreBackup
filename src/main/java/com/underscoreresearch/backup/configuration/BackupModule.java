package com.underscoreresearch.backup.configuration;

import static com.underscoreresearch.backup.configuration.CommandLineModule.DEBUG;
import static com.underscoreresearch.backup.configuration.CommandLineModule.NO_DELETE_REBUILD;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.apache.commons.cli.CommandLine;

import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.block.FileBlockUploader;
import com.underscoreresearch.backup.block.assignments.GzipLargeFileBlockAssignment;
import com.underscoreresearch.backup.block.assignments.LargeFileBlockAssignment;
import com.underscoreresearch.backup.block.assignments.RawLargeFileBlockAssignment;
import com.underscoreresearch.backup.block.assignments.SmallFileBlockAssignment;
import com.underscoreresearch.backup.block.implementation.FileBlockUploaderImpl;
import com.underscoreresearch.backup.cli.commands.BlockValidator;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.file.FileConsumer;
import com.underscoreresearch.backup.file.FileScanner;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.ScannerScheduler;
import com.underscoreresearch.backup.file.implementation.FileConsumerImpl;
import com.underscoreresearch.backup.file.implementation.FileScannerImpl;
import com.underscoreresearch.backup.file.implementation.FileSystemAccessImpl;
import com.underscoreresearch.backup.file.implementation.MapdbMetadataRepository;
import com.underscoreresearch.backup.file.implementation.ScannerSchedulerImpl;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.io.implementation.UploadSchedulerImpl;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.LoggingMetadataRepository;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.RepositoryTrimmer;
import com.underscoreresearch.backup.manifest.implementation.ManifestManagerImpl;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.utils.StateLogger;

public class BackupModule extends AbstractModule {
    public static final int DEFAULT_LARGE_MAXIMUM_SIZE = 8 * 1024 * 1024 - 10 * 1024;
    private static final int DEFAULT_SMALL_FILE_TARGET_SIZE = DEFAULT_LARGE_MAXIMUM_SIZE;
    private static final int DEFAULT_SMALL_FILE_MAXIMUM_SIZE = DEFAULT_SMALL_FILE_TARGET_SIZE / 2;
    private static final int DEFAULT_UPLOAD_THREADS = 4;
    public static final String REPOSITORY_DB_PATH = "REPOSITORY_DB_PATH";

    @Singleton
    @Provides
    public ScannerSchedulerImpl scannerScheduler(BackupConfiguration configuration,
                                                 MetadataRepository repository,
                                                 RepositoryTrimmer repositoryTrimmer,
                                                 FileScanner scanner,
                                                 StateLogger stateLogger) {
        return new ScannerSchedulerImpl(configuration, repository, repositoryTrimmer, scanner, stateLogger);
    }

    @Singleton
    @Provides
    public ScannerScheduler scannerScheduler(ScannerSchedulerImpl scannerScheduler) {
        return scannerScheduler;
    }

    @Singleton
    @Provides
    public RepositoryTrimmer metadataTrimmer(MetadataRepository repository,
                                             BackupConfiguration configuration,
                                             ManifestManager manifestManager) {
        return new RepositoryTrimmer(repository, configuration, manifestManager, false);
    }

    @Singleton
    @Provides
    public FileScanner fileScanner(FileScannerImpl scanner) {
        return scanner;
    }

    @Singleton
    @Provides
    public FileScannerImpl fileScanner(MetadataRepository repository, FileConsumer fileConsumer,
                                       FileSystemAccess access, StateLogger logger,
                                       @Named(DEBUG) boolean debug) {
        return new FileScannerImpl(repository, fileConsumer, access, logger, debug);
    }

    @Singleton
    @Provides
    public FileConsumer fileConsumer(MetadataRepository repository,
                                     SmallFileBlockAssignment smallFileBlockAssignment,
                                     LargeFileBlockAssignment largeFileBlockAssignment) {
        return new FileConsumerImpl(repository, Lists.newArrayList(smallFileBlockAssignment, largeFileBlockAssignment));
    }

    @Provides
    @Singleton
    public SmallFileBlockAssignment smallFileBlockAssignment(BackupConfiguration configuration,
                                                             BlockDownloader blockDownloader,
                                                             MetadataRepository metadataRepository,
                                                             FileBlockUploader fileBlockUploader,
                                                             FileSystemAccess fileSystemAccess) {
        return new SmallFileBlockAssignment(fileBlockUploader, blockDownloader, metadataRepository, fileSystemAccess,
                configuration.getProperty("smallFileBlockAssignment.maximumSize", DEFAULT_SMALL_FILE_MAXIMUM_SIZE),
                configuration.getProperty("smallFileBlockAssignment.targetSize", DEFAULT_SMALL_FILE_TARGET_SIZE));
    }

    @Provides
    @Singleton
    public UploadScheduler uploadScheduler(UploadSchedulerImpl uploadScheduler) {
        return uploadScheduler;
    }

    @Provides
    @Singleton
    public UploadSchedulerImpl uploadScheduler(BackupConfiguration configuration,
                                               RateLimitController rateLimitController) {
        int threads;
        if (configuration.getLimits() == null || configuration.getLimits().getMaximumUploadThreads() == null)
            threads = DEFAULT_UPLOAD_THREADS;
        else
            threads = configuration.getLimits().getMaximumUploadThreads();

        return new UploadSchedulerImpl(threads, rateLimitController);
    }

    @Provides
    @Singleton
    public FileBlockUploaderImpl fileBlockUploader(BackupConfiguration configuration,
                                                   MetadataRepository repository,
                                                   UploadScheduler uploadScheduler) {
        return new FileBlockUploaderImpl(configuration, repository, uploadScheduler);
    }

    @Provides
    @Singleton
    public FileBlockUploader fileBlockUploader(FileBlockUploaderImpl uploader) {
        return uploader;
    }

    @Provides
    @Singleton
    public LargeFileBlockAssignment largeFileBlockAssignment(BackupConfiguration configuration,
                                                             RawLargeFileBlockAssignment raw,
                                                             GzipLargeFileBlockAssignment gzip) {
        if ("true".equals(configuration.getProperty("largeBlockAssignment.raw", "false"))) {
            return raw;
        }
        return gzip;
    }

    @Provides
    @Singleton
    public GzipLargeFileBlockAssignment gzipLargeFileBlockAssignment(BackupConfiguration configuration,
                                                                     MetadataRepository metadataRepository,
                                                                     FileBlockUploader fileBlockUploader,
                                                                     BlockDownloader blockDownloader,
                                                                     FileSystemAccess fileSystemAccess) {
        int maxSize = configuration.getProperty("largeBlockAssignment.maximumSize", DEFAULT_LARGE_MAXIMUM_SIZE);
        return new GzipLargeFileBlockAssignment(fileBlockUploader, blockDownloader, fileSystemAccess,
                metadataRepository, maxSize);
    }

    @Provides
    @Singleton
    public BlockValidator blockValidator(MetadataRepository repository,
                                         BackupConfiguration configuration,
                                         ManifestManager manifestManager) {
        int maxBlockSize = configuration.getProperty("largeBlockAssignment.maximumSize", DEFAULT_LARGE_MAXIMUM_SIZE);
        return new BlockValidator(repository, configuration, manifestManager, maxBlockSize);
    }

    @Provides
    @Singleton
    public RawLargeFileBlockAssignment rawLargeFileBlockAssignment(BackupConfiguration configuration,
                                                                   MetadataRepository metadataRepository,
                                                                   FileBlockUploader fileBlockUploader,
                                                                   BlockDownloader blockDownloader,
                                                                   FileSystemAccess fileSystemAccess) {
        int maxSize = configuration.getProperty("largeBlockAssignment.maximumSize", DEFAULT_LARGE_MAXIMUM_SIZE);
        return new RawLargeFileBlockAssignment(fileBlockUploader, blockDownloader, fileSystemAccess,
                metadataRepository, maxSize);
    }

    @Singleton
    @Provides
    public ManifestManagerImpl manifestManagerImplementation(BackupConfiguration configuration,
                                                             RateLimitController rateLimitController) throws IOException {
        BackupDestination destination = configuration.getDestinations().get(configuration.getManifest()
                .getDestination());

        return new ManifestManagerImpl(configuration,
                IOProviderFactory.getProvider(destination),
                EncryptorFactory.getEncryptor(destination.getEncryption()),
                rateLimitController);
    }

    @Provides
    @Singleton
    public ManifestManager manifestManager(ManifestManagerImpl manifestManager) {
        return manifestManager;
    }

    @Singleton
    @Provides
    public MapdbMetadataRepository mapdbMetadata(@Named(REPOSITORY_DB_PATH) String dbPath) throws IOException {
        return new MapdbMetadataRepository(dbPath);
    }

    @Named(REPOSITORY_DB_PATH)
    @Singleton
    @Provides
    public String repositoryDbPath(BackupConfiguration configuration) {
        File metadataRoot = Paths.get(configuration.getManifest().getLocalLocation(), "db").toFile();
        if (!metadataRoot.isDirectory()) {
            metadataRoot.mkdirs();
        }
        return metadataRoot.toString();
    }

    @Singleton
    @Provides
    public MetadataRepository metadataRepository(MapdbMetadataRepository xodusMetadata, ManifestManager manifest,
                                                 CommandLine commandLine) {
        return new LoggingMetadataRepository(xodusMetadata, manifest, commandLine.hasOption(NO_DELETE_REBUILD));
    }

    @Singleton
    @Provides
    public LogConsumer logConsumer(MetadataRepository metadataRepository) {
        return (LogConsumer) metadataRepository;
    }

    @Provides
    @Singleton
    public RateLimitController rateLimitController(BackupConfiguration configuration) {
        return new RateLimitController(configuration.getLimits());
    }

    @Provides
    @Singleton
    public FileSystemAccess fileSystemAccess() {
        return new FileSystemAccessImpl();
    }
}
