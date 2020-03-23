package com.underscoreresearch.backup.cli.commands;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.manifest.BackupContentsAccess;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.configuration.CommandLineModule.HUMAN_READABLE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.RECURSIVE;
import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static com.underscoreresearch.backup.utils.LogUtil.printFile;
import static com.underscoreresearch.backup.utils.LogUtil.readableSize;

@CommandPlugin(value = "ls", args = "[DIRECTORIES]...", description = "List backup contents", needPrivateKey = false)
@Slf4j
public class LsCommand extends Command {

    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        BackupContentsAccess contents = manifestManager.backupContents(CommandLineModule.timestamp(commandLine));

        List<String> paths = commandLine.getArgList().subList(1, commandLine.getArgList().size());
        if (paths.size() == 0) {
            paths = Lists.newArrayList(".");
        }
        for (String path : paths.stream().map(file -> PathNormalizer.normalizePath(file)).collect(Collectors.toList())) {
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
                            System.out.println();
                        }
                    }
                }
            }

            long totalSize = files.stream()
                    .map(t -> t.getLength() != null ? t.getLength() : 0).reduce((a, b) -> a + b).orElseGet(() -> 0L);

            if (files.size() > 1 || !root) {
                System.out.println(originalPath.getPath() + ":");
            }

            if (files.size() > 1) {
                if (commandLine.hasOption(HUMAN_READABLE))
                    System.out.println("total " + readableSize(totalSize));
                else
                    System.out.println("total " + totalSize);
            }

            long newestChanged = 0;
            for (BackupFile file : files) {
                System.out.println(printFile(commandLine, file));
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
