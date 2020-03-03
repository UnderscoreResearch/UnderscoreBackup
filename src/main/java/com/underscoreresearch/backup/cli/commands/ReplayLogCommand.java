package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.configuration.BackupModule.REPOSITORY_DB_PATH;
import static com.underscoreresearch.backup.configuration.CommandLineModule.CONFIG_DATA;
import static com.underscoreresearch.backup.configuration.CommandLineModule.FORCE;

import java.io.File;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupConfiguration;

@CommandPlugin(value = "rebuild-repository", description = "Rebuild repository metadata from logs",
        readonlyRepository = false)
@Slf4j
public class ReplayLogCommand extends Command {

    private void cleanDir(File tempDir) {
        String[] entries = tempDir.list();
        for (String s : entries) {
            File currentFile = new File(tempDir.getPath(), s);
            if (currentFile.isDirectory()) {
                cleanDir(currentFile);
                currentFile.delete();
            } else {
                currentFile.delete();
            }
        }
    }

    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);
        IOProvider provider = IOProviderFactory.getProvider(configuration.getDestinations().get(configuration.getManifest().getDestination()));
        try {
            String config = new String(provider.download("configuration.json"), "UTF-8");
            if (!config.equals(InstanceFactory.getInstance(CONFIG_DATA))) {
                throw new IOException("Configuration file does not match. Use -f flag to continue");
            }
        } catch (Exception exc) {
            if (commandLine.hasOption(FORCE)) {
                log.info("Overriding error validating configuration file");
            } else {
                log.error(exc.getMessage());
                System.exit(1);
            }
        }


        log.info("Rebuilding repository from logs");
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        repository.close();
        String root = InstanceFactory.getInstance(REPOSITORY_DB_PATH);
        cleanDir(new File(root));
        repository.open(false);

        LogConsumer logConsumer = InstanceFactory.getInstance(LogConsumer.class);

        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        manifestManager.replayLog(logConsumer);
        repository.close();
    }
}
