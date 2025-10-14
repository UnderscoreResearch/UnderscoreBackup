package com.underscoreresearch.backup.ui.commands;

import com.underscoreresearch.backup.ui.helpers.RepositoryTrimmer;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.implementation.BackupStatsLogger;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupConfiguration;
import org.apache.commons.cli.CommandLine;

import static com.underscoreresearch.backup.configuration.CommandLineModule.FORCE;

/**
 * Command for applying retention policies to the backup repository.
 * This command removes old backup versions according to the configured retention policies,
 * and optionally removes files that are not part of any defined backup set.
 */
@CommandPlugin(value = "trim-repository", description = "Apply retention policies. Use --force flag to delete files not in any defined set.",
        readonlyRepository = false,
        needPrivateKey = false)
public class TrimRepositoryCommand extends Command {
    /**
     * Executes the trim-repository command to apply retention policies.
     *
     * @param commandLine The parsed command line arguments
     * @throws Exception If an error occurs during command execution
     */
    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        ManifestManager manager = InstanceFactory.getInstance(ManifestManager.class);
        manager.validateIdentity();
        BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);

        RepositoryTrimmer trimmer = new RepositoryTrimmer(repository, configuration, manager, commandLine.hasOption(FORCE));
        RepositoryTrimmer.Statistics statistics = trimmer.trimRepository(null);
        InstanceFactory.getInstance(BackupStatsLogger.class).updateStats(statistics);

        repository.flushLogging();
        manager.shutdown();
        repository.close();
    }
}
