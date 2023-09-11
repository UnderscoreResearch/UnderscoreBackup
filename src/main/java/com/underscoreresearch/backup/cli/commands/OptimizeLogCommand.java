package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.cli.commands.SimpleCommand.validateNoFiles;
import static com.underscoreresearch.backup.configuration.CommandLineModule.FORCE;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;

@CommandPlugin(value = "optimize-log", description = "Optimize repository log", needPrivateKey = false,
        readonlyRepository = false)
@Slf4j
public class OptimizeLogCommand extends Command {

    public static boolean validateRepository(CommandLine commandLine, MetadataRepository repository) throws IOException {
        repository.open(true);
        if (repository.isErrorsDetected()) {
            if (commandLine.hasOption(FORCE)) {
                log.warn("Proceeding despite corruption in local metadata repository");
            } else {
                log.error("Detected corruption in local metadata repository use --force to continue");
                repository.close();
                return false;
            }
        }
        return true;
    }

    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        validateNoFiles(commandLine);

        log.info("Rebuilding logs from repository");
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);

        if (!validateRepository(commandLine, repository))
            return;

        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        manifestManager.validateIdentity();
        manifestManager.optimizeLog(repository, InstanceFactory.getInstance(LogConsumer.class),
                commandLine.hasOption(FORCE));
        repository.flushLogging();
        manifestManager.shutdown();
        repository.close();
    }
}
