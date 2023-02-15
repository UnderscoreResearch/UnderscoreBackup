package com.underscoreresearch.backup.cli.commands;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.cli.helpers.RepositoryBackfiller;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.ManifestManager;

@CommandPlugin(value = "backfill-metadata", description = "Go through and backfill metadata that might be missing",
        readonlyRepository = false)
@Slf4j
public class BackfillMetadataCommand extends SimpleCommand {

    public void executeCommand() throws Exception {
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        manifestManager.validateIdentity();

        // Ensure we have a sharing public key and are using the current key algorithm
        EncryptionKey key = InstanceFactory.getInstance(EncryptionKey.class);
        key = EncryptionKey.changeEncryptionPassword(getPassword(), getPassword(), key);
        key.getPrivateKey(getPassword()).populateSharingKey(null);
        manifestManager.updateKeyData(key);

        RepositoryBackfiller backfiller = InstanceFactory.getInstance(RepositoryBackfiller.class);
        backfiller.executeBackfill(getPassword());

        repository.flushLogging();
        manifestManager.shutdown();
        repository.close();
    }
}
