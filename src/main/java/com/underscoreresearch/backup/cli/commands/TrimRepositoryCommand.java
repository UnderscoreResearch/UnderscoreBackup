package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.configuration.CommandLineModule.FORCE;

import org.apache.commons.cli.CommandLine;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.manifest.RepositoryTrimmer;
import com.underscoreresearch.backup.model.BackupConfiguration;

@CommandPlugin(value = "trim-repository", description = "Apply retention policies. Use --force flag to delete files not in any defined set.",
        readonlyRepository = false,
        needPrivateKey = false)
public class TrimRepositoryCommand extends Command {
    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        ManifestManager manager = InstanceFactory.getInstance(ManifestManager.class);
        BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);

        RepositoryTrimmer trimmer = new RepositoryTrimmer(repository, configuration, commandLine.hasOption(FORCE));
        trimmer.trimRepository();
        repository.flushLogging();
        manager.shutdown();
        repository.close();
    }
}
