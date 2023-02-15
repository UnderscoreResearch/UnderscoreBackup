package com.underscoreresearch.backup.configuration;

import static com.underscoreresearch.backup.configuration.CommandLineModule.ADDITIONAL_SOURCE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.DEBUG;
import static com.underscoreresearch.backup.configuration.CommandLineModule.FORCE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.INSTALLATION_IDENTITY;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.configuration.CommandLineModule.NO_DELETE_REBUILD;
import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE_CONFIG;
import static com.underscoreresearch.backup.configuration.RestoreModule.DOWNLOAD_THREADS;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.apache.commons.cli.CommandLine;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.underscoreresearch.backup.block.BlockDownloader;
import com.underscoreresearch.backup.block.FileBlockUploader;
import com.underscoreresearch.backup.block.assignments.EncryptedSmallBlockAssignment;
import com.underscoreresearch.backup.block.assignments.GzipLargeFileBlockAssignment;
import com.underscoreresearch.backup.block.assignments.LargeFileBlockAssignment;
import com.underscoreresearch.backup.block.assignments.RawLargeFileBlockAssignment;
import com.underscoreresearch.backup.block.assignments.ZipSmallBlockAssignment;
import com.underscoreresearch.backup.block.implementation.FileBlockUploaderImpl;
import com.underscoreresearch.backup.cli.helpers.BlockRefresher;
import com.underscoreresearch.backup.cli.helpers.BlockValidator;
import com.underscoreresearch.backup.cli.helpers.RepositoryTrimmer;
import com.underscoreresearch.backup.encryption.EncryptionKey;
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
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.manifest.implementation.ManifestManagerImpl;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.utils.StateLogger;
import com.underscoreresearch.backup.utils.state.MachineState;

