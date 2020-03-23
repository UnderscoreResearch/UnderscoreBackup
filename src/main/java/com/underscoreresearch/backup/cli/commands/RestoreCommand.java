package com.underscoreresearch.backup.cli.commands;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.io.DownloadScheduler;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.block.implementation.FileDownloaderImpl.NULL_FILE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.*;
import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.model.BackupActivePath.stripPath;
import static com.underscoreresearch.backup.utils.LogUtil.debug;

@CommandPlugin(value = "restore", args = "[FILES]... [DESTINATION]", description = "Restore data. Use the destination \"-\" to not write data, but simply validate that data is available from destinations")
@Slf4j
public class RestoreCommand extends Command {

    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        BackupContentsAccess contents = manifestManager.backupContents(CommandLineModule.timestamp(commandLine));
        DownloadScheduler scheduler = InstanceFactory.getInstance(DownloadScheduler.class);

        String destination;
        List<String> paths;
        if (commandLine.getArgList().size() == 1) {
            destination = "./";
            paths = Lists.newArrayList(destination);
        } else if (commandLine.getArgList().size() == 2) {
            destination = commandLine.getArgList().get(1);
            paths = Lists.newArrayList(destination);
        } else {
            destination = commandLine.getArgList().get(commandLine.getArgList().size() - 1);
            paths = commandLine.getArgList().subList(1, commandLine.getArgList().size() - 1);
        }
        if (paths.size() == 1 && destination.equals(paths.get(0)) && !commandLine.hasOption(FORCE)) {
            throw new ParseException("Must use -f option to restore over original location");
        }

        if (!destination.equals(NULL_FILE)) {
            destination = PathNormalizer.normalizePath(destination);
            if (destination.endsWith(PATH_SEPARATOR))
                destination = destination.substring(0, destination.length() - 1);
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                debug(() -> log.debug("Shutdown initiated"));

                InstanceFactory.shutdown(true);
                scheduler.shutdown();
                log.info("Restore shutdown completed");
            }
        });

        for (String path : paths.stream().map(file -> PathNormalizer.normalizePath(file)).collect(Collectors.toList())) {
            String currentDestination;
            if (paths.size() > 1 && !destination.equals(NULL_FILE)) {
                currentDestination = destination + PATH_SEPARATOR + stripPath(path);
            } else {
                currentDestination = destination;
            }
            restorePaths(commandLine, contents, scheduler, BackupFile.builder().path(path).build(),
                    currentDestination, true);
        }

        scheduler.waitForCompletion();
    }

    private void restorePaths(CommandLine commandLine,
                              BackupContentsAccess contents,
                              DownloadScheduler scheduler,
                              BackupFile source,
                              String destination,
                              boolean root) throws IOException {
        if (destination.endsWith(PATH_SEPARATOR))
            destination = destination.substring(0, destination.length() - 1);

        List<BackupFile> files = contents.directoryFiles(source.getPath());
        if (files != null) {
            File destinationFile = new File(PathNormalizer.physicalPath(destination));
            if (root && files.size() == 1 && files.get(0).getPath().startsWith(source.getPath())
                    && !destinationFile.isDirectory()) {
                downloadFile(commandLine, scheduler, files.get(0), destination);
            } else {
                if (!destinationFile.isDirectory()) {
                    if (!destinationFile.mkdirs()) {
                        throw new IOException("Failed to create destination directories " + destinationFile.toString());
                    }
                }
                for (BackupFile file : files) {
                    String currentDestination;
                    if (destination.equals(NULL_FILE))
                        currentDestination = destination;
                    else
                        currentDestination = destination + PATH_SEPARATOR + stripPath(file.getPath());
                    if (file.getPath().endsWith(PATH_SEPARATOR)) {
                        if (commandLine.hasOption(RECURSIVE)) {
                            restorePaths(commandLine, contents, scheduler, file,
                                    currentDestination, false);
                        }
                    } else {
                        downloadFile(commandLine, scheduler, file, currentDestination);
                    }
                }
            }
        }
    }

    private void downloadFile(CommandLine commandLine, DownloadScheduler scheduler, BackupFile file, String currentDestination) {
        File destinationFile = new File(PathNormalizer.physicalPath(currentDestination));
        if (commandLine.hasOption(OVER_WRITE) || !destinationFile.exists()) {
            if (destinationFile.exists() && !destinationFile.canWrite()) {
                log.error("Does not have permissions to write to existing file {}", destinationFile.toString());
            } else {
                scheduler.scheduleDownload(file, currentDestination);
            }
        } else if (destinationFile.length() != file.getLength()) {
            log.warn("File {} not of same size as in backup", currentDestination);
        }
    }
}
