package com.underscoreresearch.backup.ui.commands;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;

/**
 * Command to activate any defined shares in the backup system.
 * This enables sharing of backup data with other participants as defined in the configuration.
 */
@CommandPlugin(value = "activate-shares", description = "Activate any defined shares",
        readonlyRepository = false)
@Slf4j
public class ActivateSharesCommand extends Command {

    /**
     * Executes the activate-shares command which activates any defined shares in the configuration.
     *
     * @param commandLine The command line arguments
     * @throws Exception If there is an error activating the shares
     */
    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        log.info("Activating shares");
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);

        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        manifestManager.activateShares(InstanceFactory.getInstance(LogConsumer.class),
                InstanceFactory.getInstance(EncryptionIdentity.class).getPrivateIdentity(getPassword()));
        repository.flushLogging();
        manifestManager.shutdown();
        repository.close();
    }
}