public class BackupModule extends AbstractModule {
    public static final int DEFAULT_LARGE_MAXIMUM_SIZE = 8 * 1024 * 1024 - 10 * 1024;
    public static final String REPOSITORY_DB_PATH = "REPOSITORY_DB_PATH";
    private static final int DEFAULT_SMALL_FILE_TARGET_SIZE = DEFAULT_LARGE_MAXIMUM_SIZE;
    private static final int DEFAULT_SMALL_FILE_MAXIMUM_SIZE = DEFAULT_SMALL_FILE_TARGET_SIZE / 2;
    private static final int DEFAULT_UPLOAD_THREADS = 4;

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
                                       FileSystemAccess access, MachineState machineState, @Named(DEBUG) boolean debug,
                                       @Named(MANIFEST_LOCATION) String manifestLocation) {
        // Validate destinations is turned on either by using the --force command line or through the manifest option.
        return new FileScannerImpl(repository, fileConsumer, access, machineState, debug, manifestLocation);
    }

    @Singleton
    @Provides
    public FileConsumer fileConsumer(MetadataRepository repository,
                                     EncryptedSmallBlockAssignment smallFileBlockAssignment,
                                     LargeFileBlockAssignment largeFileBlockAssignment) {
        return new FileConsumerImpl(repository, Lists.newArrayList(smallFileBlockAssignment, largeFileBlockAssignment));
    }

    @Provides
    @Singleton
    public ZipSmallBlockAssignment zipFileBlockAssignment(BackupConfiguration configuration,
                                                          BlockDownloader blockDownloader,
                                                          MetadataRepository metadataRepository,
                                                          FileBlockUploader fileBlockUploader,
                                                          FileSystemAccess fileSystemAccess) {
        return new ZipSmallBlockAssignment(fileBlockUploader, blockDownloader, metadataRepository, fileSystemAccess,
                configuration.getProperty("smallFileBlockAssignment.maximumSize", DEFAULT_SMALL_FILE_MAXIMUM_SIZE),
                configuration.getProperty("smallFileBlockAssignment.targetSize", DEFAULT_SMALL_FILE_TARGET_SIZE));
    }

    @Provides
    @Singleton
    public EncryptedSmallBlockAssignment encryptedSmallBlockAssignment(BackupConfiguration configuration,
                                                                       BlockDownloader blockDownloader,
                                                                       MetadataRepository metadataRepository,
                                                                       FileBlockUploader fileBlockUploader,
                                                                       FileSystemAccess fileSystemAccess) {
        return new EncryptedSmallBlockAssignment(fileBlockUploader, blockDownloader, metadataRepository, fileSystemAccess,
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
                                                   UploadScheduler uploadScheduler,
                                                   ManifestManager manifestManager,
                                                   EncryptionKey key) {
        return new FileBlockUploaderImpl(configuration, repository, uploadScheduler, manifestManager, key);
    }

    @Provides
    @Singleton
    public FileBlockUploader fileBlockUploader(FileBlockUploaderImpl uploader) {
        return uploader;
    }

    @Provides
    @Singleton
    public LargeFileBlockAssignment largeFileBlockAssignment(@Named(SOURCE_CONFIG) BackupConfiguration configuration,
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
                                                                     FileSystemAccess fileSystemAccess,
                                                                     MachineState machineState) {
        int maxSize = configuration.getProperty("largeBlockAssignment.maximumSize", DEFAULT_LARGE_MAXIMUM_SIZE);
        return new GzipLargeFileBlockAssignment(fileBlockUploader, blockDownloader, fileSystemAccess,
                metadataRepository, machineState, maxSize);
    }

    @Provides
    @Singleton
    public BlockValidator blockValidator(MetadataRepository repository,
                                         BackupConfiguration configuration,
                                         BlockRefresher blockRefresher,
                                         ManifestManager manifestManager) {
        int maxBlockSize = configuration.getProperty("largeBlockAssignment.maximumSize", DEFAULT_LARGE_MAXIMUM_SIZE);
        return new BlockValidator(repository, configuration, manifestManager, blockRefresher, maxBlockSize);
    }

    @Provides
    @Singleton
    public RawLargeFileBlockAssignment rawLargeFileBlockAssignment(BackupConfiguration configuration,
                                                                   MetadataRepository metadataRepository,
                                                                   FileBlockUploader fileBlockUploader,
                                                                   BlockDownloader blockDownloader,
                                                                   FileSystemAccess fileSystemAccess,
                                                                   MachineState machineState) {
        int maxSize = configuration.getProperty("largeBlockAssignment.maximumSize", DEFAULT_LARGE_MAXIMUM_SIZE);
        return new RawLargeFileBlockAssignment(fileBlockUploader, blockDownloader, fileSystemAccess,
                metadataRepository, machineState, maxSize);
    }

    @Singleton
    @Provides
    public ManifestManagerImpl manifestManagerImplementation(@Named(SOURCE_CONFIG) BackupConfiguration configuration,
                                                             @Named(MANIFEST_LOCATION) String manifestLocation,
                                                             RateLimitController rateLimitController,
                                                             ServiceManager serviceManager,
                                                             @Named(INSTALLATION_IDENTITY) String installationIdentity,
                                                             @Named(ADDITIONAL_SOURCE) String source,
                                                             EncryptionKey encryptionKey,
                                                             CommandLine commandLine)
            throws IOException {
        BackupDestination destination = configuration.getDestinations().get(configuration.getManifest()
                .getDestination());

        return new ManifestManagerImpl(configuration,
                manifestLocation,
                IOProviderFactory.getProvider(destination),
                EncryptorFactory.getEncryptor(destination.getEncryption()),
                rateLimitController,
                serviceManager,
                installationIdentity,
                source,
                commandLine.hasOption(FORCE),
                encryptionKey);
    }

    @Provides
    @Singleton
    public ManifestManager manifestManager(ManifestManagerImpl manifestManager) {
        return manifestManager;
    }

    @Singleton
    @Provides
    public MapdbMetadataRepository mapdbMetadata(@Named(REPOSITORY_DB_PATH) String dbPath,
                                                 @Named(ADDITIONAL_SOURCE) String source) throws IOException {
        return new MapdbMetadataRepository(dbPath, !Strings.isNullOrEmpty(source));
    }

    @Named(REPOSITORY_DB_PATH)
    @Singleton
    @Provides
    public String repositoryDbPath(@Named(MANIFEST_LOCATION) String manifestLocation, @Named(ADDITIONAL_SOURCE) String source) {
        File metadataRoot;
        if (!Strings.isNullOrEmpty(source)) {
            metadataRoot = Paths.get(manifestLocation, "db", "sources",
                    source).toFile();
        } else {
            metadataRoot = Paths.get(manifestLocation, "db").toFile();
        }
        if (!metadataRoot.isDirectory()) {
            metadataRoot.mkdirs();
        }
        return metadataRoot.toString();
    }

    @Singleton
    @Provides
    public LoggingMetadataRepository loggingMetadataRepository(MapdbMetadataRepository mapdbMetadata,
                                                               ManifestManager manifest,
                                                               BackupConfiguration configuration,
                                                               CommandLine commandLine,
                                                               @Named(ADDITIONAL_SOURCE) String source) {
        if (Strings.isNullOrEmpty(source)) {
            return new LoggingMetadataRepository(mapdbMetadata,
                    manifest,
                    configuration.getShares(),
                    null,
                    commandLine.hasOption(NO_DELETE_REBUILD));
        }
        return new LoggingMetadataRepository.Readonly(mapdbMetadata,
                manifest,
                false);
    }

    @Singleton
    @Provides
    public MetadataRepository metadataRepository(LoggingMetadataRepository loggingMetadataRepository) {
        return loggingMetadataRepository;
    }

    @Singleton
    @Provides
    public LogConsumer logConsumer(LoggingMetadataRepository metadataRepository) {
        return metadataRepository;
    }

    @Provides
    @Singleton
    public RateLimitController rateLimitController(BackupConfiguration configuration) {
        return new RateLimitController(configuration.getLimits());
    }

    @Singleton
    @Provides
    public BlockRefresher blockRefresher(@Named(DOWNLOAD_THREADS) int threads,
                                         BlockDownloader fileDownloader,
                                         UploadScheduler uploadScheduler,
                                         BackupConfiguration configuration,
                                         ManifestManager manifestManager,
                                         MetadataRepository repository) {
        return new BlockRefresher(threads, fileDownloader, uploadScheduler, configuration, repository,
                manifestManager);
    }

    @Provides
    @Singleton
    public FileSystemAccess fileSystemAccess() {
        return new FileSystemAccessImpl();
    }
}
