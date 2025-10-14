package com.underscoreresearch.backup.configuration;

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
import com.underscoreresearch.backup.ui.helpers.BlockValidator;
import com.underscoreresearch.backup.ui.helpers.DestinationBlockProcessor;
import com.underscoreresearch.backup.ui.helpers.RepositoryTrimmer;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.file.ContinuousBackup;
import com.underscoreresearch.backup.file.FileChangeWatcher;
import com.underscoreresearch.backup.file.FileConsumer;
import com.underscoreresearch.backup.file.FilePermissionManager;
import com.underscoreresearch.backup.file.FileScanner;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.ScannerScheduler;
import com.underscoreresearch.backup.file.implementation.AclPermissionManager;
import com.underscoreresearch.backup.file.implementation.BackupStatsLogger;
import com.underscoreresearch.backup.file.implementation.ContinuousBackupImpl;
import com.underscoreresearch.backup.file.implementation.FileChangeWatcherImpl;
import com.underscoreresearch.backup.file.implementation.FileConsumerImpl;
import com.underscoreresearch.backup.file.implementation.FileScannerImpl;
import com.underscoreresearch.backup.file.implementation.FileSystemAccessImpl;
import com.underscoreresearch.backup.file.implementation.LockingMetadataRepository;
import com.underscoreresearch.backup.file.implementation.PermissionFileSystemAccess;
import com.underscoreresearch.backup.file.implementation.PosixPermissionManager;
import com.underscoreresearch.backup.file.implementation.ScannerSchedulerImpl;
import com.underscoreresearch.backup.file.implementation.WindowsFileSystemAccess;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.io.implementation.UploadSchedulerImpl;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.manifest.implementation.AdditionalManifestManager;
import com.underscoreresearch.backup.manifest.implementation.LoggingMetadataRepository;
import com.underscoreresearch.backup.manifest.implementation.ManifestManagerImpl;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.utils.log.StateLogger;
import com.underscoreresearch.backup.machinestate.MachineState;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang3.SystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.concurrent.atomic.AtomicReference;

import static com.underscoreresearch.backup.configuration.CommandLineModule.ADDITIONAL_SOURCE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.DEBUG;
import static com.underscoreresearch.backup.configuration.CommandLineModule.FORCE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.INSTALLATION_IDENTITY;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.configuration.CommandLineModule.NO_DELETE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE_CONFIG;
import static com.underscoreresearch.backup.configuration.RestoreModule.DOWNLOAD_THREADS;
import static com.underscoreresearch.backup.io.IOUtils.createDirectory;
import static com.underscoreresearch.backup.utils.log.LogUtil.debug;

/**
 * Guice module for configuring backup-related dependencies.
 * This module provides the necessary bindings for the backup functionality,
 * including file scanning, block assignments, metadata repositories, and more.
 */
@Slf4j
public class BackupModule extends AbstractModule {
    /**
     * Default maximum size for large blocks in bytes.
     */
    public static final int DEFAULT_LARGE_MAXIMUM_SIZE = 8 * 1024 * 1024 - 10 * 1024;
    
    /**
     * Named constant for repository database path.
     */
    public static final String REPOSITORY_DB_PATH = "REPOSITORY_DB_PATH";
    
    /**
     * Default target size for small files in bytes.
     */
    private static final int DEFAULT_SMALL_FILE_TARGET_SIZE = DEFAULT_LARGE_MAXIMUM_SIZE;
    
    /**
     * Default maximum size for small files in bytes.
     */
    private static final int DEFAULT_SMALL_FILE_MAXIMUM_SIZE = DEFAULT_SMALL_FILE_TARGET_SIZE / 2;
    
    /**
     * Default number of upload threads.
     */
    private static final int DEFAULT_UPLOAD_THREADS = 4;

    /**
     * Provides a singleton ScannerSchedulerImpl instance.
     *
     * @param configuration The backup configuration
     * @param repository The metadata repository
     * @param repositoryTrimmer The repository trimmer
     * @param scanner The file scanner
     * @param stateLogger The state logger
     * @param fileChangeWatcher The file change watcher
     * @param continuousBackup The continuous backup service
     * @param backupStatsLogger The backup stats logger
     * @param parser The command line parser
     * @return A configured ScannerSchedulerImpl instance
     */
    @Singleton
    @Provides
    public ScannerSchedulerImpl scannerScheduler(BackupConfiguration configuration,
                                                 MetadataRepository repository,
                                                 RepositoryTrimmer repositoryTrimmer,
                                                 FileScanner scanner,
                                                 StateLogger stateLogger,
                                                 FileChangeWatcher fileChangeWatcher,
                                                 ContinuousBackup continuousBackup,
                                                 BackupStatsLogger backupStatsLogger,
                                                 CommandLine parser) {
        return new ScannerSchedulerImpl(configuration, repository, repositoryTrimmer, scanner, stateLogger,
                fileChangeWatcher, continuousBackup, backupStatsLogger,
                !parser.getArgList().isEmpty() && "interactive".equals(parser.getArgList().getFirst()));
    }

