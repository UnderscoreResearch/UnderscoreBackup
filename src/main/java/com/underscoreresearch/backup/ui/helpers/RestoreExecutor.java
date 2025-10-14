package com.underscoreresearch.backup.ui.helpers;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.ui.desktop.UIHandler;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.file.implementation.BackupStatsLogger;
import com.underscoreresearch.backup.io.DownloadScheduler;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupSetRoot;
import com.underscoreresearch.backup.utils.log.StateLogger;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.block.implementation.FileDownloaderImpl.isNullFile;
import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.file.PathNormalizer.normalizePath;
import static com.underscoreresearch.backup.model.BackupActivePath.stripPath;

/**
 * Handles the execution of restore operations for backed up files and directories.
 * This class manages the process of restoring files from backup to the filesystem,
 * handling permissions, paths, and download scheduling.
 */
@Slf4j
public class RestoreExecutor {
    private final BackupContentsAccess contents;
    private final FileSystemAccess fileSystemAccess;
    private final MetadataRepository metadataRepository;
    private final DownloadScheduler scheduler;
    private final String password;
    private final BackupStatsLogger backupStatsLogger;

    /**
     * Creates a new RestoreExecutor.
     *
     * @param contents The backup contents access for retrieving file metadata
     * @param fileSystemAccess The file system access implementation
     * @param repository The metadata repository
     * @param password Optional password for encrypted backups
     * @param backupStatsLogger Logger for backup statistics
     */
    public RestoreExecutor(BackupContentsAccess contents, FileSystemAccess fileSystemAccess, MetadataRepository repository, String password, BackupStatsLogger backupStatsLogger) {
        this.contents = contents;
        this.password = password;
        this.backupStatsLogger = backupStatsLogger;
        this.fileSystemAccess = fileSystemAccess;
        this.metadataRepository = repository;
        scheduler = InstanceFactory.getInstance(DownloadScheduler.class);
    }

    /**
     * Restores the specified paths from backup to the destination.
     *
     * @param rootPaths List of backup set roots to restore
     * @param destination Destination directory for restored files
     * @param recursive Whether to restore directories recursively
     * @param overwrite Whether to overwrite existing files
     * @param skipPermisssions Whether to skip restoring file permissions
     * @throws IOException If there is an error during the restore process
     */
    public void restorePaths(List<BackupSetRoot> rootPaths,
                             String destination,
                             boolean recursive,
                             boolean overwrite,
                             boolean skipPermisssions) throws IOException {
        String commonRoot = findCommonRoot(rootPaths);
        backupStatsLogger.setDownloadRunning(true);

        try (Closeable ignored = UIHandler.registerTask("Restoring from \"" + rootPaths.stream()
                .map(BackupSetRoot::getPath)
                .map(PathNormalizer::physicalPath)
                .collect(Collectors.joining("\", \"")) + "\"", true)) {

            try (RestoreDirectoryPermissions pendingDirectories = new RestoreDirectoryPermissions(metadataRepository, scheduler, fileSystemAccess, skipPermisssions)) {
                for (BackupSetRoot root : rootPaths) {
                    String currentDestination = destination;
                    String rootPath = normalizePath(root.getPath());
                    if (currentDestination != null) {
                        if (!isNullFile(currentDestination)) {
                            currentDestination = PathNormalizer.normalizePath(currentDestination);
                            if (!currentDestination.endsWith(PATH_SEPARATOR)) {
                                currentDestination += PATH_SEPARATOR;
                            }
                            if (rootPaths.size() != 1) {
                                currentDestination += stripCommonAndDrive(commonRoot, rootPath);
                            }
                        }
                    }
                    restorePaths(root, BackupFile.builder().path(rootPath).build(), currentDestination, recursive,
                            overwrite, skipPermisssions, rootPaths.size() == 1, commonRoot, pendingDirectories);
                }
                scheduler.waitForCompletion();
            }
        } finally {
            backupStatsLogger.setDownloadRunning(false);
        }

        StateLogger logger = InstanceFactory.getInstance(StateLogger.class);
        logger.logInfo();
        logger.reset();
    }

    /**
     * Finds the common root path among a list of backup set roots.
     * This method determines the longest common path prefix shared by all roots,
     * which is used to maintain proper directory structure during restore operations.
     *
     * @param rootPaths List of backup set roots to analyze
     * @return The common root path shared by all backup set roots
     */
    private String findCommonRoot(List<BackupSetRoot> rootPaths) {
        String commonPath = null;
        for (BackupSetRoot rootPath : rootPaths) {
            String normalizedRoot = normalizePath(rootPath.getPath());
            if (commonPath == null) {
                commonPath = normalizedRoot;
                if (!commonPath.endsWith(PATH_SEPARATOR)) {
                    commonPath = commonPath.substring(0, commonPath.lastIndexOf(PATH_SEPARATOR) + 1);
                }
            } else {
                for (int i = 0; i < commonPath.length(); i++) {
                    if (normalizedRoot.charAt(i) != commonPath.charAt(i)) {
                        commonPath = commonPath.substring(0, i);
                        int lastPath = commonPath.lastIndexOf('/');
                        if (lastPath >= 0) {
                            commonPath = commonPath.substring(0, lastPath + 1);
                        } else {
                            commonPath = "";
                        }
                        break;
                    }
                }
            }
        }
        return commonPath;
    }

