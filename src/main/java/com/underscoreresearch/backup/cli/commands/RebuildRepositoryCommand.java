package com.underscoreresearch.backup.cli.commands;

import static com.underscoreresearch.backup.cli.commands.ConfigureCommand.reloadIfRunning;
import static com.underscoreresearch.backup.cli.web.RemoteRestorePost.getManifestDestination;
import static com.underscoreresearch.backup.configuration.BackupModule.REPOSITORY_DB_PATH;
import static com.underscoreresearch.backup.configuration.CommandLineModule.FORCE;
import static com.underscoreresearch.backup.manifest.implementation.ManifestManagerImpl.CONFIGURATION_FILENAME;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_READER;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;

import com.underscoreresearch.backup.cli.Command;
import com.underscoreresearch.backup.cli.CommandPlugin;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.RepositoryOpenMode;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;

@CommandPlugin(value = "rebuild-repository", description = "Rebuild repository metadata from logs",
        readonlyRepository = false, supportSource = true)
@Slf4j
public class RebuildRepositoryCommand extends Command {
    public static String downloadRemoteConfiguration(String source, String password) throws IOException {
        BackupDestination destination = getManifestDestination(source);
        if (destination == null) {
            log.warn("Could not determine destination for manifest");
            throw new IOException("Could not determine destination for manifest");
        }
        return downloadRemoteConfiguration(destination, InstanceFactory.getInstance(EncryptionKey.class).getPrivateKey(password));
    }

    public static String downloadRemoteConfiguration(BackupDestination destination,
                                                     EncryptionKey.PrivateKey privateKey) throws IOException {
        IOProvider provider = IOProviderFactory.getProvider(destination);
        byte[] data = provider.download(CONFIGURATION_FILENAME);
        return unpackConfigData(destination.getEncryption(), privateKey, data);
    }

    public static String unpackConfigData(String encryption, EncryptionKey.PrivateKey privateKey, byte[] data) throws IOException {
        try {
            BACKUP_CONFIGURATION_READER.readValue(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException exc) {
            Encryptor encryptor = EncryptorFactory.getEncryptor(encryption);
            try (ByteArrayInputStream stream = new ByteArrayInputStream(encryptor.decodeBlock(null, data,
                    privateKey))) {
                try (GZIPInputStream gzipInputStream = new GZIPInputStream(stream)) {
                    return new String(IOUtils.readAllBytes(gzipInputStream), StandardCharsets.UTF_8);
                }
            }
        }
    }

    public static void rebuildFromLog(String password, boolean async) {
        log.info("Rebuilding repository from logs");
        MetadataRepository repository = InstanceFactory.getInstance(MetadataRepository.class);
        if (InstanceFactory.getAdditionalSource() == null) {
            try {
                repository.close();
                String root = InstanceFactory.getInstance(REPOSITORY_DB_PATH);
                IOUtils.deleteContents(new File(root));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        LogConsumer logConsumer = InstanceFactory.getInstance(LogConsumer.class);

        ManifestManager manifestManager = InstanceFactory.getInstance(ManifestManager.class);
        if (async) {
            Thread thread = new Thread(() -> executeRebuildLog(manifestManager, logConsumer, repository, password),
                    "LogRebuild");
            thread.setDaemon(true);

            InstanceFactory.addOrderedCleanupHook(() -> {
                try {
                    manifestManager.shutdown();
                } catch (IOException e) {
                    log.error("Failed to shut down log replay", e);
                }
                try {
                    thread.join(1000);
                    if (!thread.isAlive())
                        return;
                } catch (InterruptedException ignored) {
                }
                log.info("Waiting for rebuild to get to a checkpoint");
                do {
                    try {
                        thread.join();
                    } catch (InterruptedException ignored) {
                    }
                } while (thread.isAlive());
            });

            thread.start();
            while (!manifestManager.isBusy() && thread.isAlive()) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    log.error("Failed to wait", e);
                }
            }
        } else {
            executeRebuildLog(manifestManager, logConsumer, repository, password);
        }
    }

    private static void executeRebuildLog(ManifestManager manifestManager,
                                          LogConsumer logConsumer,
                                          MetadataRepository repository,
                                          String password) {
        try {
            repository.open(RepositoryOpenMode.READ_WRITE);
            manifestManager.replayLog(logConsumer, password);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                repository.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static boolean configChanged(BackupConfiguration newConfig) {
        BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);
        if (configuration.getManifest() != null && newConfig.getManifest() != null) {
            newConfig.getManifest().setInteractiveBackup(configuration.getManifest().getInteractiveBackup());
        }
        return !configuration.equals(newConfig);
    }

    @Override
    public void executeCommand(CommandLine commandLine) throws Exception {
        if (InstanceFactory.getAdditionalSource() == null) {
            try {
                String config = downloadRemoteConfiguration(null, getPassword());
                if (configChanged(BACKUP_CONFIGURATION_READER.readValue(config))) {
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
        }

        rebuildFromLog(getPassword(), false);

        reloadIfRunning();
    }
}
