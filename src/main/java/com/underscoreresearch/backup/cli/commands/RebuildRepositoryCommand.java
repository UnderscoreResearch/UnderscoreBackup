package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.configuration.BackupModule.REPOSITORY_DB_PATH;
import static com.underscoreresearch.backup.configuration.CommandLineModule.CONFIG_DATA;
import static com.underscoreresearch.backup.configuration.CommandLineModule.FORCE;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;

@CommandPlugin(value = "rebuild-repository", description = "Rebuild repository metadata from logs",
        readonlyRepository = false)
@Slf4j
public class RebuildRepositoryCommand extends Command {
    private static final ObjectReader READER = new ObjectMapper().readerFor(BackupConfiguration.class);

    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        try {
            String config = downloadRemoteConfiguration();
            if (!config.equals(InstanceFactory.getInstance(CONFIG_DATA))) {
                throw new IOException("Configuration file does not match. Use -f flag to continue or use download-config command to replace");
            }
        } catch (Exception exc) {
            if (commandLine.hasOption(FORCE)) {
                log.info("Overriding error validating configuration file");
            } else {
                log.error(exc.getMessage());
                System.exit(1);
            }
        }

        rebuildFromLog();
    }

    public static String downloadRemoteConfiguration() throws IOException {
        BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);
        BackupDestination destination = configuration.getDestinations().get(configuration.getManifest().getDestination());
        IOProvider provider = IOProviderFactory.getProvider(destination);
        byte[] data = provider.download("configuration.json");
        try {
            READER.readValue(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException exc) {
            Encryptor encryptor = EncryptorFactory.getEncryptor(destination.getEncryption());
            try (ByteArrayInputStream stream = new ByteArrayInputStream(encryptor.decodeBlock(null, data))) {
                try (GZIPInputStream gzipInputStream = new GZIPInputStream(stream)) {
                    return new String(IOUtils.readAllBytes(gzipInputStream), StandardCharsets.UTF_8);
                }
            }
        }
    }

    private static void cleanDir(File tempDir) {
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

    public static void rebuildFromLog() throws IOException {
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
