package com.underscoreresearch.backup.ui.commands;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.model.ExternalBackupFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.util.List;

import static com.underscoreresearch.backup.configuration.CommandLineModule.HUMAN_READABLE;
import static com.underscoreresearch.backup.utils.log.LogUtil.printFile;
import static com.underscoreresearch.backup.utils.log.LogUtil.readableSize;

/**
 * Command for listing the version history of files in the backup repository.
 * This command displays all versions of specified files that are stored in the backup.
 */
@CommandPlugin(value = "history", args = "[FILES]...", description = "List file history",
        needPrivateKey = false, supportSource = true)
@Slf4j
public class HistoryCommand extends Command {

    /**
     * Executes the history command to list file version history.
     *
     * @param commandLine The parsed command line arguments
     * @throws Exception If an error occurs during command execution
     */
    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);

        List<String> paths = commandLine.getArgList().subList(1, commandLine.getArgList().size());

        if (paths.size() == 0) {
            throw new ParseException("No files specified");
        }

        for (String path : paths.stream().map(PathNormalizer::normalizePath).toList()) {
            listHistory(commandLine, repository, path);
        }
    }

    /**
     * Lists the version history for a specific file path.
     *
     * @param commandLine The parsed command line arguments
     * @param repository The metadata repository
     * @param path The file path to list history for
     * @throws IOException If an error occurs accessing the repository
     */
    private void listHistory(CommandLine commandLine,
                             MetadataRepository repository,
                             String path) throws IOException {
        List<ExternalBackupFile> versions = repository.file(path);
        if (versions != null) {
            System.out.println(path + ":");
            long totalSize = versions.stream().map(t -> t.getLength() != null ? t.getLength() : 0).reduce(Long::sum).orElseGet(() -> 0L);
            if (commandLine.hasOption(HUMAN_READABLE))
                System.out.println("total " + readableSize(totalSize));
            else
                System.out.println("total " + totalSize);

            for (ExternalBackupFile file : versions) {
                System.out.println(printFile(commandLine, false, file));
            }
        }
    }
}