    /**
     * Provides a singleton ScannerScheduler instance.
     *
     * @param scannerScheduler The ScannerSchedulerImpl instance
     * @return The ScannerScheduler interface implementation
     */
    @Singleton
    @Provides
    public ScannerScheduler scannerScheduler(ScannerSchedulerImpl scannerScheduler) {
        return scannerScheduler;
    }

    /**
     * Provides a singleton RepositoryTrimmer instance.
     *
     * @param repository The metadata repository
     * @param configuration The backup configuration
     * @param manifestManager The manifest manager
     * @return A configured RepositoryTrimmer instance
     */
    @Singleton
    @Provides
    public RepositoryTrimmer metadataTrimmer(MetadataRepository repository,
                                             BackupConfiguration configuration,
                                             ManifestManager manifestManager) {
        return new RepositoryTrimmer(repository, configuration, manifestManager, false);
    }

    /**
     * Provides a singleton FileScanner instance.
     *
     * @param scanner The FileScannerImpl instance
     * @return The FileScanner interface implementation
     */
    @Singleton
    @Provides
    public FileScanner fileScanner(FileScannerImpl scanner) {
        return scanner;
    }

    /**
     * Provides a singleton FileScannerImpl instance.
     *
     * @param repository The metadata repository
     * @param fileConsumer The file consumer
     * @param access The file system access
     * @param machineState The machine state
     * @param debug Whether debug mode is enabled
     * @param manifestLocation The manifest location
     * @return A configured FileScannerImpl instance
     */
    @Singleton
    @Provides
    public FileScannerImpl fileScanner(MetadataRepository repository, FileConsumer fileConsumer,
                                       FileSystemAccess access, MachineState machineState, @Named(DEBUG) boolean debug,
                                       @Named(MANIFEST_LOCATION) String manifestLocation) {
        // Validate destinations is turned on either by using the --force command line or through the manifest option.
        return new FileScannerImpl(repository, fileConsumer, access, machineState, debug, manifestLocation);
    }

    /**
     * Provides a singleton FileConsumer instance.
     *
     * @param repository The metadata repository
     * @param smallFileBlockAssignment The small file block assignment
     * @param largeFileBlockAssignment The large file block assignment
     * @return A configured FileConsumer instance
     */
    @Singleton
    @Provides
    public FileConsumer fileConsumer(MetadataRepository repository,
                                     EncryptedSmallBlockAssignment smallFileBlockAssignment,
                                     LargeFileBlockAssignment largeFileBlockAssignment) {
        return new FileConsumerImpl(repository, Lists.newArrayList(smallFileBlockAssignment, largeFileBlockAssignment));
    }

    /**
     * Provides a singleton ZipSmallBlockAssignment instance.
     *
     * @param configuration The backup configuration
     * @param blockDownloader The block downloader
     * @param metadataRepository The metadata repository
     * @param fileBlockUploader The file block uploader
     * @param fileSystemAccess The file system access
     * @param identity The encryption identity
     * @return A configured ZipSmallBlockAssignment instance
     */
    @Provides
    @Singleton
    public ZipSmallBlockAssignment zipFileBlockAssignment(BackupConfiguration configuration,
                                                          BlockDownloader blockDownloader,
                                                          MetadataRepository metadataRepository,
                                                          FileBlockUploader fileBlockUploader,
                                                          FileSystemAccess fileSystemAccess,
                                                          EncryptionIdentity identity) {
        return new ZipSmallBlockAssignment(fileBlockUploader, blockDownloader, metadataRepository, fileSystemAccess,
                identity,
                configuration.getProperty("smallFileBlockAssignment.maximumSize", DEFAULT_SMALL_FILE_MAXIMUM_SIZE),
                configuration.getProperty("smallFileBlockAssignment.targetSize", DEFAULT_SMALL_FILE_TARGET_SIZE));
    }

