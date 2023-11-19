package com.underscoreresearch.backup.cli.helpers;

import static com.underscoreresearch.backup.block.implementation.FileDownloaderImpl.isNullFile;
import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.file.PathNormalizer.normalizePath;
import static com.underscoreresearch.backup.model.BackupActivePath.stripPath;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Strings;
import com.underscoreresearch.backup.cli.ui.UIHandler;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.FileSystemAccess;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.file.implementation.BackupStatsLogger;
import com.underscoreresearch.backup.io.DownloadScheduler;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupSetRoot;
import com.underscoreresearch.backup.utils.StateLogger;

@Slf4j
public class RestoreExecutor {
    private final BackupContentsAccess contents;
    private final FileSystemAccess fileSystemAccess;
    private final MetadataRepository metadataRepository;
    private final DownloadScheduler scheduler;
    private final String password;
    private final BackupStatsLogger backupStatsLogger;

    public RestoreExecutor(BackupContentsAccess contents, FileSystemAccess fileSystemAccess, MetadataRepository repository, String password, BackupStatsLogger backupStatsLogger) {
        this.contents = contents;
        this.password = password;
        this.backupStatsLogger = backupStatsLogger;
        this.fileSystemAccess = fileSystemAccess;
        this.metadataRepository = repository;
        scheduler = InstanceFactory.getInstance(DownloadScheduler.class);
    }

    public void restorePaths(List<BackupSetRoot> rootPaths,
                             String destination,
                             boolean recursive,
                             boolean overwrite,
                             boolean skipPermisssions) throws IOException {
        String commonRoot = findCommonRoot(rootPaths);
        backupStatsLogger.setDownloadRunning(true);

        AtomicBoolean anyData = new AtomicBoolean(false);
        try (Closeable ignored = UIHandler.registerTask("Restoring from " + rootPaths.stream()
                .map(BackupSetRoot::getPath)
                .map(PathNormalizer::physicalPath)
                .collect(Collectors.joining(", ")))) {

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

    private String stripCommonAndDrive(String commonRoot, String rootPath) {
        if (!Strings.isNullOrEmpty(commonRoot) && rootPath.startsWith(commonRoot)) {
            rootPath = rootPath.substring(commonRoot.length() - 1);
        }
        if (!rootPath.startsWith(PATH_SEPARATOR)) {
            return rootPath.substring(rootPath.indexOf(PATH_SEPARATOR) + 1);
        }
        return rootPath;
    }

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
                    log.error("Does not have permissions to write to existing file \"{}\"", destinationFile.toString());
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
            }
        }
        return true;
    }

}
