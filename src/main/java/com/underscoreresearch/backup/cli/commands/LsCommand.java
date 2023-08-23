package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.configuration.CommandLineModule.FULL_PATH;
import static com.underscoreresearch.backup.configuration.CommandLineModule.HUMAN_READABLE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.INCLUDE_DELETED;
import static com.underscoreresearch.backup.configuration.CommandLineModule.RECURSIVE;
import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.utils.LogUtil.printFile;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

import java.io.IOException;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.ExternalBackupFile;

@CommandPlugin(value = "ls", args = "[DIRECTORIES]...", description = "List backup contents",
        needPrivateKey = false, supportSource = true)
@Slf4j
public class LsCommand extends Command {

    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        BackupContentsAccess contents = manifestManager.backupContents(CommandLineModule.timestamp(commandLine),
                commandLine.hasOption(INCLUDE_DELETED));

        List<String> paths = commandLine.getArgList().subList(1, commandLine.getArgList().size());
        if (paths.size() == 0) {
            paths = Lists.newArrayList(".");
        }
        for (String path : paths.stream().map(PathNormalizer::normalizePath).toList()) {
            if (!listPath(commandLine, contents, BackupFile.builder().path(path).build(), true)) {
                System.out.println(path + " not found");
            }
        }
    }

    private boolean listPath(CommandLine commandLine,
                             BackupContentsAccess contents,
                             BackupFile originalPath,
                             boolean root) throws IOException {
        List<BackupFile> files = contents.directoryFiles(originalPath.getPath());
        if (files != null) {
            if (commandLine.hasOption(RECURSIVE)) {
                for (BackupFile file : files) {
                    if (file.getPath().endsWith(PATH_SEPARATOR)) {
                        if (listPath(commandLine, contents, file, false)) {
                            if (!commandLine.hasOption(FULL_PATH)) {
                                System.out.println();
                            }
                        }
                        if (commandLine.hasOption(FULL_PATH)) {
                            System.out.println(printFile(commandLine, false, new ExternalBackupFile(file)));
                        }
                    }
                }
            }

            long totalSize = files.stream()
                    .map(t -> t.getLength() != null ? t.getLength() : 0).reduce(Long::sum).orElseGet(() -> 0L);

            if (!commandLine.hasOption(FULL_PATH)) {

                if (files.size() > 1 || !root) {
                    System.out.println(originalPath.getPath() + ":");
                }

                if (files.size() > 1) {
                    if (commandLine.hasOption(HUMAN_READABLE))
                        System.out.println("total " + readableSize(totalSize));
                    else
                        System.out.println("total " + totalSize);
                }
            }

            long newestChanged = 0;
            for (BackupFile file : files) {
                if (!commandLine.hasOption(FULL_PATH) || !file.getPath().endsWith(PATH_SEPARATOR)) {
                    System.out.println(printFile(commandLine, false, new ExternalBackupFile(file)));
                }
                if (file.getLastChanged() != null && file.getLastChanged() > newestChanged) {
                    newestChanged = file.getLastChanged();
                }
            }
            originalPath.setLength(totalSize);
            if (newestChanged > 0)
                originalPath.setLastChanged(newestChanged);
            return true;
        } else {
            return false;
        }
    }
}
