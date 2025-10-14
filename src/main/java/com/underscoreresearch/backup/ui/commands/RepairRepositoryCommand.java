package com.underscoreresearch.backup.ui.commands;

import com.underscoreresearch.backup.ui.web.methods.RepairPost;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;

import static com.underscoreresearch.backup.ui.commands.ConfigureCommand.reloadIfRunning;

/**
 * Command for repairing the repository metadata from logs.
 * This command attempts to fix corrupted repository metadata by using the backup logs.
 */
@CommandPlugin(value = "repair-repository", description = "Repair repository metadata from logs",
        readonlyRepository = false, supportSource = true)
@Slf4j
public class RepairRepositoryCommand extends Command {
    /**
     * Executes the repair-repository command to fix corrupted repository metadata.
     *
     * @param commandLine The parsed command line arguments
     * @throws Exception If an error occurs during command execution
     */
    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        RepairPost.repairRepository(getPassword(), false);

        reloadIfRunning();
    }
}
