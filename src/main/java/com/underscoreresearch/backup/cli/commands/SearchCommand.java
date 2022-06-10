package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.configuration.CommandLineModule.INCLUDE_DELETED;
import static com.underscoreresearch.backup.configuration.CommandLineModule.timestamp;
import static com.underscoreresearch.backup.utils.LogUtil.printFile;

import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.manifest.BackupSearchAccess;
import com.underscoreresearch.backup.manifest.ManifestManager;

@CommandPlugin(value = "search", args = "Regular expression of search", description = "Search backup contents", needPrivateKey = false)
@Slf4j
public class SearchCommand extends Command {

    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        if (commandLine.getArgList().size() != 2) {
            throw new ParseException("Missing search parameter");
        }

        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        BackupSearchAccess searchAccess = manifestManager.backupSearch(timestamp(commandLine),
                commandLine.hasOption(INCLUDE_DELETED));

        try (CloseableLock ignore = searchAccess.acquireLock()) {
            searchAccess.searchFiles(Pattern.compile(commandLine.getArgList().get(1), Pattern.CASE_INSENSITIVE))
                    .forEach(file -> System.out.println(printFile(commandLine, true, file)));
        }
    }
}
