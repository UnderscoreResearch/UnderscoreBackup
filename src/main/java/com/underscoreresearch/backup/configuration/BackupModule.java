package com.underscoreresearch.backup.configuration;

import static com.underscoreresearch.backup.configuration.CommandLineModule.ADDITIONAL_SOURCE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.DEBUG;
import static com.underscoreresearch.backup.configuration.CommandLineModule.FORCE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.INSTALLATION_IDENTITY;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.configuration.CommandLineModule.NO_DELETE_REBUILD;
import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE_CONFIG;
import static com.underscoreresearch.backup.configuration.RestoreModule.DOWNLOAD_THREADS;
import static com.underscoreresearch.backup.io.IOUtils.createDirectory;
import static com.underscoreresearch.backup.utils.LogUtil.debug;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;

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
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.io.implementation.UploadSchedulerImpl;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.LoggingMetadataRepository;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.manifest.implementation.AdditionalManifestManager;
import com.underscoreresearch.backup.manifest.implementation.ManifestManagerImpl;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.utils.StateLogger;
import com.underscoreresearch.backup.utils.state.MachineState;

@Slf4j
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
                                                 StateLogger stateLogger,
                                                 FileChangeWatcher fileChangeWatcher,
                                                 ContinuousBackup continuousBackup,
                                                 BackupStatsLogger backupStatsLogger,
                                                 CommandLine parser) {
        return new ScannerSchedulerImpl(configuration, repository, repositoryTrimmer, scanner, stateLogger,
                fileChangeWatcher, continuousBackup, backupStatsLogger,
                parser.getArgList().size() > 0 && "interactive".equals(parser.getArgList().get(0)));
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
                                                          FileSystemAccess fileSystemAccess,
                                                          EncryptionKey encryptionKey) {
        return new ZipSmallBlockAssignment(fileBlockUploader, blockDownloader, metadataRepository, fileSystemAccess,
                encryptionKey,
                configuration.getProperty("smallFileBlockAssignment.maximumSize", DEFAULT_SMALL_FILE_MAXIMUM_SIZE),
                configuration.getProperty("smallFileBlockAssignment.targetSize", DEFAULT_SMALL_FILE_TARGET_SIZE));
    }

    @Provides
    @Singleton
    public EncryptedSmallBlockAssignment encryptedSmallBlockAssignment(BackupConfiguration configuration,
                                                                       BlockDownloader blockDownloader,
                                                                       MetadataRepository metadataRepository,
                                                                       FileBlockUploader fileBlockUploader,
                                                                       FileSystemAccess fileSystemAccess,
                                                                       EncryptionKey encryptionKey) {
        return new EncryptedSmallBlockAssignment(fileBlockUploader, blockDownloader, metadataRepository, fileSystemAccess,
                encryptionKey,
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
                                                                     MachineState machineState,
                                                                     EncryptionKey encryptionKey) {
        int maxSize = configuration.getProperty("largeBlockAssignment.maximumSize", DEFAULT_LARGE_MAXIMUM_SIZE);
        return new GzipLargeFileBlockAssignment(fileBlockUploader, blockDownloader, fileSystemAccess,
                metadataRepository, machineState, encryptionKey, maxSize);
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
                                                                   MachineState machineState,
                                                                   EncryptionKey encryptionKey) {
        int maxSize = configuration.getProperty("largeBlockAssignment.maximumSize", DEFAULT_LARGE_MAXIMUM_SIZE);
        return new RawLargeFileBlockAssignment(fileBlockUploader, blockDownloader, fileSystemAccess,
                metadataRepository, machineState, encryptionKey, maxSize);
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
                encryptionKey,
                statsLogger,
                additionalManifestManager,
                uploadScheduler);
    }

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

    @Provides
    @Singleton
    public ManifestManager manifestManager(ManifestManagerImpl manifestManager) {
        return manifestManager;
    }

    @Singleton
    @Provides
    public LockingMetadataRepository lockingMetadataRepository(@Named(REPOSITORY_DB_PATH) String dbPath,
                                                               @Named(ADDITIONAL_SOURCE) String source) {
        return new LockingMetadataRepository(dbPath, !Strings.isNullOrEmpty(source));
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
        createDirectory(metadataRoot);
        return metadataRoot.toString();
    }

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
                    commandLine.hasOption(NO_DELETE_REBUILD));
        }
        return new LoggingMetadataRepository.Readonly(repository,
                manifest,
                false);
    }

    @Singleton
    @Provides
    public BackupStatsLogger backupStatsLogger(BackupConfiguration configuration, @Named(MANIFEST_LOCATION) String manifestLocation) {
        return new BackupStatsLogger(configuration, manifestLocation);
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

    @Singleton
    @Provides
    public FileChangeWatcherImpl fileChangeWatcher(BackupConfiguration configuration,
                                                   MetadataRepository repository,
                                                   ContinuousBackup continuousBackup,
                                                   @Named(MANIFEST_LOCATION) String manifestLocation) {
        return new FileChangeWatcherImpl(configuration, repository, continuousBackup, manifestLocation);
    }

    @Singleton
    @Provides
    public FileChangeWatcher fileChangeWatcher(FileChangeWatcherImpl fileChangeWatcher) {
        return fileChangeWatcher;
    }

    @Singleton
    @Provides
    public ContinuousBackupImpl continuousBackup(MetadataRepository repository, FileConsumer fileConsumer,
                                                 BackupConfiguration backupConfiguration) {
        return new ContinuousBackupImpl(repository, fileConsumer, backupConfiguration);
    }

    @Singleton
    @Provides
    public ContinuousBackup continuousBackup(ContinuousBackupImpl continuousBackup) {
        return continuousBackup;
    }

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
        if (permissionManager.get() == null) {
            log.warn("Permissions are not supported on this file system.");
            return new FileSystemAccessImpl();
        }
        debug(() -> log.debug("Using permission manager: " + permissionManager.get().getClass().getSimpleName()));
        return new PermissionFileSystemAccess(permissionManager.get());
    }
}
