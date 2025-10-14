package com.underscoreresearch.backup.ui.commands;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.manifest.BackupSearchAccess;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.implementation.BackupSearchAccessImpl;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.ExternalBackupFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import java.util.regex.Pattern;

import static com.underscoreresearch.backup.configuration.CommandLineModule.INCLUDE_DELETED;
import static com.underscoreresearch.backup.configuration.CommandLineModule.timestamp;
import static com.underscoreresearch.backup.utils.log.LogUtil.printFile;

/**
 * Command that searches for files in the backup repository using a regular expression.
 * This command allows users to find files in their backups by matching filenames
 * against a pattern.
 */
@CommandPlugin(value = "search", args = "Regular expression of search", description = "Search backup contents",
        needPrivateKey = false, supportSource = true)
@Slf4j
public class SearchCommand extends Command {

    /**
     * Executes the search command with the provided command line arguments.
     * Searches for files matching the specified regular expression pattern.
     *
     * @param commandLine The parsed command line arguments
     * @throws Exception If an error occurs during the search operation
     */
    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        if (commandLine.getArgList().size() != 2) {
            throw new ParseException("Missing search parameter");
        }

        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        BackupSearchAccess searchAccess = manifestManager.backupSearch(timestamp(commandLine),
                commandLine.hasOption(INCLUDE_DELETED));

        try (CloseableLock interrupt = searchAccess.acquireLock()) {
            try (CloseableStream<BackupFile> files = searchAccess.searchFiles(
                    Pattern.compile(commandLine.getArgList().get(1), Pattern.CASE_INSENSITIVE),
                    interrupt)) {
                files.stream().forEach(file -> System.out.println(printFile(commandLine, true,
                        new ExternalBackupFile(file))));
            }
        } catch (BackupSearchAccessImpl.InterruptedSearch exc) {
            log.warn("Search interrupted");
        }
    }
}
