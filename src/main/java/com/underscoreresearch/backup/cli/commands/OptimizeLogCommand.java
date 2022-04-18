package com.underscoreresearch.backup.cli.commands;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupConfiguration;

@CommandPlugin(value = "optimize-log", description = "Optimize repository log", needPrivateKey = false,
        readonlyRepository = false)
@Slf4j
public class OptimizeLogCommand extends Command {

    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);

        log.info("Rebuilding logs from repository");
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);

        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        manifestManager.optimizeLog(repository, InstanceFactory.getInstance(LogConsumer.class));
        repository.flushLogging();
        manifestManager.shutdown();
        repository.close();
    }
}
