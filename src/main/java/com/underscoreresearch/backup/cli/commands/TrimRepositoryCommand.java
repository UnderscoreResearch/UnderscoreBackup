package com.underscoreresearch.backup.cli.commands;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.RepositoryTrimmer;
import com.underscoreresearch.backup.model.BackupConfiguration;
import org.apache.commons.cli.CommandLine;

import static com.underscoreresearch.backup.configuration.CommandLineModule.FORCE;

@CommandPlugin(value = "trim-repository", description = "Apply retention policies. Use --force flag to delete files not in any defined set.",
        needPrivateKey = false)
public class TrimRepositoryCommand extends Command {
    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);

        RepositoryTrimmer trimmer = new RepositoryTrimmer(repository, configuration, commandLine.hasOption(FORCE));
        trimmer.trimRepository();
    }
}
