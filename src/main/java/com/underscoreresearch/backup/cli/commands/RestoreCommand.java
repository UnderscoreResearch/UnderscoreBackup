package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.block.implementation.FileDownloaderImpl.isNullFile;
import static com.underscoreresearch.backup.configuration.CommandLineModule.FORCE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.INCLUDE_DELETED;
import static com.underscoreresearch.backup.configuration.CommandLineModule.OVER_WRITE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.RECURSIVE;
import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.utils.LogUtil.debug;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import com.underscoreresearch.backup.cli.helpers.RestoreExecutor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.block.FileDownloader;
import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.io.DownloadScheduler;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupSetRoot;

@CommandPlugin(value = "restore", args = "[FILES]... [DESTINATION]",
        description = "Restore data. Use the destination \"-\" to not write data, but simply validate that data is available from destinations.\nUse \"=\" as destination to validate that backup data matches data locally")
@Slf4j
public class RestoreCommand extends Command {

    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        BackupContentsAccess contents = manifestManager.backupContents(CommandLineModule.timestamp(commandLine),
                commandLine.hasOption(INCLUDE_DELETED));
        FileDownloader downloader = InstanceFactory.getInstance(FileDownloader.class);

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

        if (!isNullFile(destination)) {
            destination = PathNormalizer.normalizePath(destination);
            if (destination.endsWith(PATH_SEPARATOR))
                destination = destination.substring(0, destination.length() - 1);
        }

        InstanceFactory.addOrderedCleanupHook(() -> {
            debug(() -> log.debug("Shutdown initiated"));

            InstanceFactory.shutdown();
            InstanceFactory.getInstance(DownloadScheduler.class).shutdown();

            try {
                repository.flushLogging();
                manifestManager.shutdown();
                downloader.shutdown();
                repository.close();
            } catch (IOException e) {
                log.error("Failed to close manifest", e);
            }

            log.info("Restore shutdown completed");
        });

        List<BackupSetRoot> roots = paths.stream().map(file -> BackupSetRoot.builder().path(PathNormalizer.normalizePath(file)).build())
                .collect(Collectors.toList());
        new RestoreExecutor(contents).restorePaths(roots, destination,
                commandLine.hasOption(RECURSIVE), commandLine.hasOption(OVER_WRITE));
    }
}
