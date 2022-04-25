package com.underscoreresearch.backup.cli.commands;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.ManifestManager;

@CommandPlugin(value = "validate-blocks", description = "Validate that all used blocks for files exists",
        needPrivateKey = false, needConfiguration = true, readonlyRepository = false)
@Slf4j
public class ValidateBlocksCommand extends SimpleCommand {
    public void executeCommand() throws Exception {
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);

        InstanceFactory.getInstance(BlockValidator.class).validateBlocks();

        repository.flushLogging();
        manifestManager.shutdown();
        repository.close();
    }

}
