package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.cli.commands.ChangePasswordCommand.saveKeyFile;
import static com.underscoreresearch.backup.cli.commands.ConfigureCommand.reloadIfRunning;
import static com.underscoreresearch.backup.cli.commands.SimpleCommand.validateNoFiles;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.cli.helpers.RepositoryBackfiller;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.ManifestManager;

@CommandPlugin(value = "backfill-metadata", description = "Go through and backfill metadata that might be missing",
        readonlyRepository = false)
@Slf4j
public class BackfillMetadataCommand extends Command {

    public void executeCommand(CommandLine commandLine) throws Exception {
        validateNoFiles(commandLine);

        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        manifestManager.validateIdentity();

        // Ensure we have a sharing public key and are using the current key algorithm
        EncryptionIdentity identity = InstanceFactory.getInstance(EncryptionIdentity.class);
        identity = identity.changeEncryptionPassword(getPassword(), getPassword(), false);
        manifestManager.updateKeyData(identity);

        saveKeyFile(commandLine, identity);

        RepositoryBackfiller backfiller = InstanceFactory.getInstance(RepositoryBackfiller.class);
        backfiller.executeBackfill(getPassword());

        repository.flushLogging();
        manifestManager.shutdown();
        repository.close();

        reloadIfRunning();
    }
}
