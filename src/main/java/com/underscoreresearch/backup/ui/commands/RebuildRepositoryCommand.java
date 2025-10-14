package com.underscoreresearch.backup.ui.commands;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.encryption.Encryptor;
import com.underscoreresearch.backup.encryption.EncryptorFactory;
import com.underscoreresearch.backup.encryption.IdentityKeys;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.RepositoryOpenMode;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.IOProviderUtil;
import com.underscoreresearch.backup.io.IOUtils;
import com.underscoreresearch.backup.manifest.LogConsumer;
import com.underscoreresearch.backup.manifest.ManifestManager;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.zip.GZIPInputStream;

import static com.underscoreresearch.backup.ui.commands.ConfigureCommand.reloadIfRunning;
import static com.underscoreresearch.backup.ui.web.methods.RemoteRestorePost.getManifestDestination;
import static com.underscoreresearch.backup.configuration.BackupModule.REPOSITORY_DB_PATH;
import static com.underscoreresearch.backup.configuration.CommandLineModule.FORCE;
import static com.underscoreresearch.backup.manifest.implementation.ManifestManagerImpl.CONFIGURATION_FILENAME;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_READER;

/**
 * Command for rebuilding the repository metadata from logs.
 * This command reconstructs the local repository database from the backup logs
 * stored in the manifest destination.
 */
@CommandPlugin(value = "rebuild-repository", description = "Rebuild repository metadata from logs",
        readonlyRepository = false, supportSource = true)
@Slf4j
public class RebuildRepositoryCommand extends Command {
    /**
     * Downloads the remote configuration file from the manifest destination.
     *
     * @param source The source identifier
     * @param password The password for decrypting the configuration
     * @return The downloaded configuration as a string
     * @throws IOException If an error occurs downloading or decrypting the configuration
     */
    public static String downloadRemoteConfiguration(String source, String password) throws IOException {
        BackupDestination destination = getManifestDestination(source);
        if (destination == null) {
            log.warn("Could not determine destination for manifest");
            throw new IOException("Could not determine destination for manifest");
        }
        try {
            return downloadRemoteConfiguration(destination, InstanceFactory.getInstance(EncryptionIdentity.class).getPrivateKeys(password));
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    /**
     * Downloads the remote configuration file from a specific backup destination.
     *
     * @param destination The backup destination
     * @param privateKey The private key for decryption
     * @return The downloaded configuration as a string
     * @throws IOException If an error occurs downloading or decrypting the configuration
     */
    public static String downloadRemoteConfiguration(BackupDestination destination,
                                                     IdentityKeys.PrivateKeys privateKey) throws IOException {
        IOProvider provider = IOProviderFactory.getProvider(destination);
        byte[] data = IOProviderUtil.download(provider, CONFIGURATION_FILENAME);
        return unpackConfigData(destination.getEncryption(), privateKey, data);
    }

    /**
     * Unpacks and decrypts configuration data.
     *
     * @param encryption The encryption type
     * @param privateKey The private key for decryption
     * @param data The encrypted configuration data
     * @return The decrypted configuration as a string
     * @throws IOException If an error occurs decrypting or decompressing the data
     */
    public static String unpackConfigData(String encryption, IdentityKeys.PrivateKeys privateKey, byte[] data)
            throws IOException {
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
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            }
        }
    }

    /**
     * Rebuilds the repository from logs.
     *
     * @param password The password for decryption
     * @param async Whether to perform the rebuild asynchronously
     */
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
                    Thread.currentThread().interrupt();
                }
                log.info("Waiting for rebuild to get to a checkpoint");
                do {
                    try {
                        thread.join();
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                } while (thread.isAlive());
            });

            thread.start();
            while (!manifestManager.isBusy() && thread.isAlive()) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Failed to wait", e);
                }
            }
        } else {
            executeRebuildLog(manifestManager, logConsumer, repository, password);
            try {
                manifestManager.shutdown();
            } catch (IOException e) {
                log.error("Failed to shut down log replay", e);
            }
        }
    }

    /**
     * Executes the log rebuild process.
     *
     * @param manifestManager The manifest manager
     * @param logConsumer The log consumer
     * @param repository The metadata repository
     * @param password The password for decryption
     */
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

    /**
     * Checks if the configuration has changed.
     *
     * @param newConfig The new configuration
     * @return True if the configuration has changed, false otherwise
     */
    public static boolean configChanged(BackupConfiguration newConfig) {
        BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);
        if (configuration.getManifest() != null && newConfig.getManifest() != null) {
            newConfig.getManifest().setInteractiveBackup(configuration.getManifest().getInteractiveBackup());
        }
        return !configuration.equals(newConfig);
    }

    /**
     * Executes the rebuild-repository command.
     *
     * @param commandLine The parsed command line arguments
     * @throws Exception If an error occurs during command execution
     */
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
