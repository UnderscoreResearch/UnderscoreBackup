package com.underscoreresearch.backup.ui.commands;

import com.underscoreresearch.backup.ui.helpers.BlockValidator;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.ManifestManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;

import static com.underscoreresearch.backup.ui.commands.OptimizeLogCommand.validateRepository;
import static com.underscoreresearch.backup.ui.commands.SimpleCommand.validateNoFiles;
import static com.underscoreresearch.backup.configuration.CommandLineModule.FORCE;

/**
 * Command for validating that all blocks referenced by files exist in the backup storage.
 * This command checks the integrity of the backup by ensuring that all blocks needed
 * for file restoration are available.
 */
@CommandPlugin(value = "validate-blocks", description = "Validate that all used blocks for files exists",
        needPrivateKey = false, readonlyRepository = false)
@Slf4j
public class ValidateBlocksCommand extends Command {

    /**
     * Executes the validate-blocks command to check block integrity.
     *
     * @param commandLine The parsed command line arguments
     * @throws Exception If an error occurs during command execution
     */
    public void executeCommand(CommandLine commandLine) throws Exception {
        validateNoFiles(commandLine);

        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);

        if (!validateRepository(commandLine, repository))
            return;

        manifestManager.validateIdentity();
        BlockValidator blockValidator = InstanceFactory.getInstance(BlockValidator.class);

        blockValidator.validateBlocks(commandLine.hasOption(FORCE), null);
        blockValidator.validateStorage(commandLine.hasOption(FORCE), null);

        repository.flushLogging();
        manifestManager.shutdown();
        repository.close();
    }

}