    /**
     * Strips the common root and drive letter from a path.
     * This helps create relative paths for destination directories during restore.
     *
     * @param commonRoot The common root path to strip
     * @param rootPath The original path to process
     * @return The path with common root and drive letter removed
     */
    private String stripCommonAndDrive(String commonRoot, String rootPath) {
        if (!Strings.isNullOrEmpty(commonRoot) && rootPath.startsWith(commonRoot)) {
            rootPath = rootPath.substring(commonRoot.length() - 1);
        }
        if (!rootPath.startsWith(PATH_SEPARATOR)) {
            return rootPath.substring(rootPath.indexOf(PATH_SEPARATOR) + 1);
        }
        return rootPath;
    }

    /**
     * Recursively restores files and directories from a backup set root.
     * This method handles the actual restoration of files and directories, creating
     * the necessary directory structure and scheduling file downloads.
     *
     * @param rootPath The backup set root being restored
     * @param sourceFile The current file or directory being processed
     * @param inputDestination The destination path for restored files
     * @param recursive Whether to restore directories recursively
     * @param overwrite Whether to overwrite existing files
     * @param skipPermissions Whether to skip restoring file permissions
     * @param root Whether this is a root-level restore operation
     * @param commonRoot The common root path for all files being restored
     * @param pendingDirectories Handler for directory permission restoration
     * @throws IOException If there is an error during the restore process
     */
    private void restorePaths(BackupSetRoot rootPath,
                              BackupFile sourceFile,
                              String inputDestination,
                              boolean recursive,
                              boolean overwrite,
                              boolean skipPermissions,
                              boolean root,
                              String commonRoot,
                              RestoreDirectoryPermissions pendingDirectories) throws IOException {
        if (InstanceFactory.isShutdown()) {
            return;
        }

        String destination;
        if (inputDestination == null) {
            destination = sourceFile.getPath();
        } else {
            destination = inputDestination;
        }
        if (destination != null && destination.endsWith(PATH_SEPARATOR))
            destination = destination.substring(0, destination.length() - 1);

        List<BackupFile> files = contents.directoryFiles(sourceFile.getPath());
        if (files == null && sourceFile.getPath().length() > 1) {
            final String strippedFilename;

            if (sourceFile.getPath().endsWith(PATH_SEPARATOR))
                strippedFilename = sourceFile.getPath().substring(0, sourceFile.getPath().length() - 1);
            else
                strippedFilename = sourceFile.getPath();

            files = contents.directoryFiles(strippedFilename.substring(0, strippedFilename.lastIndexOf(PATH_SEPARATOR)));
            if (files != null) {
                files = files.stream().filter(file -> file.getPath().equals(strippedFilename))
                        .collect(Collectors.toList());
            }
            root = true;
        }
        if (files != null) {
            files = files.stream().filter(rootPath::includeFileOrDirectory).collect(Collectors.toList());

            File destinationFile = new File(PathNormalizer.physicalPath(destination));
            if (root && files.size() == 1 && !files.get(0).isDirectory() && !destinationFile.isDirectory()) {
                downloadFile(scheduler, files.get(0), destination, overwrite, skipPermissions);
            } else {
                boolean needDirectory = (!isNullFile(inputDestination) && !destination.isEmpty());

                for (BackupFile file : files) {
                    String currentDestination;
                    if (inputDestination == null || isNullFile(inputDestination))
                        currentDestination = inputDestination;
                    else
                        currentDestination = destination + PATH_SEPARATOR + stripPath(stripCommonAndDrive(commonRoot,
                                file.getPath()));
                    if (file.isDirectory()) {
                        if (recursive) {
                            restorePaths(rootPath, file,
                                    currentDestination, recursive, overwrite, skipPermissions, false, commonRoot,
                                    pendingDirectories);
                        }
                    } else {
                        if (needDirectory) {
                            pendingDirectories.createDirectoryWithPermissions(destinationFile, sourceFile.getPath(),
                                    file.getPath(), contents);
                        }

                        if (!downloadFile(scheduler, file, currentDestination, overwrite, skipPermissions)) {
                            pendingDirectories.completeFile(sourceFile.getPath());
                        }
                    }
                }
            }
        }
    }

    /**
     * Schedules a file for download during the restore process.
     * This method handles the logic for determining whether a file should be downloaded,
     * checking for existing files, and scheduling the actual download operation.
     *
     * @param scheduler The download scheduler to use
     * @param file The backup file to download
     * @param currentDestination The destination path for the file
     * @param overwrite Whether to overwrite existing files
     * @param skipPermissions Whether to skip restoring file permissions
     * @return true if the file was scheduled for download, false otherwise
     */
    private boolean downloadFile(DownloadScheduler scheduler, BackupFile file, String currentDestination,
                                 boolean overwrite, boolean skipPermissions) {
        if (isNullFile(currentDestination)) {
            scheduler.scheduleDownload(file, currentDestination, password);
        } else {
            if (currentDestination == null) {
                currentDestination = file.getPath();
            }
            File destinationFile = new File(PathNormalizer.physicalPath(currentDestination));
            if (overwrite || !destinationFile.exists()) {
                if (destinationFile.exists() && !destinationFile.canWrite()) {
                    log.error("Does not have permissions to write to existing file \"{}\"", destinationFile);
                    return false;
                } else {
                    if (skipPermissions) {
                        file.setPermissions(null);
                    }
                    scheduler.scheduleDownload(file, currentDestination, password);
                }
            } else if (destinationFile.length() != file.getLength()) {
                log.warn("File \"{}\" not of same size as in backup", currentDestination);
                return false;
            } else {
                log.info("Skipping existing file \"{}\"", currentDestination);
            }
        }
        return true;
    }
}
