package com.underscoreresearch.backup.ui.commands;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;

import java.io.IOException;

import static com.underscoreresearch.backup.ui.commands.SimpleCommand.validateNoFiles;
import static com.underscoreresearch.backup.configuration.CommandLineModule.FORCE;

/**
 * Command for optimizing the repository log.
 * This command consolidates and optimizes the log files in the backup repository
 * to improve performance and reduce storage space.
 */
@CommandPlugin(value = "optimize-log", description = "Optimize repository log", needPrivateKey = false,
        readonlyRepository = false)
@Slf4j
public class OptimizeLogCommand extends Command {

    /**
     * Validates that the repository is in a good state for optimization.
     *
     * @param commandLine The parsed command line arguments
     * @param repository The metadata repository
     * @return True if the repository is valid or force flag is used, false otherwise
     * @throws IOException If an error occurs accessing the repository
     */
    public static boolean validateRepository(CommandLine commandLine, MetadataRepository repository) throws IOException {
        if (repository.isErrorsDetected()) {
            if (commandLine.hasOption(FORCE)) {
                log.warn("Proceeding despite corruption in local metadata repository");
            } else {
                log.error("Detected corruption in local metadata repository use --force to continue");
                repository.close();
                return false;
            }
        }
        return true;
    }

    /**
     * Executes the optimize-log command to consolidate and optimize repository logs.
     *
     * @param commandLine The parsed command line arguments
     * @throws Exception If an error occurs during command execution
     */
    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        validateNoFiles(commandLine);

        log.info("Rebuilding logs from repository");
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);

        if (!validateRepository(commandLine, repository))
            return;

        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        manifestManager.validateIdentity();
        manifestManager.optimizeLog(repository, InstanceFactory.getInstance(LogConsumer.class),
                commandLine.hasOption(FORCE));
        repository.flushLogging();
        manifestManager.shutdown();
        repository.close();
    }
}