    /**
     * Provides a singleton EncryptedSmallBlockAssignment instance.
     *
     * @param configuration The backup configuration
     * @param blockDownloader The block downloader
     * @param metadataRepository The metadata repository
     * @param fileBlockUploader The file block uploader
     * @param fileSystemAccess The file system access
     * @param identity The encryption identity
     * @return A configured EncryptedSmallBlockAssignment instance
     */
    @Provides
    @Singleton
    public EncryptedSmallBlockAssignment encryptedSmallBlockAssignment(BackupConfiguration configuration,
                                                                       BlockDownloader blockDownloader,
                                                                       MetadataRepository metadataRepository,
                                                                       FileBlockUploader fileBlockUploader,
                                                                       FileSystemAccess fileSystemAccess,
                                                                       EncryptionIdentity identity) {
        return new EncryptedSmallBlockAssignment(fileBlockUploader, blockDownloader, metadataRepository, fileSystemAccess,
                identity,
                configuration.getProperty("smallFileBlockAssignment.maximumSize", DEFAULT_SMALL_FILE_MAXIMUM_SIZE),
                configuration.getProperty("smallFileBlockAssignment.targetSize", DEFAULT_SMALL_FILE_TARGET_SIZE));
    }

    /**
     * Provides a singleton UploadScheduler instance.
     *
     * @param uploadScheduler The UploadSchedulerImpl instance
     * @return The UploadScheduler interface implementation
     */
    @Provides
    @Singleton
    public UploadScheduler uploadScheduler(UploadSchedulerImpl uploadScheduler) {
        return uploadScheduler;
    }

    /**
     * Provides a singleton UploadSchedulerImpl instance.
     *
     * @param configuration The backup configuration
     * @param rateLimitController The rate limit controller
     * @return A configured UploadSchedulerImpl instance
     */
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

    /**
     * Provides a singleton FileBlockUploaderImpl instance.
     *
     * @param configuration The backup configuration
     * @param repository The metadata repository
     * @param uploadScheduler The upload scheduler
     * @param manifestManager The manifest manager
     * @param encryptionIdentity The encryption identity
     * @return A configured FileBlockUploaderImpl instance
     */
    @Provides
    @Singleton
    public FileBlockUploaderImpl fileBlockUploader(BackupConfiguration configuration,
                                                   MetadataRepository repository,
                                                   UploadScheduler uploadScheduler,
                                                   ManifestManager manifestManager,
                                                   EncryptionIdentity encryptionIdentity) {
        return new FileBlockUploaderImpl(configuration, repository, uploadScheduler, manifestManager,
                encryptionIdentity);
    }

    /**
     * Provides a singleton FileBlockUploader instance.
     *
     * @param uploader The FileBlockUploaderImpl instance
     * @return The FileBlockUploader interface implementation
     */
    @Provides
    @Singleton
    public FileBlockUploader fileBlockUploader(FileBlockUploaderImpl uploader) {
        return uploader;
    }

    /**
     * Provides a singleton LargeFileBlockAssignment instance.
     * Returns either a raw or gzip implementation based on configuration.
     *
     * @param configuration The backup configuration
     * @param raw The raw large file block assignment
     * @param gzip The gzip large file block assignment
     * @return The appropriate LargeFileBlockAssignment implementation
     */
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

    /**
     * Provides a singleton GzipLargeFileBlockAssignment instance.
     *
     * @param configuration The backup configuration
     * @param metadataRepository The metadata repository
     * @param fileBlockUploader The file block uploader
     * @param blockDownloader The block downloader
     * @param fileSystemAccess The file system access
     * @param machineState The machine state
     * @param identity The encryption identity
     * @return A configured GzipLargeFileBlockAssignment instance
     */
    @Provides
    @Singleton
    public GzipLargeFileBlockAssignment gzipLargeFileBlockAssignment(BackupConfiguration configuration,
                                                                     MetadataRepository metadataRepository,
                                                                     FileBlockUploader fileBlockUploader,
                                                                     BlockDownloader blockDownloader,
                                                                     FileSystemAccess fileSystemAccess,
                                                                     MachineState machineState,
                                                                     EncryptionIdentity identity) {
        int maxSize = configuration.getProperty("largeBlockAssignment.maximumSize", DEFAULT_LARGE_MAXIMUM_SIZE);
        return new GzipLargeFileBlockAssignment(fileBlockUploader, blockDownloader, fileSystemAccess,
                metadataRepository, machineState, identity, maxSize);
    }

