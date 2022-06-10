package com.underscoreresearch.backup.cli.commands;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.cli.helpers.RepositoryBackfiller;

@CommandPlugin(value = "backfill-metadata", description = "Go through and backfill metadata that might be missing",
        needPrivateKey = true, needConfiguration = true, readonlyRepository = false)
@Slf4j
public class BackfillMetadataCommand extends SimpleCommand {

    public void executeCommand() throws Exception {
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);

        RepositoryBackfiller backfiller = InstanceFactory.getInstance(RepositoryBackfiller.class);
        backfiller.executeBackfill();

        repository.flushLogging();
        manifestManager.shutdown();
        repository.close();
    }
}
