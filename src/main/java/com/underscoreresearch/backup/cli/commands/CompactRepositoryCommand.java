package com.underscoreresearch.backup.cli.commands;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;

import static com.underscoreresearch.backup.cli.commands.ConfigureCommand.reloadIfRunning;

@CommandPlugin(value = "defrag-repository", description = "Compact local repository metadata",
        readonlyRepository = false, supportSource = true, needPrivateKey = false)
@Slf4j
public class CompactRepositoryCommand extends Command {
    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        if (repository.isErrorsDetected())
            log.info("Can't defrag repository, errors detected");
        else {
            repository.compact();
        }
        repository.close();

        reloadIfRunning();
    }
}