    /**
     * Provides a singleton BlockValidator instance.
     *
     * @param repository The metadata repository
     * @param configuration The backup configuration
     * @param destinationBlockProcessor The destination block processor
     * @param manifestManager The manifest manager
     * @param statsLogger The stats logger
     * @param manifestLocation The manifest location
     * @return A configured BlockValidator instance
     */
    @Provides
    @Singleton
    public BlockValidator blockValidator(MetadataRepository repository,
                                         BackupConfiguration configuration,
                                         DestinationBlockProcessor destinationBlockProcessor,
                                         ManifestManager manifestManager,
                                         BackupStatsLogger statsLogger,
                                         @Named(MANIFEST_LOCATION) String manifestLocation) {
        int maxBlockSize = configuration.getProperty("largeBlockAssignment.maximumSize", DEFAULT_LARGE_MAXIMUM_SIZE);
        return new BlockValidator(repository, configuration, manifestManager, destinationBlockProcessor,
                statsLogger, maxBlockSize, manifestLocation);
    }

    /**
     * Provides a singleton RawLargeFileBlockAssignment instance.
     *
     * @param configuration The backup configuration
     * @param metadataRepository The metadata repository
     * @param fileBlockUploader The file block uploader
     * @param blockDownloader The block downloader
     * @param fileSystemAccess The file system access
     * @param machineState The machine state
     * @param identity The encryption identity
     * @return A configured RawLargeFileBlockAssignment instance
     */
    @Provides
    @Singleton
    public RawLargeFileBlockAssignment rawLargeFileBlockAssignment(BackupConfiguration configuration,
                                                                   MetadataRepository metadataRepository,
                                                                   FileBlockUploader fileBlockUploader,
                                                                   BlockDownloader blockDownloader,
                                                                   FileSystemAccess fileSystemAccess,
                                                                   MachineState machineState,
                                                                   EncryptionIdentity identity) {
        int maxSize = configuration.getProperty("largeBlockAssignment.maximumSize", DEFAULT_LARGE_MAXIMUM_SIZE);
        return new RawLargeFileBlockAssignment(fileBlockUploader, blockDownloader, fileSystemAccess,
                metadataRepository, machineState, identity, maxSize);
    }

    /**
     * Provides a singleton ManifestManagerImpl instance.
     *
     * @param configuration The backup configuration
     * @param manifestLocation The manifest location
     * @param rateLimitController The rate limit controller
     * @param serviceManager The service manager
     * @param installationIdentity The installation identity
     * @param source The additional source
     * @param encryptionIdentity The encryption identity
     * @param commandLine The command line
     * @param statsLogger The stats logger
     * @param additionalManifestManager The additional manifest manager
     * @param uploadScheduler The upload scheduler
     * @return A configured ManifestManagerImpl instance
     * @throws IOException If there's an error initializing the manifest manager
     */
    @Singleton
    @Provides
    public ManifestManagerImpl manifestManagerImplementation(@Named(SOURCE_CONFIG) BackupConfiguration configuration,
                                                             @Named(MANIFEST_LOCATION) String manifestLocation,
                                                             RateLimitController rateLimitController,
                                                             ServiceManager serviceManager,
                                                             @Named(INSTALLATION_IDENTITY) String installationIdentity,
                                                             @Named(ADDITIONAL_SOURCE) String source,
                                                             EncryptionIdentity encryptionIdentity,
                                                             CommandLine commandLine,
                                                             BackupStatsLogger statsLogger,
                                                             AdditionalManifestManager additionalManifestManager,
                                                             UploadScheduler uploadScheduler)
            throws IOException {
        return new ManifestManagerImpl(configuration,
                manifestLocation,
                rateLimitController,
                serviceManager,
                installationIdentity,
                source,
                commandLine.hasOption(FORCE),
                commandLine.hasOption(NO_DELETE),
                encryptionIdentity,
                encryptionIdentity.getPrimaryKeys(),
                statsLogger,
                additionalManifestManager,
                uploadScheduler);
    }

    /**
     * Provides a singleton AdditionalManifestManager instance.
     *
     * @param source The additional source
     * @param config The backup configuration
     * @param rateLimitController The rate limit controller
     * @param uploadScheduler The upload scheduler
     * @return A configured AdditionalManifestManager instance
     */
    @Provides
    @Singleton
    public AdditionalManifestManager additionalManifestManager(@Named(ADDITIONAL_SOURCE) String source,
                                                               BackupConfiguration config,
                                                               RateLimitController rateLimitController,
                                                               UploadScheduler uploadScheduler) {
        if (!Strings.isNullOrEmpty(source)) {
            return new AdditionalManifestManager(BackupConfiguration.builder().build(), rateLimitController, uploadScheduler);
        }
        return new AdditionalManifestManager(config, rateLimitController, uploadScheduler);
    }

