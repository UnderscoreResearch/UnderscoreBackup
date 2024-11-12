package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.cli.commands.OptimizeLogCommand.validateRepository;
import static com.underscoreresearch.backup.cli.commands.SimpleCommand.validateNoFiles;
import static com.underscoreresearch.backup.configuration.CommandLineModule.FORCE;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.cli.helpers.BlockValidator;
import com.underscoreresearch.backup.configuration.EncryptionModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.ManifestManager;

@CommandPlugin(value = "validate-blocks", description = "Validate that all used blocks for files exists",
        needPrivateKey = false, readonlyRepository = false)
@Slf4j
public class ValidateBlocksCommand extends Command {

    public void executeCommand(CommandLine commandLine) throws Exception {
        validateNoFiles(commandLine);

        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);

        if (!validateRepository(commandLine, repository))
            return;

        manifestManager.validateIdentity();
        BlockValidator blockValidator = InstanceFactory.getInstance(BlockValidator.class);

        blockValidator.validateBlocks(commandLine.hasOption(FORCE));

        repository.flushLogging();
        manifestManager.shutdown();
        repository.close();
    }

}
