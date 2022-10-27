package com.underscoreresearch.backup.cli.commands;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;

@CommandPlugin(value = "activate-shares", description = "Activate any defined shares",
        readonlyRepository = false)
@Slf4j
public class ActivateSharesCommand extends Command {

    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        log.info("Activating shares");
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);

        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        manifestManager.activateShares(InstanceFactory.getInstance(LogConsumer.class),
                InstanceFactory.getInstance(EncryptionKey.class).getPrivateKey(getPassphrase()));
        repository.flushLogging();
        manifestManager.shutdown();
        repository.close();
    }
}