    /**
     * Provides a singleton ManifestManager instance.
     *
     * @param manifestManager The ManifestManagerImpl instance
     * @return The ManifestManager interface implementation
     */
    @Provides
    @Singleton
    public ManifestManager manifestManager(ManifestManagerImpl manifestManager) {
        return manifestManager;
    }

    /**
     * Provides a singleton LockingMetadataRepository instance.
     *
     * @param dbPath The repository database path
     * @param source The additional source
     * @return A configured LockingMetadataRepository instance
     */
    @Singleton
    @Provides
    public LockingMetadataRepository lockingMetadataRepository(@Named(REPOSITORY_DB_PATH) String dbPath,
                                                               @Named(ADDITIONAL_SOURCE) String source) {
        return new LockingMetadataRepository(dbPath, !Strings.isNullOrEmpty(source));
    }

    /**
     * Provides the repository database path.
     *
     * @param manifestLocation The manifest location
     * @param source The additional source
     * @return The path to the repository database
     */
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
        createDirectory(metadataRoot, true);
        try {
            IOUtils.setOwnerOnlyPermissions(metadataRoot);
        } catch (IOException e) {
            log.warn("Failed to set owner only permissions on metadata directory", e);
        }
        return metadataRoot.toString();
    }

    /**
     * Provides a singleton LoggingMetadataRepository instance.
     *
     * @param repository The locking metadata repository
     * @param manifest The manifest manager
     * @param configuration The backup configuration
     * @param commandLine The command line
     * @param source The additional source
     * @return A configured LoggingMetadataRepository instance
     */
    @Singleton
    @Provides
    public LoggingMetadataRepository loggingMetadataRepository(LockingMetadataRepository repository,
                                                               ManifestManager manifest,
                                                               BackupConfiguration configuration,
                                                               CommandLine commandLine,
                                                               @Named(ADDITIONAL_SOURCE) String source) {
        if (Strings.isNullOrEmpty(source)) {
            return new LoggingMetadataRepository(repository,
                    manifest,
                    configuration.getShares(),
                    null,
                    commandLine.hasOption(NO_DELETE));
        }
        return new LoggingMetadataRepository.Readonly(repository,
                manifest,
                false);
    }

    /**
     * Provides a singleton BackupStatsLogger instance.
     *
     * @param configuration The backup configuration
     * @param manifestLocation The manifest location
     * @return A configured BackupStatsLogger instance
     */
    @Singleton
    @Provides
    public BackupStatsLogger backupStatsLogger(BackupConfiguration configuration, @Named(MANIFEST_LOCATION) String manifestLocation) {
        return new BackupStatsLogger(configuration, manifestLocation);
    }

    /**
     * Provides a singleton MetadataRepository instance.
     *
     * @param loggingMetadataRepository The logging metadata repository
     * @return The MetadataRepository interface implementation
     */
    @Singleton
    @Provides
    public MetadataRepository metadataRepository(LoggingMetadataRepository loggingMetadataRepository) {
        return loggingMetadataRepository;
    }

    /**
     * Provides a singleton LogConsumer instance.
     *
     * @param metadataRepository The logging metadata repository
     * @return The LogConsumer interface implementation
     */
    @Singleton
    @Provides
    public LogConsumer logConsumer(LoggingMetadataRepository metadataRepository) {
        return metadataRepository;
    }

    /**
     * Provides a singleton RateLimitController instance.
     *
     * @param configuration The backup configuration
     * @return A configured RateLimitController instance
     */
    @Provides
    @Singleton
    public RateLimitController rateLimitController(BackupConfiguration configuration) {
        return new RateLimitController(configuration.getLimits());
    }

    /**
     * Provides a singleton DestinationBlockProcessor instance.
     *
     * @param threads The number of download threads
     * @param commandLine The command line
     * @param fileDownloader The file downloader
     * @param uploadScheduler The upload scheduler
     * @param configuration The backup configuration
     * @param manifestManager The manifest manager
     * @param repository The metadata repository
     * @param encryptionIdentity The encryption identity
     * @return A configured DestinationBlockProcessor instance
     */
    @Singleton
    @Provides
    public DestinationBlockProcessor blockRefresher(@Named(DOWNLOAD_THREADS) int threads,
                                                    CommandLine commandLine,
                                                    BlockDownloader fileDownloader,
                                                    UploadScheduler uploadScheduler,
                                                    BackupConfiguration configuration,
                                                    ManifestManager manifestManager,
                                                    MetadataRepository repository,
                                                    EncryptionIdentity encryptionIdentity) {
        return new DestinationBlockProcessor(threads, commandLine.hasOption(CommandLineModule.NO_DELETE),
                fileDownloader, uploadScheduler, configuration, repository, manifestManager, encryptionIdentity);
    }

    /**
     * Provides a singleton FileChangeWatcherImpl instance.
     *
     * @param configuration The backup configuration
     * @param repository The metadata repository
     * @param continuousBackup The continuous backup service
     * @param machineState The machine state
     * @param manifestLocation The manifest location
     * @return A configured FileChangeWatcherImpl instance
     */
    @Singleton
    @Provides
    public FileChangeWatcherImpl fileChangeWatcher(BackupConfiguration configuration,
                                                   MetadataRepository repository,
                                                   ContinuousBackup continuousBackup,
                                                   MachineState machineState,
                                                   @Named(MANIFEST_LOCATION) String manifestLocation) {
        return new FileChangeWatcherImpl(configuration, repository, continuousBackup, manifestLocation, machineState);
    }

    /**
     * Provides a singleton FileChangeWatcher instance.
     *
     * @param fileChangeWatcher The FileChangeWatcherImpl instance
     * @return The FileChangeWatcher interface implementation
     */
    @Singleton
    @Provides
    public FileChangeWatcher fileChangeWatcher(FileChangeWatcherImpl fileChangeWatcher) {
        return fileChangeWatcher;
    }

    /**
     * Provides a singleton ContinuousBackupImpl instance.
     *
     * @param repository The metadata repository
     * @param fileConsumer The file consumer
     * @param backupConfiguration The backup configuration
     * @param machineState The machine state
     * @return A configured ContinuousBackupImpl instance
     */
    @Singleton
    @Provides
    public ContinuousBackupImpl continuousBackup(MetadataRepository repository, FileConsumer fileConsumer,
                                                 BackupConfiguration backupConfiguration, MachineState machineState) {
        return new ContinuousBackupImpl(repository, fileConsumer, machineState, backupConfiguration);
    }

    /**
     * Provides a singleton ContinuousBackup instance.
     *
     * @param continuousBackup The ContinuousBackupImpl instance
     * @return The ContinuousBackup interface implementation
     */
    @Singleton
    @Provides
    public ContinuousBackup continuousBackup(ContinuousBackupImpl continuousBackup) {
        return continuousBackup;
    }

    /**
     * Provides a singleton FileSystemAccess instance.
     * Returns an appropriate implementation based on the operating system and configuration.
     *
     * @param configuration The backup configuration
     * @return An appropriate FileSystemAccess implementation
     */
    @Provides
    @Singleton
    public FileSystemAccess fileSystemAccess(BackupConfiguration configuration) {
        if (configuration.getManifest() != null
                && configuration.getManifest().getIgnorePermissions() != null
                && configuration.getManifest().getIgnorePermissions()) {
            return new FileSystemAccessImpl();
        }
        AtomicReference<FilePermissionManager> permissionManager = new AtomicReference<>();
        FileSystems.getDefault().getFileStores().forEach(fileStore -> {
            if (fileStore.supportsFileAttributeView(AclFileAttributeView.class)) {
                permissionManager.set(new AclPermissionManager());
            }
            if (permissionManager.get() == null) {
                if (fileStore.supportsFileAttributeView(PosixFileAttributeView.class)) {
                    permissionManager.set(new PosixPermissionManager());
                }
            }
        });
        if (SystemUtils.IS_OS_WINDOWS) {
            if (permissionManager.get() == null) {
                throw new UnsupportedOperationException("Windows file system access requires ACL support.");
            }
            return new WindowsFileSystemAccess(permissionManager.get());
        }
        if (permissionManager.get() == null) {
            log.warn("Permissions are not supported on this file system");
            return new FileSystemAccessImpl();
        }
        debug(() -> log.debug("Using permission manager: " + permissionManager.get().getClass().getSimpleName()));
        return new PermissionFileSystemAccess(permissionManager.get());
    }
}
